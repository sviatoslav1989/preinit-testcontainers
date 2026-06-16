package com.sviattech.preinittestcontainers.mysql;

import com.sviattech.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** {@link com.sviattech.preinittestcontainers.CreateContainerCommand} for MySQL containers. */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateMySQLContainerCommand extends CreateJdbcContainerCommand {}
