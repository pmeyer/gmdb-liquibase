package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("bootstrap")
class BootstrapIntegrationTest {
    private static final int POSTGRES_PORT = 5432;
    private static final String DATABASE_NAME = "gmdb";
    private static final String SUPERUSER = "gmdb_test_superuser";
    private static final String SUPERUSER_PASSWORD = "gmdb-test-superuser-password";
    private static final String ADMIN_PASSWORD = "gmdb-test-admin-password";
    private static final String APP_PASSWORD = "gmdb-test-app-password";

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>(
            new ImageFromDockerfile().withFileFromPath(".", Path.of("docker")))
            .withEnv("POSTGRES_DB", DATABASE_NAME)
            .withEnv("POSTGRES_USER", SUPERUSER)
            .withEnv("POSTGRES_PASSWORD", SUPERUSER_PASSWORD)
            .withExposedPorts(POSTGRES_PORT)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void configureBootstrapProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", BootstrapIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> SUPERUSER);
        registry.add("spring.datasource.password", () -> SUPERUSER_PASSWORD);
        registry.add("GMDB_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
        registry.add("GMDB_APP_PASSWORD", () -> APP_PASSWORD);
    }

    @Autowired
    DataSource dataSource;

    @Test
    void bootstrapCreatesExpectedSchemasAndRoles() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(schemaOwner(jdbc, "gmdb")).isEqualTo("gmdb_owner");
        assertThat(schemaOwner(jdbc, "liquibase")).isEqualTo("gmdb_owner");
        assertThat(roleAttributes(jdbc, "gmdb_readonly"))
                .containsEntry("rolcanlogin", false)
                .containsEntry("rolinherit", true);
        assertThat(roleAttributes(jdbc, "gmdb_readwrite"))
                .containsEntry("rolcanlogin", false)
                .containsEntry("rolinherit", true);
        assertThat(roleAttributes(jdbc, "gmdb_owner"))
                .containsEntry("rolcanlogin", false)
                .containsEntry("rolinherit", true);
        assertThat(roleAttributes(jdbc, "gmdb_app_user"))
                .containsEntry("rolcanlogin", true)
                .containsEntry("rolinherit", true);
        assertThat(roleAttributes(jdbc, "gmdb_admin"))
                .containsEntry("rolcanlogin", true)
                .containsEntry("rolinherit", false);
        assertThat(roleIsMemberOf(jdbc, "gmdb_app_user", "gmdb_readwrite")).isTrue();
        assertThat(roleIsMemberOf(jdbc, "gmdb_admin", "gmdb_owner")).isTrue();
    }

    @Test
    void bootstrapCreatesLiquibaseMetadataAccessibleToMigrationUser() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(tableOwner(jdbc, "liquibase", "databasechangelog")).isEqualTo("gmdb_owner");
        assertThat(tableOwner(jdbc, "liquibase", "databasechangeloglock")).isEqualTo("gmdb_owner");
        assertThat(changelogEntryExists(jdbc, "gmdb-bootstrap-001")).isTrue();
        assertThat(changelogEntryExists(jdbc, "gmdb-bootstrap-002-liquibase-metadata-ownership")).isTrue();
        assertThat(hasSchemaPrivilege(jdbc, "gmdb_admin", "liquibase", "USAGE")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangelog", "SELECT")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangelog", "INSERT")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangelog", "UPDATE")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangelog", "DELETE")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangeloglock", "SELECT")).isTrue();
        assertThat(hasTablePrivilege(jdbc, "gmdb_admin", "liquibase.databasechangeloglock", "UPDATE")).isTrue();
    }

    @Test
    void bootstrapUsersCanConnectWithExpectedSearchPathAndPrivileges() throws SQLException {
        try (final Connection appUser = connection("gmdb_app_user", APP_PASSWORD);
                final Connection admin = connection("gmdb_admin", ADMIN_PASSWORD)) {
            final JdbcTemplate appJdbc = new JdbcTemplate(new SingleConnectionDataSource(appUser, true));
            final JdbcTemplate adminJdbc = new JdbcTemplate(new SingleConnectionDataSource(admin, true));

            assertThat(currentSetting(appJdbc, "search_path")).isEqualTo("gmdb, public");
            assertThat(currentSetting(adminJdbc, "search_path")).isEqualTo("gmdb, public");
            assertThat(hasSchemaPrivilege(appJdbc, "gmdb_app_user", "gmdb", "USAGE")).isTrue();
            assertThat(hasSchemaPrivilege(appJdbc, "gmdb_app_user", "liquibase", "USAGE")).isFalse();
            assertThat(canSetRole(adminJdbc, "gmdb_owner")).isTrue();
        }
    }

    private static String jdbcUrl() {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT) + "/"
                + DATABASE_NAME;
    }

    private static Connection connection(final String username, final String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), username, password);
    }

    private static String schemaOwner(final JdbcTemplate jdbc, final String schema) {
        return jdbc.queryForObject("""
                select r.rolname
                from pg_namespace n
                join pg_roles r on r.oid = n.nspowner
                where n.nspname = ?
                """, String.class, schema);
    }

    private static Map<String, Object> roleAttributes(final JdbcTemplate jdbc, final String role) {
        return jdbc.queryForMap("""
                select rolcanlogin, rolinherit
                from pg_roles
                where rolname = ?
                """, role);
    }

    private static boolean roleIsMemberOf(final JdbcTemplate jdbc, final String member, final String role) {
        final Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                    from pg_auth_members m
                    join pg_roles member_role on member_role.oid = m.member
                    join pg_roles granted_role on granted_role.oid = m.roleid
                    where member_role.rolname = ?
                      and granted_role.rolname = ?
                )
                """, Boolean.class, member, role);
        return Boolean.TRUE.equals(result);
    }

    private static String tableOwner(final JdbcTemplate jdbc, final String schema, final String table) {
        return jdbc.queryForObject("""
                select r.rolname
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                join pg_roles r on r.oid = c.relowner
                where n.nspname = ?
                  and c.relname = ?
                  and c.relkind = 'r'
                """, String.class, schema, table);
    }

    private static boolean changelogEntryExists(final JdbcTemplate jdbc, final String id) {
        final Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                    from liquibase.databasechangelog
                    where id = ?
                )
                """, Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    private static boolean hasSchemaPrivilege(final JdbcTemplate jdbc, final String role, final String schema,
            final String privilege) {
        final Boolean result = jdbc.queryForObject("select has_schema_privilege(?, ?, ?)", Boolean.class, role, schema,
                privilege);
        return Boolean.TRUE.equals(result);
    }

    private static boolean hasTablePrivilege(final JdbcTemplate jdbc, final String role, final String table,
            final String privilege) {
        final Boolean result = jdbc.queryForObject("select has_table_privilege(?, ?, ?)", Boolean.class, role, table,
                privilege);
        return Boolean.TRUE.equals(result);
    }

    private static String currentSetting(final JdbcTemplate jdbc, final String settingName) {
        return jdbc.queryForObject("select current_setting(?)", String.class, settingName);
    }

    private static boolean canSetRole(final JdbcTemplate jdbc, final String role) {
        final Boolean result = jdbc.queryForObject("select pg_has_role(current_user, ?, 'MEMBER')", Boolean.class, role);
        return Boolean.TRUE.equals(result);
    }

}
