package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MigrationIntegrationTest extends AbstractDatabaseIntegrationTest {
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws SQLException {
        runBootstrapApplication();
        runMigrateApplication();

        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection("gmdb_admin", ADMIN_PASSWORD), true));
    }

    @Test
    void migrationCreatesExpectedCustomTypes() {
        assertThat(enumLabels("pub_type")).containsExactly("BOOK", "MAG");
        assertThat(enumLabels("artist_type")).containsExactly("PERSON", "BAND");
    }

    @Test
    void migrationCreatesExpectedTables() {
        assertThat(tableExists("pub_idx")).isTrue();
        assertThat(tableExists("artist")).isTrue();
        assertThat(tableExists("transcriber")).isTrue();
        assertThat(tableExists("pub")).isTrue();
        assertThat(tableExists("album")).isTrue();
        assertThat(tableExists("song")).isTrue();
        assertThat(tableExists("song_artist")).isTrue();
        assertThat(tableExists("transcription")).isTrue();
        assertThat(tableExists("transcription_transcriber")).isTrue();
    }

    @Test
    void migrationCreatesExpectedFunctions() {
        assertThat(functionExists("details_enforce_resource_id_immutable")).isTrue();
        assertThat(functionExists("details_default_resource_id")).isTrue();
        assertThat(functionExists("merge_details")).isTrue();
    }

    @Test
    void migrationCreatesExpectedTriggers() {
        assertThat(triggerExists("pub", "trg_pub_enforce_resource_id_immutable")).isTrue();
        assertThat(triggerExists("pub", "trg_pub_default_resource_id")).isTrue();
        assertThat(triggerExists("album", "trg_album_enforce_resource_id_immutable")).isTrue();
        assertThat(triggerExists("album", "trg_album_default_resource_id")).isTrue();
        assertThat(triggerExists("song", "trg_song_enforce_resource_id_immutable")).isTrue();
        assertThat(triggerExists("song", "trg_song_default_resource_id")).isTrue();
        assertThat(triggerExists("transcription", "trg_transcription_enforce_resource_id_immutable")).isTrue();
        assertThat(triggerExists("transcription", "trg_transcription_default_resource_id")).isTrue();
    }

    @Test
    void detailsDefaultResourceIdTriggersGenerateResourceIdsOnInsert() {
        for (final DetailTable table : detailTables()) {
            final long omittedDetailsId = table.insertOmittingDetails();
            assertGeneratedResourceId(table, omittedDetailsId);

            final long nullDetailsId = table.insertWithDetails(null);
            assertGeneratedResourceId(table, nullDetailsId);

            final long emptyDetailsId = table.insertWithDetails("{}");
            assertGeneratedResourceId(table, emptyDetailsId);

            final long nonEmptyDetailsId = table.insertWithDetails("""
                    {"source":"import","sequence":7}
                    """);
            assertGeneratedResourceId(table, nonEmptyDetailsId);
            assertThat(textDetailProperty(table, nonEmptyDetailsId, "source")).isEqualTo("import");
            assertThat(integerDetailProperty(table, nonEmptyDetailsId, "sequence")).isEqualTo(7);
        }
    }

    @Test
    void detailsDefaultResourceIdTriggersPreserveExplicitResourceIdsOnInsert() {
        for (final DetailTable table : detailTables()) {
            final String resourceId = UUID.randomUUID().toString();
            final long id = table.insertWithDetails("""
                    {"resourceId":"%s","source":"manual"}
                    """.formatted(resourceId));

            assertThat(resourceId(table, id)).isEqualTo(resourceId);
            assertThat(textDetailProperty(table, id, "source")).isEqualTo("manual");
        }
    }

    @Test
    void detailsResourceIdImmutabilityTriggersAllowNonResourceIdUpdates() {
        for (final DetailTable table : detailTables()) {
            final String resourceId = UUID.randomUUID().toString();
            final long id = table.insertWithDetails("""
                    {"resourceId":"%s","status":"draft","obsolete":true}
                    """.formatted(resourceId));

            assertThat(updateDetails(table, id, """
                    {"resourceId":"%s","status":"published","added":true}
                    """.formatted(resourceId))).isEqualTo(1);

            assertThat(resourceId(table, id)).isEqualTo(resourceId);
            assertThat(textDetailProperty(table, id, "status")).isEqualTo("published");
            assertThat(booleanDetailProperty(table, id, "added")).isTrue();
            assertThat(hasDetailProperty(table, id, "obsolete")).isFalse();
        }
    }

    @Test
    void detailsResourceIdImmutabilityTriggersRejectResourceIdUpdates() {
        for (final DetailTable table : detailTables()) {
            final String resourceId = UUID.randomUUID().toString();
            final long id = table.insertWithDetails("""
                    {"resourceId":"%s","status":"draft"}
                    """.formatted(resourceId));

            assertResourceIdMutationRejected(table, id, """
                    {"resourceId":"%s","status":"draft"}
                    """.formatted(UUID.randomUUID()));
            assertResourceIdMutationRejected(table, id, """
                    {"status":"draft"}
                    """);
            assertResourceIdMutationRejected(table, id, """
                    {"resourceId":null,"status":"draft"}
                    """);

            assertThat(resourceId(table, id)).isEqualTo(resourceId);
        }
    }

    private static List<String> enumLabels(final String typeName) {
        return jdbc.queryForList("""
                select e.enumlabel
                from pg_type t
                join pg_namespace n on n.oid = t.typnamespace
                join pg_enum e on e.enumtypid = t.oid
                where n.nspname = 'gmdb'
                  and t.typname = ?
                order by e.enumsortorder
                """, String.class, typeName);
    }

    private static boolean tableExists(final String tableName) {
        final Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'gmdb'
                      and c.relname = ?
                      and c.relkind = 'r'
                )
                """, Boolean.class, tableName);
        return Boolean.TRUE.equals(result);
    }

    private static boolean functionExists(final String functionName) {
        final Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                    from pg_proc p
                    join pg_namespace n on n.oid = p.pronamespace
                    where n.nspname = 'gmdb'
                      and p.proname = ?
                )
                """, Boolean.class, functionName);
        return Boolean.TRUE.equals(result);
    }

    private static boolean triggerExists(final String tableName, final String triggerName) {
        final Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                    from pg_trigger t
                    join pg_class c on c.oid = t.tgrelid
                    join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'gmdb'
                      and c.relname = ?
                      and t.tgname = ?
                      and not t.tgisinternal
                )
                """, Boolean.class, tableName, triggerName);
        return Boolean.TRUE.equals(result);
    }

    private static List<DetailTable> detailTables() {
        return List.of(
                new DetailTable("pub", MigrationIntegrationTest::insertPubOmittingDetails,
                        MigrationIntegrationTest::insertPubWithDetails),
                new DetailTable("album", MigrationIntegrationTest::insertAlbumOmittingDetails,
                        MigrationIntegrationTest::insertAlbumWithDetails),
                new DetailTable("song", MigrationIntegrationTest::insertSongOmittingDetails,
                        MigrationIntegrationTest::insertSongWithDetails),
                new DetailTable("transcription", MigrationIntegrationTest::insertTranscriptionOmittingDetails,
                        MigrationIntegrationTest::insertTranscriptionWithDetails));
    }

    private static void assertGeneratedResourceId(final DetailTable table, final long id) {
        assertThat(hasDetailProperty(table, id, "resourceId")).isTrue();

        final String resourceId = resourceId(table, id);
        assertThat(UUID.fromString(resourceId).toString()).isEqualTo(resourceId);
    }

    private static void assertResourceIdMutationRejected(
            final DetailTable table,
            final long id,
            final String detailsJson) {

        assertThatThrownBy(() -> updateDetails(table, id, detailsJson))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("details->resourceId is not updatable");
    }

    private static String resourceId(final DetailTable table, final long id) {
        return textDetailProperty(table, id, "resourceId");
    }

    private static String textDetailProperty(final DetailTable table, final long id, final String propertyName) {
        return jdbc.queryForObject(
                "select details ->> ? from gmdb." + table.name() + " where id = ?",
                String.class,
                propertyName,
                id);
    }

    private static Integer integerDetailProperty(final DetailTable table, final long id, final String propertyName) {
        return jdbc.queryForObject(
                "select (details ->> ?)::integer from gmdb." + table.name() + " where id = ?",
                Integer.class,
                propertyName,
                id);
    }

    private static Boolean booleanDetailProperty(final DetailTable table, final long id, final String propertyName) {
        return jdbc.queryForObject(
                "select (details ->> ?)::boolean from gmdb." + table.name() + " where id = ?",
                Boolean.class,
                propertyName,
                id);
    }

    private static boolean hasDetailProperty(final DetailTable table, final long id, final String propertyName) {
        final Boolean result = jdbc.queryForObject(
                "select jsonb_exists(details, ?) from gmdb." + table.name() + " where id = ?",
                Boolean.class,
                propertyName,
                id);

        return Boolean.TRUE.equals(result);
    }

    private static int updateDetails(final DetailTable table, final long id, final String detailsJson) {
        return jdbc.update(
                "update gmdb." + table.name() + " set details = cast(? as jsonb) where id = ?",
                detailsJson,
                id);
    }

    private static long insertPubOmittingDetails() {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.pub (pub_idx_id) values (?) returning id",
                Long.class,
                insertPubIdx());

        return Objects.requireNonNull(id);
    }

    private static long insertPubWithDetails(final String detailsJson) {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.pub (pub_idx_id, details) values (?, cast(? as jsonb)) returning id",
                Long.class,
                insertPubIdx(),
                detailsJson);

        return Objects.requireNonNull(id);
    }

    private static long insertAlbumOmittingDetails() {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.album (title) values (?) returning id",
                Long.class,
                uniqueName("album"));

        return Objects.requireNonNull(id);
    }

    private static long insertAlbumWithDetails(final String detailsJson) {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.album (title, details) values (?, cast(? as jsonb)) returning id",
                Long.class,
                uniqueName("album"),
                detailsJson);

        return Objects.requireNonNull(id);
    }

    private static long insertSongOmittingDetails() {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.song (title) values (?) returning id",
                Long.class,
                uniqueName("song"));

        return Objects.requireNonNull(id);
    }

    private static long insertSongWithDetails(final String detailsJson) {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.song (title, details) values (?, cast(? as jsonb)) returning id",
                Long.class,
                uniqueName("song"),
                detailsJson);

        return Objects.requireNonNull(id);
    }

    private static long insertTranscriptionOmittingDetails() {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.transcription (song_id, pub_id) values (?, ?) returning id",
                Long.class,
                insertSongOmittingDetails(),
                insertPubOmittingDetails());

        return Objects.requireNonNull(id);
    }

    private static long insertTranscriptionWithDetails(final String detailsJson) {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.transcription (song_id, pub_id, details) values (?, ?, cast(? as jsonb)) returning id",
                Long.class,
                insertSongOmittingDetails(),
                insertPubOmittingDetails(),
                detailsJson);

        return Objects.requireNonNull(id);
    }

    private static long insertPubIdx() {
        final Long id = jdbc.queryForObject(
                "insert into gmdb.pub_idx (name, type) values (?, 'BOOK') returning id",
                Long.class,
                uniqueName("pub-idx"));

        return Objects.requireNonNull(id);
    }

    private static String uniqueName(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record DetailTable(
            String name,
            OmittedDetailsInserter omittedDetailsInserter,
            ProvidedDetailsInserter providedDetailsInserter) {

        private long insertOmittingDetails() {
            return omittedDetailsInserter.insert();
        }

        private long insertWithDetails(final String detailsJson) {
            return providedDetailsInserter.insert(detailsJson);
        }
    }

    @FunctionalInterface
    private interface OmittedDetailsInserter {
        long insert();
    }

    @FunctionalInterface
    private interface ProvidedDetailsInserter {
        long insert(String detailsJson);
    }
}
