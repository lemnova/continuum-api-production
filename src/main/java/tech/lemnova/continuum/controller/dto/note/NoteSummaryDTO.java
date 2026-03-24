package tech.lemnova.continuum.controller.dto.note;

import tech.lemnova.continuum.domain.note.Note;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record NoteSummaryDTO(
    String id,
    String userId,
    String title,
    List<String> entityIds,
    Instant createdAt,
    Instant updatedAt
) {
    public static NoteSummaryDTO from(Note note) {
        return new NoteSummaryDTO(
            note.getId(),
            note.getUserId(),
            note.getTitle(),
            note.getEntityIds() != null ? note.getEntityIds() : Collections.emptyList(),
            note.getCreatedAt(),
            note.getUpdatedAt()
        );
    }
}
