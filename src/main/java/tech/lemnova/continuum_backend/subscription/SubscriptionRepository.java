package tech.lemnova.continuum_backend.subscription;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository
    extends MongoRepository<Subscription, String>
{
    Optional<Subscription> findByUserId(String userId);
    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
    Optional<Subscription> findByStripeSubscriptionId(
        String stripeSubscriptionId
    );
}
