package com.sviattech.preinittestcontainers;

import com.github.dockerjava.api.command.CreateContainerCmd;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder contract mirroring Testcontainers {@code GenericContainer} naming, plus
 * preinit-specific {@code with*} methods. Implementations are Lombok {@code @SuperBuilder}
 * hierarchies.
 *
 * <p>{@link #withCommand(String...)} sets extra argv appended after metadata CMD at runtime (see
 * {@link GenericContainerFactory#resolveContainerCmd}).
 */
public interface CreateContainerCommandBuilder<
        C extends CreateContainerCommand, B extends CreateContainerCommandBuilder<C, B>> {
    C build();

    B waitingFor(WaitStrategy waitStrategy);

    B withAccessToHost(boolean accessToHost);

    B withAfterPreInitStartCallback(PreInitStartCallback afterPreInitStartCallback);

    B withBaseImageName(String baseImageName);

    B withClasspathResourceMapping(ClasspathResourceMappingCommand mapping);

    B withClasspathResourceMapping(String classpathResourcePath, String containerPath);

    B withCommand(String... cmdParameters);

    B withCopyFileToContainer(String classpathResourcePath, String containerPath);

    B withCopyToContainer(Transferable transferable, String containerPath);

    B withCreateContainerCmdModifier(Consumer<CreateContainerCmd> createContainerCmdModifier);

    B withEndImageName(String endImageName);

    B withEnv(Map<String, String> environmentVariables);

    B withEnv(String key, String value);

    B withExposedPorts(Integer... exposedPorts);

    B withExtraHost(String host, String ip);

    B withFileSystemBind(String hostPath, String containerPath);

    B withFileSystemBind(String hostPath, String containerPath, BindMode bindMode);

    B withImageCreationLock(boolean imageCreationLock);

    B withImageCreationLockOption(ImageCreationLockOption imageCreationLockOption);

    B withImagePullPolicy(ImagePullPolicy imagePullPolicy);

    B withLabel(String key, String value);

    B withLabels(Map<String, String> labels);

    B withLogConsumer(Consumer<OutputFrame> logConsumer);

    B withMetadata(ContainerMetadata metadata);

    B withMinimumRunningDuration(Duration minimumRunningDuration);

    B withNetwork(Network network);

    B withNetworkAliases(String... networkAliases);

    B withNetworkMode(String networkMode);

    B withPortBinding(String portBinding);

    B withPreInitialized(boolean preInitialized);

    B withPrivilegedMode(boolean privilegedMode);

    B withReuse(boolean reusable);

    B withSharedMemorySize(long sharedMemorySize);

    B withStartupAttempts(int startupAttempts);

    B withStartupCheckStrategy(StartupCheckStrategy startupCheckStrategy);

    B withStartupTimeout(Duration startupTimeout);

    B withTmpFs(Map<String, String> tmpFs);

    B withTmpFs(String mountPath, String options);

    B withTmpFs(TmpFsSystemCommand tmpFs);

    B withWorkingDirectory(String workingDirectory);
}
