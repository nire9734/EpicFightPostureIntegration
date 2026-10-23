package dev.nire9734.epicfightposture.compat.humanoid.client;

import java.util.Map;
import java.util.TreeMap;

/** Stable, loader-neutral TSV representation of the latest renderer scan. */
public final class RendererCoverageFormatter {
    public static String format(String loader, Map<?, String> results) {
        if (!"forge".equals(loader) && !"neoforge".equals(loader)) {
            throw new IllegalArgumentException("Unknown renderer coverage loader: " + loader);
        }

        Map<String, String> ordered = new TreeMap<>();
        for (var entry : results.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                ordered.put(entry.getKey().toString(), entry.getValue());
            }
        }

        StringBuilder report = new StringBuilder("# EFPI renderer coverage v1\n");
        report.append("# loader: ").append(loader).append('\n');
        report.append("entity_id\tresult\n");
        ordered.forEach((id, result) -> report.append(id).append('\t')
                .append(result.replace('\t', '_').replace('\n', '_').replace('\r', '_'))
                .append('\n'));
        return report.toString();
    }

    private RendererCoverageFormatter() {}
}
