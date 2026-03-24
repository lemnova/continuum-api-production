package tech.lemnova.continuum.application.service;

import org.springframework.stereotype.Service;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.application.exception.PlanLimitException;
import tech.lemnova.continuum.controller.dto.metrics.DashboardMetrics;
import tech.lemnova.continuum.controller.dto.metrics.EntityTimeline;
import tech.lemnova.continuum.controller.dto.metrics.MentionEntry;
import tech.lemnova.continuum.controller.dto.metrics.TopEntity;
import tech.lemnova.continuum.domain.connection.NoteReference;
import tech.lemnova.continuum.domain.note.Note;
import tech.lemnova.continuum.domain.note.NoteIndex;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.tracking.TrackingEvent;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.entity.EntityType;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.persistence.EntityRepository;
import tech.lemnova.continuum.infra.persistence.NoteRepository;
import tech.lemnova.continuum.infra.vault.VaultDataService;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetricsService {

    private final UserRepository userRepo;
    private final NoteRepository noteRepo;
    private final EntityRepository entityRepo;
    private final VaultDataService vaultData;
    private final PlanConfiguration planConfig;
    private final EntityService entityService;
    private final TrackingService trackingService;

    public MetricsService(UserRepository userRepo,
                          NoteRepository noteRepo,
                          EntityRepository entityRepo,
                          VaultDataService vaultData,
                          PlanConfiguration planConfig,
                          EntityService entityService,
                          TrackingService trackingService) {
        this.userRepo   = userRepo;
        this.noteRepo   = noteRepo;
        this.entityRepo = entityRepo;
        this.vaultData  = vaultData;
        this.planConfig = planConfig;
        this.entityService = entityService;
        this.trackingService = trackingService;
    }

    public EntityTimeline getEntityTimeline(String userId, String entityId) {
        User user = getUser(userId);
        if (!planConfig.canAccessAdvancedMetrics(user.getPlan()))
            throw new PlanLimitException("Advanced metrics require a higher plan.");

        List<NoteReference> refs = vaultData.readRefs(user.getVaultId()).stream()
                .filter(r -> r.getEntityId().equals(entityId))
                .sorted(Comparator.comparing(NoteReference::getDate, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Map<LocalDate, Long> heatmap = refs.stream()
                .collect(Collectors.groupingBy(NoteReference::getDate, Collectors.counting()));

        Set<String> noteIds = refs.stream().map(NoteReference::getNoteId).collect(Collectors.toSet());
        Map<String, String> noteTitles = vaultData.readNoteIndex(user.getVaultId()).stream()
                .filter(n -> noteIds.contains(n.getId()))
                .collect(Collectors.toMap(NoteIndex::getId, NoteIndex::getTitle));

        List<MentionEntry> mentions = refs.stream()
                .map(r -> new MentionEntry(r.getNoteId(),
                        noteTitles.getOrDefault(r.getNoteId(), "Untitled"),
                        r.getDate(), r.getContext()))
                .collect(Collectors.toList());

        // compute mention frequency
        double freq = 0.0;
        if (!refs.isEmpty()) {
            LocalDate firstDate = refs.stream()
                    .map(NoteReference::getDate)
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());
            long days = ChronoUnit.DAYS.between(firstDate, LocalDate.now()) + 1;
            freq = days <= 0 ? 0.0 : ((double) refs.size()) / days;
        }

        return new EntityTimeline(
                entityId,
                refs.isEmpty() ? null : refs.get(0).getEntityType().name(),
                refs.isEmpty() ? null : refs.get(0).getEntityName(),
                refs.size(), heatmap, mentions, freq);
    }

    /**
     * [ARCH-6] Uma leitura do vault, agrupamento em memória.
     */
    public DashboardMetrics getDashboard(String userId) {
        User user = getUser(userId);

        long totalNotes = noteRepo.countByUserId(userId);
        long totalEntities = entityRepo.countByUserId(userId);

        List<Note> notes = noteRepo.findByUserId(userId);
        Map<String, Long> entityMentionCount = notes.stream()
                .filter(note -> note.getEntityIds() != null)
                .flatMap(note -> note.getEntityIds().stream())
                .collect(Collectors.groupingBy(entityId -> entityId, Collectors.counting()));

        long totalMentions = entityMentionCount.values().stream().mapToLong(Long::longValue).sum();

        List<String> topMentionIds = entityMentionCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, Entity> entityById = entityRepo.findByIdIn(topMentionIds).stream()
                .collect(Collectors.toMap(Entity::getId, e -> e));

        List<TopEntity> topMentions = topMentionIds.stream()
                .map(id -> {
                    long count = entityMentionCount.getOrDefault(id, 0L);
                    Entity entity = entityById.get(id);
                    String name = entity != null ? entity.getTitle() : "Unknown";
                    String type = entity != null && entity.getType() != null ? entity.getType().name() : "unknown";
                    double mentionFrequency = totalNotes > 0 ? ((double) count / totalNotes) : 0.0;
                    return new TopEntity(type, id, name, count, mentionFrequency);
                })
                .toList();

        List<NoteReference> all = vaultData.readRefs(user.getVaultId());

        Map<String, List<NoteReference>> byType = all.stream()
                .collect(Collectors.groupingBy(ref -> ref.getEntityType().name()));

        List<NoteReference> people   = byType.getOrDefault("person",  List.of());
        List<NoteReference> projects = byType.getOrDefault("project", List.of());
        List<NoteReference> habitsRefs   = byType.getOrDefault("habit",   List.of());

        long uniquePeople   = people.stream().map(NoteReference::getEntityId).distinct().count();
        long uniqueProjects = projects.stream().map(NoteReference::getEntityId).distinct().count();
        long uniqueHabits   = habitsRefs.stream().map(NoteReference::getEntityId).distinct().count();

        // global heatmap
        Map<LocalDate, Long> globalHeatmap = all.stream()
                .collect(Collectors.groupingBy(NoteReference::getDate, Collectors.counting()));

        // tracking events preloaded
        List<TrackingEvent> events = vaultData.readTrackingEvents(user.getVaultId());
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);

        Set<String> completedToday = events.stream()
                .filter(e -> today.equals(e.getDate()))
                .map(TrackingEvent::getEntityId)
                .collect(Collectors.toSet());

        // active habits from entityService
        List<Entity> habits = entityService.listByType(userId, EntityType.HABIT);
        List<String> habitsCompletedToday = habits.stream()
                .map(Entity::getId)
                .filter(completedToday::contains)
                .collect(Collectors.toList());

        // weekly average completion rate
        Map<String, Long> daysThisWeek = events.stream()
                .filter(e -> !e.getDate().isBefore(weekStart) && !e.getDate().isAfter(today))
                .collect(Collectors.groupingBy(TrackingEvent::getEntityId,
                        Collectors.mapping(TrackingEvent::getDate, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));

        double weeklyAverage = 0.0;
        if (!habits.isEmpty()) {
            double sum = 0.0;
            for (Entity h : habits) {
                long days = daysThisWeek.getOrDefault(h.getId(), 0L);
                sum += days / 7.0;
            }
            weeklyAverage = sum / habits.size();
        }

        return new DashboardMetrics(
                uniquePeople, uniqueProjects, uniqueHabits,
                totalMentions,
                totalNotes, totalEntities, topMentions,
                topEntities(people, 5), topEntities(projects, 5), topEntities(habitsRefs, 5),
                habitsCompletedToday, weeklyAverage, globalHeatmap);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private List<TopEntity> topEntities(List<NoteReference> refs, int limit) {
        Map<String, Long>   counts = refs.stream().collect(Collectors.groupingBy(NoteReference::getEntityId, Collectors.counting()));
        Map<String, String> names  = refs.stream().collect(Collectors.toMap(NoteReference::getEntityId, NoteReference::getEntityName, (a, b) -> a));
        Map<String, String> types  = refs.stream().collect(Collectors.toMap(NoteReference::getEntityId, ref -> ref.getEntityType().name(), (a, b) -> a));
        LocalDate today = LocalDate.now();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    String id = e.getKey();
                    long count = e.getValue();
                    // compute first mention date for frequency
                    LocalDate firstDate = refs.stream()
                            .filter(r -> r.getEntityId().equals(id))
                            .map(NoteReference::getDate)
                            .min(LocalDate::compareTo)
                            .orElse(today);
                    long days = ChronoUnit.DAYS.between(firstDate, today) + 1;
                    double freq = days <= 0 ? 0.0 : ((double) count) / days;
                    return new TopEntity(types.get(id), id, names.get(id), count, freq);
                })
                .collect(Collectors.toList());
    }

    private User getUser(String userId) {
        return userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Map<LocalDate, Long> getGlobalMentionsHeatmap(String userId, LocalDate from, LocalDate to) {
        User user = getUser(userId);
        return vaultData.readRefs(user.getVaultId()).stream()
                .filter(r -> (from == null || !r.getDate().isBefore(from))
                        && (to == null || !r.getDate().isAfter(to)))
                .collect(Collectors.groupingBy(NoteReference::getDate, Collectors.counting()));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// APPLICATION — AuthService
// ─────────────────────────────────────────────────────────────────────────────
