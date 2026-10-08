package com.jobtrace.interviews.infrastructure;

import com.jobtrace.interviews.application.PublicInterviewQuery;
import com.jobtrace.interviews.domain.PublicInterviewContentSanitizer;
import com.jobtrace.interviews.domain.PublicInterviewCriteria;
import com.jobtrace.interviews.domain.PublicInterviewCriteria.Sort;
import com.jobtrace.interviews.domain.PublicInterviewCursorCodec;
import com.jobtrace.interviews.domain.PublicInterviewCursorCodec.Cursor;
import com.jobtrace.interviews.domain.PublicInterviewPage;
import com.jobtrace.interviews.domain.PublicInterviewPage.Author;
import com.jobtrace.interviews.domain.PublicInterviewPage.Comment;
import com.jobtrace.interviews.domain.PublicInterviewPage.Engagement;
import com.jobtrace.interviews.domain.PublicInterviewPage.Facets;
import com.jobtrace.interviews.domain.PublicInterviewPage.Item;
import com.jobtrace.interviews.domain.PublicInterviewPage.Question;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Public-only read adapter; SQL selects an explicit privacy-safe projection. */
@Repository
public class PostgresPublicInterviewQuery implements PublicInterviewQuery {

    private static final String AVAILABLE = """
            r.visibility='public' and r.status='completed' and r.published_at is not null
            """;
    private static final String ROOT = """
            from interview_reviews r
            join applications a on a.id=r.application_id
            left join application_stage_occurrences s on s.id=r.stage_occurrence_id
            left join users u on u.id=r.owner_id and r.author_mode='attributed'
            """;
    private static final String COLUMNS = """
            r.id,r.published_at,r.hot_score,r.interviewed_on,
            r.author_mode::text as author_mode,r.highlights,r.gaps,
            r.like_count,r.comment_count,r.view_count,
            a.company_name,a.position_name,a.city,
            coalesce(s.stage,r.stage_snapshot)::text as display_stage,
            coalesce(u.display_username,u.username,u.display_name) as author_username,
            u.image as author_image,
            (select count(*)::int from interview_questions q
              where q.interview_review_id=r.id) as question_count,
            exists(select 1 from interview_review_likes viewer_like
              where viewer_like.interview_review_id=r.id
                and viewer_like.user_id=:viewerId) as liked_by_viewer
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PublicInterviewCursorCodec cursors;

    public PostgresPublicInterviewQuery(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.cursors = new PublicInterviewCursorCodec(mapper);
    }

    @Override
    public PublicInterviewPage list(String viewerId, PublicInterviewCriteria criteria) {
        var values = new MapSqlParameterSource().addValue("viewerId", viewerId);
        String filters = filters(criteria, values);
        Integer total = jdbc.queryForObject("select count(*)::int " + ROOT
                + " where " + AVAILABLE + filters, values, Integer.class);
        Facets facets = facets();
        String cursorFilter = cursorFilter(criteria, values);
        values.addValue("pageSize", criteria.limit() + 1);
        String order = criteria.sort() == Sort.HOT
                ? "r.hot_score desc,r.published_at desc,r.id desc"
                : "r.published_at desc,r.id desc";
        List<SummaryRow> selected = jdbc.query("select " + COLUMNS + ROOT
                + " where " + AVAILABLE + filters + cursorFilter
                + " order by " + order + " limit :pageSize", values,
                (rs, row) -> summary(rs));
        boolean more = selected.size() > criteria.limit();
        List<SummaryRow> pageRows = selected.stream().limit(criteria.limit()).toList();
        List<Item> items = content(pageRows, 2);
        String next = null;
        if (more) {
            SummaryRow last = pageRows.getLast();
            String value = criteria.sort() == Sort.HOT
                    ? last.hotScore() + "|" + last.publishedAt() : last.publishedAt().toString();
            next = cursors.encode(new Cursor(value, last.id(), last.publishedAt(),
                    criteria.sort() == Sort.HOT ? last.hotScore() : null));
        }
        return new PublicInterviewPage(items, next, total, criteria.limit(), facets);
    }

    @Override
    public Optional<Item> findDetail(String viewerId, UUID id) {
        try {
            jdbc.queryForObject("select view_count from public.record_public_interview_view(:viewerId,:id)",
                    Map.of("viewerId", viewerId, "id", id), Integer.class);
        } catch (DataAccessException exception) {
            if (missing(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
        var values = new MapSqlParameterSource().addValue("viewerId", viewerId).addValue("id", id);
        List<SummaryRow> rows = jdbc.query("select " + COLUMNS + ROOT
                + " where " + AVAILABLE + " and r.id=:id", values,
                (rs, row) -> summary(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(content(rows, 20).getFirst());
    }

    private Facets facets() {
        return jdbc.queryForObject("""
                select
                  coalesce(array_agg(distinct a.city order by a.city)
                    filter(where nullif(trim(a.city),'') is not null),'{}') as cities,
                  coalesce(array_agg(distinct a.position_name order by a.position_name),'{}')
                    as positions
                from interview_reviews r join applications a on a.id=r.application_id
                """ + " where " + AVAILABLE, new MapSqlParameterSource(),
                (rs, row) -> new Facets(arrayValues(rs.getArray("cities")),
                        arrayValues(rs.getArray("positions"))));
    }

    private static List<String> arrayValues(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] raw = (Object[]) sqlArray.getArray();
        var result = new ArrayList<String>(raw.length);
        for (Object value : raw) {
            result.add(String.valueOf(value));
        }
        return List.copyOf(result);
    }

    private String filters(PublicInterviewCriteria criteria, MapSqlParameterSource values) {
        StringBuilder sql = new StringBuilder();
        if (!criteria.query().isEmpty()) {
            sql.append(" and lower(a.company_name) like :query");
            values.addValue("query", "%" + criteria.query().toLowerCase(Locale.ROOT) + "%");
        }
        if (!criteria.city().isEmpty()) {
            sql.append(" and a.city=:city");
            values.addValue("city", criteria.city());
        }
        if (!criteria.position().isEmpty()) {
            sql.append(" and a.position_name=:position");
            values.addValue("position", criteria.position());
        }
        return sql.toString();
    }

    private String cursorFilter(PublicInterviewCriteria criteria, MapSqlParameterSource values) {
        if (criteria.cursor() == null) {
            return "";
        }
        Cursor cursor = cursors.decode(criteria.cursor(), criteria.sort());
        values.addValue("cursorId", cursor.id())
                .addValue("publishedAt", Timestamp.from(cursor.publishedAt()));
        if (criteria.sort() == Sort.HOT) {
            values.addValue("score", cursor.score());
            return " and (r.hot_score,r.published_at,r.id)"
                    + "<(:score,:publishedAt,:cursorId)";
        }
        return " and (r.published_at,r.id)<(:publishedAt,:cursorId)";
    }

    private List<Item> content(List<SummaryRow> rows, int commentLimit) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(SummaryRow::id).toList();
        var values = new MapSqlParameterSource().addValue("ids", ids)
                .addValue("commentLimit", commentLimit);
        Map<UUID, List<Question>> questions = new HashMap<>();
        jdbc.query("""
                select q.interview_review_id,q.category::text as category,q.question,
                  q.original_answer,q.follow_up_notes,q.improved_answer
                from interview_questions q
                where q.interview_review_id in (:ids)
                order by q.interview_review_id,q.sort_order,q.id
                """, values, (RowCallbackHandler) rs -> {
                    questions.computeIfAbsent(
                        rs.getObject("interview_review_id", UUID.class), key -> new ArrayList<>())
                        .add(new Question(rs.getString("category"),
                                PublicInterviewContentSanitizer.sanitize(rs.getString("question")),
                                rs.getString("original_answer"),rs.getString("follow_up_notes"),
                                rs.getString("improved_answer")));
                });
        Map<UUID, List<Comment>> comments = new HashMap<>();
        jdbc.query("""
                select recent.interview_review_id,recent.id,recent.content,recent.created_at,
                  coalesce(u.display_username,u.username,u.display_name) as username,u.image
                from (
                  select c.*,row_number() over(partition by c.interview_review_id
                    order by c.created_at desc,c.id desc) as rn
                  from interview_review_comments c where c.interview_review_id in (:ids)
                ) recent join users u on u.id=recent.user_id
                where recent.rn<=:commentLimit
                order by recent.interview_review_id,recent.created_at desc,recent.id desc
                """, values, (RowCallbackHandler) rs -> {
                    comments.computeIfAbsent(
                        rs.getObject("interview_review_id", UUID.class), key -> new ArrayList<>())
                        .add(new Comment(rs.getString("id"),rs.getString("content"),
                                timestamp(rs.getTimestamp("created_at")),
                                new Author(rs.getString("username"),rs.getString("image"))));
                });
        return rows.stream().map(row -> new Item(row.id().toString(), row.companyName(),
                row.positionName(), row.city(), row.stage(), row.interviewedOn(),
                row.publishedAt().toString(), row.questionCount(), row.author(),
                row.engagement(), row.highlights(), row.gaps(),
                questions.getOrDefault(row.id(), List.of()),
                comments.getOrDefault(row.id(), List.of()))).toList();
    }

    private static SummaryRow summary(ResultSet rs) throws SQLException {
        Author author = "attributed".equals(rs.getString("author_mode"))
                ? new Author(rs.getString("author_username"),rs.getString("author_image")) : null;
        return new SummaryRow(rs.getObject("id", UUID.class),
                rs.getTimestamp("published_at").toInstant(), rs.getLong("hot_score"),
                rs.getObject("interviewed_on", LocalDate.class),rs.getString("company_name"),
                rs.getString("position_name"),rs.getString("city"),rs.getString("display_stage"),
                rs.getInt("question_count"),author,
                new Engagement(rs.getInt("like_count"),rs.getInt("comment_count"),
                        rs.getInt("view_count"),rs.getBoolean("liked_by_viewer")),
                rs.getString("highlights"),rs.getString("gaps"));
    }

    private static String timestamp(Timestamp value) {
        return value.toInstant().toString();
    }

    private static boolean missing(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql && "P0002".equals(sql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record SummaryRow(UUID id, Instant publishedAt, long hotScore,
            LocalDate interviewedOn, String companyName, String positionName, String city,
            String stage, int questionCount, Author author, Engagement engagement,
            String highlights, String gaps) {}
}
