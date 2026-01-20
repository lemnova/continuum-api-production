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
@Document(collection = "stripe_events")
public class StripeEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId; // ID do evento do Stripe

    private String eventType;

    private String subscriptionId;

    private String customerId;

    private boolean processed = false;

    private Instant processedAt;

    private Instant createdAt = Instant.now();
}
