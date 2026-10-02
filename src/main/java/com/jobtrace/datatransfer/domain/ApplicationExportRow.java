package com.jobtrace.datatransfer.domain;

import java.util.List;
import java.util.Objects;

/** One ordered portable row from the unchanged legacy application schema. */
public record ApplicationExportRow(
        String id, String company, String position, String city, String jobUrl,
        String appliedDate, String typeLabel, String statusLabel, String latestDate,
        String stageHistory, String notes, String createdAt, String updatedAt) {

    public static final List<String> HEADERS = List.of(
            "ID", "公司", "岗位", "城市", "职位链接", "投递日期", "类型", "状态",
            "最新日期", "阶段历史", "备注", "创建时间", "更新时间");

    public ApplicationExportRow {
        Objects.requireNonNull(id);
        Objects.requireNonNull(company);
        Objects.requireNonNull(position);
        Objects.requireNonNull(appliedDate);
        Objects.requireNonNull(typeLabel);
        Objects.requireNonNull(statusLabel);
        Objects.requireNonNull(latestDate);
        Objects.requireNonNull(stageHistory);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public List<String> cells() {
        return java.util.Arrays.asList(id, company, position, city == null ? "" : city,
                jobUrl == null ? "" : jobUrl, appliedDate, typeLabel, statusLabel, latestDate,
                stageHistory, notes == null ? "" : notes, createdAt, updatedAt);
    }
}
