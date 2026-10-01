package com.jobtrace.analytics.infrastructure;

import com.jobtrace.analytics.application.GetAnalyticsSummary;
import com.jobtrace.analytics.application.GetAnalyticsSummary.AnalyticsSummary;
import com.jobtrace.analytics.application.GetAnalyticsSummary.FollowUp;
import com.jobtrace.analytics.application.GetAnalyticsSummary.ProgressReminder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAnalyticsSummaryQuery implements GetAnalyticsSummary.Query {

    private static final int FOLLOW_UP_THRESHOLD_DAYS = 15;
    private static final Set<String> RECRUITMENT_STAGES = Set.of(
            "screening",
            "assessment",
            "written_test",
            "interview_1",
            "interview_2",
            "interview_3",
            "hr_interview",
            "final_interview");

    private final JdbcTemplate jdbcTemplate;

    public PostgresAnalyticsSummaryQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AnalyticsSummary getSummary(String ownerId, LocalDate today) {
        Counts counts = jdbcTemplate.queryForObject(
                """
                select count(*)::int as total,
                  count(*) filter (where status = 'submitted')::int as submitted,
                  count(*) filter (where status = 'refused')::int as refused,
                  count(*) filter (where status = 'offer')::int as offers,
                  count(*) filter (
                    where applied_date >= date_trunc('week', cast(? as date))::date
                  )::int as added_this_week
                from applications
                where owner_id = ?
                """,
                (resultSet, rowNumber) -> new Counts(
                        resultSet.getInt("total"),
                        resultSet.getInt("submitted"),
                        resultSet.getInt("refused"),
                        resultSet.getInt("offers"),
                        resultSet.getInt("added_this_week")),
                today,
                ownerId);

        Map<String, Integer> stageDistribution = new LinkedHashMap<>();
        jdbcTemplate.query(
                        """
                        select s.stage::text as stage, count(distinct s.application_id)::int as total
                        from application_stage_occurrences s
                        join applications a on a.id = s.application_id
                        where a.owner_id = ?
                        group by s.stage
                        order by s.stage
                        """,
                        (resultSet, rowNumber) -> new StageCount(
                                resultSet.getString("stage"), resultSet.getInt("total")),
                        ownerId)
                .stream()
                .filter(row -> RECRUITMENT_STAGES.contains(row.stage()))
                .forEach(row -> stageDistribution.put(row.stage(), row.total()));

        List<ProgressReminder> progressReminders = jdbcTemplate.query(
                """
                select
                  a.id as application_id,
                  a.company_name,
                  a.position_name,
                  a.city,
                  latest_stage.id as stage_occurrence_id,
                  latest_stage.stage::text as stage,
                  latest_stage.occurred_on,
                  review.id as review_id,
                  review.status::text as review_status,
                  completion.id is not null as completed
                from applications a
                join lateral (
                  select s.id, s.stage, s.occurred_on, s.created_at
                  from application_stage_occurrences s
                  where s.application_id = a.id
                  order by s.occurred_on desc, s.created_at desc, s.id desc
                  limit 1
                ) latest_stage on true
                left join interview_reviews review
                  on review.stage_occurrence_id = latest_stage.id
                  and review.owner_id = a.owner_id
                left join progress_reminder_completions completion
                  on completion.stage_occurrence_id = latest_stage.id
                  and completion.owner_id = a.owner_id
                where a.owner_id = ?
                  and a.status = 'submitted'
                  and completion.id is null
                  and (
                    latest_stage.stage in ('assessment', 'written_test')
                    or (
                      latest_stage.stage in (
                        'interview_1', 'interview_2', 'interview_3',
                        'hr_interview', 'final_interview'
                      )
                      and coalesce(review.status::text, 'draft') <> 'completed'
                    )
                  )
                order by latest_stage.occurred_on desc, a.id
                limit 20
                """,
                this::mapProgressReminder,
                ownerId);

        List<FollowUp> followUps = jdbcTemplate.query(
                """
                select a.id, a.company_name, a.position_name, a.city, a.job_url,
                  a.applied_date, a.type, a.status, a.latest_date, a.version,
                  case
                    when timeline.latest_date is not null and timeline.latest_date >= a.latest_date
                      then 'timeline'
                    else 'application'
                  end as follow_up_reason,
                  case
                    when timeline.latest_date is not null and timeline.latest_date >= a.latest_date
                      then cast(? as date) - timeline.latest_date
                    else cast(? as date) - a.latest_date
                  end as follow_up_days
                from applications a
                left join lateral (
                  select max(s.occurred_on) as latest_date
                  from application_stage_occurrences s
                  where s.application_id = a.id
                ) timeline on true
                where a.owner_id = ? and a.status = 'submitted'
                  and cast(? as date) - greatest(
                    a.latest_date,
                    coalesce(timeline.latest_date, a.latest_date)
                  ) >= ?
                order by follow_up_days desc, a.id
                limit 20
                """,
                this::mapFollowUp,
                today,
                today,
                ownerId,
                today,
                FOLLOW_UP_THRESHOLD_DAYS);

        return new AnalyticsSummary(
                counts.total(),
                counts.submitted(),
                counts.refused(),
                counts.offers(),
                counts.addedThisWeek(),
                stageDistribution,
                followUps,
                progressReminders);
    }

    private ProgressReminder mapProgressReminder(ResultSet resultSet, int rowNumber) throws SQLException {
        String occurrenceId = resultSet.getString("stage_occurrence_id");
        return new ProgressReminder(
                occurrenceId,
                resultSet.getString("application_id"),
                resultSet.getString("company_name"),
                resultSet.getString("position_name"),
                resultSet.getString("city"),
                occurrenceId,
                resultSet.getString("stage"),
                resultSet.getObject("occurred_on", LocalDate.class),
                resultSet.getString("review_id"),
                resultSet.getString("review_status"),
                resultSet.getBoolean("completed"));
    }

    private FollowUp mapFollowUp(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FollowUp(
                resultSet.getString("id"),
                resultSet.getString("company_name"),
                resultSet.getString("position_name"),
                resultSet.getString("city"),
                resultSet.getString("job_url"),
                resultSet.getObject("applied_date", LocalDate.class),
                resultSet.getString("type"),
                resultSet.getString("status"),
                resultSet.getObject("latest_date", LocalDate.class),
                List.of(),
                true,
                resultSet.getInt("follow_up_days"),
                resultSet.getString("follow_up_reason"),
                resultSet.getInt("version"));
    }

    private record Counts(int total, int submitted, int refused, int offers, int addedThisWeek) {}

    private record StageCount(String stage, int total) {}
}
