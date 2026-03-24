package tech.lemnova.continuum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum.application.service.NoteService;
import tech.lemnova.continuum.controller.dto.note.NoteCreateRequest;
import tech.lemnova.continuum.controller.dto.note.NoteResponse;
import tech.lemnova.continuum.controller.dto.note.NoteSummaryDTO;
import tech.lemnova.continuum.controller.dto.note.NoteUpdateRequest;
import tech.lemnova.continuum.infra.security.CustomUserDetails;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "Notes", description = "Endpoints for managing user notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) { this.noteService = noteService; }

    @PostMapping
    @Operation(summary = "Create a new note", description = "Creates a new note with the provided title and content. Content is automatically sanitized for security.")
    public ResponseEntity<NoteResponse> create(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody NoteCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(req));
    }

    @GetMapping
    @Operation(summary = "List all notes", description = "Retrieves a list of all notes for the authenticated user")
    public ResponseEntity<List<NoteSummaryDTO>> list(@AuthenticationPrincipal CustomUserDetails user) {
        List<NoteSummaryDTO> summaries = noteService.listByUser().stream()
            .map(NoteSummaryDTO::from).toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get note by ID", description = "Retrieves a specific note with full content by its ID")
    public ResponseEntity<NoteResponse> get(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String id) {
        return ResponseEntity.ok(noteService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a note", description = "Updates the title and/or content of an existing note")
    public ResponseEntity<NoteResponse> update(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String id,
            @RequestBody NoteUpdateRequest req) {
        return ResponseEntity.ok(noteService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a note", description = "Permanently deletes a note and removes it from associated entities")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String id) {
        noteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}