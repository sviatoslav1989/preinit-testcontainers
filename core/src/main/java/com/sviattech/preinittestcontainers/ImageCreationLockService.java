package com.sviattech.preinittestcontainers;

import java.util.function.Consumer;

/**
 * Coordinates pre-initialized image builds across parallel JVMs (CI shards, Surefire forks).
 *
 * <p>Without coordination, processes can race to build the same end image, duplicating expensive
 * {@code commit} work or interfering with each other. Abstracted so {@link GenericContainerFactory}
 * stays testable: the default is {@link FileBasedImageCreationLockService}, but tests can inject
 * no-op or in-memory locks. Gated by {@link CreateContainerCommand#isImageCreationLock()} and
 * {@link ImageCreationLockOption} timeouts.
 */
public interface ImageCreationLockService {

    /**
     * Runs {@code action} while holding a lock keyed by the target image name.
     *
     * <p>The lock serializes builds for the same {@code imageName} across processes; {@code action}
     * typically performs the Docker commit that produces that image.
     */
    void withLock(String imageName, ImageCreationLockOption lockOption, Consumer<String> action);
}
