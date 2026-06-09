package by.macmonitor.preinittestcontainers.postgresql;

import com.github.dockerjava.api.command.LogContainerCmd;

import lombok.SneakyThrows;

import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class PostgreSQLContainerWaitStrategy extends AbstractWaitStrategy {

    private static final String SKIP_INIT_REGEX = ".*Skipping initialization.*";

    private static final String READY_REGEX =
            ".*database system is ready to accept connections.*\\s";

    @Override
    @SneakyThrows(IOException.class)
    protected void waitUntilReady() {
        AtomicBoolean skipInitSeen = new AtomicBoolean(false);
        AtomicInteger readyCount = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(1);

        Consumer<OutputFrame> onFrame = frame -> {
            String line = frame.getUtf8String();
            if (line.matches("(?s)" + SKIP_INIT_REGEX)) {
                skipInitSeen.set(true);
            }
            if (line.matches("(?s)" + READY_REGEX)) {
                int count = readyCount.incrementAndGet();
                if (skipInitSeen.get() ? count >= 1 : count >= 2) {
                    done.countDown();
                }
            }
        };

        LogContainerCmd cmd = waitStrategyTarget
                .getDockerClient()
                .logContainerCmd(waitStrategyTarget.getContainerId())
                .withFollowStream(true)
                .withSince(0)
                .withStdOut(true)
                .withStdErr(true);

        try (FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
            callback.addConsumer(OutputFrame.OutputType.STDOUT, onFrame);
            callback.addConsumer(OutputFrame.OutputType.STDERR, onFrame);
            cmd.exec(callback);

            try {
                if (!done.await(startupTimeout.getSeconds(), TimeUnit.SECONDS)) {
                    throw timeoutException(skipInitSeen.get(), readyCount.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ContainerLaunchException(
                        "Interrupted while waiting for PostgreSQL startup", e);
            }
        }
    }

    private static ContainerLaunchException timeoutException(boolean skipInitSeen, int readyCount) {
        return new ContainerLaunchException("Timed out waiting for PostgreSQL startup (skipInit="
                + skipInitSeen + ", readyCount=" + readyCount + ")");
    }
}
