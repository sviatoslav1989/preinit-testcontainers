package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class ShellFormCmdDockerIntegrationTest {

    @Test
    void runsShellCmd1_printsFormattedTime() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-cmd/exec-sh-cmd:latest",
                """
				FROM alpine:3.21
				CMD ["/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint()).isNull();
            assertThat(config.getCmd())
                    .containsExactly("/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'");

            TimedContainerStart.start(container);
            assertThat(container.getLogs().trim())
                    .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        }
    }

    @Test
    void runsShellCmd2_Error() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-cmd/invalid-exec-cmd:latest",
                """
				FROM alpine:3.21
				CMD ["date '+%Y-%m-%dT%H:%M:%S'"]
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint()).isNull();
            assertThat(config.getCmd()).containsExactly("date '+%Y-%m-%dT%H:%M:%S'");

            ContainerStartAssertions.assertStartFails(container);
        }
    }

    @Test
    void runsShellCmd3_printsFormattedTime() {
        DockerImageName image = DockerTestImages.build(
                "preinit-test/shell-form-cmd/shell-cmd:latest",
                """
				FROM alpine:3.21
				CMD date '+%Y-%m-%dT%H:%M:%S'
				""");
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint()).isNull();
            assertThat(config.getCmd())
                    .containsExactly("/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'");

            TimedContainerStart.start(container);
            assertThat(container.getLogs().trim())
                    .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        }
    }
}
