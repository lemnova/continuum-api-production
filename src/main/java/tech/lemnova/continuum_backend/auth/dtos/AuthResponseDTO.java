package tech.lemnova.continuum_backend.auth.dtos;

import tech.lemnova.continuum_backend.subscription.PlanType;

public record AuthResponseDTO(
    String token,
    String userId,
    String username,
    String email,
    PlanType planType
) {}
