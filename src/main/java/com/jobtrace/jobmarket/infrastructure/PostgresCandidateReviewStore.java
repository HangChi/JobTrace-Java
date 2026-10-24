package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.CandidateReviewStore;
import com.jobtrace.jobmarket.domain.SourceRegistration;
import com.jobtrace.shared.web.Problem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresCandidateReviewStore implements CandidateReviewStore {
    private final JdbcTemplate jdbc;

    public PostgresCandidateReviewStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CandidateList sources(String status) {
        List<Map<String, Object>> items = jdbc.query("""
                select candidate.id, candidate.company_id, company.canonical_name,
                  company.company_type, candidate.entry_url, candidate.adapter::text,
                  candidate.external_key, candidate.base_url, candidate.allowed_hosts,
                  candidate.confidence, candidate.evidence_code, candidate.review_status,
                  candidate.health_status, candidate.diagnostic_code,
                  candidate.diagnostic_summary, candidate.http_status,
                  candidate.approved_source_id, candidate.last_checked_at
                from job_market_source_candidates candidate
                join job_market_companies company on company.id = candidate.company_id
                where (?::text is null or candidate.review_status = ?)
                order by case candidate.review_status when 'pending' then 0
                  when 'unrecognized' then 1 when 'approved' then 2 else 3 end,
                  candidate.last_checked_at desc, candidate.id
                limit 100
                """, (rs, row) -> sourceRow(rs), status, status);
        Map<String, Integer> summary = jdbc.queryForObject("""
                select
                  (select count(distinct company_id) from job_market_campaigns
                    where listing_kind='recruitment_directory' and status='open') directory_companies,
                  (select count(distinct company_id) from job_market_sources
                    where status<>'revoked') automatic_companies,
                  (select count(distinct company_id) from job_market_campaigns
                    where listing_kind='recruitment_directory' and status='open'
                    and official_apply_url not like 'https://mp.weixin.qq.com/%') scannable_companies,
                  (select count(distinct company_id) from job_market_source_candidates) reviewed_companies,
                  (select count(*) from job_market_source_candidates
                    where review_status='pending') pending_candidates
                """, (rs, row) -> Map.of(
                "directoryCompanies", rs.getInt("directory_companies"),
                "automaticCompanies", rs.getInt("automatic_companies"),
                "scannableCompanies", rs.getInt("scannable_companies"),
                "reviewedCompanies", rs.getInt("reviewed_companies"),
                "pendingCandidates", rs.getInt("pending_candidates")));
        return new CandidateList(items, summary);
    }

    @Override
    public CandidateList companies(String status) {
        List<Map<String, Object>> items = jdbc.query("""
                select id, company_name, article_url, article_title, snippet,
                  published_at, source_engine, article_count, detected_adapter::text,
                  detected_confidence, review_status, created_company_id, created_at
                from job_market_company_candidates
                where (?::text is null or review_status = ?)
                order by case review_status when 'pending' then 0
                  when 'approved' then 1 else 2 end,
                  article_count desc, created_at desc, id
                limit 100
                """, (rs, row) -> companyRow(rs), status, status);
        Map<String, Integer> summary = jdbc.queryForObject("""
                select count(*) filter(where review_status='pending') pending,
                  count(*) filter(where review_status='approved') approved,
                  count(*) filter(where review_status='ignored') ignored
                from job_market_company_candidates
                """, (rs, row) -> Map.of("pending", rs.getInt("pending"),
                "approved", rs.getInt("approved"), "ignored", rs.getInt("ignored")));
        return new CandidateList(items, summary);
    }

    @Override
    @Transactional
    public Map<String, Object> reviewSource(UUID id, String action) {
        var rows = jdbc.query("""
                select id,company_id,adapter::text,external_key,base_url,
                  allowed_hosts,review_status,health_status
                from job_market_source_candidates where id=? for update
                """, (rs, row) -> new SourceLock(rs.getObject("company_id", UUID.class),
                rs.getString("adapter"), rs.getString("external_key"),
                rs.getString("base_url"), stringArray(rs, "allowed_hosts"),
                rs.getString("review_status"), rs.getString("health_status")), id);
        if (rows.isEmpty()) {
            throw new Problem("not_found", "没有找到候选来源。", HttpStatus.NOT_FOUND);
        }
        var candidate = rows.getFirst();
        if ("ignore".equals(action)) {
            if (!"pending".equals(candidate.reviewStatus())
                    && !"unrecognized".equals(candidate.reviewStatus())) {
                throw new Problem("not_found", "没有找到可忽略的候选来源。", HttpStatus.NOT_FOUND);
            }
            jdbc.update("""
                    update job_market_source_candidates set review_status='ignored',
                      reviewed_at=now(),updated_at=now() where id=?
                    """, id);
            return Map.of("id", id, "reviewStatus", "ignored");
        }
        if (!"pending".equals(candidate.reviewStatus())
                || !"healthy".equals(candidate.healthStatus())
                || candidate.adapter() == null || candidate.externalKey() == null
                || candidate.baseUrl() == null || candidate.allowedHosts().isEmpty()) {
            throw new Problem("candidate_not_approvable",
                    "只有健康且已识别的待审核候选才能批准。", HttpStatus.CONFLICT);
        }
        new SourceRegistration(candidate.companyId(), candidate.adapter(),
                candidate.externalKey(), candidate.baseUrl(), candidate.allowedHosts(),
                List.of("cn"), "public", true, 360);
        List<UUID> inserted = jdbc.query("""
                insert into job_market_sources(company_id,adapter,external_key,base_url,
                  allowed_hosts,country_codes,access_basis,is_official,
                  sync_interval_minutes,status,next_sync_at)
                values (?,?,?,?,?,?,?,?,?,'active',now())
                on conflict(company_id,adapter,external_key) do nothing returning id
                """, statement -> {
            statement.setObject(1, candidate.companyId());
            statement.setObject(2, candidate.adapter(), java.sql.Types.OTHER);
            statement.setString(3, candidate.externalKey());
            statement.setString(4, candidate.baseUrl());
            statement.setArray(5, statement.getConnection().createArrayOf("text",
                    candidate.allowedHosts().toArray(String[]::new)));
            statement.setArray(6, statement.getConnection().createArrayOf("text",
                    new String[] {"cn"}));
            statement.setString(7, "public");
            statement.setBoolean(8, true);
            statement.setInt(9, 360);
        }, (rs, row) -> rs.getObject("id", UUID.class));
        UUID sourceId = inserted.isEmpty() ? jdbc.queryForObject("""
                select id from job_market_sources
                where company_id=? and adapter::text=? and external_key=?
                """, UUID.class, candidate.companyId(), candidate.adapter(),
                candidate.externalKey()) : inserted.getFirst();
        if (sourceId == null) {
            throw new Problem("conflict", "该招聘来源已存在或无法创建。", HttpStatus.CONFLICT);
        }
        jdbc.update("""
                update job_market_source_candidates set review_status='approved',
                  approved_source_id=?,reviewed_at=now(),updated_at=now() where id=?
                """, sourceId, id);
        return Map.of("id", id, "reviewStatus", "approved", "sourceId", sourceId);
    }

    @Override
    @Transactional
    public Map<String, Object> reviewCompany(UUID id, String action,
            CompanyOverrides overrides) {
        var rows = jdbc.query("""
                select company_name,article_url,published_at,review_status,
                  detected_adapter::text,detected_external_key,detected_base_url,
                  detected_allowed_hosts,detected_confidence
                from job_market_company_candidates where id=? for update
                """, (rs, row) -> new CompanyLock(rs.getString("company_name"),
                rs.getString("article_url"), rs.getTimestamp("published_at"),
                rs.getString("review_status"), rs.getString("detected_adapter"),
                rs.getString("detected_external_key"), rs.getString("detected_base_url"),
                stringArray(rs, "detected_allowed_hosts"),
                rs.getString("detected_confidence")), id);
        if (rows.isEmpty()) {
            throw new Problem("not_found", "没有找到公司候选。", HttpStatus.NOT_FOUND);
        }
        var candidate = rows.getFirst();
        if (!"pending".equals(candidate.reviewStatus())) {
            throw new Problem("conflict", "只有待审核的公司候选才能处理。", HttpStatus.CONFLICT);
        }
        if ("ignore".equals(action)) {
            jdbc.update("""
                    update job_market_company_candidates set review_status='ignored',
                      reviewed_at=now(),updated_at=now() where id=?
                    """, id);
            return Map.of("id", id, "reviewStatus", "ignored");
        }
        String companyName = overrides != null && overrides.companyName() != null
                ? overrides.companyName().trim() : candidate.companyName();
        String normalized = companyName.trim().toLowerCase(java.util.Locale.CHINA);
        String companyType = overrides != null && overrides.companyType() != null
                ? overrides.companyType() : "企业";
        String industry = overrides != null && overrides.industry() != null
                ? overrides.industry().trim() : "综合行业";
        List<UUID> existing = jdbc.query("""
                select id from job_market_companies where normalized_name=? limit 1
                """, (rs, row) -> rs.getObject("id", UUID.class), normalized);
        UUID companyId;
        if (existing.isEmpty()) {
            companyId = UUID.randomUUID();
            List<UUID> created = jdbc.query("""
                    insert into job_market_companies(id,canonical_name,normalized_name,
                      company_type,industry,website_url,identity_key)
                    values (?,?,?,?,?,?,?)
                    on conflict(identity_key) do nothing returning id
                    """, (rs, row) -> rs.getObject("id", UUID.class), companyId,
                    companyName, normalized, companyType, industry,
                    candidate.articleUrl(), "runtime:intake:" + normalized);
            if (created.isEmpty()) {
                companyId = jdbc.queryForObject("""
                        select id from job_market_companies where identity_key=?
                        """, UUID.class, "runtime:intake:" + normalized);
            } else {
                boolean wechat = candidate.articleUrl()
                        .startsWith("https://mp.weixin.qq.com/")
                        || candidate.articleUrl()
                        .startsWith("https://weixin.sogou.com/wechat/");
                jdbc.update("""
                    insert into job_market_campaigns(company_id,campaign_key,name,
                      recruitment_type,status,official_apply_url,listing_kind,published_at)
                    values (?,?,?,?,'open',?,'recruitment_directory',?)
                    """, companyId, wechat ? "directory:wechat" : "directory:official_site",
                    wechat ? "公众号招聘原文" : "官方招聘网站",
                    wechat ? "公众号" : "招聘官网", candidate.articleUrl(),
                    candidate.publishedAt());
            }
        } else {
            companyId = existing.getFirst();
        }
        if (candidate.detectedAdapter() != null && candidate.detectedExternalKey() != null) {
            UUID approvedCompanyId = companyId;
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        insert into job_market_source_candidates(company_id,entry_url,adapter,
                          external_key,base_url,allowed_hosts,confidence,evidence_code,
                          review_status,health_status,last_checked_at)
                        values (?,?,?,?,?,?,?,'ats_site_scan','pending','healthy',now())
                        on conflict(company_id,entry_url) do update set
                          last_checked_at=now(),updated_at=now()
                        """);
                statement.setObject(1, approvedCompanyId);
                statement.setString(2, candidate.articleUrl());
                statement.setObject(3, candidate.detectedAdapter(), java.sql.Types.OTHER);
                statement.setString(4, candidate.detectedExternalKey());
                statement.setString(5, candidate.detectedBaseUrl());
                statement.setArray(6, connection.createArrayOf("text",
                        candidate.detectedAllowedHosts().toArray(String[]::new)));
                statement.setString(7, candidate.detectedConfidence());
                return statement;
            });
        }
        jdbc.update("""
                update job_market_company_candidates set review_status='approved',
                  reviewed_at=now(),created_company_id=?,company_name=?,updated_at=now()
                where id=?
                """, companyId, companyName, id);
        refreshReadModelIfAvailable(companyId);
        return Map.of("id", id, "reviewStatus", "approved", "companyId", companyId);
    }

    private void refreshReadModelIfAvailable(UUID companyId) {
        Boolean available = jdbc.queryForObject("""
                select to_regprocedure('public.refresh_job_market_company_read_model(uuid)')
                  is not null
                """, Boolean.class);
        if (Boolean.TRUE.equals(available)) {
            jdbc.queryForList("select public.refresh_job_market_company_read_model(?)", companyId);
        }
    }

    private static Map<String, Object> sourceRow(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getObject("id", UUID.class));
        row.put("companyId", rs.getObject("company_id", UUID.class));
        row.put("companyName", rs.getString("canonical_name"));
        row.put("companyType", rs.getString("company_type"));
        row.put("entryUrl", rs.getString("entry_url"));
        row.put("adapter", rs.getString("adapter"));
        row.put("externalKey", rs.getString("external_key"));
        row.put("baseUrl", rs.getString("base_url"));
        row.put("allowedHosts", stringArray(rs, "allowed_hosts"));
        row.put("confidence", rs.getString("confidence"));
        row.put("evidenceCode", rs.getString("evidence_code"));
        row.put("reviewStatus", rs.getString("review_status"));
        row.put("healthStatus", rs.getString("health_status"));
        row.put("diagnosticCode", rs.getString("diagnostic_code"));
        row.put("diagnosticSummary", rs.getString("diagnostic_summary"));
        row.put("httpStatus", rs.getObject("http_status"));
        row.put("approvedSourceId", rs.getObject("approved_source_id", UUID.class));
        row.put("lastCheckedAt", instant(rs, "last_checked_at"));
        return row;
    }

    private static Map<String, Object> companyRow(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getObject("id", UUID.class));
        row.put("companyName", rs.getString("company_name"));
        row.put("articleUrl", rs.getString("article_url"));
        row.put("articleTitle", rs.getString("article_title"));
        row.put("snippet", rs.getString("snippet"));
        row.put("publishedAt", instant(rs, "published_at"));
        row.put("sourceEngine", rs.getString("source_engine"));
        row.put("articleCount", rs.getInt("article_count"));
        row.put("detectedAdapter", rs.getString("detected_adapter"));
        row.put("detectedConfidence", rs.getString("detected_confidence"));
        row.put("reviewStatus", rs.getString("review_status"));
        row.put("createdCompanyId", rs.getObject("created_company_id", UUID.class));
        row.put("createdAt", instant(rs, "created_at"));
        return row;
    }

    private static List<String> stringArray(ResultSet rs, String column) throws SQLException {
        var value = rs.getArray(column);
        if (value == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object item : (Object[]) value.getArray()) {
            result.add((String) item);
        }
        return List.copyOf(result);
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record SourceLock(UUID companyId, String adapter, String externalKey,
            String baseUrl, List<String> allowedHosts, String reviewStatus,
            String healthStatus) {}

    private record CompanyLock(String companyName, String articleUrl,
            Timestamp publishedAt, String reviewStatus, String detectedAdapter,
            String detectedExternalKey, String detectedBaseUrl,
            List<String> detectedAllowedHosts, String detectedConfidence) {}
}
