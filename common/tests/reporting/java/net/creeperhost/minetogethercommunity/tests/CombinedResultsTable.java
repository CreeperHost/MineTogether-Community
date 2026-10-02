package net.creeperhost.minetogethercommunity.tests;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed-width columns keep related statuses aligned, including on narrow Discord layouts. */
final class CombinedResultsTable {
    static String format(List<TestResults> results) {
        var rows = new LinkedHashMap<String, Map<String, Boolean>>();
        for (TestResults result : results) {
            boolean suite = result.loader().equals("Suite execution");
            String loader = result.loader().startsWith("NeoForge") ? "NeoForge" : "Fabric";
            for (TestResults.Check check : result.checks()) {
                if (suite) {
                    if (check.name().startsWith("neoforge ") || check.name().startsWith("fabric ")) {
                        String phaseLoader = check.name().startsWith("neoforge ") ? "NeoForge" : "Fabric";
                        String phase = check.name().substring(check.name().indexOf(' ') + 1);
                        put(rows, phase.equals("dedicated server") ? "Server tests" : "Client tests", phaseLoader, check.passed());
                    } else {
                        String label = check.name().equals("Shared unit tests and probe compilation")
                                ? "Shared unit tests / compile" : check.name();
                        // A shared check applies to every loader selected for this run.
                        for (String selected : List.of("NeoForge", "Fabric")) {
                            if (results.stream().anyMatch(r -> r.loader().startsWith(selected))) {
                                put(rows, label, selected, check.passed());
                            }
                        }
                    }
                } else {
                    String label = (result.loader().endsWith(" dedicated server") && !check.name().startsWith("Server ")
                            ? "Server: " : "") + check.name();
                    put(rows, label, loader, check.passed());
                }
            }
        }
        int width = Math.max(5, rows.keySet().stream().mapToInt(String::length).max().orElse(5));
        var table = new StringBuilder("```\n");
        table.append(String.format("%-" + width + "s  NeoForge  Fabric\n", "Check"));
        for (var row : rows.entrySet()) {
            Boolean neoForge = row.getValue().get("NeoForge");
            table.append(String.format("%-" + width + "s  ", row.getKey()))
                    .append(status(neoForge)).append(neoForge == null ? "         " : "        ")
                    .append(status(row.getValue().get("Fabric"))).append('\n');
        }
        return table.append("```\n— = not run / not applicable").toString();
    }

    private static String status(Boolean passed) {
        return passed == null ? "—" : passed ? "✅" : "❌";
    }

    private static void put(Map<String, Map<String, Boolean>> rows, String label, String loader, boolean passed) {
        // If multiple checks share a label, retain any failure rather than overwrite it with a pass.
        rows.computeIfAbsent(label, ignored -> new LinkedHashMap<>()).merge(loader, passed, (a, b) -> a && b);
    }
}
