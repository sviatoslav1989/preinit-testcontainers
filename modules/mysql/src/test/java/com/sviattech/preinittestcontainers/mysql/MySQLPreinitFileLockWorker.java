package com.sviattech.preinittestcontainers.mysql;

import com.sviattech.preinittestcontainers.support.TimedContainerStart;

import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class MySQLPreinitFileLockWorker {

    private static final Duration GO_SIGNAL_TIMEOUT = Duration.ofMinutes(5);

    private static final Duration GO_SIGNAL_POLL_INTERVAL = Duration.ofMillis(100);

    private MySQLPreinitFileLockWorker() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: MySQLPreinitFileLockWorker <workDir> <workerIndex>");
            System.exit(1);
        }

        Path workDir = Path.of(args[0]);
        int workerIndex = Integer.parseInt(args[1]);
        Path resultFile = MySQLPreinitFileLockSupport.resultFile(workDir, workerIndex);

        try {
            Files.createFile(MySQLPreinitFileLockSupport.readyMarker(workDir, workerIndex));
            waitForGoSignal(workDir);

            try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(
                    MySQLPreinitFileLockSupport.lockTestCommand())) {
                TimedContainerStart.start(container);
                MySQLPreinitFileLockSupport.assertTablesAndDataCreated(container);
                MySQLPreinitFileLockSupport.writeResult(
                        resultFile,
                        MySQLPreinitFileLockSupport.WorkerResult.success(
                                container.getDockerImageName()));
            }
            System.exit(0);
        } catch (Exception e) {
            try {
                MySQLPreinitFileLockSupport.writeResult(
                        resultFile,
                        MySQLPreinitFileLockSupport.WorkerResult.failure(e.getMessage()));
            } catch (Exception writeError) {
                e.addSuppressed(writeError);
            }
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void waitForGoSignal(Path workDir) throws InterruptedException {
        Path goSignal = MySQLPreinitFileLockSupport.goSignal(workDir);
        long deadlineNanos = System.nanoTime() + GO_SIGNAL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (Files.exists(goSignal)) {
                return;
            }
            Thread.sleep(GO_SIGNAL_POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("Timed out waiting for go signal at " + goSignal);
    }
}
