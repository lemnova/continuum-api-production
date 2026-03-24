package tech.lemnova.continuum.controller.dto.note;

import jakarta.validation.constraints.Size;

public record NoteUpdateRequest(
    @Size(max = 255, message = "Título não pode exceder 255 caracteres") String title,
    @Size(max = 50000, message = "Conteúdo não pode exceder 50.000 caracteres") String content,
    String folderId
) {}
