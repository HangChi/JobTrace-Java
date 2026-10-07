package com.jobtrace.jobmarket.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Validated source metadata; DNS and redirect checks still belong to the fetch boundary. */
public record SourceRegistration(
        UUID companyId,
        String adapter,
        String externalKey,
        String baseUrl,
        List<String> allowedHosts,
        List<String> countryCodes,
        String accessBasis,
        boolean isOfficial,
        int syncIntervalMinutes) {

    private static final Set<String> ADAPTERS = Set.of(
            "greenhouse", "lever", "ashby", "smartrecruiters", "moka",
            "schema_org", "xiaomi", "feishu", "beisen", "dayee",
            "workday", "job51", "china_bigtech", "html_list");

    public SourceRegistration {
        if (companyId == null || !ADAPTERS.contains(adapter)) {
            throw new IllegalArgumentException("无效的公司或来源适配器。");
        }
        externalKey = externalKey == null ? "" : externalKey.trim();
        if (externalKey.isEmpty() || externalKey.length() > 200) {
            throw new IllegalArgumentException("无效的来源标识。");
        }
        if (baseUrl == null || baseUrl.length() > 2048) {
            throw new IllegalArgumentException("无效的来源网址。");
        }
        if (allowedHosts == null || allowedHosts.isEmpty() || allowedHosts.size() > 10) {
            throw new IllegalArgumentException("来源主机白名单不能为空或超过限制。");
        }
        allowedHosts = allowedHosts.stream().map(SourceRegistration::normalizeHost).toList();
        if (new HashSet<>(allowedHosts).size() != allowedHosts.size()) {
            throw new IllegalArgumentException("来源主机白名单不能重复。");
        }
        validateUrl(baseUrl, allowedHosts);
        if (countryCodes == null || countryCodes.size() > 10
                || countryCodes.stream().anyMatch(code -> code == null
                        || !code.matches("[a-z]{2}"))) {
            throw new IllegalArgumentException("无效的国家代码。");
        }
        countryCodes = List.copyOf(countryCodes);
        if (!"public".equals(accessBasis) && !"authorized".equals(accessBasis)) {
            throw new IllegalArgumentException("无效的访问依据。");
        }
        if (syncIntervalMinutes < 60 || syncIntervalMinutes > 1440) {
            throw new IllegalArgumentException("同步间隔必须介于 60 和 1440 分钟。");
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("无效的来源主机。");
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 253
                || !normalized.matches("[a-z0-9.-]+")
                || normalized.startsWith(".") || normalized.endsWith(".")
                || normalized.contains("..") || normalized.equals("localhost")
                || normalized.endsWith(".localhost") || normalized.matches("[0-9.]+")) {
            throw new IllegalArgumentException("无效的来源主机。");
        }
        return normalized;
    }

    private static void validateUrl(String value, List<String> allowedHosts) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null
                    || !allowedHosts.contains(normalizeHost(host))) {
                throw new IllegalArgumentException("来源网址不属于已批准的 HTTPS 主机。");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("无效的来源网址。", exception);
        }
    }
}
