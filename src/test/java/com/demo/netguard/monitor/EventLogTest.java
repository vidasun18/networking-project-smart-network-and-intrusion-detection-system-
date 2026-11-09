package com.demo.netguard.monitor;

import com.demo.netguard.model.FlowEvent;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Focused tests for JSON serialization and durable EventLog append behavior. */
public final class EventLogTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("  OK " + label);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("event-log-test-");
        try {
            FlowEvent special = new FlowEvent(Instant.parse("2026-11-10T12:34:56Z"),
                "192.0.2.10", "GET", "example.com", 80, "HTTP", "FAILED",
                "quote \" slash \\ newline\n tab\t control\u0001");
            String json = special.json();
            check(json.startsWith("{\"at\":\"2026-11-10T12:34:56Z\""),
                "event JSON starts with the ISO-8601 timestamp field");
            check(json.contains("\"sourceIp\":\"192.0.2.10\"")
                    && json.contains("\"method\":\"GET\"")
                    && json.contains("\"destination\":\"example.com\"")
                    && json.contains("\"port\":80")
                    && json.contains("\"protocol\":\"HTTP\"")
                    && json.contains("\"outcome\":\"FAILED\""),
                "event JSON contains all agreed metadata fields");
            check(json.contains("quote \\\" slash \\\\ newline\\n tab\\t control\\u0001")
                    && json.chars().noneMatch(c -> c < 0x20),
                "event JSON escapes quotes, slashes and every control character");

            Path file = directory.resolve("events.jsonl");
            EventLog log = new EventLog(file);
            log.append(special);
            int count = 100;
            ExecutorService workers = Executors.newFixedThreadPool(16);
            try {
                List<Future<?>> writes = new java.util.ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int eventNumber = i;
                    writes.add(workers.submit(() -> log.append(new FlowEvent(
                        Instant.parse("2026-11-10T12:34:56Z"), "10.0.0." + (eventNumber + 1),
                        "CONNECT", "example.com", 443, "HTTPS_TUNNEL", "CONNECTED",
                        "UPSTREAM_TCP_CONNECTED"))));
                }
                for (Future<?> write : writes) write.get();
            } finally {
                workers.shutdownNow();
            }
            List<String> lines = Files.readAllLines(file);
            check(lines.size() == count + 1, "concurrent appends produce one JSONL line per event");
            check(lines.stream().allMatch(line -> line.startsWith("{") && line.endsWith("}")
                    && line.contains("\"at\":") && line.contains("\"reason\":")),
                "every JSONL line is a complete event object");
            Set<String> loggedClients = new HashSet<>();
            for (int i = 1; i <= count; i++) {
                String sourceIp = "10.0.0." + i;
                long occurrences = lines.stream()
                    .filter(line -> line.contains("\"sourceIp\":\"" + sourceIp + "\""))
                    .count();
                if (occurrences == 1) loggedClients.add(sourceIp);
            }
            check(loggedClients.size() == count,
                "simultaneous events from 100 distinct clients are each written exactly once");

            EventLog restarted = new EventLog(file);
            check(restarted.summaryJson().contains("\"recentTotal\":101")
                    && restarted.summaryJson().contains("\"connected\":100")
                    && restarted.summaryJson().contains("\"failed\":1"),
                "reopening the logger restores persisted events into dashboard history");
            check(restarted.recentJson(101, "", "").contains(special.json()),
                "replay preserves escaped event metadata");
            restarted.append(new FlowEvent(Instant.parse("2026-11-10T12:35:00Z"),
                "192.0.2.25", "HEAD", "example.net", 80, "HTTP", "CONNECTED",
                "UPSTREAM_TCP_CONNECTED"));
            check(Files.readAllLines(file).size() == count + 2,
                "new events append after restored history without replacing prior lines");

            Path corruptFile = directory.resolve("with-corrupt-record.jsonl");
            FlowEvent validTail = new FlowEvent(Instant.parse("2026-11-10T12:35:01Z"),
                "192.0.2.30", "CONNECT", "secure.example", 443, "HTTPS_TUNNEL", "CONNECTED",
                "UPSTREAM_TCP_CONNECTED");
            Files.writeString(corruptFile, special.json() + "\n{not-json}\n" + validTail.json());
            EventLog recovered = new EventLog(corruptFile);
            check(recovered.summaryJson().contains("\"recentTotal\":2")
                    && recovered.recentJson(2, "", "").contains(validTail.json()),
                "replay skips corrupt records and restores valid unterminated final records");
            recovered.append(special);
            List<String> recoveredLines = Files.readAllLines(corruptFile);
            check(recoveredLines.size() == 4 && recoveredLines.get(2).equals(validTail.json())
                    && recoveredLines.get(3).equals(special.json()),
                "append separates a recovered final record from the next JSONL record");

            Path boundedFile = directory.resolve("bounded-history.jsonl");
            EventLog bounded = new EventLog(boundedFile);
            for (int i = 0; i < 510; i++) {
                bounded.append(new FlowEvent(Instant.parse("2026-11-10T12:36:00Z"),
                    "192.0.2.40", "GET", "example.org", 80, "HTTP", "CONNECTED", "test-" + i));
            }
            EventLog boundedRestart = new EventLog(boundedFile);
            check(boundedRestart.summaryJson().contains("\"recentTotal\":500"),
                "startup replay keeps only the latest 500 in-memory records");
            check(boundedRestart.recentJson(1, "", "").contains("test-509"),
                "bounded replay retains the newest persisted record");

            Path blockedFile = directory.resolve("is-a-directory");
            EventLog failingLog = new EventLog(blockedFile);
            Files.createDirectory(blockedFile);
            boolean reported = false;
            try {
                failingLog.append(special);
            } catch (UncheckedIOException expected) {
                reported = true;
            }
            check(reported, "append reports disk write failure to its caller");
            check(failingLog.summaryJson().contains("\"recentTotal\":0"),
                "failed writes are not reported in in-memory recent history");
        } finally {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                });
            }
        }
        System.out.println("PASS: " + checks + " EventLog checks");
    }
}
