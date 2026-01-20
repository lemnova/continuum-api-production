package tech.lemnova.continuum_backend.habit;

import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum_backend.auth.CustomUserDetails;
import tech.lemnova.continuum_backend.habit.dtos.HabitCreateDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitUpdateDTO;
import tech.lemnova.continuum_backend.habit.dtos.ProgressUpdateDTO;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private static final Logger logger = LoggerFactory.getLogger(
        HabitController.class
    );

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    // ✅ GET /api/habits (paginado)
    @GetMapping
    public ResponseEntity<Page<HabitDTO>> listHabits(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Pageable pageable
    ) {
        logger.info(
            "User {} fetching habits with pagination",
            userDetails.getUserId()
        );
        Page<HabitDTO> habits = habitService.listByUser(
            userDetails.getUserId(),
            pageable
        );
        return ResponseEntity.ok(habits);
    }

    // ✅ GET /api/habits/all (sem paginação)
    @GetMapping("/all")
    public ResponseEntity<List<HabitDTO>> listAllHabits(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        logger.info("User {} fetching all habits", userDetails.getUserId());
        List<HabitDTO> habits = habitService.listByUser(
            userDetails.getUserId()
        );
        return ResponseEntity.ok(habits);
    }

    // ✅ POST /api/habits
    @PostMapping
    public ResponseEntity<HabitDTO> createHabit(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody HabitCreateDTO dto
    ) {
        logger.info(
            "User {} creating habit: {}",
            userDetails.getUserId(),
            dto.name()
        );
        HabitDTO created = habitService.create(userDetails.getUserId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ✅ GET /api/habits/{id}
    @GetMapping("/{id}")
    public ResponseEntity<HabitDTO> getHabit(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String id
    ) {
        logger.info("User {} fetching habit {}", userDetails.getUserId(), id);
        HabitDTO habit = habitService.read(userDetails.getUserId(), id);
        return ResponseEntity.ok(habit);
    }

    // ✅ PUT /api/habits/{id}
    @PutMapping("/{id}")
    public ResponseEntity<HabitDTO> updateHabit(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String id,
        @Valid @RequestBody HabitUpdateDTO dto
    ) {
        logger.info("User {} updating habit {}", userDetails.getUserId(), id);
        HabitDTO updated = habitService.update(
            userDetails.getUserId(),
            id,
            dto
        );
        return ResponseEntity.ok(updated);
    }

    // ✅ DELETE /api/habits/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String id
    ) {
        logger.info("User {} deleting habit {}", userDetails.getUserId(), id);
        habitService.delete(userDetails.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ✅ POST /api/habits/{id}/progress
    @PostMapping("/{id}/progress")
    public ResponseEntity<Void> updateProgress(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String id,
        @Valid @RequestBody ProgressUpdateDTO dto
    ) {
        logger.info(
            "User {} updating progress for habit {} on date {}",
            userDetails.getUserId(),
            id,
            dto.date()
        );
        habitService.updateProgress(userDetails.getUserId(), id, dto);
        return ResponseEntity.ok().build();
    }

    // ✅ GET /api/habits/{id}/streak
    @GetMapping("/{id}/streak")
    public ResponseEntity<Integer> getCurrentStreak(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String id
    ) {
        logger.info(
            "User {} fetching streak for habit {}",
            userDetails.getUserId(),
            id
        );
        Integer streak = habitService.calculateCurrentStreak(
            userDetails.getUserId(),
            id
        );
        return ResponseEntity.ok(streak);
    }
}
