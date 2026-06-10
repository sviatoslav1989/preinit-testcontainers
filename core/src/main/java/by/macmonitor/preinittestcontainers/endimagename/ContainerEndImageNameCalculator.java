package by.macmonitor.preinittestcontainers.endimagename;

import by.macmonitor.preinittestcontainers.CreateContainerCommand;
import by.macmonitor.preinittestcontainers.GenericContainerFactory;

/**
 * Derives the deterministic local cache name for a pre-initialized image from everything that
 * affects image contents (mappings, environment, callbacks, init scripts, and so on).
 *
 * <p>Without a pluggable calculator, module-specific inputs (JDBC credentials, Redis password, DB
 * name prefix) could not influence the hash correctly.
 * {@link GenericContainerEndImageNameCalculator} is the default; modules override via
 * {@link GenericContainerFactory#resolveEndImageNameCalculator()} (for example
 * {@code JdbcEndImageNameCalculator}, {@code RedisEndImageNameCalculator}).
 */
public interface ContainerEndImageNameCalculator<T extends CreateContainerCommand> {

    /** Returns the {@code repository:tag} used as the committed pre-initialized image name. */
    String calculate(T command);
}
