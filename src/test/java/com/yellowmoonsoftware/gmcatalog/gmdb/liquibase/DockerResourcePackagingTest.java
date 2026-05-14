package com.yellowmoonsoftware.gmcatalog.gmdb.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class DockerResourcePackagingTest {
    private static final String DOCKER_RESOURCE_ROOT = "gmdb-liquibase/docker";
    private static final List<String> DOCKER_RESOURCES = List.of(
            DOCKER_RESOURCE_ROOT + "/Dockerfile",
            DOCKER_RESOURCE_ROOT + "/docker-entrypoint-initdb.d/000_enable_pg_jsonschema.sql",
            DOCKER_RESOURCE_ROOT + "/docker-entrypoint-initdb.d/010_create_liquibase_schema.sql");

    @Test
    void dockerBuildContextResourcesAreAvailableOnClasspath() throws IOException {
        for (final String resource : DOCKER_RESOURCES) {
            final byte[] content = resourceContent(resource);

            assertThat(content).as(resource).isNotEmpty();
        }
    }

    @Test
    void embeddedDockerfileReferencesEmbeddedEntrypointDirectory() throws IOException {
        final String dockerfile = resourceText(DOCKER_RESOURCE_ROOT + "/Dockerfile");

        assertThat(dockerfile).contains("COPY docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/");
    }

    private static String resourceText(final String resource) throws IOException {
        return new String(resourceContent(resource), StandardCharsets.UTF_8);
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
