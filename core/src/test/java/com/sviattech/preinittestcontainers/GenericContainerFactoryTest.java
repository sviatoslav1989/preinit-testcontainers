package com.sviattech.preinittestcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.PortBinding;
import com.sviattech.preinittestcontainers.clickhouse.ClickHouseContainerFactory;
import com.sviattech.preinittestcontainers.clickhouse.CreateClickHouseContainerCommand;
import com.sviattech.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;
import com.sviattech.preinittestcontainers.endimagename.GenericContainerEndImageNameCalculator;
import com.sviattech.preinittestcontainers.endimagename.RedisEndImageNameCalculator;
import com.sviattech.preinittestcontainers.metadata.ContainerMetadataRegistry;
import com.sviattech.preinittestcontainers.metadata.DockerImageMetadataInspector;
import com.sviattech.preinittestcontainers.metadata.FileBasedContainerMetadataRegistry;
import com.sviattech.preinittestcontainers.mysql.CreateMySQLContainerCommand;
import com.sviattech.preinittestcontainers.mysql.MySQLContainerFactory;
import com.sviattech.preinittestcontainers.postgresql.CreatePostgreSQLContainerCommand;
import com.sviattech.preinittestcontainers.postgresql.PostgreSQLContainerFactory;
import com.sviattech.preinittestcontainers.redis.CreateRedisContainerCommand;
import com.sviattech.preinittestcontainers.redis.RedisContainerFactory;
import com.sviattech.preinittestcontainers.support.ColonSeparatedArgvUtils;
import com.sviattech.preinittestcontainers.support.FileBasedImageCreationLockService;

import lombok.Builder;
import lombok.experimental.SuperBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.core.CreateContainerCmdModifier;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

class GenericContainerFactoryTest {

    private static final ContainerMetadataRegistry BUNDLED_METADATA =
            new FileBasedContainerMetadataRegistry();

    @Test
    void applyCommandProperties_appliesAccessToHost() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withAccessToHost(true)
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.isHostAccessible()).isTrue();
        }
    }

    @Test
    void applyCommandProperties_appliesCopyToContainer() throws Exception {
        String containerPath = "/opt/custom-copy";
        MountableFile transferable = MountableFile.forClasspathResource(
                GenericContainerFactory.ENTRYPOINT_CLASS_PATH, 0755);
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withCopyToContainer(transferable, containerPath)
                .build();
        assertThat(command.getCopyToTransferableContainerPathMap().get(transferable))
                .isEqualTo(containerPath);
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            Field mapField =
                    GenericContainer.class.getDeclaredField("copyToTransferableContainerPathMap");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Transferable, String> copyMap = (Map<Transferable, String>) mapField.get(container);
            assertThat(copyMap).containsKey(transferable);
            assertThat(copyMap.get(transferable)).isEqualTo(containerPath);
        }
    }

    @Test
    void applyCommandProperties_appliesCreateContainerCmdModifier() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(false)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName("preinit-test-host"))
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getCreateContainerCmdModifiers()).hasSize(1);
            assertThat(applyCreateContainerCmdModifiers(container, "redis:7.4.2")
                            .getHostName())
                    .isEqualTo("preinit-test-host");
        }
    }

    @Test
    void applyCommandProperties_appliesFileSystemBinds() throws Exception {
        String containerPath = "/opt/host-bind";
        java.nio.file.Path hostFile = Files.createTempFile("preinit-bind-", ".txt");
        try {
            String hostPath = MountableFile.forHostPath(hostFile.toString()).getResolvedPath();
            CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                    .withBaseImageName("redis:7.4.2")
                    .withFileSystemBind(FileSystemBindCommand.builder()
                            .withHostPath(hostPath)
                            .withContainerPath(containerPath)
                            .withBindMode(BindMode.READ_ONLY)
                            .withSelinuxContext(SelinuxContext.SHARED)
                            .build())
                    .build();
            try (GenericContainer<?> container =
                    new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
                ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                        container, command, false);
                assertThat(container.getBinds()).hasSize(1);
                Bind applied = container.getBinds().get(0);
                assertThat(applied.getPath()).isEqualTo(hostPath);
                assertThat(applied.getVolume().getPath()).isEqualTo(containerPath);
                assertThat(applied.getAccessMode()).isEqualTo(AccessMode.ro);
                assertThat(applied.getSecMode()).isEqualTo(SelinuxContext.SHARED.selContext);
            }
        } finally {
            Files.deleteIfExists(hostFile);
        }
    }

    @Test
    void applyCommandProperties_appliesImagePullPolicyAndLogConsumer() throws Exception {
        ImagePullPolicy imagePullPolicy = PullPolicy.alwaysPull();
        Consumer<OutputFrame> logConsumer = frame -> {};
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withImagePullPolicy(imagePullPolicy)
                .withLogConsumer(logConsumer)
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            Field imagePullPolicyField =
                    RemoteDockerImage.class.getDeclaredField("imagePullPolicy");
            imagePullPolicyField.setAccessible(true);
            assertThat(imagePullPolicyField.get(container.getImage())).isSameAs(imagePullPolicy);
            assertThat(container.getLogConsumers()).hasSize(1);
            assertThat(container.getLogConsumers().get(0)).isSameAs(logConsumer);
        }
    }

    @Test
    void applyCommandProperties_appliesStartupAttemptsAndCheckStrategy() {
        StartupCheckStrategy customStrategy = new IsRunningStartupCheckStrategy();
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withStartupAttempts(3)
                .withStartupCheckStrategy(customStrategy)
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getStartupAttempts()).isEqualTo(3);
            assertThat(container.getStartupCheckStrategy()).isSameAs(customStrategy);
        }
    }

    @Test
    void applyCommandProperties_createContainerCmdModifiers_applyInOrder() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(false)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName("host-a"))
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName("host-b"))
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getCreateContainerCmdModifiers()).hasSize(2);
            assertThat(applyCreateContainerCmdModifiers(container, "redis:7.4.2")
                            .getHostName())
                    .isEqualTo("host-b");
        }
    }

    @ParameterizedTest
    @MethodSource("invalidExtraHostsCases")
    void applyCommandProperties_invalidExtraHost_throws(String extraHost) {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withExtraHosts(List.of(extraHost))
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            assertThatThrownBy(() -> {
                        ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                                container, command, false);
                    })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("extraHosts entry must be hostname:ip, got: " + extraHost);
        }
    }

    @Test
    void applyCommandProperties_minimumRunningDuration_only_setsMinimumDurationStrategy() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withMinimumRunningDuration(Duration.ofSeconds(2))
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getStartupCheckStrategy())
                    .isInstanceOf(MinimumDurationRunningStartupCheckStrategy.class);
        }
    }

    @Test
    void applyCommandProperties_minimumRunningDuration_overridesPriorCheckStrategy() {
        StartupCheckStrategy customStrategy = new IsRunningStartupCheckStrategy();
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withStartupAttempts(3)
                .withStartupCheckStrategy(customStrategy)
                .withMinimumRunningDuration(Duration.ofSeconds(2))
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getStartupAttempts()).isEqualTo(3);
            assertThat(container.getStartupCheckStrategy())
                    .isInstanceOf(MinimumDurationRunningStartupCheckStrategy.class);
        }
    }

    @Test
    void applyCommandProperties_setsPortBindings() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPortBinding("8080:6379")
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            assertThat(container.getPortBindings()).hasSize(1);
            PortBinding applied = PortBinding.parse(container.getPortBindings().get(0));
            assertThat(applied.getBinding().getHostPortSpec()).isEqualTo("8080");
            assertThat(applied.getExposedPort().getPort()).isEqualTo(6379);
        }
    }

    @Test
    void applyCommandProperties_setsPortBindingsDuringImageBuild() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPortBinding("8080:6379")
                .build();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, true);
            assertThat(container.getPortBindings()).hasSize(1);
            PortBinding applied = PortBinding.parse(container.getPortBindings().get(0));
            assertThat(applied.getBinding().getHostPortSpec()).isEqualTo("8080");
            assertThat(applied.getExposedPort().getPort()).isEqualTo(6379);
        }
    }

    @Test
    void applyCommandProperties_startupTimeout_mutatesActiveWaitStrategy() throws Exception {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(42))
                .build();
        try (ExposingGenericContainer container =
                new ExposingGenericContainer(DockerImageName.parse("redis:7.4.2"))) {
            ExposingGenericContainerFactory.INSTANCE.applyCommandProperties(
                    container, command, false);
            AbstractWaitStrategy waitStrategy =
                    (AbstractWaitStrategy) container.exposeWaitStrategy();
            Field startupTimeoutField =
                    AbstractWaitStrategy.class.getDeclaredField("startupTimeout");
            startupTimeoutField.setAccessible(true);
            assertThat(startupTimeoutField.get(waitStrategy)).isEqualTo(Duration.ofSeconds(42));
        }
    }

    @ParameterizedTest
    @MethodSource("persistEnvTempModeCases")
    void buildPersistEnv_setsTempModeFromFlag(boolean tempBuildFlow, String expectedTempMode) {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withMetadata(ContainerMetadata.builder()
                        .withEntrypoint(new String[] {"docker-entrypoint.sh"})
                        .withCmd(new String[] {"mysqld"})
                        .build())
                .withEnv(Collections.emptyMap())
                .withTmpFsFilesystems(List.of(TmpFsSystemCommand.builder()
                        .withMountPath("/var/lib/mysql")
                        .withNeedPersist(true)
                        .build()))
                .build();
        Map<String, String> env = ExposingGenericContainerFactory.INSTANCE.buildPersistEnv(
                command, command.getMetadata(), tempBuildFlow);
        assertThat(env.get(GenericContainerFactory.TCE_TEMP_MODE)).isEqualTo(expectedTempMode);
    }

    @ParameterizedTest
    @MethodSource("upstreamEnvCases")
    void buildPersistEnv_upstreamMetadata_tableDriven(
            ContainerMetadata metadata, Map<String, String> expectedUpstream) {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withMetadata(metadata)
                .withTmpFsFilesystems(Collections.emptyList())
                .build();
        Map<String, String> expected = new LinkedHashMap<>(expectedUpstream);
        expected.put(GenericContainerFactory.TCE_PAIR_COUNT, "0");
        expected.put(GenericContainerFactory.TCE_TEMP_MODE, "0");
        assertThat(ExposingGenericContainerFactory.INSTANCE.buildPersistEnv(
                        command, metadata, false))
                .isEqualTo(expected);
    }

    @Test
    void buildTmpfsPersistEnv_emptyListReturnsPairCountZeroOnly() {
        assertThat(GenericContainerFactory.buildTmpfsPersistEnv(List.of()))
                .isEqualTo(Map.of(GenericContainerFactory.TCE_PAIR_COUNT, "0"));
    }

    @ParameterizedTest
    @MethodSource("tmpfsPersistEnvCases")
    void buildTmpfsPersistEnv_tableDriven(
            List<String> liveMountPaths, Map<String, String> expected) {
        assertThat(GenericContainerFactory.buildTmpfsPersistEnv(liveMountPaths))
                .isEqualTo(expected);
    }

    @Test
    void clickhouseDefaultTmpFs() {
        ClickHouseContainerFactory factory =
                ClickHouseContainerFactory.builder().build();
        CreateClickHouseContainerCommand command = CreateClickHouseContainerCommand.builder()
                .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                .build();
        ContainerMetadata metadata = factory.resolveMetadata(command);
        assertDefaultDataTmpFs(
                metadata.getTmpFs(), factory.resolveEffectiveTmpFsFilesystems(command, metadata));
    }

    @Test
    void clickhouseMetadata_upstreamEntrypointPaths() {
        ContainerMetadata expected =
                BUNDLED_METADATA.find("clickhouse/clickhouse-server:26.3.4.11").orElseThrow();
        assertThat(expected.getEntrypointPath()).isEqualTo("/entrypoint.sh");
        assertThat(expected.getEntrypoint()).containsExactly("/entrypoint.sh");
        assertThat(expected.getCmd()).isNull();
        assertThat(expected.getVolumes()).containsExactly("/var/lib/clickhouse");

        CreateClickHouseContainerCommand command = CreateClickHouseContainerCommand.builder()
                .withBaseImageName("clickhouse/clickhouse-server:26.3.4.11")
                .build();
        ContainerMetadata fromFactory =
                ClickHouseContainerFactory.builder().build().resolveMetadata(command);
        assertThat(fromFactory).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void createGenericContainer_returnsGenericContainerForNonPreinitializedCommand() {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(false)
                .build();
        GenericContainer<?> container = ExposingGenericContainerFactory.INSTANCE.create(command);
        assertThat(container).isNotNull().isInstanceOf(GenericContainer.class);
    }

    @Test
    void genericContainerFactory_defaultBuilderIsStable() {
        assertThat(GenericContainerFactory.builder().build())
                .usingRecursiveComparison()
                .isEqualTo(GenericContainerFactory.builder().build());
    }

    @Test
    void mysqlDefaultTmpFs() {
        MySQLContainerFactory factory = MySQLContainerFactory.builder().build();
        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .build();
        ContainerMetadata metadata = factory.resolveMetadata(command);
        assertDefaultDataTmpFs(
                metadata.getTmpFs(), factory.resolveEffectiveTmpFsFilesystems(command, metadata));
    }

    @Test
    void mysqlMetadata_upstreamEntrypointPaths() {
        ContainerMetadata expected = BUNDLED_METADATA.find("mysql:8.0.45").orElseThrow();
        assertThat(expected.getEntrypointPath()).isEqualTo("/usr/local/bin/docker-entrypoint.sh");
        assertThat(expected.getEntrypoint()).containsExactly("docker-entrypoint.sh");
        assertThat(expected.getCmd()).containsExactly("mysqld");
        assertThat(expected.getVolumes()).containsExactly("/var/lib/mysql");

        CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .build();
        ContainerMetadata fromFactory =
                MySQLContainerFactory.builder().build().resolveMetadata(command);
        assertThat(fromFactory).usingRecursiveComparison().isEqualTo(expected);
        assertThat(command.getMetadata()).isNull();
    }

    @Test
    void mysqlNewerTagResolvesSameMetadataAsCurrentMaxRecord() {
        MySQLContainerFactory factory = MySQLContainerFactory.builder().build();
        ContainerMetadata current = factory.resolveMetadata(CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:8.0.45")
                .build());
        ContainerMetadata newer = factory.resolveMetadata(CreateMySQLContainerCommand.builder()
                .withBaseImageName("mysql:99.0")
                .build());
        assertThat(newer).usingRecursiveComparison().isEqualTo(current);
    }

    @Test
    void postgresqlDefaultTmpFs() {
        PostgreSQLContainerFactory factory =
                PostgreSQLContainerFactory.builder().build();
        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .build();
        ContainerMetadata metadata = factory.resolveMetadata(command);
        assertDefaultDataTmpFs(
                metadata.getTmpFs(), factory.resolveEffectiveTmpFsFilesystems(command, metadata));
    }

    @Test
    void postgresqlDefaultWaitStrategy() {
        assertThat(CreatePostgreSQLContainerCommand.builder()
                        .withBaseImageName("postgres:17")
                        .build()
                        .getWaitStrategy())
                .isSameAs(CreatePostgreSQLContainerCommand.DEFAULT_WAIT_STRATEGY);

        WaitStrategy explicit =
                Wait.forListeningPort().withStartupTimeout(Duration.of(30, ChronoUnit.SECONDS));
        assertThat(CreatePostgreSQLContainerCommand.builder()
                        .withBaseImageName("postgres:17")
                        .waitingFor(explicit)
                        .build()
                        .getWaitStrategy())
                .isSameAs(explicit);
        assertThat(explicit).isNotSameAs(CreatePostgreSQLContainerCommand.DEFAULT_WAIT_STRATEGY);
    }

    @Test
    void postgresqlMetadata_upstreamEntrypointPaths() {
        ContainerMetadata expected = BUNDLED_METADATA.find("postgres:17").orElseThrow();
        assertThat(expected.getEntrypointPath()).isEqualTo("/usr/local/bin/docker-entrypoint.sh");
        assertThat(expected.getEntrypoint()).containsExactly("docker-entrypoint.sh");
        assertThat(expected.getCmd()).containsExactly("postgres", "-c", "fsync=off");
        assertThat(expected.getVolumes()).containsExactly("/var/lib/postgresql/data");

        CreatePostgreSQLContainerCommand command = CreatePostgreSQLContainerCommand.builder()
                .withBaseImageName("postgres:17")
                .build();
        ContainerMetadata fromFactory =
                PostgreSQLContainerFactory.builder().build().resolveMetadata(command);
        assertThat(fromFactory).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void redisDefaultTmpFs() {
        RedisContainerFactory factory = RedisContainerFactory.builder().build();
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        ContainerMetadata metadata = factory.resolveMetadata(command);
        assertDefaultDataTmpFs(
                metadata.getTmpFs(), factory.resolveEffectiveTmpFsFilesystems(command, metadata));
    }

    @Test
    void redisMetadata_upstreamEntrypointPaths() {
        ContainerMetadata expected = BUNDLED_METADATA.find("redis:7.4.2").orElseThrow();
        assertThat(expected.getEntrypointPath()).isEqualTo("/usr/local/bin/docker-entrypoint.sh");
        assertThat(expected.getEntrypoint()).containsExactly("docker-entrypoint.sh");
        assertThat(expected.getCmd()).containsExactly("redis-server");
        assertThat(expected.getVolumes()).containsExactly("/data");

        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .build();
        ContainerMetadata fromFactory =
                RedisContainerFactory.builder().build().resolveMetadata(command);
        assertThat(fromFactory).usingRecursiveComparison().isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("resolveContainerCmdCases")
    void resolveContainerCmd_tableDriven(
            boolean preInitialized,
            ContainerMetadata metadata,
            List<String> cmdParameters,
            List<String> expected) {
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withPreInitialized(preInitialized)
                .withMetadata(metadata)
                .withCmdParameters(cmdParameters)
                .build();
        assertThat(ExposingGenericContainerFactory.INSTANCE.resolveContainerCmd(command, metadata))
                .isEqualTo(expected);
    }

    @Test
    void resolveMetadata_fallsBackToDockerInspectWhenRegistryMisses() {
        ContainerMetadata stub = ContainerMetadata.builder()
                .withEntrypoint(new String[] {"stub-entry"})
                .build();
        DockerImageMetadataInspector stubInspector = imageName -> {
            assertThat(imageName).isEqualTo("unknown:1");
            return stub;
        };
        GenericContainerFactory<CreateGenericContainerCommand, GenericContainer<?>> factory =
                GenericContainerFactory.builder()
                        .withDockerClient(DockerClientFactory.lazyClient())
                        .withImageCreationLockService(new FileBasedImageCreationLockService())
                        .withEndImageNameCalculator(GenericContainerEndImageNameCalculator.INSTANCE)
                        .withContainerSupplier(imageName -> new GenericContainer<>(imageName))
                        .withMetadataRegistry(imageName -> Optional.empty())
                        .withDockerImageMetadataInspector(stubInspector)
                        .build();
        CreateGenericContainerCommand command = CreateGenericContainerCommand.builder()
                .withBaseImageName("unknown:1")
                .build();
        assertThat(factory.resolveMetadata(command)).isSameAs(stub);
    }

    @Test
    void withFileSystemBind_builderAddsFileSystemBindCommand() {
        String hostPath = "/tmp/host";
        String containerPath = "/container/path";
        CreateRedisContainerCommand command = CreateRedisContainerCommand.builder()
                .withBaseImageName("redis:7.4.2")
                .withFileSystemBind(hostPath, containerPath, BindMode.READ_WRITE)
                .build();
        assertThat(command.getFileSystemBinds()).hasSize(1);
        FileSystemBindCommand bind = command.getFileSystemBinds().get(0);
        assertThat(bind.getHostPath()).isEqualTo(hostPath);
        assertThat(bind.getContainerPath()).isEqualTo(containerPath);
        assertThat(bind.getBindMode()).isEqualTo(BindMode.READ_WRITE);
        assertThat(bind.getSelinuxContext()).isEqualTo(SelinuxContext.SHARED);
    }

    static Stream<Arguments> invalidExtraHostsCases() {
        return Stream.of(
                Arguments.of("no-colon-host"), Arguments.of(":172.17.0.1"), Arguments.of("host:"));
    }

    static Stream<Arguments> persistEnvTempModeCases() {
        return Stream.of(Arguments.of(true, "1"), Arguments.of(false, "0"));
    }

    static Stream<Arguments> resolveContainerCmdCases() {
        ContainerMetadata withMysqldCmd = ContainerMetadata.builder()
                .withEntrypoint(new String[] {"docker-entrypoint.sh"})
                .withCmd(new String[] {"mysqld"})
                .build();
        ContainerMetadata redisServerOnly = ContainerMetadata.builder()
                .withCmd(new String[] {"redis-server"})
                .build();
        return Stream.of(
                Arguments.of(true, withMysqldCmd, List.of("--foo"), List.of("mysqld", "--foo")),
                Arguments.of(true, redisServerOnly, List.of(), List.of("redis-server")),
                Arguments.of(false, withMysqldCmd, List.of("--foo"), List.of("--foo")));
    }

    static Stream<Arguments> tmpfsPersistEnvCases() {
        return Stream.of(
                Arguments.of(
                        List.of("/var/lib/mysql"),
                        Map.of(
                                GenericContainerFactory.TCE_PAIR_COUNT,
                                "1",
                                GenericContainerFactory.TCE_LIVE_DATA_PATHS,
                                "/var/lib/mysql",
                                GenericContainerFactory.TCE_SNAPSHOT_TEMP_PATHS,
                                "/var/lib/mysql_temp")),
                Arguments.of(
                        List.of("/var/lib/clickhouse"),
                        Map.of(
                                GenericContainerFactory.TCE_PAIR_COUNT,
                                "1",
                                GenericContainerFactory.TCE_LIVE_DATA_PATHS,
                                "/var/lib/clickhouse",
                                GenericContainerFactory.TCE_SNAPSHOT_TEMP_PATHS,
                                "/var/lib/clickhouse_temp")),
                Arguments.of(
                        List.of("/var/lib/mysql", "/var/log/mysql"),
                        env(
                                GenericContainerFactory.TCE_PAIR_COUNT,
                                "2",
                                GenericContainerFactory.TCE_LIVE_DATA_PATHS,
                                "/var/lib/mysql:/var/log/mysql",
                                GenericContainerFactory.TCE_SNAPSHOT_TEMP_PATHS,
                                "/var/lib/mysql_temp:/var/log/mysql_temp")),
                Arguments.of(
                        List.of("/data", "/tmp/cache"),
                        env(
                                GenericContainerFactory.TCE_PAIR_COUNT,
                                "2",
                                GenericContainerFactory.TCE_LIVE_DATA_PATHS,
                                "/data:/tmp/cache",
                                GenericContainerFactory.TCE_SNAPSHOT_TEMP_PATHS,
                                "/data_temp:/tmp/cache_temp")),
                Arguments.of(
                        List.of("/mnt/data:1"),
                        Map.of(
                                GenericContainerFactory.TCE_PAIR_COUNT,
                                "1",
                                GenericContainerFactory.TCE_LIVE_DATA_PATHS,
                                ColonSeparatedArgvUtils.encode("/mnt/data:1"),
                                GenericContainerFactory.TCE_SNAPSHOT_TEMP_PATHS,
                                ColonSeparatedArgvUtils.encode("/mnt/data:1_temp"))));
    }

    static Stream<Arguments> upstreamEnvCases() {
        return Stream.of(
                Arguments.of(
                        ContainerMetadata.builder()
                                .withEntrypoint(new String[] {"docker-entrypoint.sh"})
                                .withCmd(new String[] {"mysqld"})
                                .build(),
                        Map.of(
                                GenericContainerFactory.TCE_UPSTREAM_ENTRYPOINT,
                                "docker-entrypoint.sh")),
                Arguments.of(
                        ContainerMetadata.builder()
                                .withEntrypoint(new String[] {"/entrypoint.sh"})
                                .withCmd(null)
                                .build(),
                        Map.of(GenericContainerFactory.TCE_UPSTREAM_ENTRYPOINT, "/entrypoint.sh")),
                Arguments.of(
                        ContainerMetadata.builder()
                                .withEntrypoint(null)
                                .withCmd(new String[] {"redis-server"})
                                .build(),
                        Map.of()),
                Arguments.of(
                        ContainerMetadata.builder()
                                .withEntrypoint(null)
                                .withCmd(null)
                                .build(),
                        Map.of()),
                Arguments.of(
                        ContainerMetadata.builder()
                                .withEntrypoint(
                                        new String[] {"/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"})
                                .withCmd(null)
                                .build(),
                        Map.of(
                                GenericContainerFactory.TCE_UPSTREAM_ENTRYPOINT,
                                ColonSeparatedArgvUtils.encode(
                                        "/bin/sh", "-c", "date '+%Y-%m-%dT%H:%M:%S'"))));
    }

    private static CreateContainerCmd applyCreateContainerCmdModifiers(
            GenericContainer<?> container, String image) {
        CreateContainerCmd cmd = DockerClientFactory.lazyClient().createContainerCmd(image);
        for (CreateContainerCmdModifier modifier : container.getCreateContainerCmdModifiers()) {
            cmd = modifier.modify(cmd);
        }
        return cmd;
    }

    private static void assertDefaultDataTmpFs(
            List<TmpFsSystemCommand> expected, List<TmpFsSystemCommand> actual) {
        assertThat(actual).hasSize(expected.size());
        TmpFsSystemCommand tmpfs = actual.get(0);
        TmpFsSystemCommand expectedTmpfs = expected.get(0);
        assertThat(tmpfs.getMountPath()).isEqualTo(expectedTmpfs.getMountPath());
        assertThat(tmpfs.getOptions()).isEqualTo(expectedTmpfs.getOptions());
        assertThat(tmpfs.isNeedPersist()).isEqualTo(expectedTmpfs.isNeedPersist());
    }

    private static Map<String, String> env(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static final class ExposingGenericContainer
            extends GenericContainer<ExposingGenericContainer> {

        ExposingGenericContainer(DockerImageName imageName) {
            super(imageName);
        }

        WaitStrategy exposeWaitStrategy() {
            return getWaitStrategy();
        }
    }

    @SuperBuilder(toBuilder = true, setterPrefix = "with")
    private static final class ExposingGenericContainerFactory
            extends GenericContainerFactory<CreateRedisContainerCommand, GenericContainer<?>> {

        private static final ExposingGenericContainerFactory INSTANCE =
                ExposingGenericContainerFactory.builder().build();

        @Builder.Default
        private final Function<DockerImageName, GenericContainer<?>> containerSupplier =
                GenericContainer::new;

        @Builder.Default
        private final ContainerEndImageNameCalculator<CreateRedisContainerCommand>
                endImageNameCalculator = RedisEndImageNameCalculator.INSTANCE;

        @Override
        protected Function<DockerImageName, GenericContainer<?>> resolveContainerSupplier() {
            return containerSupplier;
        }

        @Override
        protected ContainerEndImageNameCalculator<CreateRedisContainerCommand>
                resolveEndImageNameCalculator() {
            return endImageNameCalculator;
        }
    }
}
