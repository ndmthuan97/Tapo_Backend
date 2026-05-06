package backend.config;

import backend.model.entity.User;
import backend.model.enums.AuthProvider;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeederConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    @Value("${SEED_ADMIN_PASSWORD:Admin@123}")
    private String adminPassword;

    @Value("${SEED_SALES_PASSWORD:Sales@123}")
    private String salesPassword;

    @Value("${SEED_WAREHOUSE_PASSWORD:Warehouse@123}")
    private String warehousePassword;

    @Value("${SEED_CUSTOMER_PASSWORD:Customer@123}")
    private String customerPassword;

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    @Profile("!prod")   // Chỉ chạy khi không ở môi trường production
    public CommandLineRunner seedData() {
        return args -> {
            log.info("Checking seed data...");

            // Fix PostgreSQL constraint manually since Flyway isn't triggered
            try {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> {
                    log.info("Fixing users_status_check constraint to include PENDING_VERIFICATION...");
                    entityManager.createNativeQuery("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check").executeUpdate();
                    entityManager.createNativeQuery("ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE', 'PENDING_VERIFICATION'))").executeUpdate();
                    log.info("Successfully updated users_status_check constraint.");
                });
            } catch (Exception e) {
                log.warn("Could not alter users_status_check constraint: {}", e.getMessage());
            }

            // Seed từng user theo email — idempotent: chỉ tạo nếu chưa tồn tại
            seedUserIfAbsent("admin@tapo.vn",       adminPassword,     "Quản Trị Viên",     "0900000001", UserRole.ADMIN);
            seedUserIfAbsent("sales@tapo.vn",        salesPassword,     "Nhân Viên Bán Hàng","0900000002", UserRole.SALES_STAFF);
            seedUserIfAbsent("warehouse@tapo.vn",    warehousePassword, "Nhân Viên Kho",     "0900000003", UserRole.WAREHOUSE_STAFF);
            seedUserIfAbsent("customer@tapo.vn",     customerPassword,  "Khách Hàng Mẫu",   "0900000004", UserRole.CUSTOMER);

            log.info("Seed check completed.");
        };
    }

    /**
     * Chỉ tạo user nếu email chưa tồn tại trong DB.
     * Không bao giờ cập nhật hoặc ghi đè dữ liệu đã có.
     */
    private void seedUserIfAbsent(String email, String password, String fullName,
                                   String phone, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            log.debug("  [SKIP] User already exists: {}", email);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        log.info("  [CREATED] [{}]: {}", role, email);
    }
}
