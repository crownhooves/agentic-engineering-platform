package com.example.agentic.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/runs")
public class RunEventController {

    private final RunEventStreamService streamService;

    public RunEventController(RunEventStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(
            value = "/{runId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String runId) {
        return streamService.open(runId);
    }
}