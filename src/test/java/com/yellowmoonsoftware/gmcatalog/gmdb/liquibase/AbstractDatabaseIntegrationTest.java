package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class AbstractDatabaseIntegrationTest {
    protected static final int POSTGRES_PORT = 5432;
    protected static final String DATABASE_NAME = "gmdb";
    protected static final String SUPERUSER = "gmdb_test_superuser";
    protected static final String SUPERUSER_PASSWORD = "gmdb-test-superuser-password";
    protected static final String ADMIN_PASSWORD = "gmdb-test-admin-password";
    protected static final String APP_PASSWORD = "gmdb-test-app-password";

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>(
            new ImageFromDockerfile().withFileFromPath(".", Path.of("docker", "postgres")))
            .withEnv("POSTGRES_DB", DATABASE_NAME)
            .withEnv("POSTGRES_USER", SUPERUSER)
            .withEnv("POSTGRES_PASSWORD", SUPERUSER_PASSWORD)
            .withExposedPorts(POSTGRES_PORT)
            .waitingFor(Wait.forListeningPort());

    protected static String jdbcUrl() {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/"
                + DATABASE_NAME;
    }

    protected static Connection connection(final String username, final String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), username, password);
    }

    protected static void runBootstrapApplication() {
        new SpringApplicationBuilder(GmdbLiquibaseApplication.class)
                .profiles("bootstrap")
                .run(
                        "--spring.datasource.url=" + jdbcUrl(),
                        "--spring.datasource.username=" + SUPERUSER,
                        "--spring.datasource.password=" + SUPERUSER_PASSWORD,
                        "--GMDB_SUPERUSER=" + SUPERUSER,
                        "--GMDB_SUPERUSER_PASSWORD=" + SUPERUSER_PASSWORD,
                        "--GMDB_ADMIN_PASSWORD=" + ADMIN_PASSWORD,
                        "--GMDB_APP_PASSWORD=" + APP_PASSWORD)
                .close();
    }

    protected static void runMigrateApplication() {
        new SpringApplicationBuilder(GmdbLiquibaseApplication.class)
                .profiles("migrate")
                .run(
                        "--spring.datasource.url=" + jdbcUrl(),
                        "--spring.datasource.username=gmdb_admin",
                        "--spring.datasource.password=" + ADMIN_PASSWORD,
                        "--GMDB_ADMIN_PASSWORD=" + ADMIN_PASSWORD)
                .close();
    }
}
