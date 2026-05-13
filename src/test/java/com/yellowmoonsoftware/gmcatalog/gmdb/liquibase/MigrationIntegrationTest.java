package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
}
