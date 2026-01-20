package tech.lemnova.continuum_backend.habit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.exception.ResourceNotFoundException;
import tech.lemnova.continuum_backend.exception.UnauthorizedException;
import tech.lemnova.continuum_backend.habit.dtos.HabitCreateDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitUpdateDTO;
import tech.lemnova.continuum_backend.habit.dtos.ProgressUpdateDTO;
import tech.lemnova.continuum_backend.progress.DailyProgress;
import tech.lemnova.continuum_backend.progress.DailyProgressRepository;
import tech.lemnova.continuum_backend.subscription.SubscriptionService;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

@Service
public class HabitService {

    private static final Logger logger = LoggerFactory.getLogger(
        HabitService.class
    );

    private final HabitRepository habitRepository;
    private final UserRepository userRepository;
    private final DailyProgressRepository dailyProgressRepository;
    private final SubscriptionService subscriptionService;

    public HabitService(
        HabitRepository habitRepository,
        UserRepository userRepository,
        DailyProgressRepository dailyProgressRepository,
        SubscriptionService subscriptionService
    ) {
        this.habitRepository = habitRepository;
        this.userRepository = userRepository;
        this.dailyProgressRepository = dailyProgressRepository;
        this.subscriptionService = subscriptionService;
    }

    // ✅ Validação de ownership
    private Habit getHabitAndValidateOwnership(String userId, String habitId) {
        Habit habit = habitRepository
            .findById(habitId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Habit not found with id: " + habitId
                )
            );

        if (!habit.getUserId().equals(userId)) {
            logger.warn(
                "User {} attempted to access habit {} owned by {}",
                userId,
                habitId,
                habit.getUserId()
            );
            throw new UnauthorizedException(
                "You don't have permission to access this habit"
            );
        }

        return habit;
    }

    // LIST BY USER (com paginação)
    public Page<HabitDTO> listByUser(String userId, Pageable pageable) {
        logger.debug("Listing habits for user {} with pagination", userId);

        return habitRepository
            .findAllByUserIdAndDeletedFalse(userId, pageable)
            .map(habit -> {
                Integer streak = calculateCurrentStreak(userId, habit.getId());
                Long total = dailyProgressRepository.countCompletedByHabitId(
                    habit.getId()
                );
                return HabitDTO.from(habit, streak, total);
            });
    }

    // LIST BY USER (sem paginação)
    public List<HabitDTO> listByUser(String userId) {
        logger.debug("Listing all habits for user {}", userId);

        return habitRepository
            .findAllByUserIdAndDeletedFalse(userId)
            .stream()
            .map(habit -> {
                Integer streak = calculateCurrentStreak(userId, habit.getId());
                Long total = dailyProgressRepository.countCompletedByHabitId(
                    habit.getId()
                );
                return HabitDTO.from(habit, streak, total);
            })
            .collect(Collectors.toList());
    }

    // CREATE
    @Transactional
    public HabitDTO create(String userId, HabitCreateDTO dto) {
        logger.info("User {} creating habit: {}", userId, dto.name());

        User user = userRepository
            .findById(userId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "User not found with id: " + userId
                )
            );

        // ✅ Verificar limite de hábitos do plano
        long currentHabitsCount = habitRepository.countByUserIdAndDeletedFalse(
            userId
        );

        if (
            !subscriptionService.canCreateHabit(
                userId,
                (int) currentHabitsCount
            )
        ) {
            logger.warn(
                "User {} exceeded habit limit. Current: {}",
                userId,
                currentHabitsCount
            );
            throw new BadRequestException(
                "Habit limit reached for your plan. Upgrade to PRO to create unlimited habits."
            );
        }

        Habit habit = new Habit();
        habit.setName(dto.name());
        habit.setDescription(dto.description());
        habit.setCategory(dto.category());
        habit.setIcon(dto.icon());
        habit.setColor(dto.color());
        habit.setIsActive(true);
        habit.setDeleted(false);
        habit.setUserId(user.getId());
        habit.setCreatedAt(Instant.now());
        habit.setUpdatedAt(Instant.now());

        Habit saved = habitRepository.save(habit);

        logger.info(
            "Habit {} created successfully for user {}",
            saved.getId(),
            userId
        );

        return HabitDTO.from(saved, 0, 0L);
    }

    // READ (com validação de ownership)
    public HabitDTO read(String userId, String habitId) {
        logger.debug("User {} reading habit {}", userId, habitId);

        Habit habit = getHabitAndValidateOwnership(userId, habitId);

        Integer streak = calculateCurrentStreak(userId, habitId);
        Long total = dailyProgressRepository.countCompletedByHabitId(habitId);

        return HabitDTO.from(habit, streak, total);
    }

    // UPDATE (com validação de ownership)
    @Transactional
    public HabitDTO update(String userId, String habitId, HabitUpdateDTO dto) {
        logger.info("User {} updating habit {}", userId, habitId);

        Habit habit = getHabitAndValidateOwnership(userId, habitId);

        if (dto.name() != null) {
            habit.setName(dto.name());
        }

        if (dto.description() != null) {
            habit.setDescription(dto.description());
        }

        if (dto.category() != null) {
            habit.setCategory(dto.category());
        }

        if (dto.icon() != null) {
            habit.setIcon(dto.icon());
        }

        if (dto.color() != null) {
            habit.setColor(dto.color());
        }

        if (dto.isActive() != null) {
            habit.setIsActive(dto.isActive());
        }

        habit.setUpdatedAt(Instant.now());
        Habit updated = habitRepository.save(habit);

        Integer streak = calculateCurrentStreak(userId, habitId);
        Long total = dailyProgressRepository.countCompletedByHabitId(habitId);

        logger.info("Habit {} updated successfully", habitId);

        return HabitDTO.from(updated, streak, total);
    }

    // DELETE (soft delete com validação de ownership)
    @Transactional
    public void delete(String userId, String habitId) {
        logger.info("User {} deleting habit {}", userId, habitId);

        Habit habit = getHabitAndValidateOwnership(userId, habitId);

        habit.setDeleted(true);
        habit.setUpdatedAt(Instant.now());
        habitRepository.save(habit);

        logger.info("Habit {} soft deleted successfully", habitId);
    }

    // UPDATE PROGRESS (com validação de ownership)
    @Transactional
    public void updateProgress(
        String userId,
        String habitId,
        ProgressUpdateDTO dto
    ) {
        logger.info(
            "User {} updating progress for habit {} on date {}",
            userId,
            habitId,
            dto.date()
        );

        // Validar ownership do hábito
        getHabitAndValidateOwnership(userId, habitId);

        DailyProgress progress = dailyProgressRepository
            .findByHabitIdAndDate(habitId, dto.date())
            .orElse(new DailyProgress());

        progress.setHabitId(habitId);
        progress.setUserId(userId);
        progress.setDate(dto.date());
        progress.setCompleted(dto.completed());
        progress.setNotes(dto.notes());

        if (progress.getId() == null) {
            progress.setCreatedAt(Instant.now());
        }
        progress.setUpdatedAt(Instant.now());

        dailyProgressRepository.save(progress);

        logger.debug(
            "Progress updated for habit {} on {}",
            habitId,
            dto.date()
        );
    }

    // CALCULATE CURRENT STREAK (com validação de ownership)
    public Integer calculateCurrentStreak(String userId, String habitId) {
        // Validar ownership
        getHabitAndValidateOwnership(userId, habitId);

        LocalDate today = LocalDate.now();
        LocalDate checkDate = today;
        int streak = 0;

        boolean completedToday = dailyProgressRepository
            .findByHabitIdAndDate(habitId, today)
            .map(DailyProgress::getCompleted)
            .orElse(false);

        if (!completedToday) {
            checkDate = today.minusDays(1);
        }

        while (streak < 365) {
            // Limite de segurança
            boolean completed = dailyProgressRepository
                .findByHabitIdAndDate(habitId, checkDate)
                .map(DailyProgress::getCompleted)
                .orElse(false);

            if (!completed) {
                break;
            }

            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    // GET PROGRESS FOR DATE RANGE (com validação de ownership)
    public List<DailyProgress> getProgressForDateRange(
        String userId,
        String habitId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        // Validar ownership
        getHabitAndValidateOwnership(userId, habitId);

        return dailyProgressRepository.findByHabitIdAndDateBetween(
            habitId,
            startDate,
            endDate
        );
    }
}
