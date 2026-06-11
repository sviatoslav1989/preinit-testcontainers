package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class ShellFormEntrypointDockerIntegrationTest {

    @Test
    void runsShellEntrypoint1_printsFormattedTime() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-entrypoint/exec-sh-entrypoint:latest",
                """
						FROM alpine:3.21
						ENTRYPOINT ["/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"]
						""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint())
                    .containsExactly("/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'");
            assertThat(config.getCmd()).isNull();

            TimedContainerStart.start(container);
            assertThat(container.getLogs().trim())
                    .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        }
    }

    @Test
    void runsShellEntrypoint2_Error() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-entrypoint/invalid-exec-entrypoint:latest",
                """
						FROM alpine:3.21
						ENTRYPOINT ["date '+%Y-%m-%dT%H:%M:%S'"]
						""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint()).containsExactly("date '+%Y-%m-%dT%H:%M:%S'");
            assertThat(config.getCmd()).isNull();

            ContainerStartAssertions.assertStartFails(container);
        }
    }

    @Test
    void runsShellEntrypoint3_printsFormattedTime() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-entrypoint/shell-entrypoint:latest",
                """
				FROM alpine:3.21
				ENTRYPOINT date '+%Y-%m-%dT%H:%M:%S'
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint())
                    .containsExactly("/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'");
            assertThat(config.getCmd()).isNull();

            TimedContainerStart.start(container);
            assertThat(container.getLogs().trim())
                    .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        }
    }
}
