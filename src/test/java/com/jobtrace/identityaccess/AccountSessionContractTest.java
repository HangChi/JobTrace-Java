package com.jobtrace.identityaccess;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionContractTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void responseMatchesSyntheticLegacyContract() throws Exception {
        String body = mockMvc.perform(get(AccountSessionReadController.PATH)
                        .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture(), mapper.readTree(body));
    }

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "contracts/identityaccess/session-read.legacy.json")) {
            return mapper.readTree(stream);
        }
    }
}
