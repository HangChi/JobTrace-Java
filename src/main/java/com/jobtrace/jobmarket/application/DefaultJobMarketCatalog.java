package com.jobtrace.jobmarket.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The versioned default directory is shared with the legacy application. */
@Service
public final class DefaultJobMarketCatalog {
    private final List<Item> items;

    public DefaultJobMarketCatalog(ObjectMapper mapper) {
        JsonNode source = resource(mapper, "jobmarket/default-source-catalog.json");
        JsonNode directory = resource(mapper, "jobmarket/default-company-directory.json");
        JsonNode channelHints = source.get("channelHints");
        var byCompany = new LinkedHashMap<String, Item>();
        for (JsonNode entry : source.get("sources")) {
            String externalKey = text(entry, "externalKey");
            String[] keyParts = externalKey.split("\\|", 3);
            String hint = keyParts.length > 1 && channelHints.has(keyParts[1])
                    ? channelHints.get(keyParts[1]).asText() : null;
            String adapter = text(entry, "adapter");
            String label = hint == null ? "自动同步：" + adapter
                    : "自动同步：" + adapter + "（" + hint + "）";
            Item item = new Item(text(entry, "identityKey"), text(entry, "companyName"),
                    adapter, text(entry, "industry"), text(entry, "websiteUrl"),
                    "automatic", label, hint);
            byCompany.put(item.companyName(), item);
        }
        for (JsonNode entry : directory.get("directory")) {
            Item item = new Item(text(entry, "identityKey"), text(entry, "companyName"),
                    null, text(entry, "industry"), text(entry, "entryUrl"),
                    text(entry, "channel"), text(entry, "channelLabel"), null);
            byCompany.put(item.companyName(), item);
        }
        items = List.copyOf(byCompany.values());
    }

    public Page list(String query, int page, int limit) {
        String needle = query == null ? null : query.trim().toLowerCase(Locale.CHINA);
        if (needle != null && needle.length() > 100 || page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("目录查询参数无效。");
        }
        List<Item> filtered = needle == null || needle.isEmpty() ? items : items.stream()
                .filter(item -> (item.companyName() + " " + item.industry() + " "
                        + item.channelLabel() + " " + item.adapter())
                        .toLowerCase(Locale.CHINA).contains(needle))
                .toList();
        long start = (long) (page - 1) * limit;
        if (start >= filtered.size()) {
            return new Page(List.of(), page, limit, filtered.size());
        }
        int from = (int) start;
        return new Page(new ArrayList<>(filtered.subList(from,
                Math.min(filtered.size(), from + limit))), page, limit, filtered.size());
    }

    private static JsonNode resource(ObjectMapper mapper, String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取默认岗位目录。", exception);
        }
    }

    private static String text(JsonNode node, String name) {
        return node.get(name).asText();
    }

    public record Item(String identityKey, String companyName, String adapter,
            String industry, String websiteUrl, String channel, String channelLabel,
            String channelHint) {}

    public record Page(List<Item> items, int page, int limit, int total) {}
}
