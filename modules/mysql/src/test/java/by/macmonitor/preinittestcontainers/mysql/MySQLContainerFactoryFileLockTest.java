package by.macmonitor.preinittestcontainers.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

class MySQLContainerFactoryFileLockTest {

    private static final int WORKER_COUNT = 4;

    private static final Duration TEST_TIMEOUT = Duration.ofMinutes(15);

    private static final Duration READY_BARRIER_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration READY_POLL_INTERVAL = Duration.ofMillis(100);

    @Test
    void fourJvms_coordinateImageBuildWithFileLock() throws Exception {
        Assertions.assertTimeout(TEST_TIMEOUT, () -> runFileLockTest());
    }

    private void runFileLockTest() throws Exception {
        Path workDir = Files.createTempDirectory("mysql-file-lock-");
        Path sharedTmpDir = workDir.resolve("tmp");
        Files.createDirectories(sharedTmpDir);

        String expectedEndImageName = MySQLPreinitFileLockSupport.expectedEndImageName();
        removeImageIfPresent(expectedEndImageName);

        List<Process> workers = new ArrayList<>();
        try {
            for (int workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
                workers.add(startWorker(workDir, sharedTmpDir, workerIndex));
            }

            waitForReadyMarkers(workDir);
            Files.createFile(MySQLPreinitFileLockSupport.goSignal(workDir));

            for (Process worker : workers) {
                boolean finished = worker.waitFor(TEST_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
                assertThat(finished).as("worker process timed out").isTrue();
                assertThat(worker.exitValue())
                        .as("worker process exit code for worker logs in %s", workDir)
                        .isZero();
            }

            List<MySQLPreinitFileLockSupport.WorkerResult> results = new ArrayList<>();
            for (int workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
                MySQLPreinitFileLockSupport.WorkerResult result =
                        MySQLPreinitFileLockSupport.readResult(
                                MySQLPreinitFileLockSupport.resultFile(workDir, workerIndex));
                assertThat(result.success())
                        .as("worker %d result in %s", workerIndex, workDir)
                        .isTrue();
                assertThat(result.endImageName()).isEqualTo(expectedEndImageName);
                results.add(result);
            }
            assertThat(results.stream()
                            .map(MySQLPreinitFileLockSupport.WorkerResult::endImageName)
                            .distinct())
                    .containsExactly(expectedEndImageName);

            List<Path> workerLogs = IntStream.range(0, WORKER_COUNT)
                    .mapToObj(index -> MySQLPreinitFileLockSupport.workerLogFile(workDir, index))
                    .toList();
            assertThat(MySQLPreinitFileLockSupport.countTryToBuildLogs(
                            workerLogs, expectedEndImageName))
                    .as("exactly one worker should build the preinit image")
                    .isEqualTo(1);
            assertThat(MySQLPreinitFileLockSupport.countLogLinesContaining(
                            workerLogs, MySQLPreinitFileLockSupport.LOG_ALREADY_EXISTS))
                    .as("three workers should skip build because image already exists")
                    .isEqualTo(3);
            assertThat(MySQLPreinitFileLockSupport.countSuccessfulBuildLogs(
                            workerLogs, expectedEndImageName))
                    .as("exactly one worker should log successful image build")
                    .isEqualTo(1);

            DockerClient dockerClient = DockerClientFactory.lazyClient();
            dockerClient.inspectImageCmd(expectedEndImageName).exec();
        } finally {
            destroyWorkers(workers);
            removeImageIfPresent(expectedEndImageName);
            deleteRecursively(workDir);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + p, e);
                }
            });
        }
    }

    private static void destroyWorkers(List<Process> workers) {
        for (Process worker : workers) {
            if (worker.isAlive()) {
                worker.destroyForcibly();
            }
        }
    }

    private static void removeImageIfPresent(String imageName) {
        DockerClient dockerClient = DockerClientFactory.lazyClient();
        try {
            dockerClient.removeImageCmd(imageName).exec();
        } catch (NotFoundException ignored) {
            // image already absent
        }
    }

    private static Process startWorker(Path workDir, Path sharedTmpDir, int workerIndex)
            throws IOException {
        Path logFile = MySQLPreinitFileLockSupport.workerLogFile(workDir, workerIndex);
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + sharedTmpDir.toAbsolutePath(),
                "-Dorg.slf4j.simpleLogger.defaultLogLevel=info",
                "-cp",
                System.getProperty("java.class.path"),
                MySQLPreinitFileLockWorker.class.getName(),
                workDir.toAbsolutePath().toString(),
                Integer.toString(workerIndex));
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());
        return processBuilder.start();
    }

    private static void waitForReadyMarkers(Path workDir) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + READY_BARRIER_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            boolean allReady = IntStream.range(0, WORKER_COUNT).allMatch(index -> {
                return Files.exists(MySQLPreinitFileLockSupport.readyMarker(workDir, index));
            });
            if (allReady) {
                return;
            }
            Thread.sleep(READY_POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("Timed out waiting for worker ready markers in " + workDir);
    }
}
