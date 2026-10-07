package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.ImportStore;
import com.jobtrace.datatransfer.domain.ImportBatchException;
import com.jobtrace.datatransfer.domain.ImportPreview;
import com.jobtrace.datatransfer.domain.ImportResult;
import com.jobtrace.datatransfer.domain.ImportRow;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresImportStore implements ImportStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public PostgresImportStore(NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transactions, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @Override
    public void cleanupExpired() {
        jdbc.getJdbcTemplate().update("""
                update import_batches set status='expired'
                where status='previewed' and expires_at <= now()
                """);
        jdbc.getJdbcTemplate().update("""
                delete from import_batches where expires_at < now() - interval '24 hours'
                """);
    }

    @Override
    public List<ImportRow> findDuplicates(String ownerId, List<ImportRow> rows) {
        var candidates = rows.stream().filter(row -> row.data() != null).map(row -> Map.of(
                "rowNumber", row.rowNumber(), "companyName", row.data().get("companyName"),
                "positionName", row.data().get("positionName"),
                "appliedDate", row.data().get("appliedDate"))).toList();
        if (candidates.isEmpty()) {
            return rows;
        }
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("candidates", mapper.writeValueAsString(candidates));
        Map<Integer, List<UUID>> matches = new LinkedHashMap<>();
        jdbc.query("""
                with candidates as (
                  select * from jsonb_to_recordset(cast(:candidates as jsonb)) as value(
                    "rowNumber" integer, "companyName" text,
                    "positionName" text, "appliedDate" date)
                )
                select candidate."rowNumber" as row_number,
                  coalesce(array_agg(a.id order by a.id)
                    filter (where a.id is not null), '{}') as application_ids
                from candidates candidate
                left join applications a on a.owner_id=:ownerId
                  and lower(a.company_name)=lower(candidate."companyName")
                  and lower(a.position_name)=lower(candidate."positionName")
                  and a.applied_date=candidate."appliedDate"
                group by candidate."rowNumber"
                """, values, rs -> {
                    Object[] ids = (Object[]) rs.getArray("application_ids").getArray();
                    var owned = new ArrayList<UUID>(ids.length);
                    for (Object id : ids) {
                        owned.add(id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()));
                    }
                    matches.put(rs.getInt("row_number"), List.copyOf(owned));
                });
        return rows.stream().map(row -> row.withDuplicates(
                matches.getOrDefault(row.rowNumber(), List.of()))).toList();
    }

    @Override
    public ImportPreview savePreview(String ownerId, String filename, String format,
            Map<String, String> mapping, List<ImportRow> rows, UUID replaceBatchId) {
        return transactions.execute(status -> {
            int valid = (int) rows.stream().filter(row -> row.errors().isEmpty()).count();
            int duplicate = (int) rows.stream().filter(row ->
                    !row.duplicateApplicationIds().isEmpty()).count();
            var values = new MapSqlParameterSource()
                    .addValue("ownerId", ownerId).addValue("filename", filename)
                    .addValue("format", format).addValue("columns", mapper.writeValueAsString(mapping))
                    .addValue("total", rows.size()).addValue("valid", valid)
                    .addValue("invalid", rows.size() - valid).addValue("duplicate", duplicate);
            Batch batch = jdbc.queryForObject("""
                    insert into import_batches(owner_id,file_name,format,columns,
                      total_rows,valid_rows,invalid_rows,duplicate_rows)
                    values(:ownerId,:filename,:format,cast(:columns as jsonb),
                      :total,:valid,:invalid,:duplicate)
                    returning id,expires_at
                    """, values, (rs, index) -> new Batch(
                    rs.getObject("id", UUID.class), rs.getTimestamp("expires_at")));
            jdbc.update("""
                    insert into import_rows(batch_id,row_number,normalized_data,errors,
                      duplicate_application_ids)
                    select :batchId,value."rowNumber",value.data,value.errors,
                      coalesce(array(select jsonb_array_elements_text(
                        value."duplicateApplicationIds")::uuid),'{}')
                    from jsonb_to_recordset(cast(:rows as jsonb)) as value(
                      "rowNumber" integer,data jsonb,errors jsonb,
                      "duplicateApplicationIds" jsonb)
                    """, new MapSqlParameterSource().addValue("batchId", batch.id())
                    .addValue("rows", mapper.writeValueAsString(rows)));
            if (replaceBatchId != null) {
                jdbc.update("""
                        update import_batches set status='expired'
                        where id=:id and owner_id=:ownerId and status='previewed'
                        """, Map.of("id", replaceBatchId, "ownerId", ownerId));
            }
            return new ImportPreview(batch.id(), batch.expiresAt().toInstant(),
                    rows.size(), valid, rows.size() - valid, duplicate, mapping, rows);
        });
    }

    @Override
    public List<ImportResult.Row> confirm(String ownerId, UUID batchId, List<Decision> decisions) {
        try {
            String json = jdbc.queryForObject("""
                    select public.confirm_import_batch_for_owner(
                      :ownerId,:batchId,cast(:decisions as jsonb))::text
                    """, new MapSqlParameterSource().addValue("ownerId", ownerId)
                    .addValue("batchId", batchId)
                    .addValue("decisions", mapper.writeValueAsString(decisions)), String.class);
            JsonNode nodes = mapper.readTree(json);
            var result = new ArrayList<ImportResult.Row>();
            for (JsonNode node : nodes) {
                JsonNode id = node.get("applicationId");
                JsonNode failure = node.get("error");
                result.add(new ImportResult.Row(node.get("rowNumber").intValue(),
                        node.get("result").textValue(),
                        id == null || id.isNull() ? null : UUID.fromString(id.textValue()),
                        failure == null || failure.isNull() ? null
                                : new ImportResult.Error(
                                        failure.get("code").textValue(),
                                        failure.get("message").textValue(),
                                        UUID.randomUUID().toString())));
            }
            return List.copyOf(result);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    private RuntimeException translate(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                if ("P0002".equals(sql.getSQLState())) {
                    return new ImportBatchException(ImportBatchException.Kind.NOT_FOUND,
                            "没有找到该导入批次。");
                }
                if ("40001".equals(sql.getSQLState())) {
                    return new ImportBatchException(ImportBatchException.Kind.CONFLICT,
                            "该导入批次已处理或已过期。");
                }
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private record Batch(UUID id, Timestamp expiresAt) {}
}
