package com.premisave.auth.config;

import com.premisave.auth.service.security.ConfiguredAdminService;
import com.premisave.auth.service.security.ConfiguredAdminService.Outcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the administrators from ADMIN1 to ADMIN3 at startup. Runs before AdminProvisioningRunner
 * (INITIAL_ADMIN_EMAILS), so an address listed in both places is created here and then seen as an
 * existing administrator there.
 *
 * It is idempotent: a second start finds the accounts and changes nothing. Unexpected errors are
 * logged and never stop the service, except that ADMINS_STRICT=true makes an invalid slot
 * a startup failure (thrown by the service before anything is created).
 */
@Slf4j
@Component
@Order(40)
public class ConfiguredAdminRunner implements ApplicationRunner {

    private final AdminsProperties properties;
    private final ConfiguredAdminService service;

    public ConfiguredAdminRunner(AdminsProperties properties, ConfiguredAdminService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Configured administrators are disabled (ADMINS_ENABLED=false).");
            return;
        }
        List<ConfiguredAdminService.Result> results = service.provisionAll();
        long used = results.stream().filter(r -> r.outcome() != Outcome.UNUSED).count();
        if (used == 0) {
            log.info("No ADMIN1 to ADMIN3 administrators configured.");
            return;
        }
        log.info("Configured administrators processed: {} created, {} promoted, {} already present, {} left unchanged, {} invalid, {} failed.",
                count(results, Outcome.CREATED), count(results, Outcome.PROMOTED), count(results, Outcome.ALREADY_ADMIN),
                count(results, Outcome.LEFT_UNCHANGED), count(results, Outcome.INVALID), count(results, Outcome.FAILED));
    }

    private static long count(List<ConfiguredAdminService.Result> results, Outcome outcome) {
        return results.stream().filter(r -> r.outcome() == outcome).count();
    }
}