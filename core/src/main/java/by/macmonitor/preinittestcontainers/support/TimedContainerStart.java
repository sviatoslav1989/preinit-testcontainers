package by.macmonitor.preinittestcontainers.support;

import lombok.extern.slf4j.Slf4j;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startable;

import java.time.Duration;

@Slf4j
public final class TimedContainerStart {

    private TimedContainerStart() {}

    public static void start(GenericContainer<?> container) {
        start(container, container.getDockerImageName());
    }

    public static void start(Startable container, String description) {
        long startNanos = System.nanoTime();
        container.start();
        log.info(
                "Container started in {} ms ({})",
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                description);
    }
}
