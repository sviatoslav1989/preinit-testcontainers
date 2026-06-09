package by.macmonitor.examples.core;

import static org.assertj.core.api.Assertions.assertThat;

import by.macmonitor.preinittestcontainers.CreateGenericContainerCommand;
import by.macmonitor.preinittestcontainers.GenericContainerFactory;
import by.macmonitor.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

class MavenCentralGenericContainerPreinitTest {

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreateGenericContainerCommand command = CreateGenericContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(true)
                .build();
        try (GenericContainer<?> container =
                GenericContainerFactory.createGenericContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            Container.ExecResult result = container.execInContainer("redis-cli", "PING");
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout().trim()).contains("PONG");
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }
}