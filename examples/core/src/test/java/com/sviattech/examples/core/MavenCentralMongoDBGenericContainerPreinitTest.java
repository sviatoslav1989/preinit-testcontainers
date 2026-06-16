package com.sviattech.examples.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sviattech.preinittestcontainers.CreateGenericContainerCommand;
import com.sviattech.preinittestcontainers.GenericContainerFactory;
import com.sviattech.preinittestcontainers.PreInitStartCallback;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import com.github.dockerjava.api.DockerClient;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

@Slf4j
class MavenCentralMongoDBGenericContainerPreinitTest {

    @Test
    void preinitializedContainerAppliesCallbackSeed() throws Exception {
        String imageName = null;
        CreateGenericContainerCommand command = CreateGenericContainerCommand.builder()
                .withBaseImageName("mongo:7.0")
                .withExposedPorts(27017)
                .waitingFor(Wait.forListeningPort())
                .withLogConsumer(new Slf4jLogConsumer(log))
                .withAfterPreInitStartCallback(PreInitStartCallback.of(
                        "mongo-seed-v1",
                        container -> {
                            try {
                                Container.ExecResult result = container.execInContainer(
                                        "mongosh",
                                        "--quiet",
                                        "--eval",
                                        "db.getSiblingDB('testdb').users.insertOne({name: 'alice'})");
                                if (result.getExitCode() != 0) {
                                    throw new RuntimeException(
                                            "mongosh seed failed: " + result.getStderr());
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .build();
        try (GenericContainer<?> container =
                GenericContainerFactory.createGenericContainer(command)) {
            TimedContainerStart.start(container);
            imageName = container.getDockerImageName();
            Container.ExecResult result = container.execInContainer(
                    "mongosh",
                    "--quiet",
                    "--eval",
                    "printjson(db.getSiblingDB('testdb').users.findOne({name: 'alice'}))");
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).contains("alice");
        } finally {
            if (imageName != null) {
                DockerClient dockerClient = DockerClientFactory.lazyClient();
                dockerClient.removeImageCmd(imageName).exec();
            }
        }
    }
}
