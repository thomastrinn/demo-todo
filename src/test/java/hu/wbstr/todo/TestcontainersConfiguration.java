package hu.wbstr.todo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int GARAGE_S3_PORT = 3900;
    private static final String GARAGE_ACCESS_KEY = "todoaccesskey";
    // Garage refuses a secret key below 16 characters.
    private static final String GARAGE_SECRET_KEY = "todosecretkeytodosecretkey";
    private static final String GARAGE_BUCKET = "todo-images";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"));
    }

    // Testcontainers has no Garage module and Boot has no @ServiceConnection for it, so
    // the container and its properties are wired by hand.
    @Bean
    GenericContainer<?> garageContainer() {
        return new GenericContainer<>(DockerImageName.parse("dxflrs/garage:v2.3.0"))
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", GARAGE_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", GARAGE_SECRET_KEY)
                .withEnv("GARAGE_DEFAULT_BUCKET", GARAGE_BUCKET)
                .withCopyFileToContainer(MountableFile.forHostPath("garage/garage.toml"), "/etc/garage.toml")
                .withExposedPorts(GARAGE_S3_PORT)
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    DynamicPropertyRegistrar garageProperties(GenericContainer<?> garageContainer) {
        return registry -> {
            registry.add("todo.images.endpoint", () -> "http://%s:%d"
                    .formatted(garageContainer.getHost(), garageContainer.getMappedPort(GARAGE_S3_PORT)));
            registry.add("todo.images.bucket", () -> GARAGE_BUCKET);
            registry.add("todo.images.access-key", () -> GARAGE_ACCESS_KEY);
            registry.add("todo.images.secret-key", () -> GARAGE_SECRET_KEY);
        };
    }

}
