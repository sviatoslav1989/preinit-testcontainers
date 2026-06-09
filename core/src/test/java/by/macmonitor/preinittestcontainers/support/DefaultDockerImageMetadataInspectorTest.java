package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.ContainerMetadata;
import by.macmonitor.preinittestcontainers.metadata.DefaultDockerImageMetadataInspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.util.stream.Stream;

class DefaultDockerImageMetadataInspectorTest {

    private static final String RELATIVE_ENTRYPOINT_IMAGE_NAME =
            "preinit-test/relative-entrypoint:latest";

    private final DefaultDockerImageMetadataInspector inspector =
            new DefaultDockerImageMetadataInspector(DockerClientFactory.lazyClient());

    @Test
    void inspect_relativeEntrypoint_resolvesAgainstWorkdir() {
        String imageName = new ImageFromDockerfile(RELATIVE_ENTRYPOINT_IMAGE_NAME, false)
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						WORKDIR /app
						RUN mkdir -p bin && printf '#!/bin/sh\\n' > bin/entrypoint.sh && chmod +x bin/entrypoint.sh
						ENTRYPOINT ["bin/entrypoint.sh"]
						""")
                .get();

        ContainerMetadata metadata = inspector.inspect(imageName);

        assertThat(metadata.getEntrypoint()).containsExactly("bin/entrypoint.sh");
        assertThat(metadata.getEntrypointPath()).isEqualTo("/app/bin/entrypoint.sh");
    }

    @ParameterizedTest
    @MethodSource("imageMetadataCases")
    void inspect_returnsInspectBasedMetadata(
            String imageName,
            String[] expectedEntrypoint,
            String[] expectedCmd,
            String[] expectedVolumes,
            String expectedEntrypointPath) {
        ContainerMetadata metadata = inspector.inspect(imageName);

        assertThat(metadata.getEntrypoint()).containsExactly(expectedEntrypoint);
        if (expectedCmd == null) {
            assertThat(metadata.getCmd()).isNull();
        } else {
            assertThat(metadata.getCmd()).containsExactly(expectedCmd);
        }
        if (expectedVolumes == null) {
            assertThat(metadata.getVolumes()).isNull();
        } else {
            assertThat(metadata.getVolumes()).containsExactly(expectedVolumes);
        }
        assertThat(metadata.getEntrypointPath()).isEqualTo(expectedEntrypointPath);
    }

    static Stream<Arguments> imageMetadataCases() {
        return Stream.of(
                Arguments.of(
                        "mysql:8.0.45",
                        new String[] {"docker-entrypoint.sh"},
                        new String[] {"mysqld"},
                        new String[] {"/var/lib/mysql"},
                        "/usr/local/bin/docker-entrypoint.sh"),
                Arguments.of(
                        "postgres:17",
                        new String[] {"docker-entrypoint.sh"},
                        new String[] {"postgres"},
                        new String[] {"/var/lib/postgresql/data"},
                        "/usr/local/bin/docker-entrypoint.sh"),
                Arguments.of(
                        "redis:7.4.2",
                        new String[] {"docker-entrypoint.sh"},
                        new String[] {"redis-server"},
                        new String[] {"/data"},
                        "/usr/local/bin/docker-entrypoint.sh"),
                Arguments.of(
                        "clickhouse/clickhouse-server:26.3.4.11",
                        new String[] {"/entrypoint.sh"},
                        null,
                        new String[] {"/var/lib/clickhouse"},
                        "/entrypoint.sh"));
    }
}
