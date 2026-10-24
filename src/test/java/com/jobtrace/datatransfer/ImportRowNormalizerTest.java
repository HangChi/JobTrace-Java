package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.domain.ImportRowNormalizer;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class ImportRowNormalizerTest {

    @Test
    void infersChineseHeadersAndNormalizesDefaultsAndStages() {
        var headers = List.of("公司", "岗位", "投递日期", "阶段历史", "无关列");
        var mapping = ImportRowNormalizer.inferMapping(headers);
        assertThat(mapping).containsEntry("公司", "companyName")
                .containsEntry("阶段历史", "stages").containsEntry("无关列", "");
        var row = ImportRowNormalizer.normalize(2, Map.of(
                "公司", " 示例科技 ", "岗位", " 工程师 ", "投递日期", "2026-08-13",
                "阶段历史", "interview_1/一面 + 2026-08-14；终面 @ 2026-08-15"), mapping);
        assertThat(row.errors()).isEmpty();
        assertThat(row.data()).containsEntry("companyName", "示例科技")
                .containsEntry("positionName", "工程师")
                .containsEntry("type", "campus_recruitment")
                .containsEntry("status", "submitted");
        assertThat(row.data().get("stages")).isEqualTo(List.of(
                Map.of("stage", "interview_1", "occurredOn", "2026-08-14"),
                Map.of("stage", "final_interview", "occurredOn", "2026-08-15")));
    }

    @Test
    void customMappingCannotTargetTheSameFieldTwice() {
        assertThatThrownBy(() -> ImportRowNormalizer.validateMapping(
                List.of("A", "B"), Map.of("A", "companyName", "B", "companyName")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportRowNormalizer.validateMapping(
                List.of("A"), Map.of("B", "companyName")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportRowNormalizer.validateMapping(
                List.of("A"), Map.of("A", "unknown")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidValuesAreRowErrorsNotNormalizedData() {
        var mapping = Map.of("company", "companyName", "position", "positionName",
                "date", "appliedDate", "url", "jobUrl", "stage", "stages");
        var row = ImportRowNormalizer.normalize(4, Map.of("company", " ",
                "position", "Engineer", "date", "2026-02-30", "url", "javascript:bad",
                "stage", "unknown + 2026-08-14"), mapping);
        assertThat(row.data()).isNull();
        assertThat(row.errors()).extracting("field")
                .contains("companyName", "appliedDate", "jobUrl", "stages");
    }

    @Test
    void rejectsUnknownValuesAndOverlongOptionalFields() {
        var mapping = ImportRowNormalizer.inferMapping(List.of(
                "公司", "岗位", "投递日期", "类型", "状态", "城市", "备注", "职位链接"));
        var source = new LinkedHashMap<String, String>();
        source.put("公司", "X".repeat(201));
        source.put("岗位", "Engineer");
        source.put("投递日期", "2026-08-13");
        source.put("类型", "unknown");
        source.put("状态", "unknown");
        source.put("城市", "Y".repeat(101));
        source.put("备注", "Z".repeat(10_001));
        source.put("职位链接", "http:///missing-host");
        var row = ImportRowNormalizer.normalize(2, source, mapping);
        assertThat(row.data()).isNull();
        assertThat(row.errors()).extracting("field").contains(
                "companyName", "type", "status", "city", "notes", "jobUrl");
    }

    @Test
    void acceptsStableCodesAndEmptyOptionalFieldsButRejectsExcessStages() {
        var mapping = ImportRowNormalizer.inferMapping(List.of(
                "公司", "岗位", "投递日期", "类型", "状态", "城市", "职位链接", "阶段历史"));
        var row = ImportRowNormalizer.normalize(2, Map.of("公司", "A", "岗位", "B",
                "投递日期", "2026-08-13", "类型", "social_recruitment",
                "状态", "offer", "城市", "", "职位链接", "https://example.test/a",
                "阶段历史", "screening @ 2026-08-14"), mapping);
        assertThat(row.errors()).isEmpty();
        assertThat(row.data()).containsEntry("type", "social_recruitment")
                .containsEntry("status", "offer").containsEntry("city", null)
                .containsEntry("jobUrl", "https://example.test/a");
        String stages = "screening @ 2026-08-14;".repeat(101);
        var tooMany = ImportRowNormalizer.normalize(3, Map.of("公司", "A", "岗位", "B",
                "投递日期", "2026-08-13", "阶段历史", stages), mapping);
        assertThat(tooMany.errors()).extracting("field").contains("stages");
    }

    @Test
    void malformedStageAndUrlSyntaxReturnFieldErrors() {
        var mapping = ImportRowNormalizer.inferMapping(List.of(
                "公司", "岗位", "投递日期", "阶段历史", "职位链接"));
        var row = ImportRowNormalizer.normalize(2, Map.of("公司", "A", "岗位", "B",
                "投递日期", "2026-08-13", "阶段历史", "bad;unknown @ 2026-08-14",
                "职位链接", "https://[invalid"), mapping);
        assertThat(row.errors()).extracting("field").contains("stages", "jobUrl");
        var invalidDate = ImportRowNormalizer.normalize(3, Map.of("公司", "A", "岗位", "B",
                "投递日期", "2026-08-13", "阶段历史", "screening @ 2026-02-30"), mapping);
        assertThat(invalidDate.errors()).extracting("field").contains("stages");
    }
}
