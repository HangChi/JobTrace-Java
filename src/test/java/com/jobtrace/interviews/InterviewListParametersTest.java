package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.web.InterviewListParameters;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class InterviewListParametersTest {

    @Test
    void preservesRepeatedFiltersAndDefaults() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status", "draft");
        parameters.add("status", "completed");
        parameters.add("publication", "private");
        assertThat(InterviewListParameters.from(parameters).statuses()).hasSize(2);
        assertThat(InterviewListParameters.from(parameters).limit()).isEqualTo(50);
        parameters.add("status", "invalid");
        assertThatThrownBy(() -> InterviewListParameters.from(parameters))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
