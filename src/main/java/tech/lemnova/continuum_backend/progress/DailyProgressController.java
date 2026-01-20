package tech.lemnova.continuum_backend.progress;

import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum_backend.auth.CustomUserDetails;

@RestController
@RequestMapping("/api/progress")
public class DailyProgressController {

    private static final Logger logger = LoggerFactory.getLogger(
        DailyProgressController.class
    );

    private final DailyProgressService dailyProgressService;

    public DailyProgressController(DailyProgressService dailyProgressService) {
        this.dailyProgressService = dailyProgressService;
    }

    // ✅ GET /api/progress/habit/{habitId}?startDate=...&endDate=...
    @GetMapping("/habit/{habitId}")
    public ResponseEntity<List<DailyProgressDTO>> getProgressForHabit(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String habitId,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate startDate,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate endDate
    ) {
        logger.info(
            "User {} fetching progress for habit {} from {} to {}",
            userDetails.getUserId(),
            habitId,
            startDate,
            endDate
        );

        List<DailyProgressDTO> progress =
            dailyProgressService.getProgressForHabit(
                userDetails.getUserId(),
                habitId,
                startDate,
                endDate
            );

        return ResponseEntity.ok(progress);
    }

    // ✅ GET /api/progress?startDate=...&endDate=...
    @GetMapping
    public ResponseEntity<List<DailyProgressDTO>> getProgressForUser(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate startDate,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate endDate
    ) {
        logger.info(
            "User {} fetching all progress from {} to {}",
            userDetails.getUserId(),
            startDate,
            endDate
        );

        List<DailyProgressDTO> progress =
            dailyProgressService.getProgressForUser(
                userDetails.getUserId(),
                startDate,
                endDate
            );

        return ResponseEntity.ok(progress);
    }

    // ✅ GET /api/progress/today
    @GetMapping("/today")
    public ResponseEntity<List<DailyProgressDTO>> getTodayProgress(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        logger.info(
            "User {} fetching today's progress",
            userDetails.getUserId()
        );

        List<DailyProgressDTO> progress =
            dailyProgressService.getTodayProgressForUser(
                userDetails.getUserId()
            );

        return ResponseEntity.ok(progress);
    }

    // ✅ POST /api/progress/habit/{habitId}/toggle?date=...
    @PostMapping("/habit/{habitId}/toggle")
    public ResponseEntity<DailyProgressDTO> toggleProgress(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable String habitId,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate date
    ) {
        logger.info(
            "User {} toggling progress for habit {} on {}",
            userDetails.getUserId(),
            habitId,
            date
        );

        DailyProgressDTO progress = dailyProgressService.toggleProgress(
            userDetails.getUserId(),
            habitId,
            date
        );

        return ResponseEntity.ok(progress);
    }

    // ✅ GET /api/progress/score?date=...
    @GetMapping("/score")
    public ResponseEntity<Integer> getDailyScore(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        logger.info(
            "User {} fetching score for {}",
            userDetails.getUserId(),
            targetDate
        );

        Integer score = dailyProgressService.calculateDailyScore(
            userDetails.getUserId(),
            targetDate
        );

        return ResponseEntity.ok(score);
    }
}
