package com.premisave.auth.service.storage;

import com.premisave.auth.config.StorageProperties;
import com.premisave.auth.config.StorageProperties.Protocol;
import com.premisave.auth.config.StorageProperties.Type;
import com.premisave.auth.dto.StorageDtos;
import com.premisave.auth.entity.StorageSettings;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.StorageSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Where documents are stored: the .env values until an administrator saves something from the dashboard, and from then on
 * the dashboard value, until it is reset.
 *
 * Folders can only be chosen inside the allowed roots (STORAGE_ALLOWED_ROOTS), and every change is tried before it is saved:
 * the folder is created, written to and, for a network share, checked to be really mounted. Folders used before a change are
 * remembered, so files saved earlier are still found. The result is cached for a few seconds, because every upload and
 * download asks for it.
 */
@Service
public class StorageSettingsService {

    private static final Logger log = LoggerFactory.getLogger(StorageSettingsService.class);

    private static final long CACHE_MILLIS = 5_000;
    private static final int MAX_PREVIOUS_FOLDERS = 20;
    private static final int MAX_MIN_FREE_MB = 10_000_000;

    /** The storage in use: its settings, the problems with them (empty when usable), and the resolved folder. */
    public record Active(StorageProperties props, List<String> problems, Path base, boolean fromDashboard) {
    }

    private final StorageSettingsRepository repository;
    private final StorageProperties env;
    private final Clock clock;
    private final Active environment;

    private volatile Active cached;
    private volatile StorageSettings cachedSettings;
    private volatile long cachedAt;

    public StorageSettingsService(StorageSettingsRepository repository, StorageProperties env, Clock clock) {
        this.repository = repository;
        this.env = env;
        this.clock = clock;
        this.environment = describe(env, false);
    }

    // ================================================================ what applies now

    /** The .env values as the service understands them. */
    public Active environment() {
        return environment;
    }

    /**
     * The storage in use now: what an administrator saved, or the .env values. A saved location that is no longer inside the
     * allowed roots (because .env changed) is ignored with a warning instead of being used.
     */
    public Active active() {
        long now = System.currentTimeMillis();
        Active a = cached;
        if (a != null && now - cachedAt < CACHE_MILLIS) {
            return a;
        }
        StorageSettings saved = loadSettings();
        a = environment;
        if (saved.getDocuments() != null) {
            try {
                StorageProperties p = fromDocuments(saved.getDocuments());
                Active candidate = describe(p, true);
                String problem = restriction(candidate);
                if (problem == null) {
                    a = candidate;
                } else {
                    log.warn("Ignoring the document storage saved from the dashboard: {}", problem);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring the document storage saved from the dashboard: {}", e.getMessage());
            }
        }
        cachedSettings = saved;
        cached = a;
        cachedAt = now;
        return a;
    }

    private StorageSettings loadSettings() {
        try {
            return repository.findById(StorageSettings.ID).orElseGet(StorageSettings::new);
        } catch (RuntimeException e) {
            log.warn("Could not read the saved storage settings, using the .env values: {}", e.getClass().getSimpleName());
            return new StorageSettings();
        }
    }

    private void forget() {
        cached = null;
        cachedSettings = null;
        cachedAt = 0;
    }

    /** Folders that held documents before the location changed, and the .env folder. They are searched when a file is not in the current one. */
    public List<Path> fallbackBases(Path current) {
        active(); // refreshes cachedSettings
        StorageSettings saved = cachedSettings;
        List<Path> out = new ArrayList<>();
        if (saved != null) {
            for (String s : saved.getPreviousBases()) {
                try {
                    Path p = StorageLayout.expand(s);
                    if (!p.equals(current) && !out.contains(p)) {
                        out.add(p);
                    }
                } catch (RuntimeException ignored) {
                    // a damaged entry is skipped
                }
            }
        }
        Path envBase = environment.base();
        if (envBase != null && !envBase.equals(current) && !out.contains(envBase)) {
            out.add(envBase);
        }
        return out;
    }

    /** The folders the administrators may choose from. .env lists them, and by default they are the .env folders themselves. */
    public List<Path> allowedRoots() {
        List<Path> roots = StorageLayout.parseRoots(env.getAllowedRoots());
        if (!roots.isEmpty()) {
            return roots;
        }
        List<Path> out = new ArrayList<>();
        try {
            out.add(StorageLayout.expand(env.getLocal().getPath()));
        } catch (RuntimeException ignored) {
            // .env names no usable local folder
        }
        try {
            Path mount = StorageLayout.mountPoint(env);
            if (!out.contains(mount)) {
                out.add(mount);
            }
        } catch (RuntimeException ignored) {
            // .env names no usable mount path
        }
        return out;
    }

    public boolean fromDashboard() {
        return active().fromDashboard();
    }

    // ================================================================ building a location

    private static Active describe(StorageProperties p, boolean fromDashboard) {
        List<String> problems = StorageLayout.validate(p);
        Path base = null;
        if (problems.isEmpty()) {
            try {
                base = StorageLayout.baseDirectory(p);
            } catch (RuntimeException e) {
                problems = List.of("Storage location could not be resolved.");
            }
        }
        return new Active(p, problems, base, fromDashboard);
    }

    /** Null when the folder is inside the allowed roots, otherwise what is wrong. */
    private String restriction(Active a) {
        if (!a.problems().isEmpty() || a.base() == null) {
            return String.join("; ", a.problems());
        }
        List<Path> roots = allowedRoots();
        Path checked = a.props().getType() == Type.NETWORK ? StorageLayout.mountPoint(a.props()) : a.base();
        if (!StorageLayout.isInside(checked, roots)) {
            return "The folder " + checked + " is not inside an allowed folder. Add it to STORAGE_ALLOWED_ROOTS in .env first";
        }
        return null;
    }

    private StorageProperties copyOfEnv() {
        StorageProperties p = new StorageProperties();
        p.setCreateDirectories(env.isCreateDirectories());
        p.setFailOnUnavailable(env.isFailOnUnavailable());
        p.setMinFreeMb(env.getMinFreeMb());
        p.setAllowedRoots(env.getAllowedRoots());
        return p;
    }

    /** The settings that a saved record describes. */
    private StorageProperties fromDocuments(StorageSettings.Documents d) {
        StorageProperties p = copyOfEnv();
        p.setType(parseType(d.getType()));
        if (d.getMinFreeMb() != null) {
            p.setMinFreeMb(d.getMinFreeMb());
        }
        p.getLocal().setPath(d.getLocalPath());
        StorageProperties.Network n = p.getNetwork();
        n.setProtocol(parseProtocol(d.getNetworkProtocol()));
        n.setHost(d.getNetworkHost() == null ? "" : d.getNetworkHost().trim());
        n.setShare(d.getNetworkShare() == null ? "" : d.getNetworkShare().trim());
        n.setMountPath(d.getNetworkMountPath());
        n.setDirectory(d.getNetworkDirectory() == null ? "" : d.getNetworkDirectory().trim());
        n.setRequireMount(d.isNetworkRequireMount());
        return p;
    }

    private StorageProperties fromRequest(StorageDtos.Request r) {
        if (r == null) {
            throw new IllegalArgumentException("Send the storage settings");
        }
        StorageProperties p = copyOfEnv();
        Type type = parseType(r.type());
        p.setType(type);
        if (r.minFreeMb() != null) {
            if (r.minFreeMb() < 0 || r.minFreeMb() > MAX_MIN_FREE_MB) {
                throw new IllegalArgumentException("The free space limit must be between 0 and " + MAX_MIN_FREE_MB + " MB");
            }
            p.setMinFreeMb(r.minFreeMb());
        }
        if (type == Type.LOCAL) {
            if (r.localPath() == null || r.localPath().isBlank()) {
                throw new IllegalArgumentException("LOCAL storage needs a folder");
            }
            p.getLocal().setPath(r.localPath().trim());
        } else {
            if (r.networkMountPath() == null || r.networkMountPath().isBlank()) {
                throw new IllegalArgumentException("NETWORK storage needs the mount path of the share");
            }
            StorageProperties.Network n = p.getNetwork();
            n.setProtocol(parseProtocol(r.networkProtocol()));
            n.setHost(r.networkHost() == null ? "" : r.networkHost().trim());
            n.setShare(r.networkShare() == null ? "" : r.networkShare().trim());
            n.setMountPath(r.networkMountPath().trim());
            n.setDirectory(r.networkDirectory() == null ? "" : r.networkDirectory().trim());
            n.setRequireMount(r.networkRequireMount() == null || r.networkRequireMount());
        }
        return p;
    }

    private static Type parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Choose LOCAL or NETWORK");
        }
        try {
            return Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Kept on must be LOCAL or NETWORK");
        }
    }

    private static Protocol parseProtocol(String value) {
        if (value == null || value.isBlank()) {
            return Protocol.SMB;
        }
        try {
            return Protocol.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The protocol must be SMB or NFS");
        }
    }

    // ================================================================ trying and saving

    /** Tries values without saving them: the folder is created, written to and, for a share, checked to be mounted. */
    public StorageDtos.Check check(StorageDtos.Request request) {
        Active a;
        try {
            a = candidate(request);
        } catch (IllegalArgumentException e) {
            return new StorageDtos.Check(false, e.getMessage(), null, null);
        }
        return probe(a);
    }

    /** Saves new document storage after trying it. The folder in use until now is remembered, so older files are still found. */
    public StorageDtos.Check save(StorageDtos.Request request, String adminId) {
        Active a;
        try {
            a = candidate(request);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        StorageDtos.Check result = probe(a);
        if (!result.ok()) {
            throw ApiException.badRequest(result.problem());
        }

        StorageSettings settings = repository.findById(StorageSettings.ID).orElseGet(StorageSettings::new);
        remember(settings, active().base(), a.base());

        StorageProperties p = a.props();
        StorageSettings.Documents d = new StorageSettings.Documents();
        d.setType(p.getType().name());
        d.setMinFreeMb(request.minFreeMb());
        if (p.getType() == Type.LOCAL) {
            d.setLocalPath(StorageLayout.expand(p.getLocal().getPath()).toString());
        } else {
            StorageProperties.Network n = p.getNetwork();
            d.setNetworkProtocol(n.getProtocol().name());
            d.setNetworkHost(n.getHost());
            d.setNetworkShare(n.getShare());
            d.setNetworkMountPath(StorageLayout.mountPoint(p).toString());
            d.setNetworkDirectory(n.getDirectory());
            d.setNetworkRequireMount(n.isRequireMount());
        }
        settings.setDocuments(d);
        touch(settings, adminId);
        repository.save(settings);
        forget();
        log.info("Document storage set from the dashboard by {}: {} {}", adminId, p.getType(), a.base());
        return result;
    }

    /** Goes back to the .env values. The folder in use until now is remembered. */
    public void reset(String adminId) {
        StorageSettings settings = repository.findById(StorageSettings.ID).orElseGet(StorageSettings::new);
        if (settings.getDocuments() == null) {
            return;
        }
        remember(settings, active().base(), environment.base());
        settings.setDocuments(null);
        touch(settings, adminId);
        repository.save(settings);
        forget();
        log.info("Document storage reset to .env by {}", adminId);
    }

    /** The values of a request as a storage location, after the rules: valid settings, inside the allowed roots. */
    private Active candidate(StorageDtos.Request request) {
        Active a = describe(fromRequest(request), true);
        String problem = restriction(a);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        return a;
    }

    /** Creates the folder, writes and deletes a small file, and checks the free space. Never leaves a folder outside the allowed roots. */
    private StorageDtos.Check probe(Active a) {
        Path base = a.base();
        StorageProperties p = a.props();
        try {
            if (p.getType() == Type.NETWORK && p.getNetwork().isRequireMount() && !StorageLayout.isMounted(p)) {
                return new StorageDtos.Check(false, "The network share is not mounted at " + StorageLayout.mountPoint(p)
                        + ". Mount it first, or switch the mount check off", base.toString(), null);
            }
            if (!StorageLayout.isReallyInside(base, allowedRoots())) {
                return new StorageDtos.Check(false, "The folder " + base
                        + " leads outside the allowed folders through a link", base.toString(), null);
            }
            if (!Files.exists(base)) {
                if (!p.isCreateDirectories()) {
                    return new StorageDtos.Check(false, "The folder " + base
                            + " does not exist, and STORAGE_CREATE_DIRECTORIES is off", base.toString(), null);
                }
                Files.createDirectories(base);
            }
            Path test = base.resolve(".write-test-" + UUID.randomUUID());
            Files.writeString(test, "ok");
            Files.deleteIfExists(test);
            Long free = freeMb(base);
            if (free != null && p.getMinFreeMb() > 0 && free < p.getMinFreeMb()) {
                return new StorageDtos.Check(false, "Only " + free + " MB are free, and the limit is "
                        + p.getMinFreeMb() + " MB", base.toString(), free);
            }
            return new StorageDtos.Check(true, null, base.toString(), free);
        } catch (IOException | RuntimeException e) {
            return new StorageDtos.Check(false, "The service cannot write to " + base + ": " + e.getMessage(),
                    base.toString(), null);
        }
    }

    public static Long freeMb(Path folder) {
        try {
            return Files.getFileStore(folder).getUsableSpace() / (1024 * 1024);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void remember(StorageSettings settings, Path leaving, Path arriving) {
        if (leaving == null || leaving.equals(arriving)) {
            return;
        }
        List<String> list = new ArrayList<>(settings.getPreviousBases());
        list.remove(leaving.toString());
        list.add(0, leaving.toString());
        list.remove(arriving.toString());
        while (list.size() > MAX_PREVIOUS_FOLDERS) {
            list.remove(list.size() - 1);
        }
        settings.setPreviousBases(list);
    }

    private void touch(StorageSettings settings, String adminId) {
        settings.setUpdatedBy(adminId);
        settings.setUpdatedAt(Instant.now(clock));
    }
}
