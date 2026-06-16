package com.sviattech.preinittestcontainers;

import org.testcontainers.containers.Container;

/**
 * Materializes a Testcontainers {@link Container} from an immutable {@link CreateContainerCommand}.
 *
 * <p>Separates <em>what</em> to run (the command) from <em>how</em> to build and configure the
 * container. {@link GenericContainerFactory} and module-specific factories (for example JDBC,
 * Redis) implement the same contract so callers depend on a single {@code create(command)} entry
 * point without coupling to pre-initialization internals.
 */
public interface ContainerFactory<C extends CreateContainerCommand, T extends Container<?>> {

    /**
     * Creates a configured container for the given command.
     *
     * <p>The returned container is ready to use but is not necessarily started; callers or
     * Testcontainers lifecycle hooks start it when needed.
     */
    T create(C command);
}
