package tech.lemnova.continuum.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum.application.exception.BadRequestException;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.controller.dto.auth.AuthResponse;
import tech.lemnova.continuum.controller.dto.auth.LoginRequest;
import tech.lemnova.continuum.controller.dto.auth.RegisterRequest;
import tech.lemnova.continuum.controller.dto.auth.UserContextResponse;
import tech.lemnova.continuum.domain.email.EmailVerificationToken;
import tech.lemnova.continuum.domain.email.EmailVerificationTokenRepository;
import tech.lemnova.continuum.domain.email.PasswordResetToken;
import tech.lemnova.continuum.domain.email.PasswordResetTokenRepository;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.plan.PlanType;
import tech.lemnova.continuum.domain.subscription.Subscription;
import tech.lemnova.continuum.domain.subscription.SubscriptionRepository;
import tech.lemnova.continuum.domain.subscription.SubscriptionStatus;
import tech.lemnova.continuum.domain.token.TokenBlacklist;
import tech.lemnova.continuum.domain.token.TokenBlacklistRepository;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.email.EmailService;
import tech.lemnova.continuum.infra.security.JwtService;
import tech.lemnova.continuum.infra.vault.VaultStorageService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final EmailVerificationTokenRepository tokenRepo;
    private final PasswordResetTokenRepository passwordResetRepo;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final VaultStorageService vaultStorage;
    private final PlanConfiguration planConfig;

    public AuthService(UserRepository users,
                       SubscriptionRepository subscriptions,
                       EmailVerificationTokenRepository tokenRepo,
                       PasswordResetTokenRepository passwordResetRepo,
                       TokenBlacklistRepository tokenBlacklistRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       VaultStorageService vaultStorage,
                       PlanConfiguration planConfig) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.tokenRepo = tokenRepo;
        this.passwordResetRepo = passwordResetRepo;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.vaultStorage = vaultStorage;
        this.planConfig = planConfig;
    }

    @Transactional
    public void register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new BadRequestException("Email already in use");
        }

        String vaultId = UUID.randomUUID().toString().replace("-", "");
        String rawToken = UUID.randomUUID().toString();

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setVaultId(vaultId);
        user.setPlan(PlanType.FREE);
        user.setRole("USER");
        user.setActive(false);
        user.setEmailVerified(false);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        // Salva o usuário primeiro para garantir o ID para as outras entidades
        User savedUser = users.save(user);
        log.info("Usuário persistido no MongoDB. ID: {}", savedUser.getId());

        createFreeSubscription(savedUser.getId());

        // CORREÇÃO: Salva o token na coleção EmailVerificationToken (onde o verifyEmail busca)
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(rawToken)
                .userId(savedUser.getId())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        tokenRepo.save(verificationToken);

        initVaultAsync(vaultId);

        try {
            emailService.sendVerificationEmail(savedUser.getEmail(), rawToken);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de verificação: {}", e.getMessage());
        }
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        EmailVerificationToken verificationToken = tokenRepo.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token"));

        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            tokenRepo.delete(verificationToken);
            throw new BadRequestException("Verification token expired");
        }

        User user = users.findById(verificationToken.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setEmailVerified(true);
        user.setActive(true);
        user.setUpdatedAt(Instant.now());
        users.save(user);

        tokenRepo.delete(verificationToken);
        log.info("E-mail verificado para: {}", user.getEmail());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (user.isEmailVerified()) throw new BadRequestException("Email already verified");

        // Remove tokens antigos se existirem
        tokenRepo.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken ev = EmailVerificationToken.builder()
                .token(rawToken)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        
        tokenRepo.save(ev);
        try {
            emailService.sendVerificationEmail(user.getEmail(), rawToken);
        } catch (Exception e) {
            log.error("Erro ao reenviar e-mail: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPassword()))
            throw new BadRequestException("Invalid credentials");
        if (!user.isEmailVerified())
            throw new BadRequestException("Email not verified");
        return buildAuthResponseWithTokenPair(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        // Validar que é um Refresh Token
        if (!jwtService.isValid(refreshToken)) {
            throw new BadRequestException("Refresh token expired");
        }
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Invalid token type");
        }

        // Verificar blacklist
        String jti = jwtService.extractJti(refreshToken);
        if (jti != null && tokenBlacklistRepository.findByJti(jti).isPresent()) {
            throw new BadRequestException("Refresh token has been revoked");
        }

        String userId = jwtService.extractUserId(refreshToken);
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        
        // Revogar token antigo (Refresh Token Rotation)
        if (jti != null) {
            long expirationMs = jwtService.getTimeUntilExpiration(refreshToken);
            Instant expiresAt = Instant.now().plus(expirationMs, ChronoUnit.MILLIS);
            TokenBlacklist blacklistedToken = new TokenBlacklist(jti, userId, JwtService.TOKEN_TYPE_REFRESH, expiresAt);
            tokenBlacklistRepository.save(blacklistedToken);
            log.info("Refresh token rotated for user: {}", userId);
        }
        
        return buildAuthResponseWithTokenPair(user);
    }

    @Transactional
    public AuthResponse googleAuth(tech.lemnova.continuum.infra.google.GoogleOAuthService.GoogleUserInfo googleUser) {
        User user = users.findByEmail(googleUser.email()).orElse(null);
        if (user == null) {
            String vaultId = UUID.randomUUID().toString().replace("-", "");
            user = new User();
            user.setUsername(googleUser.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", ""));
            user.setEmail(googleUser.email());
            user.setGoogleId(googleUser.googleId());
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setActive(true);
            user.setEmailVerified(true);
            user.setRole("USER");
            user.setPlan(PlanType.FREE);
            user.setVaultId(vaultId);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            user = users.save(user);
            createFreeSubscription(user.getId());
            initVaultAsync(vaultId);
        } else if (user.getGoogleId() == null) {
            user.setGoogleId(googleUser.googleId());
            user.setActive(true);
            user.setEmailVerified(true);
            users.save(user);
        }
        return buildAuthResponseWithTokenPair(user);
    }

    /**
     * Revoga todos os tokens do usuário (logout).
     * Atualiza lastLogoutAt para invalidar tokens anteriores.
     */
    @Transactional
    public void logout(String userId, String accessToken, String refreshToken) {
        log.info("Logout for user: {}", userId);
        
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setLastLogoutAt(Instant.now());
        users.save(user);
        
        // Revogar Access Token se fornecido
        if (accessToken != null && jwtService.isValid(accessToken)) {
            String jti = jwtService.extractJti(accessToken);
            if (jti != null) {
                long expirationMs = jwtService.getTimeUntilExpiration(accessToken);
                Instant expiresAt = Instant.now().plus(expirationMs, ChronoUnit.MILLIS);
                TokenBlacklist blacklistEntry = new TokenBlacklist(jti, userId, JwtService.TOKEN_TYPE_ACCESS, expiresAt);
                tokenBlacklistRepository.save(blacklistEntry);
            }
        }
        
        // Revogar Refresh Token se fornecido
        if (refreshToken != null && jwtService.isValid(refreshToken)) {
            String jti = jwtService.extractJti(refreshToken);
            if (jti != null) {
                long expirationMs = jwtService.getTimeUntilExpiration(refreshToken);
                Instant expiresAt = Instant.now().plus(expirationMs, ChronoUnit.MILLIS);
                TokenBlacklist blacklistEntry = new TokenBlacklist(jti, userId, JwtService.TOKEN_TYPE_REFRESH, expiresAt);
                tokenBlacklistRepository.save(blacklistEntry);
            }
        }
    }

    /**
     * Versão simplificada de logout.
     * Atualiza lastLogoutAt para invalidar todos os tokens anteriores.
     */
    @Transactional
    public void logout(String userId) {
        log.info("Logout for user: {} (all tokens will be invalidated via lastLogoutAt)", userId);
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setLastLogoutAt(Instant.now());
        users.save(user);
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new BadRequestException("Current password incorrect");
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
    }

    @Transactional
    public void updateUsername(String userId, String username) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setUsername(username);
        user.setUpdatedAt(Instant.now());
        users.save(user);
    }

    @Transactional
    public void initiateEmailChange(String userId, String newEmail) {
        if (users.existsByEmail(newEmail)) throw new BadRequestException("Email in use");
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        
        String token = UUID.randomUUID().toString();
        EmailVerificationToken ev = EmailVerificationToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .newEmail(newEmail)
                .build();
        
        tokenRepo.save(ev);
        try { 
            emailService.sendEmailChangeVerification(newEmail, token); 
        } catch (Exception e) {
            log.error("Erro ao enviar verificação de troca de e-mail: {}", e.getMessage());
        }
    }

    public UserContextResponse getContext(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        List<Subscription> subs = subscriptions.findAllByUserId(userId);
        Subscription sub = (subs == null || subs.isEmpty()) ? null : subs.stream()
                .max(Comparator.comparing(s -> s.getCurrentPeriodEnd() == null ? Instant.EPOCH : s.getCurrentPeriodEnd()))
                .orElse(null);
        PlanType effectivePlan = sub != null ? sub.getEffectivePlan() : user.getPlan();
        return UserContextResponse.from(user, sub, planConfig.getLimits(effectivePlan));
    }

    @Async
    public void initVaultAsync(String vaultId) {
        try { 
            vaultStorage.initializeVault(vaultId); 
        } catch (Exception e) {
            log.error("Erro ao inicializar vault {}: {}", vaultId, e.getMessage());
        }
    }

    private void createFreeSubscription(String userId) {
        Subscription sub = Subscription.builder()
                .userId(userId).planType(PlanType.FREE).status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plus(36500, ChronoUnit.DAYS)).build();
        subscriptions.save(sub);
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = users.findByEmail(email).orElse(null);
        if (user == null) return;
        
        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        
        passwordResetRepo.save(token);
        try { 
            emailService.sendPasswordResetEmail(user.getEmail(), tokenValue); 
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de reset: {}", e.getMessage());
        }
    }

    @Transactional
    public void completePasswordReset(String tokenValue, String newPassword) {
        PasswordResetToken token = passwordResetRepo.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid token"));
        if (token.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("Token expired");
        
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        passwordResetRepo.delete(token);
    }

    /**
     * Constrói AuthResponse com TokenPair (Access + Refresh).
     */
    private AuthResponse buildAuthResponseWithTokenPair(User user) {
        JwtService.TokenPair tokens = jwtService.generateTokenPairFromUser(user);
        return AuthResponse.withTokenPair(
            tokens.accessToken(), 
            tokens.refreshToken(), 
            user.getId(), 
            user.getUsername(), 
            user.getEmail(), 
            user.getPlan()
        );
    }

    /**
     * Constrói AuthResponse com apenas 1 token (compatibilidade com código antigo).
     */
    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(jwtService.generateFromUser(user), user.getId(), user.getUsername(), user.getEmail(), user.getPlan());
    }
}