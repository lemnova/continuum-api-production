package tech.lemnova.continuum.domain.tracking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackingEvent {

    private String id;
    private String userId;
    private String entityId;
    private LocalDate date;
    private Integer value;
    @JsonAlias("numericValue")
    private Double decimalValue;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    @JsonIgnore
    public Number getNumericValue() {
        return decimalValue != null ? decimalValue : (value != null ? value : 0);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOMAIN — plan
// ─────────────────────────────────────────────────────────────────────────────
