package com.sviattech.preinittestcontainers;

import com.sviattech.preinittestcontainers.support.ColonSeparatedArgvUtils;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Upstream (base image) invocation metadata for a {@link CreateGenericContainerCommand} (defaults
 * on leaf commands such as {@code CreateJdbcContainerCommand} defaults).
 *
 * <p>{@link #entrypointPath} locates the upstream ENTRYPOINT executable for wrapper script install
 * paths. {@link #entrypoint} is passed to the wrapper via {@code TCE_UPSTREAM_ENTRYPOINT} using
 * {@link ColonSeparatedArgvUtils} (colon-separated, backslash-escaped tokens). {@link #cmd} is
 * applied as Docker {@code CMD} at container create time for pre-initialized flows — not via
 * {@code TCE_UPSTREAM_*} env. Neither field is the wrapper script
 * ({@code testcontainer-entrypoint.sh}) installed by this library.
 */
@Getter
@Builder(setterPrefix = "with")
public class ContainerMetadata {

    /**
     * Image {@code Config.Cmd} as reported by {@code docker inspect}; merged into Docker
     * {@code CMD} at create time when {@link CreateGenericContainerCommand#isPreInitialized()} is
     * {@code true}. {@code null} when the image has no default CMD (e.g. ClickHouse).
     */
    private final String[] cmd;

    /**
     * Image {@code Config.Entrypoint} as reported by {@code docker inspect} (raw argv, including
     * relative names such as {@code docker-entrypoint.sh}).
     */
    private final String[] entrypoint;

    /** Full path to the upstream ENTRYPOINT executable inside the container. */
    private final String entrypointPath;

    /** Container data-directory paths used for tmpfs in pre-initialized flows. */
    private final String[] volumes;

    /**
     * Canonical default tmpfs list for {@link #volumes}: {@code rw} with {@code needPersist=true}
     * per path. Empty when {@link #volumes} is null or empty.
     */
    public List<TmpFsSystemCommand> getTmpFs() {
        if (volumes == null || volumes.length == 0) {
            return Collections.emptyList();
        }
        List<TmpFsSystemCommand> result = new ArrayList<>(volumes.length);
        for (String volume : volumes) {
            result.add(TmpFsSystemCommand.builder()
                    .withMountPath(volume)
                    .withOptions("rw")
                    .withNeedPersist(true)
                    .build());
        }
        return Collections.unmodifiableList(result);
    }
}
