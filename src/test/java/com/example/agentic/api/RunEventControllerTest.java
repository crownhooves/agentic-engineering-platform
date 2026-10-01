package com.example.agentic.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RunEventControllerTest {

    @Test
    void eventsEndpointReturnsSseContentType() throws Exception {
        RunEventStreamService service =
                mock(RunEventStreamService.class);

        given(service.open("run-1"))
                .willReturn(new SseEmitter());

        RunEventController controller =
                new RunEventController(service);

        MockMvc mvc = standaloneSetup(controller)
                .build();

        mvc.perform(
                        get("/api/runs/run-1/events")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.TEXT_EVENT_STREAM
                        )
                );
    }
}