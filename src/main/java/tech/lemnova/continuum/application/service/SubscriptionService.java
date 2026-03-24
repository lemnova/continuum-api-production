package tech.lemnova.continuum.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum.application.exception.BadRequestException;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.controller.dto.subscription.CheckoutResponse;
import tech.lemnova.continuum.controller.dto.subscription.SubscriptionDTO;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.plan.PlanType;
import tech.lemnova.continuum.domain.stripe.StripeEventLog;
import tech.lemnova.continuum.domain.stripe.StripeEventLogRepository;
import tech.lemnova.continuum.domain.subscription.Subscription;
import tech.lemnova.continuum.domain.subscription.SubscriptionRepository;
import tech.lemnova.continuum.domain.subscription.SubscriptionStatus;
import tech.lemnova.continuum.domain.user.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final int GRACE_PERIOD_DAYS = 7;

    private final SubscriptionRepository subRepo;
    private final UserRepository userRepo;
    private final StripeEventLogRepository eventLog;
    private final PlanConfiguration planConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${stripe.price-id-plus:}")   private String priceIdPlus;
    @Value("${stripe.price-id-pro:}")    private String priceIdPro;
    @Value("${stripe.price-id-vision:}") private String priceIdVision;
    @Value("${app.url}")                 private String appUrl;

    public SubscriptionService(SubscriptionRepository subRepo,
                                UserRepository userRepo,
                                StripeEventLogRepository eventLog,
                                PlanConfiguration planConfig) {
        this.subRepo    = subRepo;
        this.userRepo   = userRepo;
        this.eventLog   = eventLog;
        this.planConfig = planConfig;
    }

    public CheckoutResponse createCheckout(String userId, String email, String priceId) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(appUrl + "/dashboard?upgraded=true")
                    .setCancelUrl(appUrl + "/pricing")
                    .setCustomerEmail(email)
                    .putMetadata("userId", userId)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId).setQuantity(1L).build())
                    .build();
            Session session = Session.create(params);
            return new CheckoutResponse(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe checkout failed: {}", e.getMessage());
            throw new RuntimeException("Could not create checkout session: " + e.getMessage());
        }
    }

    public SubscriptionDTO getSubscription(String userId) {
        Subscription sub = subRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No subscription found"));
        return SubscriptionDTO.from(sub, planConfig);
    }

    @Transactional
    public SubscriptionDTO cancel(String userId) {
        Subscription sub = subRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No subscription found"));
        if (sub.getStripeSubscriptionId() == null)
            throw new BadRequestException("No active paid subscription to cancel");
        try {
            com.stripe.model.Subscription stripeSub =
                    com.stripe.model.Subscription.retrieve(sub.getStripeSubscriptionId());
            stripeSub.update(com.stripe.param.SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true).build());
            sub.setCancelAtPeriodEnd(true);
            sub.setUpdatedAt(Instant.now());
            subRepo.save(sub);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to cancel subscription: " + e.getMessage());
        }
        return SubscriptionDTO.from(sub, planConfig);
    }

    @Transactional
    public void handleCheckoutCompleted(Event event) {
        if (isProcessed(event.getId())) return;
        try {
            JsonNode node     = mapper.readTree(event.getData().getObject().toJson());
            String userId     = node.path("metadata").path("userId").asText(null);
            String subId      = node.path("subscription").asText(null);
            String customerId = node.path("customer").asText(null);
            if (userId == null || subId == null) {
                log.warn("[CHECKOUT] Missing userId or subscriptionId in event {}", event.getId());
            } else {
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subId);
                syncFromStripe(userId, customerId, stripeSub);
            }
            markProcessed(event, subId, customerId);
        } catch (Exception e) { log.error("[CHECKOUT] Error: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        if (isProcessed(event.getId())) return;
        try {
            JsonNode node     = mapper.readTree(event.getData().getObject().toJson());
            String subId      = node.path("id").asText(null);
            String customerId = node.path("customer").asText(null);
            Subscription local = subRepo.findByStripeSubscriptionId(subId).orElse(null);
            if (local != null) {
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subId);
                syncFromStripe(local.getUserId(), customerId, stripeSub);
            }
            markProcessed(event, subId, customerId);
        } catch (Exception e) { log.error("[SUB_UPDATED] Error: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        if (isProcessed(event.getId())) return;
        try {
            JsonNode node     = mapper.readTree(event.getData().getObject().toJson());
            String subId      = node.path("id").asText(null);
            String customerId = node.path("customer").asText(null);
            Subscription local = subRepo.findByStripeSubscriptionId(subId).orElse(null);
            if (local != null) {
                local.setStatus(SubscriptionStatus.CANCELED);
                local.setPlanType(PlanType.FREE);
                local.setUpdatedAt(Instant.now());
                subRepo.save(local);
                userRepo.findById(local.getUserId()).ifPresent(u -> { u.syncPlan(PlanType.FREE); userRepo.save(u); });
            }
            markProcessed(event, subId, customerId);
        } catch (Exception e) { log.error("[SUB_DELETED] Error: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    @Transactional
    public void handlePaymentSucceeded(Event event) {
        if (isProcessed(event.getId())) return;
        try {
            JsonNode node     = mapper.readTree(event.getData().getObject().toJson());
            String subId      = node.path("subscription").asText(null);
            String customerId = node.path("customer").asText(null);
            if (subId != null) {
                Subscription local = subRepo.findByStripeSubscriptionId(subId).orElse(null);
                if (local != null) {
                    com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subId);
                    syncFromStripe(local.getUserId(), customerId, stripeSub);
                }
            }
            markProcessed(event, subId, customerId);
        } catch (Exception e) { log.error("[PAYMENT_OK] Error: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    @Transactional
    public void handlePaymentFailed(Event event) {
        if (isProcessed(event.getId())) return;
        try {
            JsonNode node     = mapper.readTree(event.getData().getObject().toJson());
            String subId      = node.path("subscription").asText(null);
            String customerId = node.path("customer").asText(null);
            if (subId != null) {
                Subscription local = subRepo.findByStripeSubscriptionId(subId).orElse(null);
                if (local != null) {
                    Instant gracePeriodEnd = local.getCurrentPeriodEnd().plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
                    local.setStatus(SubscriptionStatus.PAST_DUE);
                    if (Instant.now().isAfter(gracePeriodEnd)) {
                        local.setPlanType(PlanType.FREE);
                        userRepo.findById(local.getUserId()).ifPresent(u -> { u.syncPlan(PlanType.FREE); userRepo.save(u); });
                        log.warn("[PAYMENT_FAILED] User {} downgraded after grace period", local.getUserId());
                    } else {
                        log.warn("[PAYMENT_FAILED] User {} in grace period (ends {})", local.getUserId(), gracePeriodEnd);
                    }
                    subRepo.save(local);
                }
            }
            markProcessed(event, subId, customerId);
        } catch (Exception e) { log.error("[PAYMENT_FAILED] Error: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void syncFromStripe(String userId, String customerId,
                                 com.stripe.model.Subscription stripeSub) {
        String priceId = stripeSub.getItems().getData().get(0).getPrice().getId();
        PlanType plan  = determinePlan(priceId);
        SubscriptionStatus status = mapStatus(stripeSub.getStatus());

        Subscription sub = subRepo.findByUserId(userId).orElse(new Subscription());
        sub.setUserId(userId);
        sub.setStripeSubscriptionId(stripeSub.getId());
        sub.setStripePriceId(priceId);
        sub.setPlanType(plan);
        sub.setStatus(status);
        sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()));
        sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
        sub.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());
        if (stripeSub.getCancelAt() != null)
            sub.setCancelAt(Instant.ofEpochSecond(stripeSub.getCancelAt()));
        if (sub.getCreatedAt() == null) sub.setCreatedAt(Instant.now());
        sub.setUpdatedAt(Instant.now());
        subRepo.save(sub);

        userRepo.findById(userId).ifPresent(user -> {
            if (customerId != null && user.getStripeCustomerId() == null)
                user.setStripeCustomerId(customerId);
            user.syncPlan(sub.getEffectivePlan());
            userRepo.save(user);
        });
        log.info("[SYNC] user={} plan={} status={}", userId, plan, status);
    }

    private PlanType determinePlan(String priceId) {
        if (priceId.equals(priceIdVision)) return PlanType.VISION;
        if (priceId.equals(priceIdPro))    return PlanType.PRO;
        if (priceId.equals(priceIdPlus))   return PlanType.PLUS;
        return PlanType.FREE;
    }

    private SubscriptionStatus mapStatus(String s) {
        return switch (s) {
            case "active"   -> SubscriptionStatus.ACTIVE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "unpaid"   -> SubscriptionStatus.UNPAID;
            default         -> SubscriptionStatus.INCOMPLETE;
        };
    }

    private boolean isProcessed(String eventId) { return eventLog.existsByEventId(eventId); }

    private void markProcessed(Event event, String subId, String customerId) {
        if (eventLog.existsByEventId(event.getId())) return;
        StripeEventLog entry = new StripeEventLog();
        entry.setEventId(event.getId());
        entry.setEventType(event.getType());
        entry.setSubscriptionId(subId);
        entry.setCustomerId(customerId);
        entry.setProcessedAt(Instant.now());
        eventLog.save(entry);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTROLLER DTOs
// ─────────────────────────────────────────────────────────────────────────────
