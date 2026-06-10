package by.macmonitor.preinittestcontainers;

import by.macmonitor.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;

import com.github.dockerjava.api.command.CreateContainerCmd;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Read-side contract for container creation commands, consumed by {@link ContainerFactory},
 * {@link GenericContainerFactory}, and {@link ContainerEndImageNameCalculator}.
 *
 * <p>Immutable configuration DTO so factories, end-image calculators, and tests can consume
 * container settings without Lombok builders or Testcontainers types. Module-specific commands (for
 * example {@code CreateGenericContainerCommand}, {@code CreateJdbcContainerCommand}) extend the
 * same surface so shared factory logic works across databases and generic images.
 */
public interface CreateContainerCommand {
    PreInitStartCallback getAfterPreInitStartCallback();

    String getBaseImageName();

    List<ClasspathResourceMappingCommand> getClasspathResourceMappings();

    List<String> getCmdParameters();

    Map<Transferable, String> getCopyToTransferableContainerPathMap();

    List<Consumer<CreateContainerCmd>> getCreateContainerCmdModifiers();

    String getEndImageName();

    Map<String, String> getEnvironmentVariables();

    default Map<String, String> getEnvMap() {
        return getEnvironmentVariables();
    }

    List<Integer> getExposedPorts();

    List<String> getExtraHosts();

    List<FileSystemBindCommand> getFileSystemBinds();

    ImageCreationLockOption getImageCreationLockOption();

    ImagePullPolicy getImagePullPolicy();

    Map<String, String> getLabels();

    List<Consumer<OutputFrame>> getLogConsumers();

    ContainerMetadata getMetadata();

    Duration getMinimumRunningDuration();

    Network getNetwork();

    List<String> getNetworkAliases();

    String getNetworkMode();

    List<String> getPortBindings();

    Boolean getPrivilegedMode();

    Long getSharedMemorySize();

    Integer getStartupAttempts();

    StartupCheckStrategy getStartupCheckStrategy();

    Duration getStartupTimeout();

    List<TmpFsSystemCommand> getTmpFsFilesystems();

    WaitStrategy getWaitStrategy();

    String getWorkingDirectory();

    boolean isAccessToHost();

    boolean isImageCreationLock();

    boolean isPreInitialized();

    boolean isReusable();

    default boolean isReuse() {
        return isReusable();
    }
}
