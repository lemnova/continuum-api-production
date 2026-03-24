package tech.lemnova.continuum.application.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.application.exception.PlanLimitException;
import tech.lemnova.continuum.application.exception.BadRequestException;
import tech.lemnova.continuum.controller.dto.entity.EntityContextResponse;
import tech.lemnova.continuum.controller.dto.entity.EntityCreateRequest;
import tech.lemnova.continuum.controller.dto.entity.EntityResponse;
import tech.lemnova.continuum.controller.dto.entity.EntityUpdateRequest;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.entity.EntityType;
import tech.lemnova.continuum.domain.note.Note;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.persistence.EntityRepository;
import tech.lemnova.continuum.infra.persistence.NoteRepository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EntityService {

    private final EntityRepository entityRepo;
    private final NoteRepository noteRepo;
    private final UserRepository userRepo;
    private final UserService userService;
    private final PlanConfiguration planConfig;

    public EntityService(EntityRepository entityRepo, NoteRepository noteRepo, UserRepository userRepo, UserService userService, PlanConfiguration planConfig) {
        this.entityRepo = entityRepo;
        this.noteRepo = noteRepo;
        this.userRepo = userRepo;
        this.userService = userService;
        this.planConfig = planConfig;
    }
    
    private User getUser(String userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
    
    public Entity get(String userId, String entityId) {
        User user = getUser(userId);
        return getEntity(user.getVaultId(), entityId);
    }
    
    public List<Entity> listByType(String userId, EntityType type) {
        User user = getUser(userId);
        return entityRepo.findByVaultIdAndType(user.getVaultId(), type);
    }

    public Entity create(String userId, String vaultId, EntityCreateRequest req) {
        // Verificar limite de entidades baseado no plano do usuário
        User user = getUser(userId);
        long currentEntityCount = entityRepo.countByUserId(userId);
        if (!planConfig.canCreateEntity(user.getPlan(), currentEntityCount)) {
            throw new PlanLimitException("Limite de entidades atingido para seu plano. Atualize para uma assinatura superior.");
        }
        
        Entity entity = Entity.builder()
                .userId(userId)
                .vaultId(vaultId)
                .title(req.title().trim())
                .description(req.description())
                .type(req.type() != null ? req.type() : EntityType.PERSON)
                .fileKey(null) // TODO: set fileKey when uploading to B2
                .createdAt(Instant.now())
                .build();
        Entity saved = entityRepo.save(entity);
        userService.incrementEntityCount(userId);
        return saved;
    }

    public Entity getEntity(String vaultId, String entityId) {
        return entityRepo.findById(entityId)
                .filter(e -> e.getVaultId().equals(vaultId))
                .orElseThrow(() -> new NotFoundException("Entity not found: " + entityId));
    }

    public List<Note> getNotesForEntity(String userId, String vaultId, String entityId) {
        User user = getUser(userId);
        // OWNERSHIP: Validar que a entidade pertence ao vault (e portanto ao usuário)
        Entity entity = entityRepo.findById(entityId)
                .filter(e -> e.getVaultId().equals(vaultId))
                .orElseThrow(() -> new NotFoundException("Entity not found: " + entityId));
        
        // Busca notas que contenham o ID desta entidade na lista de conexões
        return noteRepo.findByUserId(userId).stream()
                .filter(n -> n.getEntityIds() != null && n.getEntityIds().contains(entityId))
                .collect(Collectors.toList());
    }

    public List<Entity> getConnections(String userId, String vaultId, String entityId) {
        User user = getUser(userId);
        // OWNERSHIP: Validar que a entidade pertence ao vault (e portanto ao usuário)
        entityRepo.findById(entityId)
                .filter(e -> e.getVaultId().equals(vaultId))
                .orElseThrow(() -> new NotFoundException("Entity not found: " + entityId));
        
        // 1. Encontrar IDs de notas que citam esta entidade
        List<String> noteIdsWithThisEntity = noteRepo.findByUserId(userId).stream()
                .filter(n -> n.getEntityIds() != null && n.getEntityIds().contains(entityId))
                .map(Note::getId)
                .collect(Collectors.toList());

        // 2. Encontrar outras entidades que aparecem nessas mesmas notas
        List<String> connectedEntityIds = noteRepo.findByUserId(userId).stream()
                .filter(n -> noteIdsWithThisEntity.contains(n.getId()))
                .flatMap(n -> n.getEntityIds().stream())
                .filter(id -> !id.equals(entityId))
                .distinct()
                .collect(Collectors.toList());

        return entityRepo.findByIdIn(connectedEntityIds);
    }

    public Entity update(String userId, String vaultId, String entityId, EntityUpdateRequest req) {
        User user = getUser(userId);
        // OWNERSHIP: Validar que a entidade pertence ao usuário
        Entity entity = entityRepo.findById(entityId)
                .filter(e -> e.getVaultId().equals(vaultId) && e.getUserId() != null && e.getUserId().equals(userId))
                .orElseThrow(() -> new AccessDeniedException("You do not have permission to update this entity"));
        
        if (req.title() != null && !req.title().isBlank()) entity.setTitle(req.title().trim());
        if (req.description() != null) entity.setDescription(req.description());
        if (req.type() != null) entity.setType(req.type());
        return entityRepo.save(entity);
    }

    public void delete(String userId, String vaultId, String entityId) {
        User user = getUser(userId);
        // OWNERSHIP: Validar que a entidade pertence ao usuário
        Entity entity = entityRepo.findById(entityId)
                .filter(e -> e.getVaultId().equals(vaultId) && e.getUserId() != null && e.getUserId().equals(userId))
                .orElseThrow(() -> new AccessDeniedException("You do not have permission to delete this entity"));
        
        entityRepo.delete(entity);
    }

    public List<Entity> listByVault(String vaultId) {
        return entityRepo.findByVaultId(vaultId);
    }
    
    public List<Entity> listByUser(String userId) {
        return entityRepo.findByUserId(userId);
    }
    
    /**
     * Carrega apenas os dados necessários para construir o grafo de conhecimento.
     * Otimizado para trazer apenas id e title, economizando memória e banda de rede.
     * 
     * @param userId ID do usuário
     * @return Lista de entidades com apenas os campos essenciais para o grafo
     */
    public List<Entity> listByUserForGraph(String userId) {
        return entityRepo.findGraphDataByUserId(userId);
    }
    
    public EntityContextResponse getEntityContext(String userId, String entityId) {
        User user = getUser(userId);
        
        // CORREÇÃO: Usamos o vaultId para validar a posse da entidade, 
        // já que o campo 'userId' pode não existir na coleção de Entidades
        Entity entity = entityRepo.findById(entityId)
            .filter(e -> e.getVaultId() != null && e.getVaultId().equals(user.getVaultId()))
            .orElseThrow(() -> new NotFoundException("Entity not found: " + entityId));

        List<Note> connectedNotes = noteRepo.findByUserId(userId).stream()
            .filter(note -> note.getEntityIds() != null && note.getEntityIds().contains(entityId))
            .collect(Collectors.toList());

        List<EntityContextResponse.NoteSummary> summaries = connectedNotes.stream()
            .map(note -> new EntityContextResponse.NoteSummary(note.getId(), note.getTitle()))
            .collect(Collectors.toList());

        return EntityContextResponse.from(entity, summaries);
    }

    public Entity trackHabit(String userId, String entityId) {
        User user = getUser(userId);
        
        // Validar se a entidade pertence ao usuário
        Entity entity = entityRepo.findById(entityId)
            .filter(e -> e.getVaultId() != null && e.getVaultId().equals(user.getVaultId()))
            .orElseThrow(() -> new NotFoundException("Entity not found: " + entityId));
        
        // Validar se é do tipo HABIT
        if (entity.getType() != EntityType.HABIT) {
            throw new BadRequestException("Entidade não é um hábito. Tipo: " + entity.getType());
        }
        
        // Validar se o plano permite criar hábitos (verifica quantidade atual)
        long currentHabitCount = entityRepo.findByUserId(userId).stream()
                .filter(e -> e.getType() == EntityType.HABIT)
                .count();
        if (!planConfig.canCreateHabit(user.getPlan(), currentHabitCount)) {
            throw new PlanLimitException("Limite de hábitos atingido para seu plano.");
        }
        
        // Adicionar a data atual se ainda não existir (evita duplicata)
        java.time.LocalDate today = java.time.LocalDate.now();
        if (entity.getTrackingDates() == null) {
            entity.setTrackingDates(new java.util.ArrayList<>());
        }
        if (!entity.getTrackingDates().contains(today)) {
            entity.getTrackingDates().add(today);
        }
        
        return entityRepo.save(entity);
    }
}