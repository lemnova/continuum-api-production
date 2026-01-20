package tech.lemnova.continuum_backend.subscription;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(
        StripeWebhookController.class
    );

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final SubscriptionService subscriptionService;

    public StripeWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        Event event;

        logger.info("Received Stripe webhook with signature");

        // ✅ VERIFICAR ASSINATURA
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            logger.info("Webhook signature verified successfully");
        } catch (SignatureVerificationException e) {
            logger.error("Invalid webhook signature: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error parsing webhook: {}", e.getMessage());
            return ResponseEntity.status(400).body(
                "Webhook error: " + e.getMessage()
            );
        }

        // ✅ PROCESSAR EVENTO
        String eventType = event.getType();
        String eventId = event.getId();

        logger.info(
            "Processing webhook event: {} | ID: {}",
            eventType,
            eventId
        );

        try {
            EventDataObjectDeserializer dataObjectDeserializer =
                event.getDataObjectDeserializer();
            StripeObject stripeObject = null;

            if (dataObjectDeserializer.getObject().isPresent()) {
                stripeObject = dataObjectDeserializer.getObject().get();
            } else {
                logger.warn(
                    "Failed to deserialize event data for event {}",
                    eventId
                );
                return ResponseEntity.ok(
                    "Event data could not be deserialized"
                );
            }

            // Processar eventos relevantes
            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(
                        eventId,
                        eventType,
                        stripeObject
                    );
                    break;
                case "customer.subscription.created":
                case "customer.subscription.updated":
                case "customer.subscription.deleted":
                    handleSubscriptionEvent(eventId, eventType, stripeObject);
                    break;
                case "invoice.payment_failed":
                case "invoice.payment_succeeded":
                    handleInvoiceEvent(eventId, eventType, stripeObject);
                    break;
                default:
                    logger.info("Unhandled event type: {}", eventType);
            }

            logger.info("Webhook event {} processed successfully", eventId);
            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            logger.error(
                "Error processing webhook event {}: {}",
                eventId,
                e.getMessage(),
                e
            );
            // Retornar 200 para evitar retry infinito do Stripe
            return ResponseEntity.ok("Error logged: " + e.getMessage());
        }
    }

    private void handleCheckoutSessionCompleted(
        String eventId,
        String eventType,
        StripeObject stripeObject
    ) {
        com.stripe.model.checkout.Session session =
            (com.stripe.model.checkout.Session) stripeObject;
        String subscriptionId = session.getSubscription();

        logger.info("Checkout completed for subscription: {}", subscriptionId);

        if (subscriptionId != null) {
            subscriptionService.handleWebhookEvent(
                eventId,
                eventType,
                subscriptionId
            );
        } else {
            logger.warn(
                "Checkout session {} has no subscription ID",
                session.getId()
            );
        }
    }

    private void handleSubscriptionEvent(
        String eventId,
        String eventType,
        StripeObject stripeObject
    ) {
        com.stripe.model.Subscription subscription =
            (com.stripe.model.Subscription) stripeObject;
        String subscriptionId = subscription.getId();

        logger.info(
            "Subscription event {} for subscription: {}",
            eventType,
            subscriptionId
        );

        subscriptionService.handleWebhookEvent(
            eventId,
            eventType,
            subscriptionId
        );
    }

    private void handleInvoiceEvent(
        String eventId,
        String eventType,
        StripeObject stripeObject
    ) {
        com.stripe.model.Invoice invoice =
            (com.stripe.model.Invoice) stripeObject;
        String subscriptionId = invoice.getSubscription();

        if (subscriptionId != null) {
            logger.info(
                "Invoice event {} for subscription: {}",
                eventType,
                subscriptionId
            );
            subscriptionService.handleWebhookEvent(
                eventId,
                eventType,
                subscriptionId
            );
        } else {
            logger.warn("Invoice {} has no subscription ID", invoice.getId());
        }
    }
}
