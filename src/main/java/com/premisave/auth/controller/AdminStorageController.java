package com.premisave.auth.controller;

import com.premisave.auth.config.StorageProperties;
import com.premisave.auth.dto.StorageDtos;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.service.security.SecurityAuditService;
import com.premisave.auth.service.storage.FileStorageService;
import com.premisave.auth.service.storage.StorageSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * Administrators: see and change where uploaded documents are kept. The .env values are the starting point; once something
 * is saved here it wins, and DELETE goes back to .env. Every change is written to the audit trail.
 */
@RestController
@RequestMapping("/admin/storage-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorageController {

    private final StorageSettingsService settings;
    private final FileStorageService storage;
    private final StorageProperties env;
    private final SecurityAuditService audit;

    public AdminStorageController(StorageSettingsService settings, FileStorageService storage, StorageProperties env,
                                  SecurityAuditService audit) {
        this.settings = settings;
        this.storage = storage;
        this.env = env;
        this.audit = audit;
    }

    @GetMapping
    public StorageDtos.Response view() {
        return response();
    }

    /** Tries the values without saving: the folder is created, written to and, for a share, checked to be mounted. */
    @PostMapping("/check")
    public StorageDtos.Check check(@RequestBody StorageDtos.Request request) {
        return settings.check(request);
    }

    @PutMapping
    public ResponseEntity<StorageDtos.Response> save(@RequestBody StorageDtos.Request request) {
        String admin = admin();
        settings.save(request, admin);
        record("Document storage was set from the dashboard", admin);
        return ResponseEntity.ok(response());
    }

    @DeleteMapping
    public ResponseEntity<StorageDtos.Response> reset() {
        String admin = admin();
        settings.reset(admin);
        record("Document storage was reset to the .env values", admin);
        return ResponseEntity.ok(response());
    }

    private void record(String reason, String admin) {
        audit.event(SecurityEventType.STORAGE_SETTINGS_CHANGED)
                .actor(admin)
                .reason(reason)
                .detail("type", storage.type())
                .write();
    }

    private static String admin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }

    private StorageDtos.Response response() {
        StorageSettingsService.Active a = settings.active();
        StorageProperties p = a.props();
        StorageProperties.Network n = p.getNetwork();
        StorageProperties.Network en = env.getNetwork();

        StorageFileStatus status = new StorageFileStatus(storage);
        StorageDtos.Env envView = new StorageDtos.Env(env.getType().name(), env.getLocal().getPath(),
                en.getProtocol().name(), nz(en.getHost()), nz(en.getShare()), en.getMountPath(), nz(en.getDirectory()),
                en.isRequireMount(), env.getMinFreeMb());
        StorageDtos.Documents documents = new StorageDtos.Documents(
                a.fromDashboard() ? "DASHBOARD" : "ENV", p.getType().name(), p.getLocal().getPath(),
                n.getProtocol().name(), nz(n.getHost()), nz(n.getShare()), n.getMountPath(), nz(n.getDirectory()),
                n.isRequireMount(), p.getMinFreeMb(), a.base() == null ? "" : a.base().toString(),
                status.ready(), status.problem(), status.freeMb(), envView);
        List<String> roots = settings.allowedRoots().stream().map(Path::toString).toList();
        return new StorageDtos.Response(roots, documents);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** What the storage can do right now, read without creating anything. */
    private static final class StorageFileStatus {
        private final FileStorageService.Status status;
        private final Long freeMb;

        StorageFileStatus(FileStorageService storage) {
            this.status = storage.check(false);
            this.freeMb = status.available() ? storage.freeMb() : null;
        }

        boolean ready() { return status.available(); }

        String problem() { return status.available() ? null : status.message(); }

        Long freeMb() { return freeMb; }
    }
}
