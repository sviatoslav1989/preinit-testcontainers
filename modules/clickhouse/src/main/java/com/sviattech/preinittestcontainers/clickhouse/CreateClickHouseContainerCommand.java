package com.sviattech.preinittestcontainers.clickhouse;

import com.sviattech.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** {@link com.sviattech.preinittestcontainers.CreateContainerCommand} for ClickHouse containers. */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateClickHouseContainerCommand extends CreateJdbcContainerCommand {}
