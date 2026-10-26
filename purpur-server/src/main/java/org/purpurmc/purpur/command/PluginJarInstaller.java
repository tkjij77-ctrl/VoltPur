package org.purpurmc.purpur.command;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Secure, bounded downloader used by {@code /vo in}. It only writes plugin JARs. */
final class PluginJarInstaller {
    static final long MAX_BYTES = 100L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_REDIRECTS = 5;
    private static final Set<String> TARGETS = Set.of("plugins", "plugin-pro");

    private PluginJarInstaller() {
    }

    record Result(Path path, long bytes) {
    }

    static Result install(String source, String targetName) throws Exception {
        URI uri = validateRemoteUri(source);
        if (!TARGETS.contains(targetName)) {
            throw new IllegalArgumentException("Target must be plugins or plugin-pro");
        }

        Path serverRoot = Path.of(".").toAbsolutePath().normalize();
        Path targetDir = serverRoot.resolve(targetName).normalize();
        if (!targetDir.getParent().equals(serverRoot)) {
            throw new IllegalArgumentException("Invalid target directory");
        }
        Files.createDirectories(targetDir);
        if (!Files.isDirectory(targetDir)) {
            throw new IOException("Target is not a directory: " + targetName);
        }

        String fileName = safeJarName(uri);
        Path destination = targetDir.resolve(fileName).normalize();
        if (!destination.getParent().equals(targetDir)) {
            throw new IllegalArgumentException("Unsafe plugin file name");
        }

        Path temporary = Files.createTempFile(targetDir, ".voltpur-download-", ".tmp");
        try {
            long bytes = download(uri, temporary);
            validateJar(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(destination, bytes);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static URI validateRemoteUri(String raw) throws Exception {
        if (raw == null || raw.isBlank() || raw.length() > 2_048) {
            throw new IllegalArgumentException("URL is empty or too long");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim()).normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Malformed URL", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are allowed");
        }
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getRawPath() != null && (uri.getRawPath().contains("../") || uri.getRawPath().contains("..\\"))) {
            throw new IllegalArgumentException("URL must have a public host and no credentials or fragment");
        }
        int port = uri.getPort();
        if (port < -1 || port > 65_535) {
            throw new IllegalArgumentException("Invalid URL port");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Local or private network URLs are not allowed");
            }
        }
        return uri;
    }

    static String safeJarName(URI uri) {
        String path = uri.getRawPath();
        String candidate = path == null || path.isBlank() ? "plugin" : path.substring(path.lastIndexOf('/') + 1);
        candidate = URLDecoder.decode(candidate, StandardCharsets.UTF_8);
        if (candidate.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            candidate = candidate.substring(0, candidate.length() - 4);
        }
        candidate = candidate.replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (candidate.isBlank()) candidate = "plugin";
        if (candidate.length() > 100) candidate = candidate.substring(0, 100);
        return candidate + ".jar";
    }

    private static long download(URI initial, Path temporary) throws Exception {
        URI current = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            current = validateRemoteUri(current.toString());
            HttpURLConnection connection = (HttpURLConnection) new URL(current.toString()).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "VoltPur-Plugin-Installer/1.0");
            connection.setRequestProperty("Accept", "application/java-archive, application/octet-stream;q=0.9, */*;q=0.1");

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) throw new IOException("Redirect has no Location header");
                if (redirect == MAX_REDIRECTS) throw new IOException("Too many redirects");
                current = current.resolve(location);
                continue;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("Download returned HTTP " + status);
            }
            long declared = connection.getContentLengthLong();
            if (declared == 0) throw new IOException("Remote file is empty");
            if (declared > MAX_BYTES) throw new IOException("Plugin exceeds the 100 MiB size limit");
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                throw new IOException("Remote server returned HTML instead of a JAR");
            }

            long count = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > MAX_BYTES) throw new IOException("Plugin exceeds the 100 MiB size limit");
                    output.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }
            if (count == 0) throw new IOException("Remote file is empty");
            return count;
        }
        throw new IOException("Too many redirects");
    }

    private static void validateJar(Path file) throws IOException {
        byte[] prefix = new byte[16];
        int read;
        try (InputStream input = Files.newInputStream(file)) {
            read = input.read(prefix);
        }
        if (read >= 5) {
            String start = new String(prefix, 0, read, StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
            if (start.startsWith("<!doc") || start.startsWith("<html")) {
                throw new IOException("Downloaded content is HTML, not a plugin JAR");
            }
        }
        try (ZipFile jar = new ZipFile(file.toFile())) {
            ZipEntry pluginYml = jar.getEntry("plugin.yml");
            ZipEntry paperPluginYml = jar.getEntry("paper-plugin.yml");
            if (pluginYml == null && paperPluginYml == null) {
                throw new IOException("JAR has no plugin.yml or paper-plugin.yml");
            }
        } catch (java.util.zip.ZipException ex) {
            throw new IOException("Downloaded file is not a valid JAR", ex);
        }
    }
}
