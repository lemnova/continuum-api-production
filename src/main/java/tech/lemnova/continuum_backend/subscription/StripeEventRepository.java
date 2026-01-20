package tech.lemnova.continuum_backend.subscription;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeEventRepository
    extends MongoRepository<StripeEvent, String>
{
    Optional<StripeEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
