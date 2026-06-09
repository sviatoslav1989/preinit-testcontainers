package by.macmonitor.preinittestcontainers;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * Timeouts for the cross-process file lock used during pre-initialized image builds
 * ({@link ImageCreationLockService#withLock}).
 */
@Getter
@Builder(toBuilder = true, setterPrefix = "with")
public class ImageCreationLockOption {

    public static final Duration DEFAULT_LOCK_ACQUIRE_TIMEOUT = Duration.ofMillis(90_000);

    public static final Duration DEFAULT_LOCK_STALE_TIMEOUT = Duration.ofMillis(150_000);

    /** Maximum time to wait to obtain the file lock before failing. */
    @Builder.Default
    private final Duration lockAcquireTimeout = DEFAULT_LOCK_ACQUIRE_TIMEOUT;

    /** Age after which an existing lock file is treated as stale and deleted before retrying. */
    @Builder.Default
    private final Duration lockStaleTimeout = DEFAULT_LOCK_STALE_TIMEOUT;

    public static ImageCreationLockOption defaults() {
        return builder().build();
    }
}
