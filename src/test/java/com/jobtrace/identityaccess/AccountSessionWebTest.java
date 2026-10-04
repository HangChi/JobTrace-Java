package com.jobtrace.identityaccess;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountSessionReadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionWebTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsOwnedSessionsAndPreventsCaching() throws Exception {
        mockMvc.perform(get(AccountSessionReadController.PATH)
                        .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(CURRENT))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void rejectsMissingBlankAndOverlongCurrentSessionMetadataWithoutCaching() throws Exception {
        for (String value : new String[] {null, " ", "x".repeat(256)}) {
            var request = get(AccountSessionReadController.PATH).with(asOwner(OWNER));
            if (value != null) {
                request.header(AccountSessionReadController.CURRENT_SESSION_HEADER, value);
            }
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
    }
}
