package tech.lemnova.continuum_backend.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum_backend.auth.CustomUserDetails;
import tech.lemnova.continuum_backend.subscription.dtos.CheckoutSessionDTO;
import tech.lemnova.continuum_backend.subscription.dtos.SubscriptionDTO;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(
        SubscriptionController.class
    );

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // ✅ GET /api/subscriptions/me
    @GetMapping("/me")
    public ResponseEntity<SubscriptionDTO> getMySubscription(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        logger.info("User {} fetching subscription", userDetails.getUserId());
        SubscriptionDTO subscription = subscriptionService.getUserSubscription(
            userDetails.getUserId()
        );
        return ResponseEntity.ok(subscription);
    }

    // ✅ POST /api/subscriptions/upgrade
    @PostMapping("/upgrade")
    public ResponseEntity<CheckoutSessionDTO> createProCheckout(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        logger.info("User {} initiating PRO upgrade", userDetails.getUserId());
        CheckoutSessionDTO session =
            subscriptionService.createProCheckoutSession(
                userDetails.getUserId(),
                userDetails.getEmail()
            );
        return ResponseEntity.ok(session);
    }

    // ✅ POST /api/subscriptions/cancel
    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionDTO> cancelMySubscription(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        logger.info("User {} canceling subscription", userDetails.getUserId());
        SubscriptionDTO subscription = subscriptionService.cancelSubscription(
            userDetails.getUserId()
        );
        return ResponseEntity.ok(subscription);
    }

    // ℹ️ GET /api/subscriptions/checkout/success?session_id=...
    // Endpoint INFORMATIVO - não faz lógica crítica, apenas confirma para o frontend
    @GetMapping("/checkout/success")
    public ResponseEntity<String> handleCheckoutSuccess(
        @RequestParam String session_id
    ) {
        logger.info(
            "Checkout success callback received for session {}",
            session_id
        );
        // Apenas retorna sucesso - webhook é quem processa
        return ResponseEntity.ok(
            "Checkout completed. Your subscription will be activated shortly."
        );
    }
}
