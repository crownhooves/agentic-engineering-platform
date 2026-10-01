package com.example.agentic.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartRunRequest(
        @NotBlank
        @Size(max = 10000)
        String requirement,

        @Size(max = 100)
        String scenario) {
}