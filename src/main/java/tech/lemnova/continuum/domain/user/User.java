package tech.lemnova.continuum.domain.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.lemnova.continuum.domain.plan.PlanType;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Builder.Default
    private String role = "USER";

    @Builder.Default
    private Boolean active = false;

    private String googleId;

    private String avatarUrl;

    // Email verification fields
    @Builder.Default
    private boolean emailVerified = false;

    private String verificationToken;
    private Instant tokenExpiry;

    @Indexed(unique = true, sparse = true)
    private String stripeCustomerId;

    @Indexed(unique = true)
    private String vaultId;

    @Builder.Default
    private PlanType plan = PlanType.FREE;

    @Builder.Default
    private int entityCount = 0;

    @Builder.Default
    private int noteCount = 0;

    @Builder.Default
    private int habitCount = 0;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant lastLogoutAt;

    public void syncPlan(PlanType newPlan) {
        this.plan = newPlan;
        this.updatedAt = Instant.now();
    }

    public void incrementEntityCount() {
        this.entityCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementEntityCount() {
        this.entityCount = Math.max(0, this.entityCount - 1);
        this.updatedAt = Instant.now();
    }

    public void incrementNoteCount() {
        this.noteCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementNoteCount() {
        this.noteCount = Math.max(0, this.noteCount - 1);
        this.updatedAt = Instant.now();
    }

    public void incrementHabitCount() {
        this.habitCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementHabitCount() {
        this.habitCount = Math.max(0, this.habitCount - 1);
        this.updatedAt = Instant.now();
    }

}
