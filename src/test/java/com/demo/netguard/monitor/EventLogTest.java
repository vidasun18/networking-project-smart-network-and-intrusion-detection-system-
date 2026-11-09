package com.demo.netguard.monitor;

import com.demo.netguard.model.FlowEvent;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
            int count = 24;
            ExecutorService workers = Executors.newFixedThreadPool(6);
            try {
                List<Future<?>> writes = new java.util.ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int eventNumber = i;
                    writes.add(workers.submit(() -> log.append(new FlowEvent(
                        Instant.parse("2026-11-10T12:34:56Z"), "192.0.2." + eventNumber,
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

            Path blockedFile = directory.resolve("is-a-directory");
            Files.createDirectory(blockedFile);
            EventLog failingLog = new EventLog(blockedFile);
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
