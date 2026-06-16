package com.sviattech.preinittestcontainers.postgresql;

import com.sviattech.preinittestcontainers.JdbcContainerFactory;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

/**
 * {@link com.sviattech.preinittestcontainers.ContainerFactory} for PostgreSQL via
 * {@link org.testcontainers.postgresql.PostgreSQLContainer}.
 */
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PostgreSQLContainerFactory
        extends JdbcContainerFactory<CreatePostgreSQLContainerCommand, PostgreSQLContainer<?>> {

    private static final PostgreSQLContainerFactory INSTANCE = new PostgreSQLContainerFactory();

    @Builder.Default
    private final Function<DockerImageName, PostgreSQLContainer<?>> containerSupplier =
            PostgreSQLContainer::new;

    @Override
    protected Function<DockerImageName, PostgreSQLContainer<?>> resolveContainerSupplier() {
        return containerSupplier;
    }

    public static PostgreSQLContainer<?> createPostgreSQLContainer(
            CreatePostgreSQLContainerCommand command) {
        return INSTANCE.create(command);
    }
}
