package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.infrastructure.PostgresExportReadQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class SelectedApplicationExportQueryIntegrationTest extends ExportDatabaseTest {

    @Autowired private PostgresExportReadQuery query;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void selectedIgnoresFiltersButNeverIncludesForeignOrDeletedRows() {
        var selection = ApplicationExportSelection.from("selected", "csv",
                List.of(A102, A201, A101, "00000000-0000-0000-0000-000000000999"),
                "no-match", List.of("refused"), List.of(), List.of(), List.of(), null, null);
        assertThat(query.applications(OWNER, selection)).extracting(row -> row.id())
                .containsExactly(A101, A102);
    }

    @Test
    void selectedApplicationReadUsesOneSqlQuery() {
        var countedJdbc = spy(namedJdbc);
        var counted = new PostgresExportReadQuery(countedJdbc);
        var selection = ApplicationExportSelection.from("selected", "csv", List.of(A101, A102),
                null, List.of(), List.of(), List.of(), List.of(), null, null);
        assertThat(counted.applications(OWNER, selection)).hasSize(2);
        verify(countedJdbc, times(1)).query(anyString(), any(SqlParameterSource.class),
                any(RowMapper.class));
    }
}
