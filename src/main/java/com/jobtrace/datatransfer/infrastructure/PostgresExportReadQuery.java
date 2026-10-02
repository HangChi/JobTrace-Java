package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.ExportReadQuery;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized owner-bound reads against the unchanged export source tables. */
@Repository
public class PostgresExportReadQuery implements ExportReadQuery {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresExportReadQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ApplicationExportRow> applications(
            String ownerId, ApplicationExportSelection selection) {
        var parameters = new MapSqlParameterSource().addValue("ownerId", ownerId);
        StringBuilder filters = new StringBuilder("a.owner_id = :ownerId");
        if (selection.scope() == ApplicationExportSelection.Scope.SELECTED) {
            filters.append(" and a.id in (:ids)");
            parameters.addValue("ids", selection.ids());
        } else if (selection.scope() == ApplicationExportSelection.Scope.FILTERED) {
            appendFiltered(filters, parameters, selection);
        }
        return jdbc.query("""
                select a.id, a.company_name, a.position_name, a.city, a.job_url,
                  a.applied_date, a.type::text as type, a.status::text as status,
                  a.latest_date, a.notes, a.created_at, a.updated_at,
                  coalesce((select string_agg(
                    s.stage::text || '/' || case s.stage
                      when 'screening' then '简历筛选'
                      when 'assessment' then '测评/AI测评'
                      when 'written_test' then '笔试'
                      when 'interview_1' then '一面/AI面'
                      when 'interview_2' then '二面'
                      when 'interview_3' then '三面'
                      when 'hr_interview' then 'HR 面'
                      when 'final_interview' then '终面' end || ' + ' || s.occurred_on::text,
                    '；' order by s.occurred_on, s.created_at, s.id)
                    from application_stage_occurrences s
                    where s.application_id = a.id), '') as stage_history
                from applications a
                where """ + " " + filters + " order by a.applied_date desc, a.id asc",
                parameters, (rs, row) -> application(rs));
    }

    private static void appendFiltered(StringBuilder filters, MapSqlParameterSource parameters,
            ApplicationExportSelection selection) {
        if (selection.query() != null) {
            filters.append(" and lower(a.company_name || ' ' || a.position_name) like :query");
            parameters.addValue("query", "%" + selection.query().toLowerCase(Locale.ROOT) + "%");
        }
        appendSet(filters, parameters, "statuses", "a.status::text", selection.statuses());
        appendSet(filters, parameters, "types", "a.type::text", selection.types());
        if (!selection.stages().isEmpty()) {
            filters.append(" and exists (select 1 from application_stage_occurrences fs "
                    + "where fs.application_id = a.id and fs.stage::text in (:stages))");
            parameters.addValue("stages", selection.stages());
        }
        appendSet(filters, parameters, "cities", "a.city", selection.cities());
        if (selection.appliedFrom() != null) {
            filters.append(" and a.applied_date >= :appliedFrom");
            parameters.addValue("appliedFrom", selection.appliedFrom());
        }
        if (selection.appliedTo() != null) {
            filters.append(" and a.applied_date <= :appliedTo");
            parameters.addValue("appliedTo", selection.appliedTo());
        }
    }

    private static void appendSet(StringBuilder filters, MapSqlParameterSource parameters,
            String name, String column, List<String> values) {
        if (!values.isEmpty()) {
            filters.append(" and ").append(column).append(" in (:").append(name).append(')');
            parameters.addValue(name, values);
        }
    }

    private static ApplicationExportRow application(ResultSet rs) throws SQLException {
        return new ApplicationExportRow(rs.getString("id"), rs.getString("company_name"),
                rs.getString("position_name"), rs.getString("city"), rs.getString("job_url"),
                rs.getObject("applied_date", java.time.LocalDate.class).toString(),
                typeLabel(rs.getString("type")), statusLabel(rs.getString("status")),
                rs.getObject("latest_date", java.time.LocalDate.class).toString(),
                rs.getString("stage_history"), rs.getString("notes"),
                timestamp(rs.getTimestamp("created_at")), timestamp(rs.getTimestamp("updated_at")));
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "summer_internship" -> "暑期实习";
            case "daily_internship" -> "日常实习";
            case "spring_recruitment" -> "春招";
            case "early_campus_recruitment" -> "秋招提前批";
            case "campus_recruitment" -> "秋招";
            case "social_recruitment" -> "社招";
            default -> throw new IllegalArgumentException("Unknown application type");
        };
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "submitted" -> "已投递";
            case "offer" -> "Offer";
            case "refused" -> "拒绝";
            default -> throw new IllegalArgumentException("Unknown application status");
        };
    }

    private static String timestamp(Timestamp value) {
        return UTC_MILLIS.format(value.toInstant());
    }

    @Override
    public List<InterviewExportDocument> interviews(
            String ownerId, InterviewExportSelection selection) {
        var parameters = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("ids", selection.ids());
        var roots = jdbc.query("""
                select r.id, a.company_name, a.position_name,
                  coalesce(s.stage, r.stage_snapshot)::text as stage,
                  r.duration_minutes, r.highlights, r.gaps
                from interview_reviews r
                join applications a on a.id = r.application_id and a.owner_id = :ownerId
                left join application_stage_occurrences s
                  on s.id = r.stage_occurrence_id and s.application_id = a.id
                where r.owner_id = :ownerId and r.id in (:ids)
                """, parameters, (rs, row) -> new ReviewRoot(
                rs.getString("id"), rs.getString("company_name"),
                rs.getString("position_name"), rs.getString("stage"),
                (Integer) rs.getObject("duration_minutes"),
                rs.getString("highlights"), rs.getString("gaps")));
        if (roots.isEmpty()) {
            return List.of();
        }
        List<java.util.UUID> ownedIds = roots.stream().map(ReviewRoot::id)
                .map(java.util.UUID::fromString).toList();
        var children = new HashMap<String, List<InterviewExportDocument.Question>>();
        var childParameters = new MapSqlParameterSource().addValue("ids", ownedIds);
        jdbc.query("""
                select q.interview_review_id, q.question, q.original_answer,
                  q.follow_up_notes, q.improved_answer, q.self_rating
                from interview_questions q
                where q.interview_review_id in (:ids)
                order by q.interview_review_id, q.sort_order, q.id
                """, childParameters, (org.springframework.jdbc.core.RowCallbackHandler) rs -> children.computeIfAbsent(
                rs.getString("interview_review_id"), ignored -> new ArrayList<>()).add(
                new InterviewExportDocument.Question(rs.getString("question"),
                        rs.getString("original_answer"), rs.getString("follow_up_notes"),
                        rs.getString("improved_answer"), (Integer) rs.getObject("self_rating"))));
        var actionCounts = new HashMap<String, Integer>();
        jdbc.query("""
                select interview_review_id, count(*) as action_count
                from interview_action_items where interview_review_id in (:ids)
                group by interview_review_id
                """, childParameters, (org.springframework.jdbc.core.RowCallbackHandler) rs -> actionCounts.put(
                rs.getString("interview_review_id"), rs.getInt("action_count")));
        var byId = new HashMap<String, InterviewExportDocument>();
        for (var root : roots) {
            byId.put(root.id(), new InterviewExportDocument(root.id(), root.companyName(),
                    root.positionName(), root.stage(), root.durationMinutes(),
                    children.getOrDefault(root.id(), List.of()), root.highlights(), root.gaps(),
                    actionCounts.getOrDefault(root.id(), 0)));
        }
        return selection.ids().stream().map(java.util.UUID::toString).map(byId::get)
                .filter(java.util.Objects::nonNull).toList();
    }

    private record ReviewRoot(String id, String companyName, String positionName,
            String stage, Integer durationMinutes, String highlights, String gaps) {}
}
