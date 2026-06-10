package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.testcontainers.containers.Container;

import java.time.Duration;

/** Polls Docker logs for short-lived containers whose output may lag behind {@code start()}. */
final class ContainerLogAssertions {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private ContainerLogAssertions() {}

    static void assertContains(Container container, String expected) {
        assertContains(container, expected, DEFAULT_TIMEOUT);
    }

    static void assertContains(Container container, String expected, Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        String logs = "";
        while (System.nanoTime() < deadlineNanos) {
            logs = container.getLogs();
            if (logs.contains(expected)) {
                return;
            }
            sleep(POLL_INTERVAL);
        }
        assertThat(logs).contains(expected);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for container logs", e);
        }
    }
}
