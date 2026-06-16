package com.sviattech.preinittestcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.sviattech.preinittestcontainers.endimagename.ContainerEndImageNameCalculator;
import com.sviattech.preinittestcontainers.endimagename.GenericContainerEndImageNameCalculator;
import com.sviattech.preinittestcontainers.metadata.ContainerMetadataRegistry;
import com.sviattech.preinittestcontainers.metadata.DefaultDockerImageMetadataInspector;
import com.sviattech.preinittestcontainers.metadata.DockerImageMetadataInspector;
import com.sviattech.preinittestcontainers.metadata.FileBasedContainerMetadataRegistry;
import com.sviattech.preinittestcontainers.support.ColonSeparatedArgvUtils;
import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ContainerFactory} implementation that wires pre-initialized image build (metadata
 * resolution, end-image naming, locking, and optional {@link PreInitStartCallback}) with standard
 * Testcontainers configuration.
 *
 * <p>Module factories extend this class to supply container suppliers and specialized
 * {@link ContainerEndImageNameCalculator} instances. Use
 * {@link #createGenericContainer(CreateGenericContainerCommand)} for generic images.
 */
@Slf4j
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GenericContainerFactory<
                C extends CreateGenericContainerCommand, T extends GenericContainer<?>>
        implements ContainerFactory<C, T> {

    /** Classpath resource for the wrapper entrypoint copied into pre-initialized containers. */
    protected static final String ENTRYPOINT_CLASS_PATH = "docker/testcontainer-entrypoint.sh";

    /** In-container path where {@link #ENTRYPOINT_CLASS_PATH} is installed. */
    protected static final String ENTRYPOINT_FILE_NAME = "/testcontainer-entrypoint.sh";

    // Env vars consumed by testcontainer-entrypoint.sh (unit-tested via static
    // builders)
    static final String TCE_PAIR_COUNT = "TCE_PAIR_COUNT";

    static final String TCE_TEMP_MODE = "TCE_TEMP_MODE";

    static final String TCE_LIVE_DATA_PATHS = "TCE_LIVE_DATA_PATHS";

    static final String TCE_SNAPSHOT_TEMP_PATHS = "TCE_SNAPSHOT_TEMP_PATHS";

    static final String TCE_UPSTREAM_ENTRYPOINT = "TCE_UPSTREAM_ENTRYPOINT";

    private static final GenericContainerFactory<CreateGenericContainerCommand, GenericContainer<?>>
            DEFAULT = new GenericContainerFactory<>();

    /** Docker client used for image inspect, commit, and existence checks. Injectable for tests. */
    @Builder.Default
    protected final DockerClient dockerClient = DockerClientFactory.lazyClient();

    /** Lock service coordinating parallel pre-initialized image builds. Injectable for tests. */
    @Builder.Default
    protected final ImageCreationLockService imageCreationLockService =
            new FileBasedImageCreationLockService();

    @Builder.Default
    private final ContainerMetadataRegistry metadataRegistry =
            new FileBasedContainerMetadataRegistry();

    @Builder.Default
    private final DockerImageMetadataInspector dockerImageMetadataInspector =
            new DefaultDockerImageMetadataInspector(DockerClientFactory.lazyClient());

    @SuppressWarnings("unchecked")
    @Builder.Default
    private final Function<DockerImageName, T> containerSupplier = (Function<DockerImageName, T>)
            (Function<DockerImageName, GenericContainer<?>>) GenericContainer::new;

    @SuppressWarnings("unchecked")
    @Builder.Default
    private final ContainerEndImageNameCalculator<C> endImageNameCalculator =
            (ContainerEndImageNameCalculator<C>) GenericContainerEndImageNameCalculator.INSTANCE;

    @Override
    public T create(C command) {
        String baseImage = command.getBaseImageName();
        if (!command.isPreInitialized()) {
            return createNonPreinitialized(command, baseImage);
        }
        return createPreinitialized(command, baseImage);
    }

    /**
     * Applies all {@link CreateContainerCommand} settings to a Testcontainers container.
     *
     * <p>Module factories override this to add database- or image-specific configuration after
     * {@code super.applyCommandProperties(...)}. When {@code imageBuild} is {@code true}, the
     * container is the temporary instance used during pre-initialized image commit (reuse is not
     * applied; JDBC init scripts are applied in {@code JdbcContainerFactory}). When {@code false},
     * the container is returned to the caller for test runtime.
     *
     * @param container target container, already constructed with the resolved image name
     * @param command immutable creation command
     * @param imageBuild {@code true} during {@link #buildPreinitializedImage}, {@code false}
     *     otherwise
     */
    protected void applyCommandProperties(T container, C command, boolean imageBuild) {
        ContainerMetadata metadata = resolveMetadata(command);
        if (!imageBuild) {
            container.withReuse(command.isReusable());
        }
        List<TmpFsSystemCommand> tmpFsFilesystems =
                resolveEffectiveTmpFsFilesystems(command, metadata);
        if (!tmpFsFilesystems.isEmpty()) {
            Map<String, String> tmpFs = new LinkedHashMap<>();
            for (TmpFsSystemCommand filesystem : tmpFsFilesystems) {
                tmpFs.put(filesystem.getMountPath(), filesystem.getOptions());
            }
            container.withTmpFs(tmpFs);
        }
        Map<String, String> environmentVariables = command.getEnvironmentVariables();
        if (!environmentVariables.isEmpty()) {
            container.withEnv(environmentVariables);
        }
        List<Integer> exposedPorts = command.getExposedPorts();
        if (!exposedPorts.isEmpty()) {
            container.withExposedPorts(exposedPorts.toArray(new Integer[0]));
        }
        List<String> portBindings = command.getPortBindings();
        if (portBindings != null && !portBindings.isEmpty()) {
            container.setPortBindings(portBindings);
        }
        Map<String, String> labels = command.getLabels();
        if (!labels.isEmpty()) {
            container.withLabels(labels);
        }
        // network → networkMode → networkAliases → extraHosts (avoid conflicting
        // network + networkMode)
        Network network = command.getNetwork();
        if (network != null) {
            container.withNetwork(network);
        }
        String networkMode = command.getNetworkMode();
        if (networkMode != null && !networkMode.trim().isEmpty()) {
            container.withNetworkMode(networkMode);
        }
        List<String> networkAliases = command.getNetworkAliases();
        if (!networkAliases.isEmpty()) {
            container.withNetworkAliases(networkAliases.toArray(new String[0]));
        }
        List<String> extraHosts = command.getExtraHosts();
        for (String extraHost : extraHosts) {
            int sep = extraHost.indexOf(':');
            if (sep <= 0 || sep == extraHost.length() - 1) {
                throw new IllegalArgumentException(
                        "extraHosts entry must be hostname:ip, got: " + extraHost);
            }
            container.withExtraHost(extraHost.substring(0, sep), extraHost.substring(sep + 1));
        }
        Boolean privilegedMode = command.getPrivilegedMode();
        if (privilegedMode != null) {
            container.withPrivilegedMode(privilegedMode);
        }
        Long sharedMemorySize = command.getSharedMemorySize();
        if (sharedMemorySize != null) {
            container.withSharedMemorySize(sharedMemorySize);
        }
        container.withAccessToHost(command.isAccessToHost());
        String workingDirectory = command.getWorkingDirectory();
        if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
            container.withWorkingDirectory(workingDirectory);
        }
        for (ClasspathResourceMappingCommand mapping : command.getClasspathResourceMappings()) {
            container.withCopyFileToContainer(
                    MountableFile.forClasspathResource(
                            mapping.getClasspathResourcePath(), mapping.getFileMode()),
                    mapping.getContainerPath());
        }
        for (Map.Entry<Transferable, String> copy :
                command.getCopyToTransferableContainerPathMap().entrySet()) {
            container.withCopyToContainer(copy.getKey(), copy.getValue());
        }
        for (FileSystemBindCommand bind : command.getFileSystemBinds()) {
            container.addFileSystemBind(
                    bind.getHostPath(),
                    bind.getContainerPath(),
                    bind.getBindMode(),
                    bind.getSelinuxContext());
        }
        WaitStrategy waitStrategy = command.getWaitStrategy();
        if (waitStrategy != null) {
            container.waitingFor(waitStrategy);
        }
        Duration startupTimeout = command.getStartupTimeout();
        if (startupTimeout != null) {
            container.withStartupTimeout(startupTimeout);
        }
        StartupCheckStrategy startupCheckStrategy = command.getStartupCheckStrategy();
        if (startupCheckStrategy != null) {
            container.withStartupCheckStrategy(startupCheckStrategy);
        }
        Duration minimumRunningDuration = command.getMinimumRunningDuration();
        if (minimumRunningDuration != null) {
            container.withMinimumRunningDuration(minimumRunningDuration);
        }
        Integer startupAttempts = command.getStartupAttempts();
        if (startupAttempts != null) {
            container.withStartupAttempts(startupAttempts);
        }
        ImagePullPolicy imagePullPolicy = command.getImagePullPolicy();
        if (imagePullPolicy != null) {
            container.withImagePullPolicy(imagePullPolicy);
        }
        for (Consumer<OutputFrame> logConsumer : command.getLogConsumers()) {
            container.withLogConsumer(logConsumer);
        }

        List<Consumer<CreateContainerCmd>> createContainerCmdModifiers =
                command.getCreateContainerCmdModifiers();
        if (createContainerCmdModifiers != null) {
            for (Consumer<CreateContainerCmd> modifier : createContainerCmdModifiers) {
                container.withCreateContainerCmdModifier(modifier);
            }
        }
        // must be after createContainerCmdModifiers
        List<String> containerCmd = resolveContainerCmd(command, metadata);
        if (!containerCmd.isEmpty()) {
            container.withCreateContainerCmdModifier(c -> c.withCmd(containerCmd));
        }
    }

    /**
     * Installs the pre-init wrapper entrypoint and persist-related environment variables.
     *
     * <p>Sets {@link #ENTRYPOINT_FILE_NAME} as the container entrypoint and populates {@code TCE_*}
     * env vars consumed by that script (tmpfs live/snapshot paths, upstream entrypoint argv,
     * temp-build mode). Called on the temp container during image commit
     * ({@code tempBuildFlow=true}) and again on the runtime container so the same wrapper runs at
     * test time ({@code tempBuildFlow=false}).
     *
     * @param container container that already has {@link #ENTRYPOINT_CLASS_PATH} copied in
     * @param command creation command
     * @param tempBuildFlow {@code true} while building the committed image, {@code false} at
     *     runtime
     */
    protected void applyPersistEntrypoint(T container, C command, boolean tempBuildFlow) {
        ContainerMetadata metadata = resolveMetadata(command);
        container.withEnv(buildPersistEnv(command, metadata, tempBuildFlow));
        List<String> entrypoint =
                Collections.singletonList(GenericContainerFactory.ENTRYPOINT_FILE_NAME);
        container.withCreateContainerCmdModifier(c -> c.withEntrypoint(entrypoint));
    }

    /**
     * Constructs a new container instance for the given image name.
     *
     * <p>Delegates to {@link #resolveContainerSupplier()}; module factories override the supplier
     * rather than this method.
     *
     * @param imageName Docker image name (base or pre-initialized end image)
     * @return new, not-yet-started container
     */
    protected T newContainer(DockerImageName imageName) {
        return resolveContainerSupplier().apply(imageName);
    }

    /**
     * Supplies the Testcontainers container constructor for this factory.
     *
     * <p>Default is {@link GenericContainer#GenericContainer(DockerImageName)}. Database modules
     * override to return {@code MySQLContainer::new}, {@code PostgreSQLContainer::new}, and so on.
     * Injectable via the Lombok builder for tests.
     *
     * @return function that constructs {@code T} for a resolved image name
     */
    protected Function<DockerImageName, T> resolveContainerSupplier() {
        return containerSupplier;
    }

    /**
     * Resolves tmpfs mounts used for data persistence and entrypoint env wiring.
     *
     * <p>Explicit {@link CreateContainerCommand#getTmpFsFilesystems()} on the command wins;
     * otherwise bundled {@link ContainerMetadata#getTmpFs()} from {@link #resolveMetadata} is used.
     * Only mounts marked {@link TmpFsSystemCommand#isNeedPersist()} contribute to persist env vars.
     *
     * @param command creation command
     * @param metadata resolved upstream image metadata (may be {@code null})
     * @return effective tmpfs list, never {@code null}
     */
    protected List<TmpFsSystemCommand> resolveEffectiveTmpFsFilesystems(
            C command, ContainerMetadata metadata) {
        List<TmpFsSystemCommand> explicit = command.getTmpFsFilesystems();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        if (metadata != null && metadata.getTmpFs() != null) {
            return metadata.getTmpFs();
        }
        return Collections.emptyList();
    }

    /**
     * Calculator used when {@link CreateContainerCommand#getEndImageName()} is unset and
     * {@link CreateContainerCommand#isPreInitialized()} is {@code true}.
     *
     * <p>Default is {@link GenericContainerEndImageNameCalculator#INSTANCE}. Module factories
     * override to include database credentials, Redis password, and other module-specific hash
     * inputs. Injectable via the Lombok builder.
     *
     * @return calculator that derives the committed image {@code repository:tag}
     */
    protected ContainerEndImageNameCalculator<C> resolveEndImageNameCalculator() {
        return endImageNameCalculator;
    }

    /**
     * Resolves upstream image metadata needed to wrap entrypoint/cmd and tmpfs defaults.
     *
     * <p>Order: explicit {@link CreateContainerCommand#getMetadata()} on the command, then
     * {@link ContainerMetadataRegistry#find(String)} for bundled {@code .metadata} files, then
     * {@link DockerImageMetadataInspector#inspect(String)} as a live Docker fallback.
     *
     * @param command creation command (must have non-null
     *     {@link CreateContainerCommand#getBaseImageName()} when metadata is not set on the
     *     command)
     * @return resolved metadata, never {@code null} when a base image is configured
     */
    protected ContainerMetadata resolveMetadata(C command) {
        if (command.getMetadata() != null) {
            return command.getMetadata();
        }
        String base = command.getBaseImageName();
        if (base == null) {
            throw new IllegalArgumentException("baseImageName can't be null");
        }
        return metadataRegistry
                .find(base)
                .orElseGet(() -> dockerImageMetadataInspector.inspect(base));
    }

    Map<String, String> buildPersistEnv(
            C command, ContainerMetadata metadata, boolean tempBuildFlow) {
        Map<String, String> env =
                new LinkedHashMap<>(buildTmpfsPersistEnv(findPersistMountPaths(command, metadata)));
        env.put(TCE_TEMP_MODE, tempBuildFlow ? "1" : "0");
        if (metadata != null) {
            String[] upstreamEntrypoint = metadata.getEntrypoint();
            if (upstreamEntrypoint != null && upstreamEntrypoint.length > 0) {
                env.put(
                        TCE_UPSTREAM_ENTRYPOINT,
                        ColonSeparatedArgvUtils.encode(upstreamEntrypoint));
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(env));
    }

    List<String> resolveContainerCmd(C command, ContainerMetadata metadata) {
        List<String> cmd = new ArrayList<>();
        if (command.isPreInitialized() && metadata != null) {
            String[] metadataCmd = metadata.getCmd();
            if (metadataCmd != null && metadataCmd.length > 0) {
                cmd.addAll(Arrays.asList(metadataCmd));
            }
        }
        cmd.addAll(command.getCmdParameters());
        return Collections.unmodifiableList(new ArrayList<>(cmd));
    }

    /**
     * Builds and commits a pre-initialized Docker image from a short-lived temporary container.
     *
     * <p>Invoked from {@link #createPreinitialized} when the resolved end image is not present
     * locally (optionally under {@link ImageCreationLockService} when
     * {@link CreateContainerCommand#isImageCreationLock()} is enabled). Steps:
     *
     * <ol>
     *   <li>Start a temp container from {@code baseImage} and apply command properties with
     *       {@code imageBuild=true} (see {@link #applyCommandProperties})
     *   <li>Copy {@link #ENTRYPOINT_CLASS_PATH} into the container and call
     *       {@link #applyPersistEntrypoint} with {@code tempBuildFlow=true}
     *   <li>Start the container so tmpfs and env are live, then run {@link PreInitStartCallback} if
     *       configured
     *   <li>{@link #commitAndRelabelPreinitializedImage} to produce {@code finalImageName}
     * </ol>
     *
     * The temp container is always closed in a {@code finally} block; callers use the committed
     * image on subsequent {@code create} calls.
     *
     * @param command creation command driving configuration and optional callback
     * @param baseImage upstream Docker image to build from
     * @param finalImageName committed image name ({@code repository:tag}) to create locally
     */
    private void buildPreinitializedImage(C command, String baseImage, String finalImageName) {
        T tempContainer = newContainer(DockerImageName.parse(baseImage));
        applyCommandProperties(tempContainer, command, true);
        try {
            tempContainer.withCopyToContainer(
                    MountableFile.forClasspathResource(ENTRYPOINT_CLASS_PATH, 0755),
                    ENTRYPOINT_FILE_NAME);
            applyPersistEntrypoint(tempContainer, command, true);
            TimedContainerStart.start(
                    tempContainer,
                    String.format(
                            "pre-initialization (baseImage=%s, targetImage=%s)",
                            baseImage, finalImageName));
            PreInitStartCallback afterPreInitStart = command.getAfterPreInitStartCallback();
            if (afterPreInitStart != null) {
                afterPreInitStart.run(tempContainer);
            }
            commitAndRelabelPreinitializedImage(tempContainer, finalImageName);
        } finally {
            tempContainer.close();
        }
    }

    /**
     * Stops the temp container, commits it with a {@code -tmp} tag, relabels to a
     * session-persistent image, then removes the temp tag.
     */
    private void commitAndRelabelPreinitializedImage(T tempContainer, String finalImageName) {
        DockerImageName parsed = DockerImageName.parse(finalImageName);
        String repository = parsed.getRepository();
        String tag = parsed.getVersionPart();
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected image name in repository:tag form, got: " + finalImageName);
        }
        String tempTag = tag + "-tmp";
        String tempImageName = repository + ":" + tempTag;

        String containerId = tempContainer.getContainerId();
        try {
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient
                    .commitCmd(containerId)
                    .withRepository(repository)
                    .withTag(tempTag)
                    .exec();
            new ImageFromDockerfile(finalImageName, false)
                    .withFileFromString(
                            "Dockerfile",
                            String.format(
                                    "FROM %s\nLABEL %s=permanent\n",
                                    tempImageName,
                                    DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL))
                    .get();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to build preinitialized image: " + finalImageName, e);
        } finally {
            try {
                dockerClient.removeImageCmd(tempImageName).exec();
            } catch (Exception e) {
                log.warn("Could not remove temp image {}: {}", tempImageName, e.getMessage());
            }
        }
    }

    private T createNonPreinitialized(C command, String baseImage) {
        T container = newContainer(DockerImageName.parse(baseImage));
        applyCommandProperties(container, command, false);
        log.info("Local image configured (no pre initialization): {}", baseImage);
        return container;
    }

    private T createPreinitialized(C command, String baseImage) {
        String resolvedEndImageName = command.getEndImageName();
        if (resolvedEndImageName == null) {
            resolvedEndImageName = resolveEndImageNameCalculator().calculate(command);
        }
        final String endImageName = Objects.requireNonNull(
                resolvedEndImageName,
                "endImageName could not be resolved when preInitialized=true");

        if (!isImageExists(endImageName)) {
            new RemoteDockerImage(DockerImageName.parse(baseImage)).get();
            if (!isImageExists(endImageName)) {
                Consumer<String> buildIfAbsent = imageName -> {
                    if (!isImageExists(imageName)) {
                        log.info("Try to build local image: {}", imageName);
                        buildPreinitializedImage(command, baseImage, imageName);
                        log.info("Successful build of local image: {}", imageName);
                    } else {
                        log.info("Image {} already exists (built by another process)", imageName);
                    }
                };
                if (command.isImageCreationLock()) {
                    imageCreationLockService.withLock(
                            endImageName, command.getImageCreationLockOption(), buildIfAbsent);
                } else {
                    buildIfAbsent.accept(endImageName);
                }
            }
        }

        DockerImageName imageName =
                DockerImageName.parse(endImageName).asCompatibleSubstituteFor(baseImage);

        T container = newContainer(imageName);
        applyCommandProperties(container, command, false);
        container.withCopyToContainer(
                MountableFile.forClasspathResource(ENTRYPOINT_CLASS_PATH, 0755),
                ENTRYPOINT_FILE_NAME);
        applyPersistEntrypoint(container, command, false);

        log.info("Local image configured: {}", endImageName);
        return container;
    }

    private List<String> findPersistMountPaths(C command, ContainerMetadata metadata) {
        return resolveEffectiveTmpFsFilesystems(command, metadata).stream()
                .filter(TmpFsSystemCommand::isNeedPersist)
                .map(TmpFsSystemCommand::getMountPath)
                .collect(Collectors.toList());
    }

    private boolean isImageExists(String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    public static GenericContainer<?> createGenericContainer(
            CreateGenericContainerCommand command) {
        return DEFAULT.create(command);
    }

    static Map<String, String> buildTmpfsPersistEnv(List<String> liveMountPaths) {
        Map<String, String> env = new LinkedHashMap<>();
        if (liveMountPaths == null || liveMountPaths.isEmpty()) {
            env.put(TCE_PAIR_COUNT, "0");
            return Collections.unmodifiableMap(new LinkedHashMap<>(env));
        }
        env.put(TCE_PAIR_COUNT, String.valueOf(liveMountPaths.size()));
        env.put(TCE_LIVE_DATA_PATHS, ColonSeparatedArgvUtils.encode(liveMountPaths));
        List<String> snapshotTempPaths = new ArrayList<>();
        for (String path : liveMountPaths) {
            snapshotTempPaths.add(path + "_temp");
        }
        env.put(TCE_SNAPSHOT_TEMP_PATHS, ColonSeparatedArgvUtils.encode(snapshotTempPaths));
        return Collections.unmodifiableMap(new LinkedHashMap<>(env));
    }
}
