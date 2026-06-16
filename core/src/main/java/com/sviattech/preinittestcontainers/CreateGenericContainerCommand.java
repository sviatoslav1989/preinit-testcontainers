package com.sviattech.preinittestcontainers;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.sviattech.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable {@link CreateContainerCommand} for generic Testcontainers images, built via
 * {@link CreateContainerCommandBuilder}.
 *
 * <p>Base type for module commands such as {@code CreateJdbcContainerCommand} and
 * {@code CreateRedisContainerCommand}.
 */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateGenericContainerCommand implements CreateContainerCommand {

    /**
     * When {@code true}, passed to Testcontainers
     * {@link org.testcontainers.containers.GenericContainer#withAccessToHost(boolean)}. Enables
     * {@code host.testcontainers.internal} for reaching the test host from inside the container.
     */
    @Builder.Default
    private final boolean accessToHost = false;

    /**
     * Runs once after the temp container starts during pre-initialized image build; not used at
     * runtime.
     */
    private final PreInitStartCallback afterPreInitStartCallback;

    private final String baseImageName;

    @Getter(lombok.AccessLevel.NONE)
    private final List<ClasspathResourceMappingCommand> classpathResourceMappings;

    @Getter(lombok.AccessLevel.NONE)
    private final List<String> cmdParameters;

    private final LinkedHashMap<Transferable, String> copyToTransferableContainerPathMap;

    /**
     * Low-level docker-java tweaks applied in list order during container creation. Applied in
     * {@link GenericContainerFactory#applyCommandProperties}; does not replace built-in CMD /
     * persist entrypoint handling. Pre-init entrypoint is applied later via
     * {@link GenericContainerFactory#applyPersistEntrypoint} unless callers account for it.
     */
    @Singular("createContainerCmdModifier")
    private final List<Consumer<CreateContainerCmd>> createContainerCmdModifiers;

    /**
     * Optional when {@link #preInitialized} is {@code true}: if omitted, the matching
     * {@code *ContainerFactory} computes the tag via {@link ContainerEndImageNameCalculator}. An
     * explicit value is still allowed for tests or pinned tags.
     */
    private final String endImageName;

    @Getter(lombok.AccessLevel.NONE)
    private final Map<String, String> environmentVariables;

    /**
     * Container ports to expose; Docker assigns a random free host port for each (use
     * {@code getMappedPort} at runtime). Prefer this over {@link #portBindings} for tests. See
     * {@link #portBindings} when both lists target the same container port.
     */
    @Getter(lombok.AccessLevel.NONE)
    private final List<Integer> exposedPorts;

    /**
     * Extra host mappings in {@code hostname:ipAddress} form (same format as Testcontainers
     * {@code withExtraHost}).
     */
    @Getter(lombok.AccessLevel.NONE)
    private final List<String> extraHosts;

    /**
     * Host path bind mounts applied at container start (not baked into a pre-initialized image by
     * {@code docker commit}). For file content that must appear in the committed image, prefer
     * {@link #classpathResourceMappings} or {@link #copyToTransferableContainerPathMap}.
     */
    @Singular("fileSystemBind")
    private final List<FileSystemBindCommand> fileSystemBinds;

    /**
     * When {@code true} (default), pre-init image builds use a cross-process file lock
     * ({@link ImageCreationLockService#withLock}). When locking is enabled, acquire and stale
     * timeouts come from {@link #imageCreationLockOption}. Keep {@code true} in forked JVMs (for
     * example Gradle/JUnit parallel test workers) so only one process commits a given end image tag
     * and others wait—{@code false} can cause race conditions there. Set to {@code false} only when
     * builds are serialized in a single JVM (e.g. local one-off runs).
     */
    @Builder.Default
    private final boolean imageCreationLock = true;

    @Builder.Default
    private final ImageCreationLockOption imageCreationLockOption =
            ImageCreationLockOption.defaults();

    /** Nullable: omit to leave Testcontainers default. */
    private final ImagePullPolicy imagePullPolicy;

    @Getter(lombok.AccessLevel.NONE)
    private final Map<String, String> labels;

    @Singular("logConsumer")
    private final List<Consumer<OutputFrame>> logConsumers;

    /**
     * Optional explicit override. When null, the factory resolves metadata in order: (1) its
     * {@link com.sviattech.preinittestcontainers.metadata.ContainerMetadataRegistry registry}
     * (classpath {@code preinit-testcontainers/metadata/{repo-last-segment}.metadata} by default),
     * then (2) Docker image inspect via
     * {@link com.sviattech.preinittestcontainers.metadata.DockerImageMetadataInspector}.
     */
    private final ContainerMetadata metadata;

    /** Nullable: omit to leave Testcontainers default. */
    private final Duration minimumRunningDuration;

    /** Nullable: omit to leave Testcontainers default. */
    private final Network network;

    @Getter(lombok.AccessLevel.NONE)
    private final List<String> networkAliases;

    /** Nullable: omit to leave Testcontainers default. */
    private final String networkMode;

    /**
     * Fixed host-to-container port mappings in Docker form (e.g. {@code "8080:6379"},
     * {@code "9090/udp:53"}), passed to Testcontainers {@code setPortBindings}. Prefer
     * {@link #exposedPorts} with {@code getMappedPort(containerPort)} for tests so Docker assigns a
     * free host port. Fixed bindings can cause port conflicts and flaky CI when tests run in
     * parallel. If the same container port appears in both {@code exposedPorts} and
     * {@code portBindings}, the fixed binding wins (Testcontainers merge semantics).
     */
    @Singular("portBinding")
    private final List<String> portBindings;

    @Builder.Default
    private final boolean preInitialized = true;

    /** Nullable: omit to leave Testcontainers default. */
    private final Boolean privilegedMode;

    private final boolean reusable;

    /** Nullable: omit to leave Testcontainers default. */
    private final Long sharedMemorySize;

    /** Nullable: omit to leave Testcontainers default. */
    private final Integer startupAttempts;

    /** Nullable: omit to leave Testcontainers default. */
    private final StartupCheckStrategy startupCheckStrategy;

    /** Nullable: omit to leave Testcontainers default. */
    private final Duration startupTimeout;

    @Getter(lombok.AccessLevel.NONE)
    private final List<TmpFsSystemCommand> tmpFsFilesystems;

    /** When null, {@link #getWaitStrategy()} uses {@link #defaultWaitStrategy()}. */
    @Getter(lombok.AccessLevel.NONE)
    private final WaitStrategy waitStrategy;

    /** Nullable: omit to leave Testcontainers default. */
    private final String workingDirectory;

    public final List<ClasspathResourceMappingCommand> getClasspathResourceMappings() {
        return classpathResourceMappings != null
                ? classpathResourceMappings
                : Collections.emptyList();
    }

    public final List<String> getCmdParameters() {
        return cmdParameters != null ? cmdParameters : Collections.emptyList();
    }

    public final Map<String, String> getEnvironmentVariables() {
        return environmentVariables != null ? environmentVariables : Collections.emptyMap();
    }

    public final List<Integer> getExposedPorts() {
        return exposedPorts != null ? exposedPorts : Collections.emptyList();
    }

    public final List<String> getExtraHosts() {
        return extraHosts != null ? extraHosts : Collections.emptyList();
    }

    public final Map<String, String> getLabels() {
        return labels != null ? labels : Collections.emptyMap();
    }

    public final ContainerMetadata getMetadata() {
        return metadata;
    }

    public final List<String> getNetworkAliases() {
        return networkAliases != null ? networkAliases : Collections.emptyList();
    }

    public final List<TmpFsSystemCommand> getTmpFsFilesystems() {
        return tmpFsFilesystems != null ? tmpFsFilesystems : Collections.emptyList();
    }

    public final WaitStrategy getWaitStrategy() {
        return waitStrategy != null ? waitStrategy : defaultWaitStrategy();
    }

    /**
     * Override on leaf commands that need a non-Testcontainers-default wait; default {@code null} =
     * do not call {@code waitingFor}.
     */
    protected WaitStrategy defaultWaitStrategy() {
        return null;
    }

    public abstract static class CreateGenericContainerCommandBuilder<
                    C extends CreateGenericContainerCommand,
                    B extends CreateGenericContainerCommandBuilder<C, B>>
            implements CreateContainerCommandBuilder<C, B> {

        private LinkedHashMap<Transferable, String> copyToTransferableContainerPathMap =
                new LinkedHashMap<>();

        @Override
        public B waitingFor(WaitStrategy waitStrategy) {
            return withWaitStrategy(waitStrategy);
        }

        @Override
        public B withClasspathResourceMapping(ClasspathResourceMappingCommand mapping) {
            List<ClasspathResourceMappingCommand> merged =
                    new ArrayList<>(currentClasspathResourceMappings());
            merged.add(mapping);
            return withClasspathResourceMappings(merged);
        }

        @Override
        public B withClasspathResourceMapping(String classpathResourcePath, String containerPath) {
            return withClasspathResourceMapping(ClasspathResourceMappingCommand.builder()
                    .withClasspathResourcePath(classpathResourcePath)
                    .withContainerPath(containerPath)
                    .build());
        }

        public B withCmdParameters(List<String> cmdParameters) {
            this.cmdParameters = cmdParameters;
            return self();
        }

        @Override
        public B withCommand(String... cmdParameters) {
            return withCmdParameters(Arrays.asList(cmdParameters));
        }

        @Override
        public B withCopyFileToContainer(String classpathResourcePath, String containerPath) {
            return withClasspathResourceMapping(classpathResourcePath, containerPath);
        }

        @Override
        public B withCopyToContainer(Transferable transferable, String containerPath) {
            copyToTransferableContainerPathMap.put(transferable, containerPath);
            return self();
        }

        @Override
        public B withEnv(Map<String, String> environmentVariables) {
            return withEnvironmentVariables(new LinkedHashMap<>(environmentVariables));
        }

        @Override
        public B withEnv(String key, String value) {
            Map<String, String> merged = new LinkedHashMap<>(currentEnvironmentVariables());
            merged.put(key, value);
            return withEnvironmentVariables(merged);
        }

        @Override
        public B withExposedPorts(Integer... exposedPorts) {
            return withExposedPorts(Arrays.asList(exposedPorts));
        }

        public B withExposedPorts(List<Integer> exposedPorts) {
            this.exposedPorts = exposedPorts;
            return self();
        }

        @Override
        public B withExtraHost(String host, String ip) {
            List<String> merged = new ArrayList<>(currentExtraHosts());
            merged.add(host + ":" + ip);
            return withExtraHosts(merged);
        }

        @Override
        public B withFileSystemBind(String hostPath, String containerPath) {
            return withFileSystemBind(FileSystemBindCommand.builder()
                    .withHostPath(hostPath)
                    .withContainerPath(containerPath)
                    .build());
        }

        @Override
        public B withFileSystemBind(String hostPath, String containerPath, BindMode bindMode) {
            return withFileSystemBind(FileSystemBindCommand.builder()
                    .withHostPath(hostPath)
                    .withContainerPath(containerPath)
                    .withBindMode(bindMode)
                    .build());
        }

        @Override
        public B withLabel(String key, String value) {
            Map<String, String> merged = new LinkedHashMap<>(currentLabels());
            merged.put(key, value);
            return withLabels(merged);
        }

        public B withNetworkAliases(List<String> networkAliases) {
            this.networkAliases = networkAliases;
            return self();
        }

        @Override
        public B withNetworkAliases(String... networkAliases) {
            return withNetworkAliases(Arrays.asList(networkAliases));
        }

        @Override
        public B withPrivilegedMode(boolean privilegedMode) {
            return withPrivilegedMode(Boolean.valueOf(privilegedMode));
        }

        public B withPrivilegedMode(Boolean privilegedMode) {
            this.privilegedMode = privilegedMode;
            return self();
        }

        @Override
        public B withReuse(boolean reusable) {
            return withReusable(reusable);
        }

        @Override
        public B withSharedMemorySize(long sharedMemorySize) {
            return withSharedMemorySize(Long.valueOf(sharedMemorySize));
        }

        public B withSharedMemorySize(Long sharedMemorySize) {
            this.sharedMemorySize = sharedMemorySize;
            return self();
        }

        @Override
        public B withStartupAttempts(int startupAttempts) {
            return withStartupAttempts(Integer.valueOf(startupAttempts));
        }

        public B withStartupAttempts(Integer startupAttempts) {
            this.startupAttempts = startupAttempts;
            return self();
        }

        @Override
        public B withTmpFs(Map<String, String> tmpFs) {
            List<TmpFsSystemCommand> filesystems = new ArrayList<>();
            for (Map.Entry<String, String> entry : tmpFs.entrySet()) {
                filesystems.add(TmpFsSystemCommand.builder()
                        .withMountPath(entry.getKey())
                        .withOptions(entry.getValue())
                        .withNeedPersist(false)
                        .build());
            }
            return withTmpFsFilesystems(filesystems);
        }

        @Override
        public B withTmpFs(String mountPath, String options) {
            return withTmpFs(TmpFsSystemCommand.builder()
                    .withMountPath(mountPath)
                    .withOptions(options)
                    .withNeedPersist(false)
                    .build());
        }

        @Override
        public B withTmpFs(TmpFsSystemCommand tmpFs) {
            List<TmpFsSystemCommand> merged = new ArrayList<>(currentTmpFsFilesystems());
            merged.add(tmpFs);
            return withTmpFsFilesystems(merged);
        }

        private List<ClasspathResourceMappingCommand> currentClasspathResourceMappings() {
            return classpathResourceMappings != null
                    ? classpathResourceMappings
                    : Collections.emptyList();
        }

        private Map<String, String> currentEnvironmentVariables() {
            return environmentVariables != null ? environmentVariables : Collections.emptyMap();
        }

        private List<String> currentExtraHosts() {
            return extraHosts != null ? extraHosts : Collections.emptyList();
        }

        private Map<String, String> currentLabels() {
            return labels != null ? labels : Collections.emptyMap();
        }

        private List<TmpFsSystemCommand> currentTmpFsFilesystems() {
            return tmpFsFilesystems != null ? tmpFsFilesystems : Collections.emptyList();
        }
    }
}
