package tech.lemnova.continuum.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum.application.service.SubscriptionService;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final SubscriptionService subscriptionService;

    public StripeWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripe(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sig) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sig, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        }

        log.info("Stripe event: {} [{}]", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed"    -> {
                    log.debug("Processing checkout.session.completed event");
                    subscriptionService.handleCheckoutCompleted(event);
                }
                case "customer.subscription.updated" -> {
                    log.debug("Processing customer.subscription.updated event");
                    subscriptionService.handleSubscriptionUpdated(event);
                }
                case "customer.subscription.deleted" -> {
                    log.debug("Processing customer.subscription.deleted event");
                    subscriptionService.handleSubscriptionDeleted(event);
                }
                case "invoice.payment_succeeded"     -> {
                    log.debug("Processing invoice.payment_succeeded event");
                    subscriptionService.handlePaymentSucceeded(event);
                }
                case "invoice.payment_failed"        -> {
                    log.debug("Processing invoice.payment_failed event");
                    subscriptionService.handlePaymentFailed(event);
                }
                default -> log.debug("Ignored Stripe event: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Webhook processing error for {}: {}", event.getId(), e.getMessage(), e);
        }

        return ResponseEntity.ok("ok");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// [ARCH-2] VaultController — GET /api/vault/entity-index
// ─────────────────────────────────────────────────────────────────────────────
