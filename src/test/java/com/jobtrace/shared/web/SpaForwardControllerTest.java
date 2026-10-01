package com.jobtrace.shared.web;

import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaForwardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SpaForwardController()).build();
    }

    @Test
    void forwardsSingleSegmentBrowserRouteToTheApplicationShell() throws Exception {
        mockMvc.perform(get("/applications").accept(TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsNestedBrowserRouteToTheApplicationShell() throws Exception {
        mockMvc.perform(get("/applications/example").accept(TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void doesNotForwardApiRoutes() throws Exception {
        mockMvc.perform(get("/api/missing").accept(TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void doesNotForwardStaticAssetRoutes() throws Exception {
        mockMvc.perform(get("/assets/missing.js").accept(TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void doesNotForwardNestedFileRequests() throws Exception {
        mockMvc.perform(get("/applications/missing.js").accept(TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }
}
