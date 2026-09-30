package com.jobtrace.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    @Test
    void preservesAValidRequestIdAndAddsItToTheResponse() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(requestId);
        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesMissingOrInvalidExternalRequestIds() {
        String missingReplacement = RequestIdFilter.resolveRequestId(null);
        String invalidReplacement = RequestIdFilter.resolveRequestId("not-a-uuid");

        assertThatCodeParsesAsUuid(missingReplacement);
        assertThatCodeParsesAsUuid(invalidReplacement);
        assertThat(invalidReplacement).isNotEqualTo("not-a-uuid");
    }

    private void assertThatCodeParsesAsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}

