package com.jobtrace.datatransfer.web;

import com.jobtrace.datatransfer.application.ConfirmImport;
import com.jobtrace.datatransfer.application.ImportStore;
import com.jobtrace.datatransfer.application.PreviewImport;
import com.jobtrace.datatransfer.domain.ImportBatchException;
import com.jobtrace.datatransfer.domain.ImportInputException;
import com.jobtrace.datatransfer.domain.ImportPreview;
import com.jobtrace.datatransfer.domain.ImportResult;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Private two-step import; preview never creates applications. */
@RestController
public class ImportController {

    private final PreviewImport preview;
    private final ConfirmImport confirm;
    private final ObjectMapper mapper;

    public ImportController(PreviewImport preview, ConfirmImport confirm, ObjectMapper mapper) {
        this.preview = preview;
        this.confirm = confirm;
        this.mapper = mapper;
    }

    @PostMapping(path = "/api/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportPreview> preview(@RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String mapping,
            @RequestParam(required = false) String replaceBatchId,
            Principal principal) throws IOException {
        String ownerId = BridgePrincipalOwner.require(principal);
        if (file == null) {
            throw new ImportInputException(ImportInputException.Kind.VALIDATION,
                    "请选择 CSV 或 XLSX 文件。");
        }
        Map<String, String> columns = parseMapping(mapping);
        UUID replace = replaceBatchId == null || replaceBatchId.isBlank()
                ? null : UUID.fromString(replaceBatchId);
        ImportPreview result = preview.execute(ownerId, file.getOriginalFilename(),
                file.getBytes(), columns, replace);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(result);
    }

    @PostMapping("/api/imports/{id}/confirm")
    ResponseEntity<ImportResult> confirm(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        String ownerId = BridgePrincipalOwner.require(principal);
        List<ImportStore.Decision> decisions = parseDecisions(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(confirm.execute(ownerId, id, decisions));
    }

    @ExceptionHandler(ImportInputException.class)
    ResponseEntity<ProblemDetail> invalidImport(ImportInputException exception,
            HttpServletRequest request) {
        return switch (exception.kind()) {
            case VALIDATION -> problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
            case UNSUPPORTED_FORMAT -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_format", exception.getMessage(), request);
            case PAYLOAD_TOO_LARGE -> problem(HttpStatus.PAYLOAD_TOO_LARGE,
                    "payload_too_large", exception.getMessage(), request);
        };
    }

    @ExceptionHandler(ImportBatchException.class)
    ResponseEntity<ProblemDetail> batchConflict(ImportBatchException exception,
            HttpServletRequest request) {
        return exception.kind() == ImportBatchException.Kind.NOT_FOUND
                ? problem(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request)
                : problem(HttpStatus.CONFLICT, "import_batch_conflict",
                        exception.getMessage(), request);
    }

    private Map<String, String> parseMapping(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = mapper.readTree(value);
            if (!parsed.isObject()) {
                throw new IllegalArgumentException("Column mapping must be a JSON object");
            }
            var mapping = new LinkedHashMap<String, String>();
            parsed.properties().forEach(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw new IllegalArgumentException("Column mapping values must be strings");
                }
                mapping.put(entry.getKey(), entry.getValue().textValue());
            });
            return mapping;
        } catch (RuntimeException exception) {
            throw new ImportInputException(ImportInputException.Kind.VALIDATION,
                    "列映射必须是有效的 JSON 对象。");
        }
    }

    private List<ImportStore.Decision> parseDecisions(JsonNode body) {
        JsonNode values = body == null ? null : body.get("decisions");
        if (values == null || !values.isArray() || values.size() > 10_000) {
            throw new ImportInputException(ImportInputException.Kind.VALIDATION,
                    "导入选择无效。");
        }
        var decisions = new ArrayList<ImportStore.Decision>();
        for (JsonNode value : values) {
            JsonNode row = value.get("rowNumber");
            JsonNode action = value.get("action");
            if (row == null || !row.isIntegralNumber() || !row.canConvertToInt()
                    || row.intValue() < 1
                    || action == null || !action.isTextual()
                    || !("import".equals(action.textValue()) || "skip".equals(action.textValue()))) {
                throw new ImportInputException(ImportInputException.Kind.VALIDATION,
                        "导入选择无效。");
            }
            decisions.add(new ImportStore.Decision(row.intValue(), action.textValue()));
        }
        return List.copyOf(decisions);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code,
            String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String ? requestId : "unavailable");
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
