package by.macmonitor.preinittestcontainers.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import com.github.dockerjava.api.command.InspectContainerResponse;

import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerStatus;

import java.time.Duration;

/**
 * Asserts that a misconfigured one-shot container fails, covering both outcomes Testcontainers may
 * produce for short-lived containers (~100&nbsp;ms).
 *
 * <p>{@code assertThatThrownBy(container::start)} is unreliable here: Testcontainers 2.x
 * {@code IsRunningStartupCheckStrategy} can treat the first post-start inspect with
 * {@code Running=true} as success, so {@code start()} returns normally even though the command
 * failed moments later.
 *
 * <p>This helper accepts either a {@link ContainerLaunchException} from {@code start()}, or a
 * successful {@code start()} followed by a stopped container with a non-zero exit code (and
 * optionally matching logs). Prefer it over {@code assertThatThrownBy(container::start)} for Docker
 * integration tests that expect runtime entrypoint/cmd failure on containers with no exposed ports.
 */
final class ContainerStartAssertions {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private ContainerStartAssertions() {}

    /**
     * Expects {@link ContainerLaunchException} from {@code start()}, or a non-zero exit after stop.
     */
    static void assertStartFails(GenericContainer<?> container) {
        assertStartFails(container, null);
    }

    /**
     * Same as {@link #assertStartFails(GenericContainer)}, plus on the non-throwing path asserts
     * container logs contain {@code expectedLogSubstring} after exit code is confirmed.
     */
    static void assertStartFails(GenericContainer<?> container, String expectedLogSubstring) {
        Throwable thrown = catchThrowable(container::start);
        if (thrown instanceof ContainerLaunchException) {
            return;
        }
        if (thrown != null) {
            fail(
                    "Expected container start to fail with ContainerLaunchException or non-zero exit, but got: "
                            + thrown,
                    thrown);
        }

        InspectContainerResponse.ContainerState state = waitForContainerStopped(container);
        assertThat(state.getExitCode()).isNotNull();
        assertThat(state.getExitCode()).isNotEqualTo(0);
        assertThat(container.isRunning()).isFalse();

        if (expectedLogSubstring != null) {
            ContainerLogAssertions.assertContains(container, expectedLogSubstring);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for container to stop", e);
        }
    }

    private static InspectContainerResponse.ContainerState waitForContainerStopped(
            GenericContainer<?> container) {
        long deadlineNanos = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        InspectContainerResponse.ContainerState state = null;
        while (System.nanoTime() < deadlineNanos) {
            state = container
                    .getDockerClient()
                    .inspectContainerCmd(container.getContainerId())
                    .exec()
                    .getState();
            if (DockerStatus.isContainerStopped(state)) {
                return state;
            }
            sleep(POLL_INTERVAL);
        }
        assertThat(DockerStatus.isContainerStopped(state))
                .as("container should stop within %s", DEFAULT_TIMEOUT)
                .isTrue();
        return state;
    }
}
