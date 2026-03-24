package tech.lemnova.continuum.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.lemnova.continuum.controller.dto.auth.RegisterRequest;
import tech.lemnova.continuum.controller.dto.auth.LoginRequest;
import tech.lemnova.continuum.domain.email.EmailVerificationToken;
import tech.lemnova.continuum.domain.email.EmailVerificationTokenRepository;
import tech.lemnova.continuum.domain.email.PasswordResetTokenRepository;
import tech.lemnova.continuum.domain.token.TokenBlacklistRepository;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.plan.PlanType;
import tech.lemnova.continuum.domain.subscription.SubscriptionRepository;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.email.EmailService;
import tech.lemnova.continuum.infra.security.JwtService;
import tech.lemnova.continuum.infra.vault.VaultStorageService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceUnitTest {

    @Mock
    UserRepository users;

    @Mock
    SubscriptionRepository subscriptions;

    @Mock
    EmailVerificationTokenRepository tokenRepo;

    @Mock
    PasswordResetTokenRepository passwordResetRepo;

    @Mock
    TokenBlacklistRepository tokenBlacklistRepo;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    EmailService emailService;

    @Mock
    VaultStorageService vaultStorage;

    @Mock
    PlanConfiguration planConfig;

    @InjectMocks
    AuthService authService;

    @Captor
    ArgumentCaptor<User> userCaptor;

    @BeforeEach
    void setup() {
        // nothing
    }

    @Test
    void register_createsUser_and_sendsVerification() {
        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "secret123");

        when(users.existsByEmail(req.email())).thenReturn(false);
        when(users.existsByUsername(req.username())).thenReturn(false);

        User saved = new User();
        saved.setId("u-1");
        saved.setUsername(req.username());
        saved.setEmail(req.email());
        saved.setActive(false);
        saved.setEmailVerified(false);

        when(passwordEncoder.encode(any())).thenReturn("encoded-pass");
        when(users.save(any())).thenReturn(saved);
        lenient().when(subscriptions.findAllByUserId(any())).thenReturn(null);
        lenient().doNothing().when(emailService).sendVerificationEmail(any(), any());

        authService.register(req);

        verify(users).save(userCaptor.capture());
        User u = userCaptor.getValue();
        assertThat(u.getUsername()).isEqualTo("alice");
        assertThat(u.isEmailVerified()).isFalse();
        assertThat(u.getVerificationToken()).isNotNull();
        verify(emailService).sendVerificationEmail(eq("alice@example.com"), any());
        verify(subscriptions).save(any());
    }

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        LoginRequest req = new LoginRequest("alice@example.com", "secret123");
        User user = new User();
        user.setId("u-2");
        user.setEmail(req.email());
        user.setPassword("encoded");
        user.setActive(true);
        user.setEmailVerified(true);

        when(users.findByEmail(req.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.password(), user.getPassword())).thenReturn(true);
        when(jwtService.generateFromUser(user)).thenReturn("tok-2");

        var resp = authService.login(req);
        assertThat(resp.token()).isEqualTo("tok-2");
    }

    @Test
    void verifyEmail_activatesUser_and_deletesToken() {
        String tokenValue = "t1";
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(tokenValue);
        verificationToken.setUserId("u-3");
        verificationToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));

        User user = new User();
        user.setId("u-3");
        user.setActive(false);
        user.setEmailVerified(false);

        when(tokenRepo.findByToken(tokenValue)).thenReturn(Optional.of(verificationToken));
        when(users.findById("u-3")).thenReturn(Optional.of(user));

        authService.verifyEmail(tokenValue);

        verify(users).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getActive()).isTrue();
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();

        verify(tokenRepo).delete(verificationToken);
    }
}
