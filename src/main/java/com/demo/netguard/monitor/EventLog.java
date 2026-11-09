package com.demo.netguard.monitor;

import com.demo.netguard.model.FlowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Concurrent-safe append-only JSONL file and bounded current-session dashboard history.
 *  The disk file survives restart; replay and rotation are Developer 2's next tasks.
 */
public final class EventLog {
    private final Deque<FlowEvent> events = new ArrayDeque<>();
    private final Path file;
    private static final int LIMIT = 500;
    public EventLog(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (this.file.getParent() != null) Files.createDirectories(this.file.getParent());
    }
    public synchronized void append(FlowEvent event) {
        if (events.size() >= LIMIT) events.removeFirst();
        events.addLast(event);
        try {
            Files.writeString(file, event.json() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Fail visibly: connection logging is an explicit requirement of the product.
            System.err.println("NETWORK LOG WRITE FAILED: " + e.getMessage());
        }
    }
    public synchronized String recentJson(int max, String client, String destination) {
        List<FlowEvent> copy = new ArrayList<>(events);
        Collections.reverse(copy);
        StringJoiner join = new StringJoiner(",", "[", "]");
        int returned = 0;
        for (FlowEvent e : copy) {
            if (!client.isEmpty() && !e.sourceIp().equals(client)) continue;
            if (!destination.isEmpty() && !e.destination().contains(destination.toLowerCase(Locale.ROOT))) continue;
            if (returned >= Math.max(0, Math.min(max, LIMIT))) break;
            join.add(e.json()); returned++;
        }
        return join.toString();
    }
    public synchronized String summaryJson() {
        long connected = events.stream().filter(e -> e.outcome().equals("CONNECTED")).count();
        long failed = events.stream().filter(e -> e.outcome().equals("FAILED")).count();
        long denied = events.stream().filter(e -> e.outcome().equals("DENIED")).count();
        return "{\"recentTotal\":" + events.size() + ",\"connected\":" + connected
            + ",\"failed\":" + failed + ",\"denied\":" + denied + "}";
    }
    public Path file() { return file; }
}
