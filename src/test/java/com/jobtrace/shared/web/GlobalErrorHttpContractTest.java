package com.jobtrace.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalErrorHttpContractTest {
    @Test
    void malformedJsonAndMissingQueryStayClientErrorsWithRequestId() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter()).build();
        String requestId = "0199a55c-9b00-7000-8000-000000000029";

        mvc.perform(post("/contract/body")
                        .header(RequestIdFilter.HEADER_NAME, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{secret:"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        mvc.perform(get("/contract/required")
                        .header(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @RestController
    static class ContractController {
        @PostMapping("/contract/body")
        Map<String, Object> body(@RequestBody Map<String, Object> value) {
            return value;
        }

        @GetMapping("/contract/required")
        int required(@RequestParam int limit) {
            return limit;
        }
    }
}
