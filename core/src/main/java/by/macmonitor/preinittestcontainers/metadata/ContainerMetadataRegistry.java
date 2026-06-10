package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;
import by.macmonitor.preinittestcontainers.GenericContainerFactory;

import java.util.Optional;

/**
 * Resolves bundled {@link ContainerMetadata} for a Docker image name.
 *
 * <p>Pre-initialized image builds need the upstream image {@code ENTRYPOINT}, {@code CMD}, and
 * {@code VOLUMES} to wrap the custom entrypoint script. Bundled {@code .metadata} files are faster
 * and more reliable than Docker inspect for supported images. The interface allows swapping lookup
 * (classpath files today, a custom registry in tests) without changing factory logic.
 * {@link GenericContainerFactory} consults this registry first in {@code resolveMetadata} and only
 * falls back to {@link DockerImageMetadataInspector} when {@link #find(String)} returns empty.
 *
 * <p>The default {@link FileBasedContainerMetadataRegistry} loads
 * {@code preinit-testcontainers/metadata/{repo-last-segment}.metadata} from the classpath.
 */
@FunctionalInterface
public interface ContainerMetadataRegistry {
    Optional<ContainerMetadata> find(String imageName);
}
