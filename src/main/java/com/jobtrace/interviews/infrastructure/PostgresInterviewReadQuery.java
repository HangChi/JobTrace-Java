package com.jobtrace.interviews.infrastructure;

import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.domain.InterviewActionItem;
import com.jobtrace.interviews.domain.InterviewCatalog.AuthorMode;
import com.jobtrace.interviews.domain.InterviewCatalog.Format;
import com.jobtrace.interviews.domain.InterviewCatalog.Publication;
import com.jobtrace.interviews.domain.InterviewCatalog.QuestionCategory;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import com.jobtrace.interviews.domain.InterviewCatalog.Visibility;
import com.jobtrace.interviews.domain.InterviewCursorCodec;
import com.jobtrace.interviews.domain.InterviewCursorCodec.Cursor;
import com.jobtrace.interviews.domain.InterviewDetail;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import com.jobtrace.interviews.domain.InterviewPage;
import com.jobtrace.interviews.domain.InterviewQuestion;
import com.jobtrace.interviews.domain.InterviewSummary;
import com.jobtrace.interviews.domain.StageInterviewSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Parameterized, owner-scoped read adapter for the unchanged legacy schema. */
@Repository
public class PostgresInterviewReadQuery implements InterviewReadQuery {

    private static final String ROOT_FROM = """
            from interview_reviews r
            join applications a on a.id = r.application_id and a.owner_id = :ownerId
            left join application_stage_occurrences s
              on s.id = r.stage_occurrence_id and s.application_id = a.id
            """;
    private static final String SUMMARY_COLUMNS = """
            r.id, r.application_id, r.stage_occurrence_id,
            a.company_name, a.position_name,
            coalesce(s.stage, r.stage_snapshot)::text as display_stage,
            r.interviewed_on, r.status::text as status,
            r.round_result::text as round_result,
            r.visibility::text as visibility, r.author_mode::text as author_mode,
            r.published_at,
            (select count(*)::int from interview_questions q
             where q.interview_review_id = r.id) as question_count,
            (select count(*)::int from interview_action_items i
             where i.interview_review_id = r.id) as action_count
            """;
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;
    private final InterviewCursorCodec cursorCodec;

    public PostgresInterviewReadQuery(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.cursorCodec = new InterviewCursorCodec(mapper);
    }

    @Override
    public InterviewPage list(String ownerId, InterviewListCriteria criteria) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("ownerId", ownerId);
        String filters = filters(criteria, parameters);
        int total = jdbc.queryForObject(
                "select count(*) " + ROOT_FROM + " where " + filters,
                parameters, Integer.class);
        String cursor = cursorPredicate(criteria, parameters);
        parameters.addValue("pageSize", criteria.limit() + 1);
        List<InterviewSummary> rows = jdbc.query(
                "select " + SUMMARY_COLUMNS + ROOT_FROM + " where " + filters + cursor
                        + " order by r.interviewed_on desc, r.id desc limit :pageSize",
                parameters, (rs, row) -> summary(rs));
        boolean hasMore = rows.size() > criteria.limit();
        List<InterviewSummary> items = rows.stream().limit(criteria.limit()).toList();
        String next = hasMore ? cursorCodec.encode(new Cursor(
                items.getLast().interviewedOn(), UUID.fromString(items.getLast().id()))) : null;
        return new InterviewPage(items, next, total, criteria.limit());
    }

    @Override
    public Optional<InterviewDetail> findDetail(String ownerId, UUID id) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("id", id);
        List<DetailRow> roots = jdbc.query(
                "select " + SUMMARY_COLUMNS + """
                    , r.format::text as format, r.duration_minutes,
                      r.interviewer_notes, r.highlights, r.gaps, r.version,
                      r.created_at, r.updated_at
                    """ + ROOT_FROM + " where r.owner_id = :ownerId and r.id = :id",
                parameters, (rs, row) -> new DetailRow(summary(rs),
                        rs.getString("format") == null ? null
                                : Format.fromWire(rs.getString("format")).orElseThrow(),
                        (Integer) rs.getObject("duration_minutes"),
                        rs.getString("interviewer_notes"), rs.getString("highlights"),
                        rs.getString("gaps"), rs.getInt("version"),
                        timestamp(rs.getTimestamp("created_at")),
                        timestamp(rs.getTimestamp("updated_at"))));
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        List<InterviewQuestion> questions = jdbc.query("""
                select q.id, q.category::text as category, q.question, q.original_answer,
                  q.follow_up_notes, q.improved_answer, q.self_rating
                from interview_questions q
                join interview_reviews r on r.id = q.interview_review_id
                join applications a on a.id = r.application_id
                where r.id = :id and r.owner_id = :ownerId and a.owner_id = :ownerId
                order by q.sort_order, q.id
                """, parameters, (rs, row) -> new InterviewQuestion(
                rs.getString("id"), QuestionCategory.fromWire(rs.getString("category")).orElseThrow(),
                rs.getString("question"), rs.getString("original_answer"),
                rs.getString("follow_up_notes"), rs.getString("improved_answer"),
                (Integer) rs.getObject("self_rating")));
        List<InterviewActionItem> actions = jdbc.query("""
                select i.id, i.content, i.completed
                from interview_action_items i
                join interview_reviews r on r.id = i.interview_review_id
                join applications a on a.id = r.application_id
                where r.id = :id and r.owner_id = :ownerId and a.owner_id = :ownerId
                order by i.sort_order, i.id
                """, parameters, (rs, row) -> new InterviewActionItem(
                rs.getString("id"), rs.getString("content"), rs.getBoolean("completed")));
        DetailRow root = roots.getFirst();
        InterviewSummary s = root.summary();
        return Optional.of(new InterviewDetail(s.id(), s.applicationId(), s.stageOccurrenceId(),
                s.companyName(), s.positionName(), s.stage(), s.interviewedOn(), s.status(),
                s.roundResult(), s.linked(), s.questionCount(), s.actionCount(),
                s.visibility(), s.authorMode(), s.publishedAt(), root.format(),
                root.durationMinutes(), root.interviewerNotes(), root.highlights(),
                root.gaps(), root.version(), questions, actions, root.createdAt(), root.updatedAt()));
    }

    @Override
    public List<StageInterviewSummary> listForApplication(String ownerId, UUID applicationId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("applicationId", applicationId);
        return jdbc.query("""
                select r.id, r.stage_occurrence_id,
                  coalesce(s.stage, r.stage_snapshot)::text as display_stage,
                  r.interviewed_on, r.status::text as status,
                  (select count(*)::int from interview_questions q
                   where q.interview_review_id = r.id) as question_count
                """ + ROOT_FROM + """
                 where r.owner_id = :ownerId and r.application_id = :applicationId
                 order by r.interviewed_on desc, r.id desc
                """, parameters, (rs, row) -> new StageInterviewSummary(
                rs.getString("id"), Stage.fromWire(rs.getString("display_stage")).orElseThrow(),
                rs.getObject("interviewed_on", LocalDate.class),
                Status.fromWire(rs.getString("status")).orElseThrow(),
                rs.getInt("question_count"), rs.getString("stage_occurrence_id")));
    }

    private static String filters(InterviewListCriteria criteria, MapSqlParameterSource parameters) {
        StringBuilder sql = new StringBuilder("r.owner_id = :ownerId");
        if (criteria.applicationId() != null) {
            sql.append(" and r.application_id = :applicationId");
            parameters.addValue("applicationId", criteria.applicationId());
        }
        if (criteria.query() != null) {
            sql.append("""
                     and (lower(a.company_name || ' ' || a.position_name) like :query
                       or exists (select 1 from interview_questions search_q
                           where search_q.interview_review_id = r.id
                             and lower(search_q.question) like :query))
                    """);
            parameters.addValue("query", "%" + criteria.query().toLowerCase(Locale.ROOT) + "%");
        }
        appendEnum(sql, parameters, "statuses", "r.status", criteria.statuses().stream()
                .map(Status::value).toList());
        appendEnum(sql, parameters, "stages", "coalesce(s.stage, r.stage_snapshot)",
                criteria.stages().stream().map(Stage::value).toList());
        appendEnum(sql, parameters, "results", "r.round_result", criteria.results().stream()
                .map(Result::value).toList());
        if (criteria.interviewedFrom() != null) {
            sql.append(" and r.interviewed_on >= :interviewedFrom");
            parameters.addValue("interviewedFrom", criteria.interviewedFrom());
        }
        if (criteria.interviewedTo() != null) {
            sql.append(" and r.interviewed_on <= :interviewedTo");
            parameters.addValue("interviewedTo", criteria.interviewedTo());
        }
        if (!criteria.publications().isEmpty()) {
            sql.append(" and ((:private and r.visibility = 'private')"
                    + " or (:anonymous and r.visibility = 'public' and r.author_mode = 'anonymous')"
                    + " or (:attributed and r.visibility = 'public' and r.author_mode = 'attributed'))");
            parameters.addValue("private", criteria.publications().contains(Publication.PRIVATE));
            parameters.addValue("anonymous", criteria.publications().contains(Publication.ANONYMOUS));
            parameters.addValue("attributed", criteria.publications().contains(Publication.ATTRIBUTED));
        }
        return sql.toString();
    }

    private static void appendEnum(StringBuilder sql, MapSqlParameterSource parameters,
            String name, String column, List<String> values) {
        if (!values.isEmpty()) {
            sql.append(" and ").append(column).append("::text in (:").append(name).append(')');
            parameters.addValue(name, values);
        }
    }

    private String cursorPredicate(InterviewListCriteria criteria, MapSqlParameterSource parameters) {
        if (criteria.cursor() == null) {
            return "";
        }
        Cursor cursor = cursorCodec.decode(criteria.cursor());
        parameters.addValue("cursorDate", cursor.value());
        parameters.addValue("cursorId", cursor.id());
        return " and (r.interviewed_on, r.id) < (cast(:cursorDate as date), cast(:cursorId as uuid))";
    }

    private static InterviewSummary summary(ResultSet rs) throws SQLException {
        String stageOccurrenceId = rs.getString("stage_occurrence_id");
        return new InterviewSummary(rs.getString("id"), rs.getString("application_id"),
                stageOccurrenceId, rs.getString("company_name"), rs.getString("position_name"),
                Stage.fromWire(rs.getString("display_stage")).orElseThrow(),
                rs.getObject("interviewed_on", LocalDate.class),
                Status.fromWire(rs.getString("status")).orElseThrow(),
                Result.fromWire(rs.getString("round_result")).orElseThrow(),
                stageOccurrenceId != null, rs.getInt("question_count"),
                rs.getInt("action_count"),
                Visibility.fromWire(rs.getString("visibility")).orElseThrow(),
                AuthorMode.fromWire(rs.getString("author_mode")).orElseThrow(),
                timestamp(rs.getTimestamp("published_at")));
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : UTC_MILLIS.format(value.toInstant());
    }

    private record DetailRow(InterviewSummary summary, Format format, Integer durationMinutes,
            String interviewerNotes, String highlights, String gaps, int version,
            String createdAt, String updatedAt) {}
}
