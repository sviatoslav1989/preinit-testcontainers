package com.sviattech.examples.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.redis.CreateRedisContainerCommand;
import com.sviattech.preinittestcontainers.redis.RedisContainerFactory;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;
import com.redis.testcontainers.RedisContainer;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;

class MavenCentralRedisPreinitTest {

    @Test
    void preinitializedContainerStarts() throws Exception {
        String imageName = null;
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        try (RedisContainer container = RedisContainerFactory.createRedisContainer(command)) {
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