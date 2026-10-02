package com.jobtrace.applications.infrastructure;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationSort;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import com.jobtrace.applications.domain.ApplicationCatalog.SortDirection;
import com.jobtrace.applications.domain.ApplicationCursorCodec;
import com.jobtrace.applications.domain.ApplicationCursorCodec.Cursor;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationDetail.ApplicationEvent;
import com.jobtrace.applications.domain.ApplicationDetail.StageOccurrence;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import com.jobtrace.applications.domain.ApplicationPage;
import com.jobtrace.applications.domain.ApplicationSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresApplicationReadQuery implements ApplicationReadQuery {

    private static final String ROOT_COLUMNS = """
            a.id, a.company_name, a.position_name, a.city, a.job_url,
            a.applied_date, a.type::text as type, a.status::text as status,
            a.latest_date, a.version, a.notes, a.created_at, a.updated_at,
            timeline.latest_date as timeline_latest_date,
            (select string_agg(stage.stage::text, ',' order by stage.stage)
               from (select distinct s.stage from application_stage_occurrences s
                     where s.application_id = a.id) stage) as stages
            """;
    private static final String ROOT_FROM = """
            from applications a
            left join lateral (
              select max(s.occurred_on) as latest_date
              from application_stage_occurrences s
              where s.application_id = a.id
            ) timeline on true
            """;
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationCursorCodec cursorCodec;
    private final ObjectMapper mapper;

    public PostgresApplicationReadQuery(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cursorCodec = new ApplicationCursorCodec(mapper);
    }

    @Override
    public ApplicationPage list(String ownerId, ApplicationListCriteria criteria, LocalDate today) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId);
        String filters = filters(criteria, parameters);
        int total = jdbc.queryForObject(
                "select count(*) from applications a where " + filters,
                parameters,
                Integer.class);

        String order = orderBy(criteria);
        String cursorPredicate = cursorPredicate(criteria, parameters);
        int offset = criteria.cursor() == null
                ? (int) Math.min((long) (criteria.page() - 1) * criteria.limit(), Integer.MAX_VALUE)
                : 0;
        parameters.addValue("limit", criteria.limit() + 1).addValue("offset", offset);
        String sql = "select " + ROOT_COLUMNS + ROOT_FROM
                + " where " + filters + cursorPredicate
                + " order by " + order + " limit :limit offset :offset";
        List<RootRow> rows = jdbc.query(sql, parameters, rootMapper(today));
        boolean hasMore = rows.size() > criteria.limit();
        List<ApplicationSummary> items = rows.stream()
                .limit(criteria.limit())
                .map(RootRow::summary)
                .toList();
        String nextCursor = null;
        if (hasMore) {
            RootRow last = rows.get(criteria.limit() - 1);
            nextCursor = cursorCodec.encode(new Cursor(
                    sortValue(last, criteria.sort()),
                    UUID.fromString(last.summary().id()),
                    criteria.defaultOrder()
                            ? last.summary().status() == ApplicationStatus.SUBMITTED ? 0 : 1
                            : null));
        }
        return new ApplicationPage(items, nextCursor, total, criteria.page(), criteria.limit());
    }

    @Override
    public Optional<ApplicationDetail> findDetail(String ownerId, UUID id, LocalDate today) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("id", id);
        List<RootRow> roots = jdbc.query(
                "select " + ROOT_COLUMNS + ROOT_FROM
                        + " where a.owner_id = :ownerId and a.id = :id",
                parameters,
                rootMapper(today));
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        RootRow root = roots.getFirst();
        List<StageOccurrence> occurrences = jdbc.query("""
                select s.id, s.stage::text as stage, s.occurred_on
                from application_stage_occurrences s
                join applications a on a.id = s.application_id
                where a.owner_id = :ownerId and a.id = :id
                order by s.occurred_on, s.created_at, s.id
                """, parameters, (rs, row) -> new StageOccurrence(
                rs.getString("id"),
                RecruitmentStage.fromWire(rs.getString("stage")).orElseThrow(),
                rs.getObject("occurred_on", LocalDate.class)));
        List<ApplicationEvent> events = jdbc.query("""
                select e.id, e.type::text as type, e.occurred_on,
                  e.before::text as before_json, e.after::text as after_json, e.created_at
                from application_events e
                join applications a on a.id = e.application_id
                where a.owner_id = :ownerId and a.id = :id
                order by e.occurred_on desc, e.created_at desc, e.id desc
                """, parameters, (rs, row) -> new ApplicationEvent(
                rs.getString("id"),
                rs.getString("type"),
                rs.getObject("occurred_on", LocalDate.class),
                json(rs.getString("before_json")),
                json(rs.getString("after_json")),
                timestamp(rs.getTimestamp("created_at"))));
        ApplicationSummary summary = root.summary();
        return Optional.of(new ApplicationDetail(
                summary.id(), summary.companyName(), summary.positionName(),
                summary.city(), summary.jobUrl(), summary.appliedDate(), summary.type(),
                summary.status(), summary.latestDate(), summary.stages(),
                summary.needsFollowUp(), summary.followUpDays(), summary.followUpReason(),
                summary.version(), root.notes(), occurrences, events,
                root.createdAt(), root.updatedAt()));
    }

    private String filters(ApplicationListCriteria criteria, MapSqlParameterSource parameters) {
        StringBuilder sql = new StringBuilder("a.owner_id = :ownerId");
        if (criteria.query() != null) {
            sql.append(" and lower(a.company_name || ' ' || a.position_name) like :query");
            parameters.addValue("query", "%" + criteria.query().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        appendEnumFilter(sql, parameters, "statuses", "a.status", criteria.statuses()
                .stream().map(ApplicationStatus::value).toList());
        appendEnumFilter(sql, parameters, "types", "a.type", criteria.types()
                .stream().map(ApplicationType::value).toList());
        if (!criteria.stages().isEmpty()) {
            sql.append("""
                     and exists (select 1 from application_stage_occurrences fs
                       where fs.application_id = a.id and fs.stage::text in (:stages))
                    """);
            parameters.addValue("stages", criteria.stages().stream()
                    .map(RecruitmentStage::value).toList());
        }
        if (!criteria.cities().isEmpty()) {
            sql.append(" and a.city in (:cities)");
            parameters.addValue("cities", criteria.cities());
        }
        if (criteria.appliedFrom() != null) {
            sql.append(" and a.applied_date >= :appliedFrom");
            parameters.addValue("appliedFrom", criteria.appliedFrom());
        }
        if (criteria.appliedTo() != null) {
            sql.append(" and a.applied_date <= :appliedTo");
            parameters.addValue("appliedTo", criteria.appliedTo());
        }
        return sql.toString();
    }

    private static void appendEnumFilter(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String parameter,
            String column,
            List<String> values) {
        if (!values.isEmpty()) {
            sql.append(" and ").append(column).append("::text in (:").append(parameter).append(')');
            parameters.addValue(parameter, values);
        }
    }

    private String cursorPredicate(
            ApplicationListCriteria criteria,
            MapSqlParameterSource parameters) {
        if (criteria.cursor() == null) {
            return "";
        }
        Cursor cursor = cursorCodec.decode(criteria.cursor());
        parameters.addValue("cursorId", cursor.id());
        if (criteria.defaultOrder()) {
            parameters.addValue("cursorRank", cursor.statusRank() == null ? 0 : cursor.statusRank());
            parameters.addValue("cursorDate", cursorDate(cursor.value()));
            return """
                     and (
                       (case when a.status = 'submitted' then 0 else 1 end) > :cursorRank
                       or ((case when a.status = 'submitted' then 0 else 1 end) = :cursorRank
                           and (a.latest_date, a.id) < (cast(:cursorDate as date), cast(:cursorId as uuid)))
                     )
                    """;
        }
        String operator = criteria.direction() == SortDirection.ASC ? " > " : " < ";
        String column = sortColumn(criteria.sort());
        if (criteria.sort() == ApplicationSort.APPLIED_DATE
                || criteria.sort() == ApplicationSort.LATEST_DATE) {
            parameters.addValue("cursorValue", cursorDate(cursor.value()));
            return " and (" + column + ", a.id)" + operator
                    + "(cast(:cursorValue as date), cast(:cursorId as uuid))";
        }
        parameters.addValue("cursorValue", cursor.value());
        return " and (" + column + ", a.id)" + operator
                + "(:cursorValue, cast(:cursorId as uuid))";
    }

    private static LocalDate cursorDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor date is invalid", exception);
        }
    }

    private static String orderBy(ApplicationListCriteria criteria) {
        if (criteria.defaultOrder()) {
            return "case when a.status = 'submitted' then 0 else 1 end asc, "
                    + "a.latest_date desc, a.id desc";
        }
        String direction = criteria.direction() == SortDirection.ASC ? " asc" : " desc";
        return sortColumn(criteria.sort()) + direction + ", a.id" + direction;
    }

    private static String sortColumn(ApplicationSort sort) {
        return switch (sort) {
            case COMPANY -> "a.company_name";
            case POSITION -> "a.position_name";
            case APPLIED_DATE -> "a.applied_date";
            case LATEST_DATE -> "a.latest_date";
        };
    }

    private static String sortValue(RootRow row, ApplicationSort sort) {
        return switch (sort) {
            case COMPANY -> row.summary().companyName();
            case POSITION -> row.summary().positionName();
            case APPLIED_DATE -> row.summary().appliedDate().toString();
            case LATEST_DATE -> row.summary().latestDate().toString();
        };
    }

    private RowMapper<RootRow> rootMapper(LocalDate today) {
        return (rs, rowNumber) -> {
            LocalDate latestDate = rs.getObject("latest_date", LocalDate.class);
            LocalDate timeline = rs.getObject("timeline_latest_date", LocalDate.class);
            boolean timelineLatest = timeline != null && !timeline.isBefore(latestDate);
            LocalDate activity = timelineLatest ? timeline : latestDate;
            int followUpDays = Math.max(0, (int) java.time.temporal.ChronoUnit.DAYS.between(activity, today));
            ApplicationStatus status = ApplicationStatus.fromWire(rs.getString("status")).orElseThrow();
            boolean needsFollowUp = status == ApplicationStatus.SUBMITTED && followUpDays >= 15;
            String stagesValue = rs.getString("stages");
            List<RecruitmentStage> stages = stagesValue == null
                    ? List.of()
                    : Arrays.stream(stagesValue.split(","))
                            .map(value -> RecruitmentStage.fromWire(value).orElseThrow())
                            .toList();
            ApplicationSummary summary = new ApplicationSummary(
                    rs.getString("id"), rs.getString("company_name"),
                    rs.getString("position_name"), rs.getString("city"),
                    rs.getString("job_url"), rs.getObject("applied_date", LocalDate.class),
                    ApplicationType.fromWire(rs.getString("type")).orElseThrow(),
                    status, latestDate, stages, needsFollowUp, followUpDays,
                    needsFollowUp ? timelineLatest ? "timeline" : "application" : null,
                    rs.getInt("version"));
            return new RootRow(
                    summary, rs.getString("notes"),
                    timestamp(rs.getTimestamp("created_at")),
                    timestamp(rs.getTimestamp("updated_at")));
        };
    }

    private Object json(String value) {
        return value == null ? null : mapper.readTree(value);
    }

    private static String timestamp(Timestamp value) {
        Instant instant = value.toInstant();
        return UTC_MILLIS.format(instant);
    }

    private record RootRow(ApplicationSummary summary, String notes, String createdAt, String updatedAt) {}
}
