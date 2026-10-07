package com.jobtrace.interviews.infrastructure;

import com.jobtrace.interviews.application.PublicInterviewEngagementStore;
import com.jobtrace.interviews.domain.PublicInterviewNotFoundException;
import com.jobtrace.interviews.domain.PublicInterviewPage.Author;
import com.jobtrace.interviews.domain.PublicInterviewPage.Comment;
import com.jobtrace.interviews.domain.PublicInterviewPage.CommentResult;
import com.jobtrace.interviews.domain.PublicInterviewPage.Engagement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresPublicInterviewEngagementStore implements PublicInterviewEngagementStore {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresPublicInterviewEngagementStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Engagement toggleLike(String viewerId, UUID id) {
        try {
            Engagement result = jdbc.queryForObject("""
                    select liked,like_count,comment_count,view_count
                    from public.toggle_public_interview_like(:viewerId,:id)
                    """, Map.of("viewerId", viewerId, "id", id), (rs, row) ->
                    new Engagement(rs.getInt("like_count"),rs.getInt("comment_count"),
                            rs.getInt("view_count"),rs.getBoolean("liked")));
            if (result == null) {
                throw new PublicInterviewNotFoundException();
            }
            return result;
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional
    public CommentResult addComment(String viewerId, UUID id, String content) {
        var values = new MapSqlParameterSource().addValue("viewerId", viewerId)
                .addValue("id", id).addValue("content", content);
        try {
            List<Comment> created = jdbc.query("""
                    select created.id,created.content,created.created_at,
                      coalesce(u.display_username,u.username,u.display_name) as username,u.image
                    from public.add_public_interview_comment(:viewerId,:id,:content) created
                    join users u on u.id=created.user_id
                    """, values, (rs, row) -> new Comment(rs.getString("id"),
                    rs.getString("content"),timestamp(rs.getTimestamp("created_at")),
                    new Author(rs.getString("username"),rs.getString("image"))));
            Engagement engagement = jdbc.queryForObject("""
                    select r.like_count,r.comment_count,r.view_count,
                      exists(select 1 from interview_review_likes l
                        where l.interview_review_id=r.id and l.user_id=:viewerId) as liked
                    from interview_reviews r where r.id=:id
                    """, values, (rs, row) -> new Engagement(rs.getInt("like_count"),
                    rs.getInt("comment_count"),rs.getInt("view_count"),rs.getBoolean("liked")));
            if (created.isEmpty() || engagement == null) {
                throw new PublicInterviewNotFoundException();
            }
            return new CommentResult(created.getFirst(), engagement);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<Comment> listComments(UUID id) {
        var values = new MapSqlParameterSource().addValue("id", id);
        List<CommentRow> rows = jdbc.query("""
                select r.id as review_id,c.id,c.content,c.created_at,
                  coalesce(u.display_username,u.username,u.display_name) as username,u.image
                from interview_reviews r
                left join lateral (
                  select * from interview_review_comments
                  where interview_review_id=r.id
                  order by created_at desc,id desc limit 50
                ) c on true
                left join users u on u.id=c.user_id
                where r.id=:id and r.visibility='public' and r.status='completed'
                  and r.published_at is not null
                order by c.created_at desc,c.id desc
                """, values, (rs, row) -> new CommentRow(rs.getString("id"),
                rs.getString("content"),rs.getTimestamp("created_at"),
                rs.getString("username"),rs.getString("image")));
        if (rows.isEmpty()) {
            throw new PublicInterviewNotFoundException();
        }
        var comments = new ArrayList<Comment>();
        for (CommentRow row : rows) {
            if (row.id() != null) {
                comments.add(new Comment(row.id(),row.content(),timestamp(row.createdAt()),
                        new Author(row.username(),row.image())));
            }
        }
        return List.copyOf(comments);
    }

    private static String timestamp(Timestamp value) {
        return value.toInstant().toString();
    }

    private static RuntimeException translate(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                if ("P0002".equals(sql.getSQLState())) {
                    return new PublicInterviewNotFoundException();
                }
                if ("22023".equals(sql.getSQLState())) {
                    return new IllegalArgumentException("评论内容长度必须为 1–1000 字符。");
                }
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private record CommentRow(String id, String content, Timestamp createdAt,
            String username, String image) {}
}
