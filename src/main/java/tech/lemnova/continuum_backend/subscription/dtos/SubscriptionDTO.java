package tech.lemnova.continuum_backend.subscription.dtos;

import java.time.Instant;
import tech.lemnova.continuum_backend.subscription.PlanType;
import tech.lemnova.continuum_backend.subscription.Subscription;
import tech.lemnova.continuum_backend.subscription.SubscriptionStatus;

public record SubscriptionDTO(
    String id,
    String userId,
    PlanType planType,
    SubscriptionStatus status,
    Integer habitsLimit,
    Double price,
    Instant currentPeriodEnd,
    Boolean cancelAtPeriodEnd,
    Instant createdAt
) {
    public static SubscriptionDTO from(Subscription subscription) {
        return new SubscriptionDTO(
            subscription.getId(),
            subscription.getUserId(),
            subscription.getPlanType(),
            subscription.getStatus(),
            subscription.getPlanType().getHabitsLimit(),
            subscription.getPlanType().getPrice(),
            subscription.getCurrentPeriodEnd(),
            subscription.getCancelAtPeriodEnd(),
            subscription.getCreatedAt()
        );
    }
}
