package com.jobtrace.admin.infrastructure;

import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.domain.AdminAuditEvent;
import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.AdminSummary;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parameterized, read-only adapter over the legacy administration tables. */
@Repository
public class PostgresAdminReadQuery implements AdminReadQuery {

    private static final int CONTENT_PAGE_SIZE = 10;
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);
    private static final String USER_FIELDS = """
            u.id,u.username,u.display_username,u.email,u.recovery_email,u.role,u.disabled,
            u.access_version,u.created_at,
            (select max(s.created_at) from sessions s where s.user_id=u.id) last_sign_in_at,
            (select count(*) from applications a where a.owner_id=u.id) application_count,
            (select count(*) from interview_reviews i where i.owner_id=u.id) interview_count
            """;
    private static final String USER_CONDITIONS = """
            (cast(:pattern as text) is null or
              lower(coalesce(u.username,'') || ' ' || u.email || ' ' ||
                coalesce(u.recovery_email,'')) like lower(:pattern))
            and (cast(:role as text) is null or u.role=:role)
            and (cast(:disabled as boolean) is null or u.disabled=:disabled)
            and (cast(:registeredFrom as date) is null or u.created_at >=
              (cast(:registeredFrom as date)::timestamp at time zone 'Asia/Shanghai'))
            and (cast(:registeredTo as date) is null or u.created_at <
              ((cast(:registeredTo as date)+1)::timestamp at time zone 'Asia/Shanghai'))
            """;
    private static final String AUDIT_FIELDS = """
            e.id,e.request_id,e.actor_id,e.actor_identifier_snapshot,e.target_user_id,
            e.target_identifier_snapshot,e.event_type,e.outcome,e.reason,e.before_data,
            e.after_data,e.failure_code,e.created_at
            """;
    private static final String AUDIT_CONDITIONS = """
            (cast(:actorPattern as text) is null or e.actor_id=:actor or
              lower(e.actor_identifier_snapshot) like lower(:actorPattern))
            and (cast(:targetPattern as text) is null or e.target_user_id=:target or
              lower(e.target_identifier_snapshot) like lower(:targetPattern))
            and (cast(:eventType as text) is null or e.event_type=:eventType)
            and (cast(:outcome as text) is null or e.outcome=:outcome)
            and (cast(:occurredFrom as date) is null or e.created_at >=
              (cast(:occurredFrom as date)::timestamp at time zone 'Asia/Shanghai'))
            and (cast(:occurredTo as date) is null or e.created_at <
              ((cast(:occurredTo as date)+1)::timestamp at time zone 'Asia/Shanghai'))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresAdminReadQuery(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public AdminSummary.Counts counts() {
        return jdbc.queryForObject("""
                select (select count(*) from users) users,
                  (select count(*) from users where disabled=false) active_users,
                  (select count(*) from users where disabled=true) disabled_users,
                  (select count(*) from users where role='admin' and disabled=false)
                    administrators,
                  (select count(*) from applications) applications,
                  (select count(*) from interview_reviews) interviews
                """, new MapSqlParameterSource(), (rs, row) -> new AdminSummary.Counts(
                rs.getInt("users"), rs.getInt("active_users"),
                rs.getInt("disabled_users"), rs.getInt("administrators"),
                rs.getInt("applications"), rs.getInt("interviews")));
    }

    @Override
    public AdminSummary.Activity activity() {
        AdminSummary.ActivityWindows windows = jdbc.queryForObject("""
                with bounds as (select (now() at time zone 'Asia/Shanghai')::date today)
                select
                  (select count(*) from users,bounds where
                    (created_at at time zone 'Asia/Shanghai')::date >= today-6) registered_7d,
                  (select count(distinct s.user_id) from sessions s join users u on u.id=s.user_id,
                    bounds where u.disabled=false and
                    (s.created_at at time zone 'Asia/Shanghai')::date >= today-6) active_7d,
                  (select count(*) from users,bounds where
                    (created_at at time zone 'Asia/Shanghai')::date >= today-29) registered_30d,
                  (select count(distinct s.user_id) from sessions s join users u on u.id=s.user_id,
                    bounds where u.disabled=false and
                    (s.created_at at time zone 'Asia/Shanghai')::date >= today-29) active_30d
                """, new MapSqlParameterSource(), (rs, row) ->
                new AdminSummary.ActivityWindows(rs.getInt("registered_7d"),
                        rs.getInt("active_7d"), rs.getInt("registered_30d"),
                        rs.getInt("active_30d")));
        List<AdminSummary.ActivityPoint> trend = jdbc.query("""
                with days as (
                  select (now() at time zone 'Asia/Shanghai')::date-(29-day_index)::int activity_date
                  from generate_series(0,29) day_index
                ), registrations as (
                  select (created_at at time zone 'Asia/Shanghai')::date activity_date,count(*) total
                  from users where (created_at at time zone 'Asia/Shanghai')::date >=
                    (now() at time zone 'Asia/Shanghai')::date-29 group by 1
                ), activity as (
                  select (s.created_at at time zone 'Asia/Shanghai')::date activity_date,
                    count(distinct s.user_id) total
                  from sessions s join users u on u.id=s.user_id
                  where u.disabled=false and (s.created_at at time zone 'Asia/Shanghai')::date >=
                    (now() at time zone 'Asia/Shanghai')::date-29 group by 1
                )
                select to_char(days.activity_date,'YYYY-MM-DD') date,
                  coalesce(registrations.total,0) registered_users,
                  coalesce(activity.total,0) active_users
                from days left join registrations using(activity_date)
                  left join activity using(activity_date)
                order by days.activity_date
                """, new MapSqlParameterSource(), (rs, row) -> new AdminSummary.ActivityPoint(
                rs.getString("date"), rs.getInt("registered_users"),
                rs.getInt("active_users")));
        return new AdminSummary.Activity(windows, trend);
    }

    @Override
    public PageResult<AdminUser.Summary> users(AdminQuery.Users query) {
        MapSqlParameterSource parameters = userParameters(query);
        int total = Optional.ofNullable(jdbc.queryForObject(
                "select count(*) from users u where " + USER_CONDITIONS,
                parameters, Integer.class)).orElse(0);
        List<AdminUser.Summary> items = jdbc.query(
                "select " + USER_FIELDS + " from users u where " + USER_CONDITIONS
                        + " order by u.created_at desc,u.id desc limit :limit offset :offset",
                parameters, (rs, row) -> user(rs));
        return PageResult.of(items, total, query.page(), query.limit());
    }

    @Override
    public Optional<AdminUser.Summary> user(String userId) {
        List<AdminUser.Summary> users = jdbc.query(
                "select " + USER_FIELDS + " from users u where u.id=:userId",
                new MapSqlParameterSource("userId", userId), (rs, row) -> user(rs));
        return users.stream().findFirst();
    }

    @Override
    public List<AdminAuditEvent> recentAudit(String userId, int limit) {
        return jdbc.query("select " + AUDIT_FIELDS + " from admin_audit_events e "
                        + "where e.target_user_id=:userId order by e.created_at desc,e.id desc "
                        + "limit :limit",
                new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit),
                (rs, row) -> audit(rs));
    }

    @Override
    public PageResult<AdminUser.Application> applications(String userId, int page) {
        var parameters = contentParameters(userId, page);
        int total = count("applications", "owner_id", userId);
        List<AdminUser.Application> items = jdbc.query("""
                select a.id,a.company_name,a.position_name,a.city,a.job_url,a.type,a.status,
                  a.applied_date,a.latest_date,a.notes,
                  coalesce((select jsonb_agg(jsonb_build_object('stage',s.stage,
                    'occurredOn',s.occurred_on) order by s.occurred_on,s.created_at)
                    from application_stage_occurrences s where s.application_id=a.id),'[]')::text
                    stages
                from applications a where a.owner_id=:userId
                order by a.latest_date desc,a.id desc limit :limit offset :offset
                """, parameters, (rs, row) -> new AdminUser.Application(
                rs.getString("id"), rs.getString("company_name"),
                rs.getString("position_name"), rs.getString("city"), rs.getString("job_url"),
                rs.getString("type"), rs.getString("status"), date(rs, "applied_date"),
                date(rs, "latest_date"), rs.getString("notes"), stages(rs.getString("stages"))));
        return PageResult.of(items, total, page, CONTENT_PAGE_SIZE);
    }

    @Override
    public PageResult<AdminUser.Interview> interviews(String userId, int page) {
        var parameters = contentParameters(userId, page);
        int total = count("interview_reviews", "owner_id", userId);
        List<AdminUser.Interview> items = jdbc.query("""
                select r.id,r.application_id,a.company_name,a.position_name,
                  coalesce(s.stage,r.stage_snapshot) stage,r.interviewed_on,r.status,
                  r.round_result,r.format,r.duration_minutes,r.interviewer_notes,r.highlights,r.gaps,
                  coalesce((select jsonb_agg(jsonb_build_object('category',q.category,
                    'question',q.question,'originalAnswer',q.original_answer,
                    'followUpNotes',q.follow_up_notes,'improvedAnswer',q.improved_answer,
                    'selfRating',q.self_rating) order by q.sort_order)
                    from interview_questions q where q.interview_review_id=r.id),'[]')::text questions,
                  coalesce((select jsonb_agg(jsonb_build_object('content',i.content,
                    'completed',i.completed) order by i.sort_order)
                    from interview_action_items i where i.interview_review_id=r.id),'[]')::text actions
                from interview_reviews r join applications a on a.id=r.application_id
                left join application_stage_occurrences s on s.id=r.stage_occurrence_id
                where r.owner_id=:userId order by r.interviewed_on desc,r.id desc
                limit :limit offset :offset
                """, parameters, (rs, row) -> new AdminUser.Interview(
                rs.getString("id"), rs.getString("application_id"),
                rs.getString("company_name"), rs.getString("position_name"),
                rs.getString("stage"), date(rs, "interviewed_on"), rs.getString("status"),
                rs.getString("round_result"), rs.getString("format"), integer(rs, "duration_minutes"),
                rs.getString("interviewer_notes"), rs.getString("highlights"), rs.getString("gaps"),
                questions(rs.getString("questions")), actions(rs.getString("actions"))));
        return PageResult.of(items, total, page, CONTENT_PAGE_SIZE);
    }

    @Override
    public PageResult<AdminAuditEvent> audit(AdminQuery.Audit query) {
        MapSqlParameterSource parameters = auditParameters(query);
        int total = Optional.ofNullable(jdbc.queryForObject(
                "select count(*) from admin_audit_events e where " + AUDIT_CONDITIONS,
                parameters, Integer.class)).orElse(0);
        List<AdminAuditEvent> items = jdbc.query(
                "select " + AUDIT_FIELDS + " from admin_audit_events e where "
                        + AUDIT_CONDITIONS + " order by e.created_at desc,e.id desc "
                        + "limit :limit offset :offset",
                parameters, (rs, row) -> audit(rs));
        return PageResult.of(items, total, query.page(), query.limit());
    }

    private int count(String table, String ownerColumn, String userId) {
        if (!Map.of("applications", "owner_id", "interview_reviews", "owner_id")
                .getOrDefault(table, "").equals(ownerColumn)) {
            throw new IllegalArgumentException("unrecognized count target");
        }
        return Optional.ofNullable(jdbc.queryForObject(
                "select count(*) from " + table + " where " + ownerColumn + "=:userId",
                new MapSqlParameterSource("userId", userId), Integer.class)).orElse(0);
    }

    private static MapSqlParameterSource userParameters(AdminQuery.Users query) {
        return new MapSqlParameterSource()
                .addValue("pattern", like(query.q()))
                .addValue("role", query.role() == AdminQuery.UserRole.ALL
                        ? null : query.role().name().toLowerCase(Locale.ROOT))
                .addValue("disabled", query.status() == AdminQuery.UserStatus.ALL
                        ? null : query.status() == AdminQuery.UserStatus.DISABLED)
                .addValue("registeredFrom", query.registeredFrom())
                .addValue("registeredTo", query.registeredTo())
                .addValue("limit", query.limit())
                .addValue("offset", (query.page() - 1) * query.limit());
    }

    private static MapSqlParameterSource auditParameters(AdminQuery.Audit query) {
        return new MapSqlParameterSource()
                .addValue("actor", query.actor()).addValue("actorPattern", like(query.actor()))
                .addValue("target", query.target()).addValue("targetPattern", like(query.target()))
                .addValue("eventType", wire(query.eventType()))
                .addValue("outcome", wire(query.outcome()))
                .addValue("occurredFrom", query.occurredFrom())
                .addValue("occurredTo", query.occurredTo())
                .addValue("limit", query.limit())
                .addValue("offset", (query.page() - 1) * query.limit());
    }

    private static MapSqlParameterSource contentParameters(String userId, int page) {
        return new MapSqlParameterSource().addValue("userId", userId)
                .addValue("limit", CONTENT_PAGE_SIZE)
                .addValue("offset", (page - 1) * CONTENT_PAGE_SIZE);
    }

    private AdminUser.Summary user(ResultSet rs) throws SQLException {
        String internalEmail = rs.getString("email");
        int separator = internalEmail.indexOf('@');
        String emailPrefix = separator < 0 ? internalEmail : internalEmail.substring(0, separator);
        String username = first(rs.getString("display_username"), rs.getString("username"),
                emailPrefix);
        return new AdminUser.Summary(rs.getString("id"), username,
                first(rs.getString("recovery_email"), internalEmail), internalEmail,
                rs.getString("role"), rs.getBoolean("disabled"), rs.getLong("access_version"),
                timestamp(rs.getTimestamp("created_at")), timestamp(rs.getTimestamp("last_sign_in_at")),
                rs.getInt("application_count"), rs.getInt("interview_count"));
    }

    private AdminAuditEvent audit(ResultSet rs) throws SQLException {
        String actorId = rs.getString("actor_id");
        String targetId = rs.getString("target_user_id");
        return new AdminAuditEvent(rs.getString("id"), rs.getString("request_id"), actorId,
                rs.getString("actor_identifier_snapshot"), actorId == null, targetId,
                rs.getString("target_identifier_snapshot"), targetId == null,
                rs.getString("event_type"), rs.getString("outcome"), rs.getString("reason"),
                accessState(rs.getString("before_data")), accessState(rs.getString("after_data")),
                rs.getString("failure_code"), timestamp(rs.getTimestamp("created_at")));
    }

    private AdminAuditEvent.AccessState accessState(String json) {
        if (json == null) {
            return null;
        }
        JsonNode node = tree(json);
        return new AdminAuditEvent.AccessState(node.get("role").asText(),
                node.get("disabled").asBoolean(), node.get("accessVersion").asLong());
    }

    private List<AdminUser.Stage> stages(String json) {
        List<AdminUser.Stage> values = new ArrayList<>();
        for (JsonNode node : tree(json)) {
            values.add(new AdminUser.Stage(node.get("stage").asText(),
                    node.get("occurredOn").asText()));
        }
        return values;
    }

    private List<AdminUser.Question> questions(String json) {
        List<AdminUser.Question> values = new ArrayList<>();
        for (JsonNode node : tree(json)) {
            values.add(new AdminUser.Question(text(node, "category"), text(node, "question"),
                    text(node, "originalAnswer"), text(node, "followUpNotes"),
                    text(node, "improvedAnswer"), nullableInt(node, "selfRating")));
        }
        return values;
    }

    private List<AdminUser.ActionItem> actions(String json) {
        List<AdminUser.ActionItem> values = new ArrayList<>();
        for (JsonNode node : tree(json)) {
            values.add(new AdminUser.ActionItem(text(node, "content"),
                    node.get("completed").asBoolean()));
        }
        return values;
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid administration JSON projection", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String date(ResultSet rs, String column) throws SQLException {
        LocalDate value = rs.getObject(column, LocalDate.class);
        return value == null ? null : value.toString();
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : UTC_MILLIS.format(value.toInstant());
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("at least one fallback is required");
    }

    private static String like(String value) {
        return value == null ? null : "%" + value + "%";
    }

    private static String wire(Enum<?> value) {
        return value.name().equals("ALL") ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
