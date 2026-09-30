package com.jobtrace.shared.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

    @Test
    void liveReportsOkWithoutAccessingTheDatabase() {
        HealthController controller = new HealthController(mock(JdbcTemplate.class));

        assertThat(controller.live()).containsEntry("status", "ok");
    }

    @Test
    void readyReportsOkWhenTheDatabaseResponds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        var response = new HealthController(jdbcTemplate).ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
    }

    @Test
    void readyReportsErrorWhenTheDatabaseReturnsAnUnexpectedResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(0);

        var response = new HealthController(jdbcTemplate).ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", "error");
    }

    @Test
    void readyReportsErrorWhenTheDatabaseReturnsNoResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(null);

        var response = new HealthController(jdbcTemplate).ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", "error");
    }

    @Test
    void readyReportsUnavailableWhenTheDatabaseCannotBeReached() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));

        var response = new HealthController(jdbcTemplate).ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "error");
    }
}
