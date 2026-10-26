package org.purpurmc.purpur.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VoltPur AtomicConfigWriter (§3.3).
 *
 * Enforces atomic and non-destructive configuration updates:
 * - Line-by-line editing preserving ALL comments, blank lines, ordering, and indentation.
 * - Never uses Properties.store() or standard YAML dumpers which strip user comments.
 * - Atomic write lifecycle:
 *     1. Write modified content to a temporary file in the same directory.
 *     2. Copy the original file to a backup (.bak).
 *     3. Atomically move the temporary file to the target path (StandardCopyOption.ATOMIC_MOVE).
 *     4. Clean up temporary files on error.
 */
public final class AtomicConfigWriter {

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\s*)([a-zA-Z0-9_.-]+)\\s*[:=]\\s*(.*)$");

    private AtomicConfigWriter() {}

    /**
     * Atomically writes lines of text to the target path, creating a .bak backup and using ATOMIC_MOVE.
     */
    public static synchronized void writeLinesAtomically(Path targetPath, List<String> lines) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Path dir = parent != null ? parent : Paths.get(".");
        String fileName = targetPath.getFileName().toString();
        Path tempPath = dir.resolve(fileName + ".tmp." + UUID.randomUUID());

        try {
            Files.write(tempPath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            if (Files.exists(targetPath)) {
                Path backupPath = dir.resolve(fileName + ".bak");
                try {
                    Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    VoltPurLogger.warn("AtomicWriter", "Could not create backup " + backupPath, e);
                }
            }

            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Updates key-value properties in a .properties file preserving ALL comments, blank lines, and formatting.
     */
    public static synchronized boolean writeProperties(Path path, Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) return true;
        try {
            List<String> lines;
            if (Files.exists(path)) {
                lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            } else {
                lines = new ArrayList<>();
            }

            Map<String, String> remaining = new LinkedHashMap<>(updates);
            List<String> updatedLines = new ArrayList<>(lines.size() + updates.size());

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    updatedLines.add(line);
                    continue;
                }

                int eqIdx = line.indexOf('=');
                if (eqIdx != -1) {
                    String key = line.substring(0, eqIdx).trim();
                    if (remaining.containsKey(key)) {
                        String newVal = remaining.remove(key);
                        updatedLines.add(key + "=" + newVal);
                        continue;
                    }
                }
                updatedLines.add(line);
            }

            // Append any new properties that were not in the file
            for (Map.Entry<String, String> entry : remaining.entrySet()) {
                updatedLines.add(entry.getKey() + "=" + entry.getValue());
            }

            writeLinesAtomically(path, updatedLines);
            return true;
        } catch (Exception e) {
            VoltPurLogger.error("AtomicWriter", "Failed updating properties in " + path, e);
            return false;
        }
    }

    /**
     * Updates hierarchical keys in a YAML file (e.g. spigot.yml) preserving ALL comments, indentation, and structure.
     * Dotted paths (e.g. "world-settings.default.merge-radius.item") are parsed hierarchically.
     */
    public static synchronized boolean writeYaml(Path path, Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) return true;
        try {
            List<String> lines;
            if (Files.exists(path)) {
                lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            } else {
                lines = new ArrayList<>();
            }

            Map<String, String> remaining = new LinkedHashMap<>(updates);
            List<String> updatedLines = new ArrayList<>(lines.size() + updates.size() * 2);

            // Stack to track current YAML hierarchy: list of [indent, key]
            Deque<IndentKey> stack = new ArrayDeque<>();

            for (String line : lines) {
                String trimmed = line.trim();

                // Preserve comment lines and blank lines completely
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    updatedLines.add(line);
                    continue;
                }

                int indent = countLeadingSpaces(line);

                // Pop stack until parent indent is strictly less than current indent
                while (!stack.isEmpty() && stack.peek().indent >= indent) {
                    stack.pop();
                }

                Matcher matcher = KEY_VALUE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String key = matcher.group(2).trim();
                    String rawValue = matcher.group(3);

                    // Build full path
                    String currentPath = buildPath(stack, key);

                    if (remaining.containsKey(currentPath)) {
                        String newValue = remaining.remove(currentPath);
                        // Extract inline comment if present
                        String inlineComment = "";
                        int commentIdx = rawValue.indexOf('#');
                        if (commentIdx != -1) {
                            inlineComment = " " + rawValue.substring(commentIdx);
                        }
                        String indentStr = line.substring(0, matcher.start(2));
                        updatedLines.add(indentStr + key + ": " + newValue + inlineComment);

                        // If it had children or was a section, push to stack
                        if (rawValue.trim().isEmpty() || rawValue.trim().startsWith("#")) {
                            stack.push(new IndentKey(indent, key));
                        }
                        continue;
                    }

                    if (rawValue.trim().isEmpty() || rawValue.trim().startsWith("#")) {
                        stack.push(new IndentKey(indent, key));
                    }
                }

                updatedLines.add(line);
            }

            // If any updates remain (sections/keys that were missing), insert them
            if (!remaining.isEmpty()) {
                insertMissingYamlEntries(updatedLines, remaining);
            }

            writeLinesAtomically(path, updatedLines);
            return true;
        } catch (Exception e) {
            VoltPurLogger.error("AtomicWriter", "Failed updating YAML in " + path, e);
            return false;
        }
    }

    private static String buildPath(Deque<IndentKey> stack, String key) {
        if (stack.isEmpty()) return key;
        StringBuilder sb = new StringBuilder();
        Iterator<IndentKey> it = stack.descendingIterator();
        while (it.hasNext()) {
            sb.append(it.next().key).append('.');
        }
        sb.append(key);
        return sb.toString();
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') count++;
            else if (c == '\t') count += 2;
            else break;
        }
        return count;
    }

    private static void insertMissingYamlEntries(List<String> lines, Map<String, String> remaining) {
        for (Map.Entry<String, String> entry : remaining.entrySet()) {
            String path = entry.getKey();
            String value = entry.getValue();
            String[] parts = path.split("\\.");

            int currentLineIdx = -1;
            int currentIndent = 0;

            // Search for existing parent segments
            for (int i = 0; i < parts.length - 1; i++) {
                String segment = parts[i];
                int found = findSectionLine(lines, segment, currentIndent, currentLineIdx);
                if (found != -1) {
                    currentLineIdx = found;
                    currentIndent += 2;
                } else {
                    // Create missing parent section
                    String indentStr = " ".repeat(currentIndent);
                    if (currentLineIdx == -1 || currentLineIdx >= lines.size()) {
                        lines.add(indentStr + segment + ":");
                        currentLineIdx = lines.size() - 1;
                    } else {
                        lines.add(currentLineIdx + 1, indentStr + segment + ":");
                        currentLineIdx++;
                    }
                    currentIndent += 2;
                }
            }

            // Insert final key: value
            String leafKey = parts[parts.length - 1];
            String indentStr = " ".repeat(currentIndent);
            String lineToAdd = indentStr + leafKey + ": " + value;
            if (currentLineIdx == -1 || currentLineIdx >= lines.size()) {
                lines.add(lineToAdd);
            } else {
                lines.add(currentLineIdx + 1, lineToAdd);
            }
        }
    }

    private static int findSectionLine(List<String> lines, String sectionName, int expectedIndent, int afterLine) {
        int start = Math.max(0, afterLine + 1);
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
            int indent = countLeadingSpaces(line);
            if (indent == expectedIndent) {
                Matcher m = KEY_VALUE_PATTERN.matcher(line);
                if (m.matches() && m.group(2).trim().equals(sectionName)) {
                    return i;
                }
            } else if (indent < expectedIndent && afterLine != -1) {
                // Left parent scope
                break;
            }
        }
        return -1;
    }

    private static class IndentKey {
        final int indent;
        final String key;

        IndentKey(int indent, String key) {
            this.indent = indent;
            this.key = key;
        }
    }
}
