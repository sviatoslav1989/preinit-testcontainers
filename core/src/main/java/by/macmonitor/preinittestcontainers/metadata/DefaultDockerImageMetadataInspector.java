package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;

import lombok.extern.slf4j.Slf4j;

import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LogUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default {@link DockerImageMetadataInspector} that pulls the image when missing and reads
 * {@code ENTRYPOINT}, {@code CMD}, {@code VOLUMES}, and working directory via the Docker API.
 *
 * <p>Resolves relative entrypoint paths against image {@code WORKDIR}. Used when
 * {@link FileBasedContainerMetadataRegistry} has no bundled metadata for the base image. Results
 * are cached per canonical image name for the lifetime of this inspector instance.
 */
@Slf4j
public final class DefaultDockerImageMetadataInspector implements DockerImageMetadataInspector {

    private final DockerClient dockerClient;

    private final ConcurrentMap<String, ContainerMetadata> cache = new ConcurrentHashMap<>();

    public DefaultDockerImageMetadataInspector(DockerClient dockerClient) {
        this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient");
    }

    @Override
    public ContainerMetadata inspect(String imageName) {
        Objects.requireNonNull(imageName, "imageName");
        DockerImageName parsed = DockerImageName.parse(imageName);
        String canonicalImage = parsed.asCanonicalNameString();
        return cache.computeIfAbsent(canonicalImage, key -> inspectUncached(parsed));
    }

    private void ensureImagePresent(DockerImageName imageName) {
        String canonicalImage = imageName.asCanonicalNameString();
        try {
            dockerClient.inspectImageCmd(canonicalImage).exec();
        } catch (NotFoundException e) {
            new RemoteDockerImage(imageName).get();
            dockerClient.inspectImageCmd(canonicalImage).exec();
        }
    }

    private ContainerMetadata inspectUncached(DockerImageName parsed) {
        String canonicalImage = parsed.asCanonicalNameString();
        ensureImagePresent(parsed);

        InspectImageResponse response =
                dockerClient.inspectImageCmd(canonicalImage).exec();
        ContainerConfig config = response.getConfig();

        String[] entrypoint = normalizeArgv(config.getEntrypoint());
        String[] cmd = normalizeArgv(config.getCmd());
        String[] volumes = normalizeVolumes(config.getVolumes());
        String entrypointPath =
                resolveEntrypointPath(canonicalImage, entrypoint, config.getWorkingDir());

        return ContainerMetadata.builder()
                .withEntrypointPath(entrypointPath)
                .withEntrypoint(entrypoint)
                .withCmd(cmd)
                .withVolumes(volumes)
                .build();
    }

    private String readContainerStdout(String containerId) {
        return LogUtils.getOutput(dockerClient, containerId, OutputFrame.OutputType.STDOUT)
                .trim();
    }

    private String resolveEntrypointPath(
            String canonicalImage, String[] entrypoint, String workingDir) {
        if (entrypoint == null || entrypoint.length == 0) {
            return null;
        }
        String executable = entrypoint[0];
        if (executable.startsWith("/")) {
            return executable;
        }
        if (executable.contains("/")) {
            return resolveRelativeEntrypointPath(workingDir, executable);
        }
        return resolveEntrypointPathViaRuntime(canonicalImage, executable);
    }

    private String resolveEntrypointPathViaRuntime(String canonicalImage, String entrypointName) {
        String containerId = null;
        try {
            containerId = dockerClient
                    .createContainerCmd(canonicalImage)
                    .withEntrypoint("/bin/sh", "-c")
                    .withCmd("command -v -- '" + shellQuote(entrypointName) + "'")
                    .exec()
                    .getId();
            dockerClient.startContainerCmd(containerId).exec();
            waitForContainerExit(containerId);
            String stdout = readContainerStdout(containerId);
            if (stdout.isEmpty()) {
                return null;
            }
            return stdout;
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception e) {
                    log.warn(
                            "Could not remove ephemeral container {}: {}",
                            containerId,
                            e.getMessage());
                }
            }
        }
    }

    private void waitForContainerExit(String containerId) {
        while (true) {
            Boolean running = dockerClient
                    .inspectContainerCmd(containerId)
                    .exec()
                    .getState()
                    .getRunning();
            if (running == null || !running) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for container exit", e);
            }
        }
    }

    private static String[] normalizeArgv(String[] argv) {
        if (argv == null || argv.length == 0) {
            return null;
        }
        return Arrays.copyOf(argv, argv.length);
    }

    private static String normalizePosixPath(String path) {
        boolean absolute = path.startsWith("/");
        String[] segments = path.split("/", -1);
        List<String> resolved = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
                continue;
            }
            resolved.add(segment);
        }
        String joined = String.join("/", resolved);
        return absolute ? "/" + joined : joined;
    }

    private static String[] normalizeVolumes(Map<String, ?> volumes) {
        if (volumes == null || volumes.isEmpty()) {
            return null;
        }
        return new ArrayList<>(volumes.keySet()).toArray(new String[0]);
    }

    /**
     * Resolves exec-form ENTRYPOINT paths relative to image {@code WORKDIR} (defaults to {@code /}
     * when unset).
     */
    private static String resolveRelativeEntrypointPath(
            String workingDir, String relativeExecutable) {
        String base = (workingDir == null || workingDir.trim().isEmpty()) ? "/" : workingDir.trim();
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        String combined =
                base.endsWith("/") ? base + relativeExecutable : base + "/" + relativeExecutable;
        return normalizePosixPath(combined);
    }

    private static String shellQuote(String value) {
        return value.replace("'", "'\\''");
    }
}
