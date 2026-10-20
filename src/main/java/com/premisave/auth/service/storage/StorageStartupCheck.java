package com.premisave.auth.service.storage;

import com.premisave.auth.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Checks the document storage once at startup and says what it found.
 *
 * By default an unusable location only logs a warning: sign-in and everything else keep working
 * and document uploads answer 503 until the location is fixed (it is re-checked on every upload).
 * With STORAGE_FAIL_ON_UNAVAILABLE=true the service refuses to start instead.
 *
 * The log names the storage type and directory, which are not secrets, and never prints
 * credentials, because the application holds none.
 */
@Slf4j
@Component
@Order(20)
public class StorageStartupCheck implements ApplicationRunner {

    private final FileStorageService storage;
    private final StorageProperties properties;

    public StorageStartupCheck(FileStorageService storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        FileStorageService.Status status = storage.check(true);
        if (status.available()) {
            log.info("Document storage ready: type={}, directory={}", properties.getType(), storage.baseDirectory());
            if (properties.getType() == StorageProperties.Type.NETWORK
                    && properties.getNetwork().isRequireMount()
                    && (properties.getNetwork().getHost() == null || properties.getNetwork().getHost().isBlank())) {
                log.warn("STORAGE_NETWORK_HOST is empty, so only the presence of a mount is checked, not which NAS it belongs to.");
            }
            return;
        }
        String message = "Document storage is not usable (type=" + properties.getType() + "): " + status.message();
        if (properties.isFailOnUnavailable()) {
            throw new IllegalStateException(message + " STORAGE_FAIL_ON_UNAVAILABLE is on, so the service will not start.");
        }
        log.warn("{} Document uploads will answer 503 until this is fixed.", message);
    }
}