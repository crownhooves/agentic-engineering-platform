package com.example.agentic.api.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record PlanApprovalRequest(
        @Size(max = 100)
        Set<@Size(max = 200) String> removedTaskKeys) {
}