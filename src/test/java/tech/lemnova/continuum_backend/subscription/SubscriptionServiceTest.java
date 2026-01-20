package tech.lemnova.continuum_backend.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.subscription.dtos.CheckoutSessionDTO;
import tech.lemnova.continuum_backend.subscription.dtos.SubscriptionDTO;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StripeService stripeService;

    @Mock
    private StripeEventRepository stripeEventRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User testUser;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user123");
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");

        testSubscription = new Subscription();
        testSubscription.setId("sub123");
        testSubscription.setUserId("user123");
        testSubscription.setPlanType(PlanType.FREE);
        testSubscription.setStatus(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should create FREE subscription successfully")
    void shouldCreateFreeSubscriptionSuccessfully() {
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);

        Subscription result = subscriptionService.createFreeSubscription("user123");

        assertNotNull(result);
        assertEquals(PlanType.FREE, result.getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("Should get user subscription")
    void shouldGetUserSubscription() {
        when(subscriptionRepository.findByUserId("user123"))
            .thenReturn(Optional.of(testSubscription));

        SubscriptionDTO result = subscriptionService.getUserSubscription("user123");

        assertNotNull(result);
        assertEquals("user123", result.userId());
        assertEquals(PlanType.FREE, result.planType());
    }

    @Test
    @DisplayName("Should create FREE subscription if not exists")
    void shouldCreateFreeSubscriptionIfNotExists() {
        when(subscriptionRepository.findByUserId("user123")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);

        SubscriptionDTO result = subscriptionService.getUserSubscription("user123");

        assertNotNull(result);
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("Should create PRO checkout session successfully")
    void shouldCreateProCheckoutSessionSuccessfully() throws StripeException {
        Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_123");

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/...");

        when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.findByUserId("user123"))
            .thenReturn(Optional.of(testSubscription));
        when(stripeService.createCustomer(any(User.class))).thenReturn(mockCustomer);
        when(stripeService.createCheckoutSession(anyString(), anyString()))
            .thenReturn(mockSession);

        CheckoutSessionDTO result = subscriptionService.createProCheckoutSession(
            "user123", "test@example.com"
        );

        assertNotNull(result);
        assertEquals("cs_123", result.sessionId());
        assertNotNull(result.url());
    }

    @Test
    @DisplayName("Should throw exception when creating checkout for existing PRO user")
    void shouldThrowExceptionWhenCreatingCheckoutForProUser() {
        testSubscription.setPlanType(PlanType.PRO);

        when(userRepository.findById("user123")).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.findByUserId("user123"))
            .thenReturn(Optional.of(testSubscription));

        assertThrows(BadRequestException.class, () -> 
            subscriptionService.createProCheckoutSession("user123", "test@example.com")
        );
    }

    @Test
    @DisplayName("Should check habit limit correctly for FREE plan")
    void shouldCheckHabitLimitForFreePlan() {
        when(subscriptionRepository.findByUserId("user123"))
            .thenReturn(Optional.of(testSubscription));

        boolean canCreate = subscriptionService.canCreateHabit("user123", 4);
        assertTrue(canCreate);

        boolean cannotCreate = subscriptionService.canCreateHabit("user123", 5);
        assertFalse(cannotCreate);
    }

    @Test
    @DisplayName("Should allow unlimited habits for PRO plan")
    void shouldAllowUnlimitedHabitsForProPlan() {
        testSubscription.setPlanType(PlanType.PRO);

        when(subscriptionRepository.findByUserId("user123"))
            .thenReturn(Optional.of(testSubscription));

        boolean canCreate = subscriptionService.canCreateHabit("user123", 100);
        assertTrue(canCreate);
    }

    @Test
    @DisplayName("Should handle webhook event idempotently")
    void shouldHandleWebhookEventIdempotently() {
        when(stripeEventRepository.existsByEventId("evt_123")).thenReturn(true);

        assertDoesNotThrow(() -> 
            subscriptionService.handleWebhookEvent("evt_123", "customer.subscription.created", "sub_123")
        );

        verify(subscriptionRepository, never()).save(any());
    }
}
