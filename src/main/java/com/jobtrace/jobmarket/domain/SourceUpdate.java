package com.jobtrace.jobmarket.domain;

public record SourceUpdate(String status, Integer syncIntervalMinutes, String accessBasis) {

    public SourceUpdate {
        if (status == null && syncIntervalMinutes == null && accessBasis == null) {
            throw new IllegalArgumentException("至少提供一个更新字段。");
        }
        if (status != null && !status.equals("active")
                && !status.equals("paused") && !status.equals("revoked")) {
            throw new IllegalArgumentException("无效的来源状态。");
        }
        if (syncIntervalMinutes != null
                && (syncIntervalMinutes < 60 || syncIntervalMinutes > 1440)) {
            throw new IllegalArgumentException("同步间隔必须介于 60 和 1440 分钟。");
        }
        if (accessBasis != null && !accessBasis.equals("public")
                && !accessBasis.equals("authorized")) {
            throw new IllegalArgumentException("无效的访问依据。");
        }
    }
}
