package tech.lemnova.continuum_backend.subscription;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "subscriptions")
public class Subscription {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private PlanType planType = PlanType.FREE;

    @Indexed
    private String stripeCustomerId;

    @Indexed
    private String stripeSubscriptionId;

    private String stripePriceId;

    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private Instant currentPeriodStart;

    private Instant currentPeriodEnd;

    private Instant cancelAt;

    private Boolean cancelAtPeriodEnd = false;

    // Metadata útil
    private String lastEventId; // Último evento processado

    private Instant lastEventProcessedAt;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    // Métodos auxiliares
    public boolean isActive() {
        return (
            status == SubscriptionStatus.ACTIVE &&
            (currentPeriodEnd == null ||
                currentPeriodEnd.isAfter(Instant.now()))
        );
    }

    public boolean isPro() {
        return planType == PlanType.PRO && isActive();
    }

    public int getHabitsLimit() {
        return planType.getHabitsLimit();
    }
}
