package tech.lemnova.continuum.application.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.application.exception.PlanLimitException;
import tech.lemnova.continuum.controller.dto.note.NoteCreateRequest;
import tech.lemnova.continuum.controller.dto.note.NoteResponse;
import tech.lemnova.continuum.controller.dto.note.NoteUpdateRequest;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.note.Note;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.persistence.NoteRepository;
import tech.lemnova.continuum.infra.persistence.EntityRepository;
import tech.lemnova.continuum.infra.security.CustomUserDetails;
import tech.lemnova.continuum.infra.vault.VaultStorageService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private final NoteRepository noteRepo;
    private final EntityRepository entityRepo;
    private final ExtractionService extractionService;
    private final VaultStorageService storageService;
    private final UserService userService;
    private final PlanConfiguration planConfig;
    private final UserRepository userRepo;

    public NoteService(NoteRepository noteRepo, EntityRepository entityRepo, ExtractionService extractionService, VaultStorageService storageService, UserService userService, PlanConfiguration planConfig, UserRepository userRepo) {
        this.noteRepo = noteRepo;
        this.entityRepo = entityRepo;
        this.extractionService = extractionService;
        this.storageService = storageService;
        this.userService = userService;
        this.planConfig = planConfig;
        this.userRepo = userRepo;
    }

    public NoteResponse create(NoteCreateRequest req) {
        String userId = getCurrentUserId();
        String vaultId = getCurrentVaultId();
        
        // Verificar limite de notas baseado no plano do usuário
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        long currentNoteCount = noteRepo.countByUserId(userId);
        if (!planConfig.canCreateNote(user.getPlan(), currentNoteCount)) {
            throw new PlanLimitException("Limite de notas atingido para seu plano. Atualize para uma assinatura superior.");
        }
        
        // Sanitizar conteúdo para prevenir XSS
        String content = req.content() != null ? sanitizeContent(req.content()) : "";
        String title = req.title() != null && !req.title().isBlank() ? req.title() : extractTitle(content);
        List<String> entityIds = findMatchingEntityIds(userId, content);

        // Gerar ID do MongoDB primeiro
        String noteId = UUID.randomUUID().toString();

        // Fazer upload para B2
        String fileKey = storageService.saveNoteContent(vaultId, noteId, content);

        // Se upload bem-sucedido, salvar no MongoDB
        Note note = new Note();
        note.setId(noteId);
        note.setUserId(userId);
        note.setTitle(title);
        note.setContent(content);
        note.setFileKey(fileKey);
        note.setEntityIds(entityIds);
        note.setCreatedAt(Instant.now());
        note.setUpdatedAt(Instant.now());

        note = noteRepo.save(note);

        userService.incrementNoteCount(userId);

        return NoteResponse.from(note, content);
    }

    public NoteResponse update(String noteId, NoteUpdateRequest req) {
        String userId = getCurrentUserId();
        String vaultId = getCurrentVaultId();
        Note note = noteRepo.findById(noteId)
            .filter(n -> n.getUserId().equals(userId))
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));

        // Sanitizar novo conteúdo para prevenir XSS
        String newContent = req.content() != null ? sanitizeContent(req.content()) : note.getContent();
        note.setTitle(req.title() != null ? req.title() : note.getTitle());
        note.setContent(newContent);
        note.setEntityIds(findMatchingEntityIds(userId, newContent));
        note.setUpdatedAt(Instant.now());

        // Fazer upload para B2 primeiro
        String fileKey = storageService.saveNoteContent(vaultId, noteId, newContent);
        note.setFileKey(fileKey);

        // Depois salvar no MongoDB com o fileKey atualizado
        noteRepo.save(note);

        return NoteResponse.from(note, newContent);
    }

    public NoteResponse getById(String noteId) {
        String userId = getCurrentUserId();
        String vaultId = getCurrentVaultId();
        Note note = noteRepo.findById(noteId)
            .filter(n -> n.getUserId().equals(userId))
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        String content = storageService.loadNoteContent(vaultId, noteId).orElse("");
        return NoteResponse.from(note, content);
    }

    public List<Note> listByUser() {
        return noteRepo.findByUserId(getCurrentUserId());
    }

    /**
     * Carrega apenas os dados necessários para construir o grafo de conhecimento.
     * Otimizado para trazer apenas id, title e entityIds, economizando memória e banda de rede.
     * O campo content não é incluído nesta query.
     */
    public List<Note> listByUserForGraph() {
        String userId = getCurrentUserId();
        return noteRepo.findGraphDataByUserId(userId);
    }

    public void deleteNote(String noteId) {
        String userId = getCurrentUserId();
        String vaultId = getCurrentVaultId();
        Note note = noteRepo.findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        
        // OWNERSHIP: Validar que a nota pertence ao usuário autenticado
        if (!note.getUserId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to delete this note");
        }

        // Delete file from B2 if fileKey exists
        if (note.getFileKey() != null && !note.getFileKey().isEmpty()) {
            storageService.deleteNote(vaultId, noteId);
        }

        // Delete from MongoDB
        noteRepo.deleteById(noteId);

        // Decrement user count
        userService.decrementNoteCount(userId);
    }

    private List<String> findMatchingEntityIds(String userId, String content) {
        List<Entity> userEntities = entityRepo.findByUserId(userId);
        return extractionService.extractEntityIds(content, userEntities);
    }

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new IllegalStateException("Authenticated user not found");
    }

    private String getCurrentVaultId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getVaultId();
        }
        throw new IllegalStateException("Authenticated user not found");
    }

    private String extractTitle(String content) {
        if (content == null || content.isBlank()) return "Untitled";
        String firstLine = content.trim().split("\\n")[0];
        return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
    }

    /**
     * Sanitiza o conteúdo da nota removendo tags <script>, eventos JS (onclick, onload, etc)
     * e outras tags potencialmente maliciosas, enquanto preserva formatação HTML segura.
     * Protege contra ataques XSS (Cross-Site Scripting).
     */
    private String sanitizeContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        // Usar jsoup com Safelist para permitir apenas tags seguras
        // Safelist.basic() permite: b, em, i, strong, u, thead, tbody, tr, th, td...
        // Mas vamos usar basicWithImages() para permitir imagens também
        Safelist safelist = Safelist.basicWithImages()
                .addTags("p", "div", "span", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "code", "blockquote")
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "title")
                .addAttributes("code", "class"); // Para syntax highlighting class names

        // Usar jsoup para limpar. removeAll remove scripts, iframes e outros maliciosos
        String sanitized = Jsoup.clean(content, "", safelist);

        // Remover qualquer ocorrência de javascript: protocol
        sanitized = sanitized.replaceAll("(?i)javascript:", "");

        // Remover atributos de evento (onclick, onload, etc)
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=", "");

        return sanitized;
    }
}