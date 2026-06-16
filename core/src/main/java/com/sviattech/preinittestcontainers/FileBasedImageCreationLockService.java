package com.sviattech.preinittestcontainers;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.function.Consumer;

/**
 * Cross-process file lock under {@code ${java.io.tmpdir}/.testcontainers-locks}, keyed by MD5 of
 * the target image name.
 *
 * <p>Default {@link ImageCreationLockService} used by {@link GenericContainerFactory}. Stale lock
 * files older than {@link ImageCreationLockOption#getLockStaleTimeout()} are removed before
 * acquisition; acquisition fails after {@link ImageCreationLockOption#getLockAcquireTimeout()}.
 */
@Slf4j
public class FileBasedImageCreationLockService implements ImageCreationLockService {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    @Override
    @SneakyThrows
    public void withLock(
            String imageName, ImageCreationLockOption lockOption, Consumer<String> action) {
        long lockAcquireTimeoutMs = lockOption.getLockAcquireTimeout().toMillis();
        long lockStaleTimeoutMs = lockOption.getLockStaleTimeout().toMillis();
        Path lockDir = Paths.get(System.getProperty("java.io.tmpdir"), ".testcontainers-locks");
        Files.createDirectories(lockDir);

        String lockFileName = formatHex(MessageDigest.getInstance("MD5")
                        .digest(imageName.getBytes(StandardCharsets.UTF_8)))
                + ".lock";
        Path lockFilePath = lockDir.resolve(lockFileName);

        removeStaleLockFileIfNecessary(lockFilePath, lockStaleTimeoutMs);

        try (RandomAccessFile file = new RandomAccessFile(lockFilePath.toFile(), "rw");
                FileChannel channel = file.getChannel()) {
            long startTime = System.currentTimeMillis();
            FileLock lock;

            do {
                lock = channel.tryLock();
                if (lock != null) {
                    break;
                }

                if (System.currentTimeMillis() - startTime > lockAcquireTimeoutMs) {
                    throw new IllegalStateException(
                            "Failed to acquire lock for image creation after "
                                    + lockAcquireTimeoutMs + "ms. "
                                    + "Another process may be building the image: " + lockFilePath);
                }

                Thread.sleep(100);
            } while (true);

            try {
                file.setLength(0);
                file.writeLong(System.currentTimeMillis());
                action.accept(imageName);
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private static String formatHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_DIGITS[v >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(chars);
    }

    private static void removeStaleLockFileIfNecessary(Path lockFilePath, long lockStaleTimeoutMs) {
        if (!Files.exists(lockFilePath)) {
            return;
        }

        boolean stale = false;
        try (RandomAccessFile file = new RandomAccessFile(lockFilePath.toFile(), "r")) {
            if (file.length() > 0) {
                file.seek(0);
                long lockTimestampMs = file.readLong();
                long fileAge = System.currentTimeMillis() - lockTimestampMs;
                if (fileAge > lockStaleTimeoutMs) {
                    log.warn(
                            "Stale lock file detected (age: {}ms), deleting: {}",
                            fileAge,
                            lockFilePath);
                    stale = true;
                }
            } else {
                BasicFileAttributes attrs =
                        Files.readAttributes(lockFilePath, BasicFileAttributes.class);
                long creationMillis = attrs.creationTime().toMillis();
                long fileAge = System.currentTimeMillis() - creationMillis;
                if (fileAge > lockStaleTimeoutMs) {
                    log.warn(
                            "Stale empty lock file detected (file age from creation time: {}ms), deleting: {}",
                            fileAge,
                            lockFilePath);
                    stale = true;
                }
            }
        } catch (IOException e) {
            log.warn("Could not inspect lock file {}: {}", lockFilePath, e.getMessage());
            return;
        }

        if (stale) {
            try {
                Files.delete(lockFilePath);
            } catch (IOException e) {
                log.warn(
                        "Could not delete stale lock file {} (another process may hold it): {}",
                        lockFilePath,
                        e.getMessage());
            }
        }
    }
}
