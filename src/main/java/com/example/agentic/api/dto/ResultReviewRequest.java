package com.example.agentic.api.dto;

import jakarta.validation.constraints.Size;

public record ResultReviewRequest(
        @Size(max = 2000)
        String reason) {
}