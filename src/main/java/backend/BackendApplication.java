package backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		try {
			Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
			dotenv.entries().forEach(e -> {
				if (System.getProperty(e.getKey()) == null) {
					System.setProperty(e.getKey(), e.getValue());
				}
			});
		} catch (Exception e) {
			System.err.println("Warning: Could not load .env configuration. " + e.getMessage());
		}
		SpringApplication.run(BackendApplication.class, args);
	}
}
