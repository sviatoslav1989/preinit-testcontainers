package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import lombok.Getter;

/** One inclusive {@link ImageVersion} range and its {@link ContainerMetadata}. */
@Getter
public final class VersionedContainerMetadata {

    private final ImageVersion startVersion;

    private final ImageVersion endVersion;

    private final ContainerMetadata metadata;

    public VersionedContainerMetadata(
            ImageVersion startVersion, ImageVersion endVersion, ContainerMetadata metadata) {
        this.startVersion = startVersion;
        this.endVersion = endVersion;
        this.metadata = metadata;
    }
}
