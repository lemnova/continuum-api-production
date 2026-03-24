package tech.lemnova.continuum.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.lemnova.continuum.application.service.EntityService;
import tech.lemnova.continuum.application.service.NoteService;
import tech.lemnova.continuum.controller.dto.graph.GraphDTO;
import tech.lemnova.continuum.controller.dto.graph.LinkDTO;
import tech.lemnova.continuum.controller.dto.graph.NodeDTO;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.note.Note;
import tech.lemnova.continuum.infra.security.CustomUserDetails;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final NoteService noteService;
    private final EntityService entityService;

    public GraphController(NoteService noteService, EntityService entityService) {
        this.noteService = noteService;
        this.entityService = entityService;
    }

    @GetMapping("/data")
    public ResponseEntity<GraphDTO> getGraphData(@AuthenticationPrincipal CustomUserDetails user) {
        String userId = user.getUserId();

        // Buscar apenas os dados necessários para o grafo (id, title, entityIds).
        // Usa uma query otimizada que não carrega o campo content para economizar memória e banda.
        List<Note> notes = noteService.listByUserForGraph();

        // Buscar apenas os dados necessários das entidades (id, title).
        // Usa uma query otimizada que não carrega campos desnecessários para economizar memória e banda.
        List<Entity> entities = entityService.listByUserForGraph(userId);

        // Criar nós das notas
        List<NodeDTO> nodes = new ArrayList<>();
        for (Note note : notes) {
            nodes.add(new NodeDTO(note.getId(), note.getTitle(), "NOTE"));
        }

        // Criar nós das entidades
        for (Entity entity : entities) {
            nodes.add(new NodeDTO(entity.getId(), entity.getTitle(), "ENTITY"));
        }

        // Criar links (conexões entre notas e entidades)
        List<LinkDTO> links = new ArrayList<>();
        for (Note note : notes) {
            if (note.getEntityIds() != null && !note.getEntityIds().isEmpty()) {
                for (String entityId : note.getEntityIds()) {
                    links.add(new LinkDTO(note.getId(), entityId));
                }
            }
        }

        return ResponseEntity.ok(new GraphDTO(nodes, links));
    }
}
