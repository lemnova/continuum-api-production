package tech.lemnova.continuum.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "entities")
public class Entity {

    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed
    private String vaultId;
    @Indexed
    private String title;
    private EntityType type;
    private String description;
    private String fileKey;
    private Instant createdAt;
    @Builder.Default
    private List<LocalDate> trackingDates = new ArrayList<>();

    // Método solicitado pelo TrackingService
    public boolean isTrackable() {
        return type == EntityType.HABIT || type == EntityType.PROJECT;
    }
}