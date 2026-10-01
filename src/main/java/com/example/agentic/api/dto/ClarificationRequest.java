package com.example.agentic.api.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record ClarificationRequest(
        @Size(max = 50)
        Map<@Size(max = 200) String, @Size(max = 5000) String> answers) {
}