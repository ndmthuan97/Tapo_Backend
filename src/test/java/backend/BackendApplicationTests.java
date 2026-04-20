package backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring context load test — disabled in CI because requires live Redis/DB env.
 * Run manually with: mvn test -Dtest=BackendApplicationTests
 */
@Disabled("Requires live Redis, DB and full env. Run manually.")
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
