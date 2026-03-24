package tech.lemnova.continuum.controller.dto.note;

import tech.lemnova.continuum.domain.note.Note;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record NoteResponse(
    String id, String userId, String folderId, String title, List<String> entityIds, String content,
    Instant createdAt, Instant updatedAt
) {
    public static NoteResponse from(Note note, String content) {
        return new NoteResponse(
            note.getId(), note.getUserId(), null,
            note.getTitle(), note.getEntityIds() != null ? note.getEntityIds() : Collections.emptyList(), content,
            note.getCreatedAt(), note.getUpdatedAt());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
