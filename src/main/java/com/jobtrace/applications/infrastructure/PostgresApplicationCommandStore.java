package com.jobtrace.applications.infrastructure;

import com.jobtrace.applications.application.ApplicationCommandStore;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationMutation;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.applications.domain.ApplicationWriteException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresApplicationCommandStore implements ApplicationCommandStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public PostgresApplicationCommandStore(
            NamedParameterJdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public UUID create(String ownerId, ApplicationMutation command) {
        try {
            return transactions.execute(status -> createInTransaction(ownerId, command));
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    private UUID createInTransaction(String ownerId, ApplicationMutation command) {
        Map<String, Object> payload = command.payload();
        JobPost post = null;
        if (command.jobMarketPostId() != null) {
            var values = new MapSqlParameterSource()
                    .addValue("ownerId", ownerId)
                    .addValue("postId", command.jobMarketPostId());
            jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended("
                    + "cast(:ownerId as text) || ':' || cast(:postId as text), 0))", values, Object.class);
            List<UUID> existing = jdbc.query("""
                    select application_id from application_job_market_links
                    where owner_id=:ownerId and post_id=:postId
                    """, values, (rs, row) -> rs.getObject(1, UUID.class));
            if (!existing.isEmpty()) {
                throw new ApplicationWriteException(ApplicationWriteException.Kind.ALREADY_LINKED,
                        "你已经记录过这个岗位。");
            }
            List<JobPost> posts = jdbc.query("""
                    select post.id, post.title, company.canonical_name as company_name,
                      post.primary_apply_url as apply_url, post.status::text as status,
                      source_record.source_id, source_record.external_job_id,
                      (select string_agg(location.display_name, '、' order by location.display_name)
                         from job_market_post_locations pl
                         join job_market_locations location on location.id=pl.location_id
                        where pl.post_id=post.id) as location
                    from job_market_posts post
                    join job_market_companies company on company.id=post.company_id
                    left join lateral (
                      select record.* from job_market_source_records record
                      join job_market_sources source on source.id=record.source_id
                      where record.post_id=post.id
                      order by source.is_official desc,record.last_seen_at desc limit 1
                    ) source_record on true
                    where post.id=:postId
                    """, values, (rs, row) -> new JobPost(
                    rs.getObject("id", UUID.class), rs.getString("title"),
                    rs.getString("company_name"), rs.getString("location"),
                    rs.getString("apply_url"), rs.getObject("source_id", UUID.class),
                    rs.getString("external_job_id"), rs.getString("status")));
            if (posts.isEmpty() || !"open".equals(posts.getFirst().status())) {
                throw new ApplicationWriteException(ApplicationWriteException.Kind.POST_UNAVAILABLE,
                        "该公共岗位已失效或不存在。");
            }
            post = posts.getFirst();
            var linkedPayload = new LinkedHashMap<>(payload);
            linkedPayload.put("companyName", post.companyName());
            linkedPayload.put("positionName", post.title());
            linkedPayload.put("city", post.location());
            linkedPayload.put("jobUrl", post.applyUrl());
            payload = linkedPayload;
        }
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("payload", mapper.writeValueAsString(payload));
        UUID id = jdbc.queryForObject("""
                select id from public.create_application_for_owner(
                  :ownerId, cast(:payload as jsonb))
                """, values, UUID.class);
        if (post != null) {
            jdbc.update("""
                    insert into application_job_market_links(
                      application_id,owner_id,post_id,source_id,external_job_id,
                      job_title_snapshot,company_name_snapshot,location_snapshot,apply_url_snapshot)
                    values(:applicationId,:ownerId,:postId,:sourceId,:externalJobId,
                      :title,:companyName,:location,:applyUrl)
                    """, new MapSqlParameterSource()
                    .addValue("applicationId", id).addValue("ownerId", ownerId)
                    .addValue("postId", post.id()).addValue("sourceId", post.sourceId())
                    .addValue("externalJobId", post.externalJobId()).addValue("title", post.title())
                    .addValue("companyName", post.companyName()).addValue("location", post.location())
                    .addValue("applyUrl", post.applyUrl()));
        }
        return id;
    }

    @Override
    public void update(String ownerId, UUID id, ApplicationMutation command) {
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("id", id)
                .addValue("version", command.version()).addValue("changeDate", command.changeDate())
                .addValue("payload", mapper.writeValueAsString(command.payload()));
        try {
            jdbc.queryForObject("""
                    select id from public.update_application_for_owner(
                      :ownerId,:id,:version,:changeDate,cast(:payload as jsonb))
                    """, values, UUID.class);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public StatusResult updateStatus(String ownerId, UUID id, int version,
            ApplicationStatus status, LocalDate changeDate) {
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("id", id)
                .addValue("version", version).addValue("changeDate", changeDate)
                .addValue("payload", mapper.writeValueAsString(Map.of("status", status.value())));
        try {
            return jdbc.queryForObject("""
                    select id,status::text as status,latest_date,version
                    from public.update_application_for_owner(
                      :ownerId,:id,:version,:changeDate,cast(:payload as jsonb))
                    """, values, (rs, row) -> new StatusResult(
                    rs.getObject("id", UUID.class),
                    ApplicationStatus.fromWire(rs.getString("status")).orElseThrow(),
                    rs.getObject("latest_date", LocalDate.class), rs.getInt("version")));
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean delete(String ownerId, UUID id) {
        return jdbc.update("delete from applications where id=:id and owner_id=:ownerId",
                Map.of("id", id, "ownerId", ownerId)) > 0;
    }

    @Override
    public int deleteMany(String ownerId, List<UUID> ids) {
        return jdbc.update("delete from applications where owner_id=:ownerId and id in (:ids)",
                Map.of("ownerId", ownerId, "ids", ids));
    }

    private RuntimeException translate(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                return switch (sql.getSQLState()) {
                    case "40001" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.CONFLICT, "记录已被更新，请刷新后重试。");
                    case "P0002" -> new ApplicationNotFoundException();
                    case "23505" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.CONFLICT, "该招聘阶段已经记录过，不能重复添加。");
                    case "22023", "22P02" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.VALIDATION, "投递数据无效。");
                    default -> exception;
                };
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private record JobPost(UUID id, String title, String companyName, String location,
            String applyUrl, UUID sourceId, String externalJobId, String status) {}
}
