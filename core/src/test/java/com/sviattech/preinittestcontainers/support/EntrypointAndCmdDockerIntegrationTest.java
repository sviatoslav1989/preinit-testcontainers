package com.sviattech.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class EntrypointAndCmdDockerIntegrationTest {

    private static final String TIMESTAMP_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}";

    @Test
    void badExecEntrypoint_execCmd_Error() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/bad-exec-entrypoint:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT ["date '+%Y-%m-%dT%H:%M:%S'"]
				CMD ["ignored"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(
                    container, image, new String[] {"date '+%Y-%m-%dT%H:%M:%S'"}, new String[] {
                        "ignored"
                    });

            ContainerStartAssertions.assertStartFails(container);
        }
    }

    @Test
    void execEntrypoint_execCmd_printsFormattedTime() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/exec-entrypoint-exec-cmd:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT ["date"]
				CMD ["+%Y-%m-%dT%H:%M:%S"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(
                    container, image, new String[] {"date"}, new String[] {"+%Y-%m-%dT%H:%M:%S"});

            TimedContainerStart.start(container);
            assertTimestampOutput(container.getLogs().trim());
        }
    }

    @Test
    void execEntrypoint_shellCmd_Error() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/exec-entrypoint-shell-cmd-error:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT ["date"]
				CMD +%Y-%m-%dT%H:%M:%S
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(container, image, new String[] {"date"}, new String[] {
                "/bin/sh", "-c", "+%Y-%m-%dT%H:%M:%S"
            });

            ContainerStartAssertions.assertStartFails(container, "unrecognized option");
        }
    }

    @Test
    void shellEntrypoint_execCmd_printsFormattedTime() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/shell-entrypoint-exec-cmd:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT ["/bin/sh", "-c", "exec \\"$0\\" \\"$@\\""]
				CMD ["date", "+%Y-%m-%dT%H:%M:%S"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(
                    container,
                    image,
                    new String[] {"/bin/sh", "-c", "exec \"$0\" \"$@\""},
                    new String[] {"date", "+%Y-%m-%dT%H:%M:%S"});

            TimedContainerStart.start(container);
            assertTimestampOutput(container.getLogs().trim());
        }
    }

    @Test
    void shellEntrypoint_shellCmd_printsDefaultDate() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/shell-entrypoint-shell-cmd:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT date
				CMD +%Y-%m-%dT%H:%M:%S
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(
                    container, image, new String[] {"/bin/sh", "-c", "date"}, new String[] {
                        "/bin/sh", "-c", "+%Y-%m-%dT%H:%M:%S"
                    });

            TimedContainerStart.start(container);
            String output = container.getLogs().trim();
            assertThat(output).matches("^[A-Z][a-z]{2} [A-Z][a-z]{2} .*\\d{4}.*");
        }
    }

    @Test
    void shellEntrypointWithScript_execCmd_printsFormattedTime() {
        DockerImageName image = buildImage(
                "preinit-test/entrypoint-and-cmd/shell-entrypoint-script:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT ["/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"]
				CMD ["ignored-arg"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            assertImageConfig(
                    container,
                    image,
                    new String[] {"/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"},
                    new String[] {"ignored-arg"});

            TimedContainerStart.start(container);
            assertTimestampOutput(container.getLogs().trim());
        }
    }

    private static void assertImageConfig(
            GenericContainer<?> container,
            DockerImageName image,
            String[] expectedEntrypoint,
            String[] expectedCmd) {
        DockerClient dockerClient = container.getDockerClient();
        InspectImageResponse response =
                dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
        ContainerConfig config = response.getConfig();
        assertThat(config.getEntrypoint()).containsExactly(expectedEntrypoint);
        assertThat(config.getCmd()).containsExactly(expectedCmd);
    }

    private static void assertTimestampOutput(String output) {
        assertThat(output).matches(TIMESTAMP_PATTERN);
    }

    private static DockerImageName buildImage(String repositoryTag, String dockerfile) {
        return DockerTestImages.build(repositoryTag, dockerfile);
    }
}
