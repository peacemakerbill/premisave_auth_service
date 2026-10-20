package com.premisave.auth.service.storage;

import com.premisave.auth.config.StorageProperties;
import com.premisave.auth.config.StorageProperties.Protocol;
import com.premisave.auth.config.StorageProperties.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules that decide where files may live, with no I/O beyond reading paths: configuration
 * checks, the resolved base directory, file and folder names, key-to-path resolution that cannot
 * escape the base directory, and recognition of a mounted network share.
 *
 * Kept free of Spring so each rule can be tested on its own.
 */
public final class StorageLayout {

    private static final Pattern HOST = Pattern.compile("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ._()\\-]{0,119}$");
    private static final Set<String> SYSTEM_DIRS = Set.of(
            "/", "/bin", "/boot", "/dev", "/etc", "/lib", "/lib64", "/proc", "/root", "/run", "/sbin", "/sys", "/usr", "/var");

    private StorageLayout() {
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /** Problems with the configuration in plain words. Empty when it can be used. Never contains a secret. */
    public static List<String> validate(StorageProperties p) {
        List<String> problems = new ArrayList<>();
        if (p.getType() == Type.LOCAL) {
            String raw = p.getLocal().getPath();
            if (raw == null || raw.isBlank()) {
                problems.add("STORAGE_LOCAL_PATH is empty");
            } else {
                checkDirectory(raw, "STORAGE_LOCAL_PATH", problems);
            }
            return problems;
        }

        StorageProperties.Network n = p.getNetwork();
        if (n.getMountPath() == null || n.getMountPath().isBlank()) {
            problems.add("STORAGE_NETWORK_MOUNT_PATH is empty");
        } else {
            checkDirectory(n.getMountPath(), "STORAGE_NETWORK_MOUNT_PATH", problems);
        }
        if (!isBlank(n.getHost()) && !HOST.matcher(n.getHost().trim()).matches() && !IPV6.matcher(n.getHost().trim()).matches()) {
            problems.add("STORAGE_NETWORK_HOST is not a valid IP address or host name");
        }
        String share = n.getShare();
        if (!isBlank(share) && (share.contains("..") || share.contains("\\") || share.indexOf('\0') >= 0)) {
            problems.add("STORAGE_NETWORK_SHARE contains characters that are not allowed");
        }
        String dir = n.getDirectory();
        if (!isBlank(dir)) {
            for (String part : dir.trim().replace('\\', '/').split("/")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!SEGMENT.matcher(part).matches() || part.equals("..") || part.equals(".")) {
                    problems.add("STORAGE_NETWORK_DIRECTORY must be a relative folder name without .. or special characters");
                    break;
                }
            }
        }
        return problems;
    }

    private static void checkDirectory(String raw, String variable, List<String> problems) {
        if (raw.indexOf('\0') >= 0) {
            problems.add(variable + " contains an invalid character");
            return;
        }
        Path path;
        try {
            // Judge absoluteness before expand() makes every path absolute
            if (!Path.of(withHome(raw.trim())).isAbsolute()) {
                problems.add(variable + " must be an absolute path (or start with ~/)");
                return;
            }
            path = expand(raw);
        } catch (InvalidPathException e) {
            problems.add(variable + " is not a valid path");
            return;
        }
        if (SYSTEM_DIRS.contains(path.toString()) || path.getNameCount() == 0) {
            problems.add(variable + " must not point at the root or a system directory");
        }
    }

    /** Expands a leading ~ and normalizes. Throws InvalidPathException for impossible paths. */
    public static Path expand(String raw) {
        return Path.of(withHome(raw.trim())).toAbsolutePath().normalize();
    }

    private static String withHome(String value) {
        if (value.equals("~")) {
            return System.getProperty("user.home", "");
        }
        if (value.startsWith("~/")) {
            return System.getProperty("user.home", "") + value.substring(1);
        }
        return value;
    }

    /**
     * The directory that holds all application folders. Call validate() first.
     * For NETWORK this is mount-path/directory, which can never leave the mount path.
     */
    public static Path baseDirectory(StorageProperties p) {
        if (p.getType() == Type.LOCAL) {
            return expand(p.getLocal().getPath());
        }
        Path mount = mountPoint(p);
        String dir = p.getNetwork().getDirectory();
        if (isBlank(dir)) {
            return mount;
        }
        Path base = mount.resolve(dir.trim().replace('\\', '/')).normalize();
        if (!base.startsWith(mount)) {
            throw new IllegalArgumentException("STORAGE_NETWORK_DIRECTORY leaves the mount path");
        }
        return base;
    }

    public static Path mountPoint(StorageProperties p) {
        return expand(p.getNetwork().getMountPath());
    }

    // ------------------------------------------------------------------
    // Names and keys
    // ------------------------------------------------------------------

    /** Turns any text into a safe single file or folder name. Never empty, never starts with a dot. */
    public static String sanitizeName(String name, String fallback) {
        String cleaned = name == null ? "" : name
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("^[.\\s_-]+", "");
        cleaned = cleaned.replaceAll("[.\\s]+$", "");
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120).trim();
        }
        if (cleaned.isEmpty() || isWindowsReserved(cleaned)) {
            return fallback;
        }
        return cleaned;
    }

    private static boolean isWindowsReserved(String name) {
        String base = name.toUpperCase(Locale.ROOT);
        int dot = base.indexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        return base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    }

    /** True when the key is one or two plain segments (folder or folder/file). */
    public static boolean isWellFormedKey(String key) {
        if (key == null || key.isBlank() || key.length() > 260 || key.indexOf('\0') >= 0
                || key.indexOf('\\') >= 0 || key.startsWith("/") || key.endsWith("/")) {
            return false;
        }
        String[] parts = key.split("/", -1);
        if (parts.length > 2) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves a key below the base directory and checks, on the lexical path, that it stays there.
     * Symbolic links are checked separately by the service when the file system is touched.
     *
     * @throws IllegalArgumentException when the key is malformed or escapes the base directory
     */
    public static Path resolveKey(Path base, String key) {
        if (!isWellFormedKey(key)) {
            throw new IllegalArgumentException("Malformed storage key");
        }
        Path resolved = base.resolve(key).normalize();
        if (!resolved.startsWith(base) || resolved.equals(base)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory");
        }
        return resolved;
    }

    // ------------------------------------------------------------------
    // Mount detection
    // ------------------------------------------------------------------

    /**
     * Decides from the text of /proc/mounts whether the share is mounted at the mount point.
     *
     * Lines look like "//192.168.1.20/premisave /mnt/premisave-nas cifs rw,..." (SMB) or
     * "192.168.1.20:/volume1/premisave /mnt/premisave-nas nfs4 rw,..." (NFS). Spaces in paths
     * appear as \040. When a host is configured the source must name it; when a share is also
     * configured it must match too. With neither configured, any filesystem at the mount point counts.
     */
    public static boolean isMounted(List<String> procMountsLines, Path mountPoint, Protocol protocol, String host, String share) {
        String wanted = mountPoint.toString();
        String h = isBlank(host) ? "" : host.trim().toLowerCase(Locale.ROOT);
        String s = isBlank(share) ? "" : trimSlashes(share.trim().replace('\\', '/')).toLowerCase(Locale.ROOT);

        for (String line : procMountsLines) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 3) {
                continue;
            }
            String target = unescape(f[1]);
            if (!stripTrailingSlash(target).equals(stripTrailingSlash(wanted))) {
                continue;
            }
            String source = unescape(f[0]).toLowerCase(Locale.ROOT);
            String type = f[2].toLowerCase(Locale.ROOT);
            if (h.isEmpty()) {
                return true; // nothing to compare the source with; something is mounted there
            }
            if (protocol == Protocol.SMB) {
                if (!(type.equals("cifs") || type.equals("smb3") || type.equals("smbfs") || type.startsWith("fuse"))) {
                    continue;
                }
                String prefix = "//" + h + "/";
                if (source.startsWith(prefix) && (s.isEmpty() || trimSlashes(source.substring(prefix.length())).equals(s)
                        || trimSlashes(source.substring(prefix.length())).startsWith(s + "/"))) {
                    return true;
                }
            } else {
                if (!type.startsWith("nfs")) {
                    continue;
                }
                String prefix = h + ":";
                if (source.startsWith(prefix) && (s.isEmpty() || trimSlashes(source.substring(prefix.length())).equals(s)
                        || trimSlashes(source.substring(prefix.length())).startsWith(s + "/"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Reads /proc/mounts. Returns null when it cannot be read (not Linux, or restricted). */
    public static List<String> readProcMounts() {
        Path proc = Path.of("/proc/mounts");
        try {
            if (Files.isReadable(proc)) {
                return Files.readAllLines(proc);
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through: the caller uses another way to decide
        }
        return null;
    }

    private static String unescape(String value) {
        return value.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\");
    }

    private static String stripTrailingSlash(String v) {
        return v.length() > 1 && v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private static String trimSlashes(String v) {
        String r = v;
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}