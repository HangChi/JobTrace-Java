package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.jobmarket.application.ManageJobMarketSources;
import com.jobtrace.jobmarket.domain.SourceHealth;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/job-market/sources")
public class JobMarketSourceAdminController {

    private final ManageJobMarketSources sources;

    public JobMarketSourceAdminController(ManageJobMarketSources sources) {
        this.sources = sources;
    }

    @GetMapping
    public ResponseEntity<SourceList> list(Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new SourceList(sources.list()));
    }

    @PostMapping
    public ResponseEntity<SourceHealth> register(
            @RequestBody(required = false) SourceRegistrationRequest request,
            Principal principal) {
        BridgePrincipalAdmin.require(principal);
        if (request == null) {
            throw new IllegalArgumentException("请求内容不能为空。");
        }
        try {
            SourceHealth source = sources.register(request.toDomain())
                    .orElseThrow(() -> new Problem("not_found", "没有找到对应公司。",
                            HttpStatus.NOT_FOUND));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(source);
        } catch (DuplicateKeyException exception) {
            throw new Problem("conflict", "该招聘来源已经登记。", HttpStatus.CONFLICT);
        }
    }

    @PatchMapping("/{sourceId}")
    public ResponseEntity<SourceHealth> update(
            @PathVariable UUID sourceId,
            @RequestBody(required = false) SourceUpdateRequest request,
            Principal principal) {
        BridgePrincipalAdmin.require(principal);
        if (request == null) {
            throw new IllegalArgumentException("请求内容不能为空。");
        }
        SourceHealth source = sources.update(sourceId, request.toDomain())
                .orElseThrow(() -> new Problem("not_found", "没有找到该招聘来源。",
                        HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(source);
    }

    @PostMapping("/{sourceId}/sync")
    public void syncOwnedByLegacy(@PathVariable UUID sourceId, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        throw new Problem("ingestion_owned_externally",
                "岗位采集由现有服务负责，Java 不会单独执行同步。", HttpStatus.GONE);
    }

    public record SourceList(List<SourceHealth> items) {
    }
}
