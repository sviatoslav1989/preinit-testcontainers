package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

public interface DockerImageMetadataInspector {
    ContainerMetadata inspect(String imageName);
}
