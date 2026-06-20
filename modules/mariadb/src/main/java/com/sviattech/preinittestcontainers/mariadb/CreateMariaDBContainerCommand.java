package com.sviattech.preinittestcontainers.mariadb;

import com.sviattech.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** {@link com.sviattech.preinittestcontainers.CreateContainerCommand} for MariaDB containers. */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateMariaDBContainerCommand extends CreateJdbcContainerCommand {}
