package tech.lemnova.continuum_backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.lemnova.continuum_backend.auth.dtos.AuthResponseDTO;
import tech.lemnova.continuum_backend.auth.dtos.LoginDTO;
import tech.lemnova.continuum_backend.auth.emailToken.EmailVerificationService;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.subscription.PlanType;
import tech.lemnova.continuum_backend.subscription.SubscriptionService;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user123");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole("USER");
        testUser.setActive(true);
        testUser.setPlanType(PlanType.FREE);
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUserSuccessfully() {
        String username = "newuser";
        String email = "new@example.com";
        String password = "password123";
        String token = "jwt.token.here";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn(token);

        AuthResponseDTO response = authService.register(username, email, password);

        assertNotNull(response);
        assertEquals(token, response.token());
        assertEquals(testUser.getId(), response.userId());
        assertEquals(PlanType.FREE, response.planType());

        verify(subscriptionService).createFreeSubscription(testUser.getId());
        verify(emailVerificationService).createAndSendVerificationToken(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when username already exists")
    void shouldThrowExceptionWhenUsernameExists() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> 
            authService.register("existing", "new@example.com", "password123")
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> 
            authService.register("newuser", "existing@example.com", "password123")
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginDTO loginDTO = new LoginDTO("test@example.com", "password123");
        String token = "jwt.token.here";

        when(userRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginDTO.password(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(testUser.getId(), testUser.getUsername())).thenReturn(token);

        AuthResponseDTO response = authService.login(loginDTO);

        assertNotNull(response);
        assertEquals(token, response.token());
        assertEquals(testUser.getId(), response.userId());
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        LoginDTO loginDTO = new LoginDTO("notfound@example.com", "password123");
        when(userRepository.findByEmail(loginDTO.email())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> authService.login(loginDTO));
    }

    @Test
    @DisplayName("Should throw exception when password is invalid")
    void shouldThrowExceptionWhenPasswordInvalid() {
        LoginDTO loginDTO = new LoginDTO("test@example.com", "wrongpassword");

        when(userRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginDTO.password(), testUser.getPassword())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.login(loginDTO));
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }
}
