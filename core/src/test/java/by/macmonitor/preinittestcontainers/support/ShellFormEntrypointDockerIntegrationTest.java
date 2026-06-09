package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

class ShellFormEntrypointDockerIntegrationTest {

    private static final String IMAGE_NAME = "preinit-test/shell-form-time:latest";

    @Test
    void runsShellEntrypoint1_printsFormattedTime() {
        DockerImageName image = DockerImageName.parse(new ImageFromDockerfile(IMAGE_NAME, false)
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						ENTRYPOINT ["/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"]
						""")
                .get());
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
        DockerImageName image = DockerImageName.parse(new ImageFromDockerfile(IMAGE_NAME, false)
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						ENTRYPOINT ["date '+%Y-%m-%dT%H:%M:%S'"]
						""")
                .get());
        try (GenericContainer<?> container = new GenericContainer<>(image)) {
            DockerClient dockerClient = container.getDockerClient();
            InspectImageResponse response =
                    dockerClient.inspectImageCmd(image.asCanonicalNameString()).exec();
            ContainerConfig config = response.getConfig();
            assertThat(config.getEntrypoint()).containsExactly("date '+%Y-%m-%dT%H:%M:%S'");
            assertThat(config.getCmd()).isNull();

            assertThatThrownBy(container::start).isInstanceOf(ContainerLaunchException.class);
        }
    }

    @Test
    void runsShellEntrypoint3_printsFormattedTime() {
        DockerImageName image = DockerImageName.parse(new ImageFromDockerfile(IMAGE_NAME, false)
                .withFileFromString(
                        "Dockerfile",
                        """
						FROM alpine:3.21
						ENTRYPOINT date '+%Y-%m-%dT%H:%M:%S'
						""")
                .get());
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
