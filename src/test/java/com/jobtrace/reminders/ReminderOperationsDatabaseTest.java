package com.jobtrace.reminders;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

abstract class ReminderOperationsDatabaseTest extends ReminderReadDatabaseTest {

    protected static final String APPLICATION_ID = "00000000-0000-0000-0000-000000000201";
    protected static final String FOREIGN_APPLICATION_ID = "00000000-0000-0000-0000-000000000202";
    protected static final String STAGE_ID = "00000000-0000-0000-0000-000000000401";
    protected static final String FOREIGN_STAGE_ID = "00000000-0000-0000-0000-000000000402";

    @Autowired private DataSource dataSource;

    @BeforeEach
    void extendReminderFixture() {
        jdbc.execute("drop table if exists progress_reminder_completions,"
                + "application_stage_occurrences cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/reminder-operations.sql")).execute(dataSource);
    }
}
