package tech.lemnova.continuum.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.application.service.EntityIndexService;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.security.CustomUserDetails;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final EntityIndexService entityIndexService;
    private final UserRepository userRepo;

    public VaultController(EntityIndexService entityIndexService, UserRepository userRepo) {
        this.entityIndexService = entityIndexService;
        this.userRepo = userRepo;
    }

    @GetMapping(value = "/entity-index", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getEntityIndex(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepo.findById(userDetails.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ResponseEntity.ok(entityIndexService.loadIndex(user.getVaultId()));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────
