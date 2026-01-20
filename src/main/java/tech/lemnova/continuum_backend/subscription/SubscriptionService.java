package tech.lemnova.continuum_backend.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.exception.ResourceNotFoundException;
import tech.lemnova.continuum_backend.subscription.dtos.CheckoutSessionDTO;
import tech.lemnova.continuum_backend.subscription.dtos.SubscriptionDTO;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

import java.time.Instant;

@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;
    private final StripeEventRepository stripeEventRepository;

    public SubscriptionService(
        SubscriptionRepository subscriptionRepository,
        UserRepository userRepository,
        StripeService stripeService,
        StripeEventRepository stripeEventRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.stripeService = stripeService;
        this.stripeEventRepository = stripeEventRepository;
    }

    // Criar assinatura FREE para novo usuário
    @Transactional
    public Subscription createFreeSubscription(String userId) {
        logger.info("Creating FREE subscription for user {}", userId);

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanType(PlanType.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());

        Subscription saved = subscriptionRepository.save(subscription);

        logger.info("FREE subscription created successfully for user {}", userId);

        return saved;
    }

    // Buscar assinatura do usuário
    public SubscriptionDTO getUserSubscription(String userId) {
        logger.debug("Fetching subscription for user {}", userId);

        Subscription subscription = subscriptionRepository
            .findByUserId(userId)
            .orElseGet(() -> {
                logger.info("No subscription found for user {}, creating FREE", userId);
                return createFreeSubscription(userId);
            });

        return SubscriptionDTO.from(subscription);
    }

    // Criar sessão de checkout para upgrade PRO
    @Transactional
    public CheckoutSessionDTO createProCheckoutSession(String userId, String email) {
        logger.info("User {} initiating PRO checkout", userId);

        User user = userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Subscription subscription = subscriptionRepository
            .findByUserId(userId)
            .orElseGet(() -> createFreeSubscription(userId));

        // Verificar se já é PRO ativo
        if (subscription.isPro()) {
            logger.warn("User {} already has active PRO subscription", userId);
            throw new BadRequestException("You already have an active PRO subscription");
        }

        try {
            // Criar ou usar customer existente
            String customerId = subscription.getStripeCustomerId();

            if (customerId == null) {
                logger.info("Creating Stripe customer for user {}", userId);
                Customer customer = stripeService.createCustomer(user);
                customerId = customer.getId();
                subscription.setStripeCustomerId(customerId);
                subscription.setUpdatedAt(Instant.now());
                subscriptionRepository.save(subscription);
                logger.info("Stripe customer {} created for user {}", customerId, userId);
            }

            // Criar checkout session
            Session session = stripeService.createCheckoutSession(customerId, userId);

            logger.info("Checkout session {} created for user {}", session.getId(), userId);

            return new CheckoutSessionDTO(session.getId(), session.getUrl());

        } catch (StripeException e) {
            logger.error("Error creating checkout session for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error creating checkout session: " + e.getMessage());
        }
    }

    // ⚠️ MÉTODO DEPRECIADO - Webhook é a fonte de verdade
    @Deprecated
    @Transactional
    public void processCheckoutSuccess(String sessionId) {
        logger.warn("DEPRECATED: processCheckoutSuccess called for session {}. Use webhook instead.", sessionId);
        // Mantido apenas para compatibilidade, mas não faz nada crítico
    }

    // Cancelar assinatura PRO
    @Transactional
    public SubscriptionDTO cancelSubscription(String userId) {
        logger.info("User {} canceling subscription", userId);

        Subscription subscription = subscriptionRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for user: " + userId));

        if (!subscription.isPro()) {
            logger.warn("User {} attempted to cancel non-PRO subscription", userId);
            throw new BadRequestException("You don't have a PRO subscription to cancel");
        }

        try {
            com.stripe.model.Subscription stripeSubscription =
                stripeService.cancelSubscription(subscription.getStripeSubscriptionId());

            subscription.setCancelAtPeriodEnd(true);
            subscription.setCancelAt(Instant.ofEpochSecond(stripeSubscription.getCancelAt()));
            subscription.setUpdatedAt(Instant.now());

            subscriptionRepository.save(subscription);

            logger.info("Subscription canceled for user {}. Will end at {}",
                        userId, subscription.getCancelAt());

            return SubscriptionDTO.from(subscription);

        } catch (StripeException e) {
            logger.error("Error canceling subscription for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error canceling subscription: " + e.getMessage());
        }
    }

    // Verificar se usuário pode criar mais hábitos
    public boolean canCreateHabit(String userId, int currentHabitsCount) {
        logger.debug("Checking habit limit for user {}. Current: {}", userId, currentHabitsCount);

        Subscription subscription = subscriptionRepository
            .findByUserId(userId)
            .orElseGet(() -> createFreeSubscription(userId));

        boolean canCreate = currentHabitsCount < subscription.getHabitsLimit();

        logger.debug("User {} can create habit: {} (limit: {})",
                     userId, canCreate, subscription.getHabitsLimit());

        return canCreate;
    }

    // ✅ PROCESSAR WEBHOOK COM IDEMPOTÊNCIA
    @Transactional
    public void handleWebhookEvent(String eventId, String eventType, String subscriptionId) {
        logger.info("Processing webhook event: {} | Type: {} | Subscription: {}",
                    eventId, eventType, subscriptionId);

        // ✅ IDEMPOTÊNCIA - Verificar se evento já foi processado
        if (stripeEventRepository.existsByEventId(eventId)) {
            logger.warn("Event {} already processed. Skipping.", eventId);
            return;
        }

        // Registrar evento
        StripeEvent stripeEvent = new StripeEvent();
        stripeEvent.setEventId(eventId);
        stripeEvent.setEventType(eventType);
        stripeEvent.setSubscriptionId(subscriptionId);
        stripeEvent.setProcessed(false);
        stripeEventRepository.save(stripeEvent);

        try {
            Subscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(subscriptionId)
                .orElseThrow(() -> {
                    logger.error("Subscription not found for Stripe subscription {}", subscriptionId);
                    return new ResourceNotFoundException("Subscription not found: " + subscriptionId);
                });

            User user = userRepository.findById(subscription.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            switch (eventType) {
                case "checkout.session.completed":
                case "customer.subscription.created":
                    handleSubscriptionCreated(subscription, user);
                    break;

                case "customer.subscription.updated":
                    handleSubscriptionUpdated(subscription, user);
                    break;

                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(subscription, user);
                    break;

                case "invoice.payment_failed":
                    handlePaymentFailed(subscription);
                    break;

                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(subscription, user);
                    break;

                default:
                    logger.info("Unhandled event type: {}", eventType);
            }

            // Marcar evento como processado
            stripeEvent.setProcessed(true);
            stripeEvent.setProcessedAt(Instant.now());
            stripeEventRepository.save(stripeEvent);

            logger.info("Event {} processed successfully", eventId);

        } catch (Exception e) {
            logger.error("Error processing webhook event {}: {}", eventId, e.getMessage(), e);
            throw e;
        }
    }

    private void handleSubscriptionCreated(Subscription subscription, User user) {
        logger.info("Activating PRO subscription for user {}", user.getId());

        subscription.setPlanType(PlanType.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        // Sincronizar com User
        user.setPlanType(PlanType.PRO);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        logger.info("PRO subscription activated for user {}", user.getId());
    }

    private void handleSubscriptionUpdated(Subscription subscription, User user) {
        logger.info("Updating subscription for user {}", user.getId());

        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        logger.info("Subscription updated for user {}", user.getId());
    }

    private void handleSubscriptionDeleted(Subscription subscription, User user) {
        logger.info("Deactivating PRO subscription for user {}", user.getId());

        subscription.setPlanType(PlanType.FREE);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        // Sincronizar com User
        user.setPlanType(PlanType.FREE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        logger.info("Subscription canceled. User {} downgraded to FREE", user.getId());
    }

    private void handlePaymentFailed(Subscription subscription) {
        logger.warn("Payment failed for subscription {}", subscription.getId());

        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        logger.warn("Subscription {} marked as PAST_DUE", subscription.getId());
    }

    private void handlePaymentSucceeded(Subscription subscription, User user) {
            logger.info("Payment succeeded for subscription {}", subscription.getId());

            // Reativar se estava PAST_DUE
            if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
                subscription.setStatus(SubscriptionStatus.ACTIVE);

                logger.info("Subscription {} reactivated after successful payment", subscription.getId());
            }

            subscription.setUpdatedAt(Instant.now());
            subscriptionRepository.save(subscription);

            // Garantir que User está em PRO
            if (user.getPlanType() != PlanType.PRO) {
                user.setPlanType(PlanType.PRO);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);

                logger.info("User {} plan synced to PRO", user.getId());
            }

            logger.info("Payment succeeded processed for user {}", user.getId());
        }
    }
