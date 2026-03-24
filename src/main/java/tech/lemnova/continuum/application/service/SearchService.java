package tech.lemnova.continuum.application.service;

import org.springframework.stereotype.Service;
import tech.lemnova.continuum.controller.dto.search.SearchResponseDTO;
import tech.lemnova.continuum.controller.dto.search.SearchResultEntityDTO;
import tech.lemnova.continuum.controller.dto.search.SearchResultNoteDTO;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.note.Note;
import tech.lemnova.continuum.infra.persistence.EntityRepository;
import tech.lemnova.continuum.infra.persistence.NoteRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final NoteRepository noteRepo;
    private final EntityRepository entityRepo;

    public SearchService(NoteRepository noteRepo, EntityRepository entityRepo) {
        this.noteRepo = noteRepo;
        this.entityRepo = entityRepo;
    }

    public SearchResponseDTO search(String userId, String query) {
        String lowerQuery = query.toLowerCase().trim();

        // Buscar notas onde title ou content contenham a query (case-insensitive)
        List<SearchResultNoteDTO> notes = noteRepo.findByUserId(userId).stream()
            .filter(note -> 
                note.getTitle().toLowerCase().contains(lowerQuery) ||
                (note.getContent() != null && note.getContent().toLowerCase().contains(lowerQuery))
            )
            .map(note -> new SearchResultNoteDTO(
                note.getId(),
                note.getTitle(),
                note.getCreatedAt(),
                note.getUpdatedAt()
            ))
            .collect(Collectors.toList());

        // Buscar entidades onde title contenha a query (case-insensitive)
        List<SearchResultEntityDTO> entities = entityRepo.findByUserId(userId).stream()
            .filter(entity -> entity.getTitle().toLowerCase().contains(lowerQuery))
            .map(entity -> new SearchResultEntityDTO(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getType()
            ))
            .collect(Collectors.toList());

        return new SearchResponseDTO(notes, entities);
    }
}
