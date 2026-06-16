package com.sviattech.preinittestcontainers;

import lombok.Builder;
import lombok.Getter;

/**
 * Tmpfs mount for a container. Persisted mount paths may contain {@code :} or {@code \}; they are
 * escaped when written to {@code TCE_LIVE_DATA_PATHS} / {@code TCE_SNAPSHOT_TEMP_PATHS}.
 */
@Getter
@Builder(toBuilder = true, setterPrefix = "with")
public class TmpFsSystemCommand {

    private final String mountPath;

    private final boolean needPersist;

    private final String options;
}
