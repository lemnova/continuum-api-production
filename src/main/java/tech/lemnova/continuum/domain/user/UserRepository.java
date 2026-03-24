package tech.lemnova.continuum.domain.user;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByStripeCustomerId(String stripeCustomerId);
    Optional<User> findByVerificationToken(String token);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
