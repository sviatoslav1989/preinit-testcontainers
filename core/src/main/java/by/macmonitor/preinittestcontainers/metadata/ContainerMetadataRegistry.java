package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import java.util.Optional;

/**
 * Resolves bundled {@link ContainerMetadata} for a Docker image name.
 *
 * <p>The default {@link FileBasedContainerMetadataRegistry} loads
 * {@code preinit-testcontainers/metadata/{repo-last-segment}.metadata} from the classpath.
 */
@FunctionalInterface
public interface ContainerMetadataRegistry {
    Optional<ContainerMetadata> find(String imageName);
}
