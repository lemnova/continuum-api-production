package tech.lemnova.continuum.controller.dto.auth;

import tech.lemnova.continuum.domain.plan.PlanLimits;
import tech.lemnova.continuum.domain.plan.PlanType;
import tech.lemnova.continuum.domain.subscription.Subscription;
import tech.lemnova.continuum.domain.subscription.SubscriptionStatus;
import tech.lemnova.continuum.domain.user.User;

import java.time.Instant;

public record UserContextResponse(
    String id, String username, String email, String role, Boolean active,
    PlanType plan, SubscriptionStatus subscriptionStatus,
    int maxEntities, int maxNotes, int maxHabits, int historyDays,
    boolean advancedMetrics, boolean dataExport, boolean calendarSync,
    Instant subscriptionEndsAt, Boolean cancelAtPeriodEnd
) {
    public static UserContextResponse from(User user, Subscription sub, PlanLimits limits) {
        SubscriptionStatus status = sub != null ? sub.getStatus() : SubscriptionStatus.ACTIVE;
        return new UserContextResponse(
            user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getActive(),
            user.getPlan(), status,
            limits.maxEntities(), limits.maxNotes(), limits.maxHabits(), limits.maxHistoryDays(),
            limits.advancedMetrics(), limits.dataExport(), limits.calendarSync(),
            sub != null ? sub.getCurrentPeriodEnd() : null,
            sub != null ? sub.getCancelAtPeriodEnd() : false);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
