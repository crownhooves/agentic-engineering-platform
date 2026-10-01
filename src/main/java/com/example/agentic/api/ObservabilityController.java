package com.example.agentic.api;

import com.example.agentic.observability.RunHealthService;
import com.example.agentic.observability.RunHealthService.HealthSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class ObservabilityController {

    private final RunHealthService health;

    public ObservabilityController(RunHealthService health) {
        this.health = health;
    }

    @GetMapping("/runs")
    public HealthSnapshot runs() {
        return health.snapshot();
    }
}