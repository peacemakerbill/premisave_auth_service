package com.premisave.auth.config;

import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@premisave.com";

        // Check if admin already exists
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            System.out.println("Admin user already exists. Skipping initialization.");
            return;
        }

        User admin = new User();
        
        admin.setEmail(adminEmail);
        admin.setUsername("premisave_admin");
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setPassword(passwordEncoder.encode("premisaveadmin"));
        
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setVerified(true);
        admin.setArchived(false);
        
        // Additional fields
        admin.setPhoneNumber("+254700000000");
        admin.setCountry("Kenya");
        admin.setLanguage(Language.ENGLISH);

        userRepository.save(admin);

        System.out.println("Admin user created successfully!");
        System.out.println("   Email    : " + adminEmail);
        System.out.println("   Username : premisave_admin");
        System.out.println("   Password : admin***pa***");
        System.out.println("   Role     : ADMIN");
    }
}