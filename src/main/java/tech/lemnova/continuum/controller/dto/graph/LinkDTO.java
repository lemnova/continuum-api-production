package tech.lemnova.continuum.controller.dto.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LinkDTO(
    @JsonProperty("source")
    String source,
    
    @JsonProperty("target")
    String target
) {}
