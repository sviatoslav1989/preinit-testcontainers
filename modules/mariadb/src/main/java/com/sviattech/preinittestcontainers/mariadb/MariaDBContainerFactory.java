package com.sviattech.preinittestcontainers.mariadb;

import com.sviattech.preinittestcontainers.JdbcContainerFactory;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

/**
 * {@link com.sviattech.preinittestcontainers.ContainerFactory} for MariaDB via
 * {@link org.testcontainers.mariadb.MariaDBContainer}.
 */
@SuperBuilder(toBuilder = true, setterPrefix = "with")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MariaDBContainerFactory
        extends JdbcContainerFactory<CreateMariaDBContainerCommand, MariaDBContainer> {

    private static final MariaDBContainerFactory INSTANCE = new MariaDBContainerFactory();

    @Builder.Default
    private final Function<DockerImageName, MariaDBContainer> containerSupplier =
            MariaDBContainer::new;

    @Override
    protected Function<DockerImageName, MariaDBContainer> resolveContainerSupplier() {
        return containerSupplier;
    }

    public static MariaDBContainer createMariaDBContainer(CreateMariaDBContainerCommand command) {
        return INSTANCE.create(command);
    }
}
