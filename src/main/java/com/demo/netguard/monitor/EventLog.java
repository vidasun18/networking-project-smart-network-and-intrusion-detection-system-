package com.demo.netguard.monitor;

import com.demo.netguard.model.FlowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Thread-safe append-only JSONL file with bounded rotation and dashboard history restored at startup.
 *  Synchronized appends serialize writes from concurrent proxy workers so each client event
 *  is stored as a complete JSONL record. The active file and each of three archives are capped
 *  at 1 MiB. Replay keeps the latest 500 valid event records.
 */
public final class EventLog {
    private final Deque<FlowEvent> events = new ArrayDeque<>();
    private final Path file;
    private static final int LIMIT = 500;
    static final long MAX_ACTIVE_BYTES = 1024L * 1024;
    static final int MAX_ARCHIVE_FILES = 3;
    private boolean needsLineSeparator;

    public EventLog(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (this.file.getParent() != null) Files.createDirectories(this.file.getParent());
        for (int index = 1; index <= MAX_ARCHIVE_FILES; index++) {
            trimOversizedLog(archivePath(index));
        }
        trimOversizedLog(this.file);
        restoreRecentEvents();
    }

    public synchronized void append(FlowEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            String record = event.json() + "\n";
            int recordBytes = record.getBytes(StandardCharsets.UTF_8).length;
            if (recordBytes > MAX_ACTIVE_BYTES) {
                throw new IOException("Event exceeds the maximum JSONL file size of " + MAX_ACTIVE_BYTES + " bytes");
            }
            long activeSize = Files.exists(file) ? Files.size(file) : 0;
            long requiredBytes = recordBytes + (needsLineSeparator ? 1 : 0);
            if (activeSize > MAX_ACTIVE_BYTES - requiredBytes) {
                if (activeSize > 0) rotateFiles();
                else needsLineSeparator = false;
            }
            String line = (needsLineSeparator ? "\n" : "") + record;
            Files.writeString(file, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append network event to " + file, e);
        }
        needsLineSeparator = false;
        if (events.size() >= LIMIT) events.removeFirst();
        events.addLast(event);
    }

    private void restoreRecentEvents() throws IOException {
        for (int index = MAX_ARCHIVE_FILES; index > 0; index--) {
            restoreFile(archivePath(index), false);
        }
        restoreFile(file, true);
    }

    private void restoreFile(Path source, boolean active) throws IOException {
        if (!Files.exists(source)) return;
        long size = Files.size(source);
        if (size == 0) return;

        long start = Math.max(0, size - MAX_ACTIVE_BYTES);
        int byteCount = Math.toIntExact(size - start);
        byte[] tail = readRange(source, start, byteCount);
        if (active) {
            try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
                ByteBuffer finalByte = ByteBuffer.allocate(1);
                channel.position(size - 1);
                if (channel.read(finalByte) == 1) {
                    needsLineSeparator = finalByte.array()[0] != (byte) '\n';
                }
            }
        }

        String text = new String(tail, StandardCharsets.UTF_8);
        if (start > 0) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline < 0) return;
            text = text.substring(firstNewline + 1);
        }
        String[] lines = text.split("\\n", -1);
        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                remember(parseEvent(line));
            } catch (IllegalArgumentException ignored) {
                // A damaged or unsupported JSONL line must not prevent later records from loading.
            }
        }
    }

    private byte[] readRange(Path source, long start, int byteCount) throws IOException {
        byte[] bytes = new byte[byteCount];
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break;
            }
        }
        return bytes;
    }

    private void trimOversizedLog(Path logFile) throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) <= MAX_ACTIVE_BYTES) return;
        long size = Files.size(logFile);
        long start = size - MAX_ACTIVE_BYTES;
        String text = new String(readRange(logFile, start, (int) MAX_ACTIVE_BYTES), StandardCharsets.UTF_8);
        int firstNewline = text.indexOf('\n');
        String retainedTail = firstNewline < 0 ? "" : text.substring(firstNewline + 1);
        Path temp = Files.createTempFile(logFile.getParent(), "netguard-", ".retention");
        try {
            Files.writeString(temp, retainedTail, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            moveReplace(temp, logFile);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void rotateFiles() throws IOException {
        for (int index = MAX_ARCHIVE_FILES; index >= 1; index--) {
            Path source = index == 1 ? file : archivePath(index - 1);
            if (Files.exists(source)) moveReplace(source, archivePath(index));
        }
        needsLineSeparator = false;
    }

    private Path archivePath(int index) {
        return file.resolveSibling(file.getFileName() + "." + index);
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void remember(FlowEvent event) {
        if (events.size() >= LIMIT) events.removeFirst();
        events.addLast(event);
    }

    private static FlowEvent parseEvent(String line) {
        Map<String, Object> fields = new JsonObjectReader(line).readObject();
        if (!fields.keySet().equals(Set.of("at", "sourceIp", "method", "destination",
                "port", "protocol", "outcome", "reason"))) {
            throw new IllegalArgumentException("Unexpected event fields");
        }
        try {
            return new FlowEvent(
                Instant.parse(stringField(fields, "at")),
                stringField(fields, "sourceIp"),
                stringField(fields, "method"),
                stringField(fields, "destination"),
                integerField(fields, "port"),
                stringField(fields, "protocol"),
                stringField(fields, "outcome"),
                stringField(fields, "reason"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid event fields", e);
        }
    }

    private static String stringField(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (!(value instanceof String string)) throw new IllegalArgumentException("Invalid " + name);
        return string;
    }

    private static int integerField(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (!(value instanceof Integer integer)) throw new IllegalArgumentException("Invalid " + name);
        return integer;
    }

    /** Small strict parser for the event contract's flat JSON object. */
    private static final class JsonObjectReader {
        private final String input;
        private int offset;

        JsonObjectReader(String input) { this.input = input; }

        Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            expect('{');
            whitespace();
            if (take('}')) return finish(result);
            while (true) {
                whitespace();
                String key = readString();
                whitespace();
                expect(':');
                whitespace();
                if (offset >= input.length()) throw new IllegalArgumentException("Missing JSON value");
                Object value = input.charAt(offset) == '"' ? readString() : readInteger();
                if (result.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Duplicate JSON field");
                }
                whitespace();
                if (take('}')) return finish(result);
                expect(',');
            }
        }

        private Map<String, Object> finish(Map<String, Object> result) {
            whitespace();
            if (offset != input.length()) throw new IllegalArgumentException("Trailing JSON data");
            return result;
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (offset < input.length()) {
                char c = input.charAt(offset++);
                if (c == '"') return value.toString();
                if (c < 0x20) throw new IllegalArgumentException("Unescaped control character");
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (offset >= input.length()) throw new IllegalArgumentException("Incomplete escape");
                char escaped = input.charAt(offset++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char readUnicodeEscape() {
            if (offset + 4 > input.length()) throw new IllegalArgumentException("Incomplete unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char c = input.charAt(offset++);
                int digit = c >= '0' && c <= '9' ? c - '0'
                    : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (digit < 0) throw new IllegalArgumentException("Invalid unicode escape");
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Integer readInteger() {
            int start = offset;
            take('-');
            if (offset >= input.length()) throw new IllegalArgumentException("Invalid number");
            int digitsStart = offset;
            while (offset < input.length() && input.charAt(offset) >= '0' && input.charAt(offset) <= '9') offset++;
            if (digitsStart == offset || (offset - digitsStart > 1 && input.charAt(digitsStart) == '0')) {
                throw new IllegalArgumentException("Invalid number");
            }
            try {
                return Integer.valueOf(input.substring(start, offset));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number", e);
            }
        }

        private void whitespace() {
            while (offset < input.length()) {
                char c = input.charAt(offset);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
                offset++;
            }
        }

        private boolean take(char expected) {
            if (offset < input.length() && input.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }

    /**
     * Returns up to {@code max} newest matching events as JSON, ordered oldest to newest.
     * Client matching is exact; destination matching is a case-insensitive domain substring.
     */
    public synchronized String recentJson(int max, String client, String destination) {
        int limit = Math.max(0, Math.min(max, LIMIT));
        if (limit == 0) return "[]";
        String clientFilter = client == null ? "" : client;
        String domainFilter = destination == null ? "" : destination.toLowerCase(Locale.ROOT);
        List<FlowEvent> matches = new ArrayList<>();
        for (FlowEvent event : events) {
            if (!clientFilter.isEmpty() && !clientFilter.equals(event.sourceIp())) continue;
            String domain = Objects.toString(event.destination(), "").toLowerCase(Locale.ROOT);
            if (!domainFilter.isEmpty() && !domain.contains(domainFilter)) continue;
            matches.add(event);
        }
        matches.sort(Comparator.comparing(FlowEvent::at));
        int first = Math.max(0, matches.size() - limit);
        StringJoiner join = new StringJoiner(",", "[", "]");
        for (int i = first; i < matches.size(); i++) join.add(matches.get(i).json());
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
