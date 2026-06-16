package com.sviattech.preinittestcontainers.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.redis.testcontainers.RedisContainer;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;

import java.util.Collections;

class RedisContainerFactoryTest {

    @Test
    void redisContainerFactory_defaultBuilderIsStable() {
        assertThat(RedisContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(RedisContainerFactory.builder().build());
    }

    @Test
    void testCreationOfNotPreinitializedContainer_SUCCESS() throws Exception {
        try (RedisContainer container =
                RedisContainerFactory.createRedisContainer(CreateRedisContainerCommand.builder()
                        .withBaseImageName("redis:7.4.2")
                        .withPreInitialized(false)
                        .build())) {
            TimedContainerStart.start(container);
            assertPingReturnsPong(container);
        }
    }

    @Test
    void testCreationOfPreinitializedContainer_SUCCESS() throws Exception {
        String imageName = null;
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        try (RedisContainer container = RedisContainerFactory.createRedisContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            assertPingReturnsPong(container);
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    @Test
    void testCreationOfPreinitializedContainerWithZeroTmpfs_SUCCESS() throws Exception {
        String imageName = null;
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withTmpFsFilesystems(Collections.emptyList())
                .build();
        try (RedisContainer container = RedisContainerFactory.createRedisContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            assertPingReturnsPong(container);
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }

    private static void assertPingReturnsPong(RedisContainer container) throws Exception {
        Container.ExecResult result = container.execInContainer("redis-cli", "PING");
        assertThat(result.getExitCode()).as("redis-cli PING should succeed").isZero();
        assertThat(result.getStdout().trim()).contains("PONG");
    }
}
