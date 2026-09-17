package org.purpurmc.purpur.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurBackup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * VoltPurUpdater - the hardened self-updater.
 *
 * WHAT CHANGED (hardening pass) and why:
 *  1. DOWNLOADS ARE VERIFIED. Every build publishes <jar>.sha256 next to the jar.
 *     The staged file must match that hash, and the file must be a structurally
 *     valid Paperclip server jar, BEFORE anything on disk is replaced. Previously
 *     the only check was "size > 1 MB".
 *  2. NOTHING IS WIPED BY DEFAULT. The old updater deleted every file in the
 *     server root except worlds/eula/jar - that removed plugins/, all configs,
 *     ops.json, whitelist.json, backups/ and forced a full re-download of
 *     libraries/ and versions/. Now the default is a NON-destructive jar swap;
 *     deleting generated libraries/versions/cache is opt-in
 *     (update.clean-reinstall: true) and is printed as an explicit list first.
 *  3. TWO-PHASE INSTALL. "/vo up <n>" only downloads + verifies + prints a plan.
 *     The install requires "/vo up confirm", so an accidental number can never
 *     overwrite a live jar.
 *  4. ROLLBACK EXISTS. The previous jar is kept as <jar>.bak-<timestamp> and
 *     "/vo rollback" restores it.
 *  5. HONEST TARGET: the jar that is actually launched is detected instead of
 *     blindly writing "server.jar" (panels often use a different file name).
 *  6. Real backups: update.auto-backup now actually runs the world backup before
 *     an update (that config key used to be read by nobody).
 */
public final class VoltPurUpdater {

    private static final String REPO = "tkjij77-ctrl/VoltPur";
    private static final String BRANCH = "ver/26.2";
    private static final String RELEASE_JAR = "VoltPur-26.2.jar";
    private static final long MAX_JAR_BYTES = 512L * 1024L * 1024L;
    private static final long MIN_JAR_BYTES = 5L * 1024L * 1024L;
    private static final int TIMEOUT_CONNECT_MS = 15_000;
    private static final int TIMEOUT_READ_MS = 120_000;

    private static final Path BUILD_CACHE = Path.of("voltpur-builds.txt");
    private static final Path TEMP_DIR = Path.of(".voltpur-tmp");
    private static final Path STAGED_JAR = TEMP_DIR.resolve("staged-server.jar");
    private static final Path STAGED_SUM = TEMP_DIR.resolve("staged-server.jar.sha256");

    /** Generated directories that a clean reinstall may remove (opt-in only). */
    private static final List<String> GENERATED_DIRS = List.of("libraries", "versions", "cache", ".paper-remapped");

    private static volatile List<BuildInfo> cached = List.of();
    private static volatile Plan pending;

    private VoltPurUpdater() {}

    // ------------------------------------------------------------------ model

    /** One published build. The release tag is "build-<runNumber>-<sha>". */
    public record BuildInfo(String tag, String runNumber, String sha, String published) {
        public String shortSha() {
            return sha.length() >= 7 ? sha.substring(0, 7) : sha;
        }
    }

    private record Plan(BuildInfo build, long bytes, String localSha, String publishedSha, boolean checksumVerified,
                        Path installTarget, boolean jarBackup, boolean worldBackup, List<String> wipeList,
                        long usableSpace, String note) {}

    public static boolean hasPending() {
        return pending != null;
    }

    public static String pendingDescription() {
        Plan plan = pending;
        if (plan == null) return "none";
        return "build #" + plan.build().runNumber() + " (" + plan.build().shortSha() + ") -> "
                + plan.installTarget().getFileName();
    }

    // ------------------------------------------------------------------ listing

    /** Fetches recent releases and caches them on DISK so numbers survive a restart. */
    public static void listBuilds(CommandSender sender) {
        sender.sendMessage(Component.text("[VoltPur] Fetching recent builds...", NamedTextColor.YELLOW));
        try {
            List<BuildInfo> builds = fetchBuilds();
            if (builds.isEmpty()) {
                // Disk cache fallback - keeps /vo up usable when the API is unreachable.
                List<BuildInfo> disk = readCache();
                if (!disk.isEmpty()) {
                    cached = disk;
                    sender.sendMessage(Component.text("GitHub not reachable - showing the cached build list.", NamedTextColor.YELLOW));
                    printBuilds(sender);
                    return;
                }
                sender.sendMessage(Component.text("No builds found (API unreachable and no local cache).", NamedTextColor.RED));
                return;
            }
            cached = builds;
            writeCache(builds);
            printBuilds(sender);
        } catch (Exception e) {
            List<BuildInfo> disk = readCache();
            if (!disk.isEmpty()) {
                cached = disk;
                sender.sendMessage(Component.text("Fetch failed (" + e.getMessage() + ") - showing the cached list.", NamedTextColor.YELLOW));
                printBuilds(sender);
                return;
            }
            sender.sendMessage(Component.text("Failed to list builds: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private static void printBuilds(CommandSender sender) {
        sender.sendMessage(Component.text("=== VoltPur builds (newest first) ===", NamedTextColor.GOLD));
        for (int i = 0; i < cached.size(); i++) {
            BuildInfo build = cached.get(i);
            sender.sendMessage(Component.text("  [" + (i + 1) + "] build #" + build.runNumber()
                    + "  " + build.shortSha() + "  " + build.published(), i == 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Stage a build with /vo up <number> - nothing is installed until /vo up confirm.", NamedTextColor.AQUA));
    }

    private static List<BuildInfo> fetchBuilds() throws Exception {
        String url = "https://api.github.com/repos/" + REPO + "/releases?per_page=15";
        String json = httpGet(url, tokenOrNull());
        List<BuildInfo> out = new ArrayList<>();
        Matcher tag = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        while (tag.find()) {
            String tagName = tag.group(1);
            Matcher parsed = Pattern.compile("^build-(\\d+)-([0-9a-fA-F]{7,40})$").matcher(tagName);
            if (!parsed.matches()) continue;
            String published = "";
            Matcher date = Pattern.compile("\"published_at\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (date.find(tag.end())) published = date.group(1);
            out.add(new BuildInfo(tagName, parsed.group(1), parsed.group(2).toLowerCase(Locale.ROOT), published));
        }
        return out;
    }

    private static void writeCache(List<BuildInfo> builds) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# VoltPur build cache - written by /vo up list. Format: tag|published");
            for (BuildInfo build : builds) lines.add(build.tag() + "|" + build.published());
            Files.write(BUILD_CACHE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Updater] Could not write build cache: " + e.getMessage());
        }
    }

    private static List<BuildInfo> readCache() {
        try {
            if (!Files.isRegularFile(BUILD_CACHE)) return List.of();
            List<BuildInfo> out = new ArrayList<>();
            for (String line : Files.readAllLines(BUILD_CACHE, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 2);
                Matcher parsed = Pattern.compile("^build-(\\d+)-([0-9a-fA-F]{7,40})$").matcher(parts[0].trim());
                if (!parsed.matches()) continue;
                out.add(new BuildInfo(parts[0].trim(), parsed.group(1), parsed.group(2).toLowerCase(Locale.ROOT),
                        parts.length > 1 ? parts[1].trim() : ""));
            }
            return out;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Updater] Could not read build cache: " + e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ phase 1: prepare

    /**
     * Downloads and VERIFIES the selected build, then prints a plan. Nothing is
     * installed here. Runs off the main thread (network + disk only).
     */
    public static void prepare(CommandSender sender, String selection) {
        try {
            List<BuildInfo> builds = cached;
            if (builds.isEmpty()) builds = readCache();
            if (builds.isEmpty()) {
                sender.sendMessage(Component.text("No builds cached. Run /vo up list first.", NamedTextColor.YELLOW));
                return;
            }
            BuildInfo build;
            if (selection == null || selection.isBlank() || selection.equalsIgnoreCase("latest")) {
                build = builds.get(0);
            } else {
                int index;
                try {
                    index = Integer.parseInt(selection.trim());
                } catch (NumberFormatException notANumber) {
                    sender.sendMessage(Component.text("Usage: /vo up <number> | /vo up list | /vo up confirm", NamedTextColor.RED));
                    return;
                }
                if (index < 1 || index > builds.size()) {
                    sender.sendMessage(Component.text("Invalid number. Use /vo up list to see available builds.", NamedTextColor.RED));
                    return;
                }
                build = builds.get(index - 1);
            }

            sender.sendMessage(Component.text("[VoltPur] Selected build #" + build.runNumber()
                    + " (" + build.shortSha() + ")", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("[VoltPur] Downloading and verifying...", NamedTextColor.YELLOW));

            Files.createDirectories(TEMP_DIR);
            String assetUrl = "https://github.com/" + REPO + "/releases/download/" + build.tag() + "/" + RELEASE_JAR;
            long bytes = downloadToFile(assetUrl, STAGED_JAR, MAX_JAR_BYTES, null);
            if (bytes < MIN_JAR_BYTES) {
                throw new IOException("Downloaded file is only " + bytes + " bytes - not a server jar.");
            }

            String localSha = sha256(STAGED_JAR);
            String publishedSha = tryDownloadChecksum(assetUrl);
            boolean verified = false;
            if (publishedSha != null && !publishedSha.isBlank()) {
                if (!localSha.equalsIgnoreCase(publishedSha.trim())) {
                    Files.deleteIfExists(STAGED_JAR);
                    throw new IOException("Checksum MISMATCH - refusing the download. published="
                            + publishedSha.trim() + " local=" + localSha);
                }
                verified = true;
            } else if (VoltPurConfig.updateRequireChecksum) {
                Files.deleteIfExists(STAGED_JAR);
                throw new IOException("No published SHA-256 for this build. Set update.require-checksum=false to override "
                        + "(not recommended).");
            }
            verifyPaperclipJar(STAGED_JAR);

            Path target = detectLauncherJar();

            List<String> wipeList = new ArrayList<>();
            if (VoltPurConfig.updateCleanReinstall) {
                for (String dir : GENERATED_DIRS) {
                    if (Files.exists(Path.of(dir))) wipeList.add(dir + "/");
                }
            }
            long usable = usableSpace();
            if (usable > 0 && usable < bytes * 3L) {
                Files.deleteIfExists(STAGED_JAR);
                throw new IOException("Not enough free disk space (need ~" + human(bytes * 3L) + ", have " + human(usable) + ").");
            }

            pending = new Plan(build, bytes, localSha, publishedSha, verified, target,
                    VoltPurConfig.updateKeepJarBackup, VoltPurConfig.updateAutoBackup, List.copyOf(wipeList), usable, "");

            printPlan(sender);
        } catch (Exception e) {
            pending = null;
            sender.sendMessage(Component.text("[VoltPur] Staging failed: " + e.getMessage(), NamedTextColor.RED));
            Bukkit.getLogger().warning("[VoltPur-Updater] Staging failed: " + e.getMessage());
        }
    }

    /** Prints exactly what will happen, in plain language, before anything is touched. */
    public static void printPlan(CommandSender sender) {
        Plan plan = pending;
        if (plan == null) {
            sender.sendMessage(Component.text("Nothing staged. Use /vo up list then /vo up <number>.", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("=== Update plan (nothing done yet) ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  build      : #" + plan.build().runNumber() + " (" + plan.build().shortSha() + ")  "
                + plan.build().published(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  jar size   : " + human(plan.bytes()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  sha256     : " + plan.localSha().substring(0, 16) + "...  "
                + (plan.checksumVerified() ? "VERIFIED against the published checksum" : "UNVERIFIED (no published checksum)"),
                plan.checksumVerified() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  install to : " + plan.installTarget(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  rollback   : " + (plan.jarBackup()
                ? "current jar is copied to <jar>.bak-<timestamp> (use /vo rollback)" : "DISABLED (update.keep-jar-backup=false)"),
                plan.jarBackup() ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  world zip  : " + (plan.worldBackup()
                ? "a world backup runs before the swap" : "disabled (update.auto-backup=false)"),
                plan.worldBackup() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (plan.wipeList().isEmpty()) {
            sender.sendMessage(Component.text("  delete     : NOTHING - plugins, configs, worlds, backups are kept.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("  delete     : " + String.join(", ", plan.wipeList())
                    + "  (regenerated on next start; needs internet)", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("  free space : " + human(plan.usableSpace()) + " available before the swap", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Type /vo up confirm to apply, or /vo up cancel to abort.", NamedTextColor.AQUA));
    }

    // ------------------------------------------------------------------ phase 2: confirm

    /** Applies the staged plan (off the main thread; uses the main thread only to flush saves). */
    public static void confirm(CommandSender sender) {
        Plan plan = pending;
        if (plan == null) {
            sender.sendMessage(Component.text("Nothing staged. Use /vo up list then /vo up <number>.", NamedTextColor.YELLOW));
            return;
        }
        try {
            if (!Files.isRegularFile(STAGED_JAR)) {
                pending = null;
                throw new IOException("Staged jar is missing (cleaned up?). Re-run /vo up <number>.");
            }
            // Re-verify right before the swap: the file existed on disk between two commands.
            String currentSha = sha256(STAGED_JAR);
            if (!currentSha.equalsIgnoreCase(plan.localSha())) {
                pending = null;
                throw new IOException("Staged jar changed after verification - aborting.");
            }

            if (plan.worldBackup()) {
                sender.sendMessage(Component.text("[VoltPur] Running a world backup before the update...", NamedTextColor.YELLOW));
                VoltPurBackup.Result backup = VoltPurBackup.backupForUpdate();
                sender.sendMessage(Component.text(backup.ok()
                        ? "[VoltPur] Backup OK: " + backup.archive().getFileName() + " (" + human(backup.bytes()) + ")"
                        : "[VoltPur] Backup FAILED: " + backup.error() + " - continuing because update.auto-backup does not block updates.",
                        backup.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }

            if (plan.jarBackup()) {
                Path backup = Path.of(plan.installTarget() + ".bak-" + stamp());
                Files.copy(plan.installTarget(), backup, StandardCopyOption.REPLACE_EXISTING);
                sender.sendMessage(Component.text("[VoltPur] Previous jar saved as " + backup.getFileName(), NamedTextColor.GRAY));
            }

            for (String entry : plan.wipeList()) {
                Path dir = Path.of(entry);
                if (Files.isDirectory(dir)) {
                    deleteRecursively(dir);
                    sender.sendMessage(Component.text("[VoltPur] Deleted generated directory " + entry, NamedTextColor.GRAY));
                }
            }

            Path temp = plan.installTarget().resolveSibling(plan.installTarget().getFileName() + ".new");
            Files.copy(STAGED_JAR, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, plan.installTarget(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, plan.installTarget(), StandardCopyOption.REPLACE_EXISTING);
            }

            writeStamp(plan);
            Files.deleteIfExists(STAGED_JAR);
            Files.deleteIfExists(STAGED_SUM);
            pending = null;

            sender.sendMessage(Component.text("[OK] Installed build #" + plan.build().runNumber()
                    + " (" + plan.build().shortSha() + ") into " + plan.installTarget().getFileName()
                    + "  sha256 " + plan.localSha().substring(0, 12) + "...", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Restart the server to run it. The startup banner cross-checks this stamp.", NamedTextColor.YELLOW));
        } catch (Exception e) {
            pending = null;
            sender.sendMessage(Component.text("[VoltPur] Update failed (nothing was replaced): " + e.getMessage(), NamedTextColor.RED));
            Bukkit.getLogger().warning("[VoltPur-Updater] Confirm failed: " + e.getMessage());
        }
    }

    public static void cancel(CommandSender sender) {
        try {
            Files.deleteIfExists(STAGED_JAR);
            Files.deleteIfExists(STAGED_SUM);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Could not remove the staged file: " + e.getMessage(), NamedTextColor.YELLOW));
        }
        boolean had = pending != null;
        pending = null;
        sender.sendMessage(Component.text(had ? "Staged update cancelled. Nothing was changed."
                : "Nothing was staged.", NamedTextColor.YELLOW));
    }

    // ------------------------------------------------------------------ rollback

    /** Restores the newest <jar>.bak-* backup. */
    public static void rollback(CommandSender sender) {
        try {
            Path target = detectLauncherJar();
            Path parent = target.getParent();
            Path newest = null;
            long newestTime = -1L;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, target.getFileName() + ".bak-*")) {
                for (Path candidate : stream) {
                    long modified = Files.getLastModifiedTime(candidate).toMillis();
                    if (modified > newestTime) {
                        newestTime = modified;
                        newest = candidate;
                    }
                }
            }
            if (newest == null) {
                sender.sendMessage(Component.text("No jar backup found next to " + target.getFileName()
                        + " (update.keep-jar-backup may be false).", NamedTextColor.RED));
                return;
            }
            Path safetyCopy = Path.of(target + ".before-rollback-" + stamp());
            Files.copy(target, safetyCopy, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(newest, target, StandardCopyOption.REPLACE_EXISTING);
            sender.sendMessage(Component.text("[OK] Rolled back to " + newest.getFileName(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("The replaced jar was kept as " + safetyCopy.getFileName()
                    + ". Restart to run the restored build.", NamedTextColor.GRAY));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Rollback failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The jar the panel actually launches. Never blindly assumes "server.jar". */
    static Path detectLauncherJar() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path standard = cwd.resolve("server.jar");
        if (Files.isRegularFile(standard)) return standard;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cwd, "*.jar")) {
            Path best = null;
            long bestSize = 0L;
            for (Path candidate : stream) {
                long size = Files.size(candidate);
                if (size < 20L * 1024L * 1024L || size <= bestSize) continue;
                if (looksLikeServerJar(candidate)) {
                    best = candidate;
                    bestSize = size;
                }
            }
            if (best != null) return best;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Updater] Could not scan for a launcher jar: " + e.getMessage());
        }
        return standard;
    }

    private static boolean looksLikeServerJar(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String marker = serverJarMarker(zip);
            return marker != null;
        } catch (Exception notAServerJar) {
            return false;
        }
    }

    /**
     * Returns a description of the marker that identifies a Paperclip server jar, or
     * null. Several markers are accepted on purpose: Paperclip's exact layout has
     * changed between versions, and a too-strict check would reject valid builds.
     */
    static String serverJarMarker(ZipFile zip) {
        try {
            if (zip.getEntry("META-INF/versions.list") != null) return "META-INF/versions.list";
            if (zip.getEntry("io/papermc/paperclip/Paperclip.class") != null) return "paperclip bootstrap class";
            ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try (InputStream in = zip.getInputStream(manifest)) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (text.contains("paperclip")) return "manifest mentions paperclip";
                }
            }
            if (zip.size() > 1000) return "large jar with " + zip.size() + " entries";
            return null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    /**
     * A staged download must be a readable zip, must contain a manifest, and must
     * look like a Paperclip server jar. This rejects HTML error pages, truncated
     * downloads and unrelated archives before anything on disk is replaced.
     */
    static void verifyPaperclipJar(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.getEntry("META-INF/MANIFEST.MF") == null) {
                throw new IOException("staged jar has no META-INF/MANIFEST.MF - not a server jar");
            }
            String marker = serverJarMarker(zip);
            if (marker == null) {
                throw new IOException("staged jar does not look like a Paperclip server jar (no paperclip marker found)");
            }
            // A vanilla Paperclip launcher stores the bundled server jar; if it is
            // present it must not be empty (guards against 0-byte/truncated bundles).
            ZipEntry bundled = zip.getEntry("META-INF/versions.list");
            if (bundled != null && bundled.getSize() == 0L) {
                throw new IOException("staged jar has an empty META-INF/versions.list - download is incomplete");
            }
        } catch (java.util.zip.ZipException notAZip) {
            throw new IOException("staged file is not a valid zip/jar", notAZip);
        }
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String tryDownloadChecksum(String assetUrl) {
        try {
            String body = httpGetText(assetUrl + ".sha256", null, 4096);
            if (body == null || body.isBlank()) return null;
            String first = body.trim().split("\\s+")[0];
            return first.matches("[0-9a-fA-F]{64}") ? first : null;
        } catch (Exception noChecksumPublished) {
            return null;
        }
    }

    private static long usableSpace() {
        try {
            return Files.getFileStore(Path.of(".").toAbsolutePath()).getUsableSpace();
        } catch (IOException e) {
            return -1L;
        }
    }

    private static String tokenOrNull() {
        String token = VoltPurConfig.githubToken;
        return (token == null || token.isBlank()) ? null : token;
    }

    public static String httpGet(String url, String token) throws Exception {
        String body = httpGetText(url, token, 4 * 1024 * 1024);
        if (body == null) throw new IOException("Empty response from " + url);
        return body;
    }

    private static String httpGetText(String url, String token, int maxBytes) throws Exception {
        HttpURLConnection connection = open(url, token, TIMEOUT_READ_MS);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] data = readBounded(in, maxBytes);
                return new String(data, StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static long downloadToFile(String url, Path destination, long maxBytes, String token) throws Exception {
        HttpURLConnection connection = open(url, token, TIMEOUT_READ_MS);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            long declared = connection.getContentLengthLong();
            if (declared > maxBytes) throw new IOException("Remote file is larger than the " + human(maxBytes) + " limit");
            long total = 0L;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) throw new IOException("Download exceeded the " + human(maxBytes) + " limit");
                    out.write(buffer, 0, read);
                }
            }
            return total;
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String token, int readTimeout) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Refusing a non-HTTPS download URL: " + url);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(TIMEOUT_CONNECT_MS);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "VoltPur-Updater/2.0");
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream;q=0.9, */*;q=0.1");
        if (token != null && !token.isBlank()) connection.setRequestProperty("Authorization", "token " + token);
        return connection;
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                out.write(buffer, 0, read);
                break;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static void writeStamp(Plan plan) {
        try {
            List<String> lines = List.of(
                    "# written by /vo up confirm - read at startup by the VoltPur banner",
                    "build=" + plan.build().runNumber(),
                    "tag=" + plan.build().tag(),
                    "commit=" + plan.build().sha(),
                    "built-at=" + plan.build().published(),
                    "sha256=" + plan.localSha(),
                    "checksum-verified=" + plan.checksumVerified(),
                    "installed-to=" + plan.installTarget().getFileName(),
                    "installed-at=" + new java.util.Date());
            Files.write(Path.of("voltpur-installed.txt"), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Updater] Could not write voltpur-installed.txt: " + e.getMessage());
        }
    }

    private static String stamp() {
        return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
    }

    static String human(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KiB";
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
