// User.java - ATUALIZADO
package tech.lemnova.continuum_backend.user;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.lemnova.continuum_backend.subscription.PlanType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String role = "USER"; // ✅ Role real (não plan)

    private Boolean active = false;

    // ✅ CACHE: Sincronizado via webhook (não é fonte de verdade)
    private PlanType planType = PlanType.FREE;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
