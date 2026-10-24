package com.jobtrace.analytics.infrastructure;

import com.jobtrace.analytics.application.AnalyticsReportAggregate;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Dimension;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Metrics;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.ReviewMetrics;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.ReviewStage;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.StageCount;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Trend;
import com.jobtrace.analytics.application.AnalyticsReportQuery;
import com.jobtrace.analytics.domain.AnalyticsReportRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresAnalyticsReportQuery implements AnalyticsReportQuery {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresAnalyticsReportQuery(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public AnalyticsReportAggregate aggregate(String ownerId, AnalyticsReportRange range,
            LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("fromDate", from).addValue("toDate", to)
                .addValue("type", range.type() == null ? null : range.type().value())
                .addValue("hasCityFilter", range.hasCityFilter())
                .addValue("city", range.city() == null ? "" : range.city())
                .addValue("granularity", range.granularity());
        return jdbc.queryForObject("""
                with base_applications as materialized (
                  select a.id,a.applied_date,a.status::text as status,a.type::text as type,
                    coalesce(a.city,'') as city
                  from applications a
                  where a.owner_id=:ownerId
                    and (cast(:fromDate as date) is null or a.applied_date>=cast(:fromDate as date))
                    and (cast(:toDate as date) is null or a.applied_date<=cast(:toDate as date))
                    and (cast(:type as text) is null or a.type::text=cast(:type as text))
                    and (:hasCityFilter=false or coalesce(a.city,'')=:city)
                ),
                stage_facts as (
                  select s.application_id,
                    bool_or(s.stage::text in ('interview_1','interview_2','interview_3',
                      'hr_interview','final_interview')) as interviewed,
                    bool_or(s.stage::text='final_interview') as final_interview,
                    min(s.occurred_on) filter(where s.stage::text in
                      ('interview_1','interview_2','interview_3','hr_interview','final_interview'))
                      as first_interview_on,
                    array_agg(distinct s.stage::text) as stages
                  from application_stage_occurrences s
                  join base_applications a on a.id=s.application_id
                  group by s.application_id
                ),
                cohort as materialized (
                  select a.*,coalesce(sf.interviewed,false) as interviewed,
                    coalesce(sf.final_interview,false) as final_interview,
                    sf.first_interview_on,coalesce(sf.stages,array[]::text[]) as stages
                  from base_applications a
                  left join stage_facts sf on sf.application_id=a.id
                ),
                metrics as (
                  select count(*)::int as applications,
                    count(*) filter(where interviewed)::int as interviewed,
                    count(*) filter(where status='offer')::int as offers,
                    count(*) filter(where final_interview)::int as final_interviews,
                    count(*) filter(where status='offer' and interviewed and final_interview)::int
                      as path_offers,
                    percentile_cont(0.5) within group(order by first_interview_on-applied_date)
                      filter(where first_interview_on is not null) as median_days_to_first_interview,
                    count(*) filter(where status='offer' and not interviewed)::int
                      as offers_without_interview,
                    count(*) filter(where status='offer' and interviewed and not final_interview)::int
                      as offers_without_final
                  from cohort
                ),
                review_metrics as (
                  select count(r.id)::int as total,
                    count(r.id) filter(where r.status::text='completed')::int as completed,
                    count(r.id) filter(where r.round_result::text in ('passed','failed'))::int as resolved,
                    count(r.id) filter(where r.round_result::text='passed')::int as passed
                  from cohort c
                  left join interview_reviews r on r.application_id=c.id and r.owner_id=:ownerId
                )
                select to_jsonb(metrics.*)::text as metrics,
                  to_jsonb(review_metrics.*)::text as review_metrics,
                  coalesce((select jsonb_agg(to_jsonb(t.*) order by t.period_start)
                    from (select case when :granularity='week'
                        then date_trunc('week',applied_date)::date
                        else date_trunc('month',applied_date)::date end as period_start,
                      count(*)::int as applications,
                      count(*) filter(where interviewed)::int as interviewed,
                      count(*) filter(where status='offer')::int as offers
                      from cohort group by period_start) t),'[]'::jsonb)::text as trend,
                  coalesce((select jsonb_agg(to_jsonb(s.*) order by s.stage)
                    from (select stage,count(*)::int as count
                      from cohort c cross join lateral unnest(c.stages) stage
                      group by stage) s),'[]'::jsonb)::text as stage_rows,
                  coalesce((select jsonb_agg(to_jsonb(d.*) order by d.key)
                    from (select type as key,count(*)::int as applications,
                      count(*) filter(where interviewed)::int as interviewed,
                      count(*) filter(where status='offer')::int as offers
                      from cohort group by type) d),'[]'::jsonb)::text as type_rows,
                  coalesce((select jsonb_agg(to_jsonb(d.*) order by d.key)
                    from (select city as key,count(*)::int as applications,
                      count(*) filter(where interviewed)::int as interviewed,
                      count(*) filter(where status='offer')::int as offers
                      from cohort group by city) d),'[]'::jsonb)::text as city_rows,
                  coalesce((select jsonb_agg(to_jsonb(r.*) order by r.stage)
                    from (select review.stage_snapshot::text as stage,count(*)::int as total,
                      count(*) filter(where review.round_result::text='pending')::int as pending,
                      count(*) filter(where review.round_result::text='passed')::int as passed,
                      count(*) filter(where review.round_result::text='failed')::int as failed
                      from cohort c join interview_reviews review
                        on review.application_id=c.id and review.owner_id=:ownerId
                      group by review.stage_snapshot) r),'[]'::jsonb)::text as review_stage_rows
                from metrics cross join review_metrics
                """, params, (rs, row) -> new AnalyticsReportAggregate(
                metrics(json(rs.getString("metrics"))),
                reviewMetrics(json(rs.getString("review_metrics"))),
                trends(json(rs.getString("trend"))),
                stages(json(rs.getString("stage_rows"))),
                dimensions(json(rs.getString("type_rows"))),
                dimensions(json(rs.getString("city_rows"))),
                reviewStages(json(rs.getString("review_stage_rows")))));
    }

    @Override
    public List<String> availableCities(String ownerId) {
        return jdbc.query("""
                select distinct city from applications
                where owner_id=:ownerId and city is not null and trim(city)<>''
                order by city
                """, new MapSqlParameterSource("ownerId", ownerId),
                (rs, row) -> rs.getString("city"));
    }

    private JsonNode json(String value) {
        return mapper.readTree(value);
    }

    private Metrics metrics(JsonNode node) {
        JsonNode median = node.get("median_days_to_first_interview");
        return new Metrics(number(node,"applications"),number(node,"interviewed"),
                number(node,"offers"),number(node,"final_interviews"),
                number(node,"path_offers"),median == null || median.isNull() ? null : median.doubleValue(),
                number(node,"offers_without_interview"),number(node,"offers_without_final"));
    }

    private ReviewMetrics reviewMetrics(JsonNode node) {
        return new ReviewMetrics(number(node,"total"),number(node,"completed"),
                number(node,"resolved"),number(node,"passed"));
    }

    private List<Trend> trends(JsonNode nodes) {
        var result = new ArrayList<Trend>();
        for (JsonNode node : nodes) {
            result.add(new Trend(LocalDate.parse(node.get("period_start").textValue()),
                    number(node,"applications"),number(node,"interviewed"),number(node,"offers")));
        }
        return List.copyOf(result);
    }

    private List<StageCount> stages(JsonNode nodes) {
        var result = new ArrayList<StageCount>();
        for (JsonNode node : nodes) {
            result.add(new StageCount(node.get("stage").textValue(),number(node,"count")));
        }
        return List.copyOf(result);
    }

    private List<Dimension> dimensions(JsonNode nodes) {
        var result = new ArrayList<Dimension>();
        for (JsonNode node : nodes) {
            result.add(new Dimension(node.get("key").textValue(),number(node,"applications"),
                    number(node,"interviewed"),number(node,"offers")));
        }
        return List.copyOf(result);
    }

    private List<ReviewStage> reviewStages(JsonNode nodes) {
        var result = new ArrayList<ReviewStage>();
        for (JsonNode node : nodes) {
            result.add(new ReviewStage(node.get("stage").textValue(),
                    number(node,"total"),number(node,"pending"),
                    number(node,"passed"),number(node,"failed")));
        }
        return List.copyOf(result);
    }

    private int number(JsonNode node, String field) {
        return node.get(field).intValue();
    }
}
