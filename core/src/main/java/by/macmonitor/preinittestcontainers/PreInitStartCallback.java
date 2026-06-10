package by.macmonitor.preinittestcontainers;

import org.testcontainers.containers.GenericContainer;

import java.util.function.Consumer;

/**
 * One-shot setup that runs after the temporary container starts during pre-initialized image build
 * (see {@link GenericContainerFactory}), not at test runtime.
 *
 * <p>Needed when image contents depend on in-container work (for example applying init scripts or
 * warming data) that cannot be expressed purely via classpath copies or environment variables.
 * {@link #uniqueKey()} must be stable because it is folded into the end-image name hash (via
 * {@link by.macmonitor.preinittestcontainers.endimagename.GenericContainerEndImageNameCalculator})
 * so different callbacks produce different cached images.
 */
public interface PreInitStartCallback {

    /** Runs once after the temp container starts during pre-initialized image build. */
    void run(GenericContainer<?> container);

    /**
     * Stable value included in the pre-initialized end image name hash when this callback is set.
     *
     * <p>Must distinguish callbacks that change image contents; identical keys for different
     * {@link #run(GenericContainer)} behavior would incorrectly share a cached image.
     */
    String uniqueKey();

    /**
     * Delegates to {@link SimplePreInitStartCallback}.
     *
     * <p>{@code uniqueKey} participates in end-image name hashing; choose a value that reflects
     * everything {@code run} does that affects the committed image.
     */
    static PreInitStartCallback of(String uniqueKey, Consumer<GenericContainer<?>> run) {
        return new SimplePreInitStartCallback(uniqueKey, run);
    }
}
