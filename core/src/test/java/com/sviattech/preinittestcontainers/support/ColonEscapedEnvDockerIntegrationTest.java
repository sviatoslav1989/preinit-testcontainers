package com.sviattech.preinittestcontainers.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class ColonEscapedEnvDockerIntegrationTest {

    private static final String ENTRYPOINT_CLASS_PATH = "docker/testcontainer-entrypoint.sh";

    private static final String TCE_LIVE_DATA_PATHS = "TCE_LIVE_DATA_PATHS";

    private static final String TCE_PAIR_COUNT = "TCE_PAIR_COUNT";

    private static final String TCE_SNAPSHOT_TEMP_PATHS = "TCE_SNAPSHOT_TEMP_PATHS";

    private static final String TCE_TEMP_MODE = "TCE_TEMP_MODE";

    private static final String TCE_UPSTREAM_ENTRYPOINT = "TCE_UPSTREAM_ENTRYPOINT";

    private static final String TMPFS_IMAGE = "preinit-test/colon-escaped-env-tmpfs:latest";

    private static final String WRAPPER_IMAGE = "preinit-test/colon-escaped-env-wrapper:latest";

    @Test
    void escapedTmpfsPaths_restoreAndInvokeUpstream() {
        String livePath = "/tmp/data:1";
        String snapshotPath = "/tmp/data:1_temp";
        String encodedUpstream = ColonSeparatedArgvUtils.encode("/bin/sh", "-c", "echo OK");
        DockerImageName image = buildTmpfsImage();
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            container.withEnv(Map.of(
                    TCE_PAIR_COUNT,
                    "1",
                    TCE_TEMP_MODE,
                    "0",
                    TCE_LIVE_DATA_PATHS,
                    ColonSeparatedArgvUtils.encode(livePath),
                    TCE_SNAPSHOT_TEMP_PATHS,
                    ColonSeparatedArgvUtils.encode(snapshotPath),
                    TCE_UPSTREAM_ENTRYPOINT,
                    encodedUpstream));
            TimedContainerStart.start(container);
            ContainerLogAssertions.assertContains(container, "OK");
        }
    }

    @Test
    void escapedUpstreamEntrypoint_executesColonInToken() {
        String encodedUpstream = ColonSeparatedArgvUtils.encode("/bin/sh", "-c", "echo 'hi:there'");
        DockerImageName image = buildWrapperImage();
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            container.withEnv(Map.of(
                    TCE_PAIR_COUNT,
                    "0",
                    TCE_TEMP_MODE,
                    "0",
                    TCE_UPSTREAM_ENTRYPOINT,
                    encodedUpstream));
            TimedContainerStart.start(container);
            ContainerLogAssertions.assertContains(container, "hi:there");
        }
    }

    private static DockerImageName buildTmpfsImage() {
        return DockerImageName.parse(new ImageFromDockerfile(TMPFS_IMAGE, false)
                .withFileFromString("testcontainer-entrypoint.sh", readClasspathResource())
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						RUN apk add --no-cache bash coreutils
						COPY testcontainer-entrypoint.sh /testcontainer-entrypoint.sh
						RUN chmod +x /testcontainer-entrypoint.sh \
						    && mkdir -p '/tmp/data:1' '/tmp/data:1_temp'
						ENTRYPOINT ["/bin/bash", "/testcontainer-entrypoint.sh"]
						""")
                .get());
    }

    private static DockerImageName buildWrapperImage() {
        return DockerImageName.parse(new ImageFromDockerfile(WRAPPER_IMAGE, false)
                .withFileFromString("testcontainer-entrypoint.sh", readClasspathResource())
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						RUN apk add --no-cache bash
						COPY testcontainer-entrypoint.sh /testcontainer-entrypoint.sh
						RUN chmod +x /testcontainer-entrypoint.sh
						ENTRYPOINT ["/bin/bash", "/testcontainer-entrypoint.sh"]
						""")
                .get());
    }

    private static String readClasspathResource() {
        try (InputStream input = ColonEscapedEnvDockerIntegrationTest.class
                .getClassLoader()
                .getResourceAsStream(ENTRYPOINT_CLASS_PATH)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing classpath resource: " + ENTRYPOINT_CLASS_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + ENTRYPOINT_CLASS_PATH, e);
        }
    }
}
