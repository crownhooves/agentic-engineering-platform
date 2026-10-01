package com.example.agentic.api;

import com.example.agentic.domain.RunStatus;
import com.example.agentic.observability.RunHealthService;
import com.example.agentic.observability.RunHealthService.HealthSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ObservabilityControllerTest {

    @Test
    void exposesRunHealthSnapshot() throws Exception {
        RunHealthService service =
                mock(RunHealthService.class);

        HealthSnapshot snapshot =
                new HealthSnapshot(
                        true,
                        10,
                        Map.of(
                                RunStatus.COMPLETED, 8L,
                                RunStatus.EXECUTING, 2L
                        ),
                        2,
                        0,
                        0
                );

        given(service.snapshot())
                .willReturn(snapshot);

        ObservabilityController controller =
                new ObservabilityController(service);

        MockMvc mvc = standaloneSetup(controller)
                .build();

        mvc.perform(
                        get("/api/health/runs")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy")
                        .value(true))
                .andExpect(jsonPath("$.totalRuns")
                        .value(10))
                .andExpect(jsonPath("$.runningTasks")
                        .value(2))
                .andExpect(jsonPath("$.failedTasks")
                        .value(0))
                .andExpect(jsonPath("$.staleRuns")
                        .value(0))
                .andExpect(jsonPath("$.runsByStatus.COMPLETED")
                        .value(8))
                .andExpect(jsonPath("$.runsByStatus.EXECUTING")
                        .value(2));
    }
}