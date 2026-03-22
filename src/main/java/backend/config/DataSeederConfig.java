package backend.config;

import backend.model.entity.User;
import backend.model.enums.AuthProvider;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
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

    @Bean
    @Profile("!prod")   // Chỉ chạy khi không ở môi trường production
    public CommandLineRunner seedData() {
        return args -> {
            log.info("Checking seed data...");

            // Seed từng user theo email — idempotent: chỉ tạo nếu chưa tồn tại
            seedUserIfAbsent("admin@tapo.vn",       "Admin@123",     "Quản Trị Viên",     "0900000001", UserRole.ADMIN);
            seedUserIfAbsent("sales@tapo.vn",        "Sales@123",     "Nhân Viên Bán Hàng","0900000002", UserRole.SALES_STAFF);
            seedUserIfAbsent("warehouse@tapo.vn",    "Warehouse@123", "Nhân Viên Kho",     "0900000003", UserRole.WAREHOUSE_STAFF);
            seedUserIfAbsent("customer@tapo.vn",     "Customer@123",  "Khách Hàng Mẫu",   "0900000004", UserRole.CUSTOMER);

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
