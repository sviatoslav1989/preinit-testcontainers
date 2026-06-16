package com.sviattech.preinittestcontainers.metadata;

import com.sviattech.preinittestcontainers.ContainerMetadata;

/**
 * Fallback metadata source when no bundled entry exists for a base image (custom or unsupported
 * tags).
 *
 * <p>Inspects the live Docker image so pre-initialization can still snapshot entrypoint, command,
 * and volumes correctly. Separated from {@link ContainerMetadataRegistry} so Docker I/O stays
 * behind one replaceable boundary (mock in unit tests, alternate client in constrained
 * environments). Default implementation: {@link DefaultDockerImageMetadataInspector}.
 */
public interface DockerImageMetadataInspector {

    /**
     * Returns non-empty {@link ContainerMetadata} for a pullable image.
     *
     * <p>Used when {@link ContainerMetadataRegistry#find(String)} returns empty.
     */
    ContainerMetadata inspect(String imageName);
}
