package by.macmonitor.preinittestcontainers;

import org.testcontainers.containers.GenericContainer;

import java.util.function.Consumer;

public interface PreInitStartCallback {
    /** Runs once after the temp container starts during pre-initialized image build. */
    void run(GenericContainer<?> container);

    /** Stable value included in pre-initialized end image name hash when this callback is set. */
    String uniqueKey();

    /** Delegates to {@link SimplePreInitStartCallback}. */
    static PreInitStartCallback of(String uniqueKey, Consumer<GenericContainer<?>> run) {
        return new SimplePreInitStartCallback(uniqueKey, run);
    }
}
