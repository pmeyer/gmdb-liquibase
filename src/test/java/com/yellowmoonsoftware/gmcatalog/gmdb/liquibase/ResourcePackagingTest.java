package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResourcePackagingTest {
    private static final Path RESOURCES_CLASSIFIER_DIRECTORY = Path.of("target", "resources-classifier");
    private static final String DOCKER_RESOURCE_ROOT = "gmdb-liquibase/docker";
    private static final List<String> APPLICATION_CLASSPATH_RESOURCES = List.of(
            "application.yml",
            "application-bootstrap.yml",
            "application-migrate.yml",
            "db/changelog/bootstrap-changelog.xml",
            "db/changelog/db-changelog.xml",
            "db/changelog/changes/0010-types.xml",
            "db/changelog/changes/0015-trigger-functions-constraints.xml",
            "db/changelog/changes/0100-pub_idx.xml",
            "db/changelog/changes/0200-artist.xml",
            "db/changelog/changes/0300-transcriber.xml",
            "db/changelog/changes/0400-pub.xml",
            "db/changelog/changes/0500-album.xml",
            "db/changelog/changes/0600-song.xml",
            "db/changelog/changes/0700-song_artist.xml",
            "db/changelog/changes/0800-transcription.xml",
            "db/changelog/changes/0900-transcription_transcriber.xml",
            "db/changelog/changes/1000-functions.xml");
    private static final List<String> RESOURCES_CLASSIFIER_RESOURCES = List.of(
            "application.yml",
            "application-bootstrap.yml",
            "application-migrate.yml",
            "db/changelog/bootstrap-changelog.xml",
            "db/changelog/db-changelog.xml",
            "db/changelog/changes/0010-types.xml",
            "db/changelog/changes/0015-trigger-functions-constraints.xml",
            "db/changelog/changes/0100-pub_idx.xml",
            "db/changelog/changes/0200-artist.xml",
            "db/changelog/changes/0300-transcriber.xml",
            "db/changelog/changes/0400-pub.xml",
            "db/changelog/changes/0500-album.xml",
            "db/changelog/changes/0600-song.xml",
            "db/changelog/changes/0700-song_artist.xml",
            "db/changelog/changes/0800-transcription.xml",
            "db/changelog/changes/0900-transcription_transcriber.xml",
            "db/changelog/changes/1000-functions.xml",
            DOCKER_RESOURCE_ROOT + "/Dockerfile",
            DOCKER_RESOURCE_ROOT + "/docker-entrypoint-initdb.d/000_enable_pg_jsonschema.sql",
            DOCKER_RESOURCE_ROOT + "/docker-entrypoint-initdb.d/010_create_liquibase_schema.sql");

    @Test
    void applicationResourcesAreAvailableOnClasspath() throws IOException {
        for (final String resource : APPLICATION_CLASSPATH_RESOURCES) {
            final byte[] content = resourceContent(resource);

            assertThat(content).as(resource).isNotEmpty();
        }
    }

    @Test
    void dockerBuildContextResourcesAreNotAvailableOnApplicationClasspath() {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThat(classLoader.getResource(DOCKER_RESOURCE_ROOT + "/Dockerfile")).isNull();
    }

    @Test
    void resourcesClassifierContentIncludesConsumerResources() throws IOException {
        for (final String resource : RESOURCES_CLASSIFIER_RESOURCES) {
            final Path resourcePath = RESOURCES_CLASSIFIER_DIRECTORY.resolve(resource);

            assertThat(resourcePath).as(resource).exists().isRegularFile();
            assertThat(Files.readAllBytes(resourcePath)).as(resource).isNotEmpty();
        }
    }

    @Test
    void resourcesClassifierContentExcludesLocalConfiguration() {
        assertThat(RESOURCES_CLASSIFIER_DIRECTORY.resolve("application-local.yml")).doesNotExist();
    }

    @Test
    void resourcesClassifierDockerfileReferencesClassifierEntrypointDirectory() throws IOException {
        final String dockerfile = Files.readString(
                RESOURCES_CLASSIFIER_DIRECTORY.resolve(DOCKER_RESOURCE_ROOT + "/Dockerfile"),
                StandardCharsets.UTF_8);

        assertThat(dockerfile).contains("COPY docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/");
    }

    private static byte[] resourceContent(final String resource) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final URL resourceUrl = classLoader.getResource(resource);
        assertThat(resourceUrl).as(resource).isNotNull();

        try (final InputStream inputStream = resourceUrl.openStream()) {
            return inputStream.readAllBytes();
        }
    }
}
