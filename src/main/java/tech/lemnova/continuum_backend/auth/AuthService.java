package tech.lemnova.continuum_backend.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum_backend.auth.dtos.AuthResponseDTO;
import tech.lemnova.continuum_backend.auth.dtos.LoginDTO;
import tech.lemnova.continuum_backend.auth.emailToken.EmailVerificationService;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.subscription.PlanType;
import tech.lemnova.continuum_backend.subscription.SubscriptionService;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final SubscriptionService subscriptionService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        EmailVerificationService emailVerificationService,
        SubscriptionService subscriptionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public AuthResponseDTO register(String username, String email, String password) {
        logger.info("Registering new user: {}", username);

        if (userRepository.existsByUsername(username)) {
            logger.warn("Registration failed: username {} already exists", username);
            throw new BadRequestException("Username already exists"); // ✅ CORRIGIDO
        }

        if (userRepository.existsByEmail(email)) {
            logger.warn("Registration failed: email {} already exists", email);
            throw new BadRequestException("Email already exists"); // ✅ CORRIGIDO
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setActive(false);
        user.setPlanType(PlanType.FREE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        User savedUser = userRepository.save(user);

        logger.info("User {} created successfully", savedUser.getId());

        // Criar assinatura FREE
        subscriptionService.createFreeSubscription(savedUser.getId());

        // Enviar email de verificação
        emailVerificationService.createAndSendVerificationToken(savedUser);

        String token = jwtService.generateToken(
            savedUser.getId(),
            savedUser.getUsername()
        );

        logger.info("Registration completed for user {}", savedUser.getId());

        return new AuthResponseDTO(
            token,
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail(),
            savedUser.getPlanType()
        );
    }

    public AuthResponseDTO login(LoginDTO dto) {
        logger.info("Login attempt for email: {}", dto.email());

        User user = userRepository
            .findByEmail(dto.email())
            .orElseThrow(() -> {
                logger.warn("Login failed: user not found for email {}", dto.email());
                return new BadRequestException("Invalid credentials"); // ✅ CORRIGIDO
            });

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            logger.warn("Login failed: invalid password for user {}", user.getId());
            throw new BadRequestException("Invalid credentials"); // ✅ CORRIGIDO
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername());

        logger.info("Login successful for user {}", user.getId());

        return new AuthResponseDTO(
            token,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPlanType()
        );
    }

    public void verifyEmail(String token) {
        logger.info("Email verification attempt with token");
        emailVerificationService.verifyToken(token);
        logger.info("Email verified successfully");
    }
}
