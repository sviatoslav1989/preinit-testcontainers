# preinit-testcontainers

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-orange.svg)](build-logic/src/main/groovy/preinit.testcontainers.publishing.gradle)

Faster [Testcontainers](https://java.testcontainers.org/) integration tests by baking initialization into local Docker images.

**Repository:** [github.com/sviatoslav1989/preinit-testcontainers](https://github.com/sviatoslav1989/preinit-testcontainers)

**Contents:** [What and why](#what-and-why) · [How it works](#how-it-works) · [Prerequisites](#prerequisites) · [Installation](#installation) · [Quick start](#quick-start) · [Tips](#tips) · [Performance](#performance) · [Internals](#internals) · [Extension points](#extension-points) · [Examples](#examples)

## What and why

Testcontainers starts fresh containers on every test run. Each cold start pays a high cost: image pull, process boot, and initialization (SQL scripts, migrations, custom setup).

**preinit-testcontainers** addresses initialization by:

- On first use, starting a **temporary** container, running your init (JDBC scripts, [`PreInitStartCallback`](core/src/main/java/by/macmonitor/preinittestcontainers/PreInitStartCallback.java), etc.), then **committing** a local end image with a deterministic name ([hash of config](#end-image-naming)).
- On later starts, using that baked image instead of cold-starting from upstream.
- Using a bundled entrypoint ([`testcontainer-entrypoint.sh`](core/src/main/resources/docker/testcontainer-entrypoint.sh)) with **tmpfs snapshot/restore** so mutable data dirs (e.g. MySQL `/var/lib/mysql`) stay fast while reflecting pre-baked state.
- Using **cross-process file locking** so parallel test workers do not rebuild the same image twice.

**When to use it:**

- Integration tests with heavy DB bootstrap (SQL init scripts, custom server flags).
- Suites where container startup dominates CI time.
- You already use Testcontainers 2.x and Docker locally/CI.

**When not to:** Ephemeral one-off containers with no init cost, or environments without Docker commit support.

## How it works

1. Build a `Create*ContainerCommand` (immutable builder).
2. First run: factory builds/commits the end image (one-time cost).
3. Test run: container starts from the end image + tmpfs restore.
4. Same API as stock Testcontainers (`start()`, JDBC URL, JUnit extension).

Preinit replaces the image entrypoint with [`testcontainer-entrypoint.sh`](core/src/main/resources/docker/testcontainer-entrypoint.sh). To delegate correctly to the upstream image and mount tmpfs on the right data dirs, the factory needs **image metadata** (`ENTRYPOINT`, `CMD`, `VOLUMES`). DB modules ship this automatically; custom images may need `withMetadata(...)` — see [Image metadata](#image-metadata).

```mermaid
sequenceDiagram
    participant Test
    participant Factory as Container factory
    participant Docker

    Test->>Factory: createMySQLContainer(command)

    alt End image not cached
        Factory->>Docker: Start temp container
        Factory->>Docker: Run init scripts
        Factory->>Docker: Commit end image
    else End image cached
        Note over Factory: Skip one-time build
    end

    Factory->>Docker: Start from end image
    Note over Docker: tmpfs restore
    Docker-->>Factory: Container ready
    Factory-->>Test: MySQLContainer
    Test->>Test: Run assertions
```

## Prerequisites

- **Docker** — daemon reachable; image commit supported.
- **Java 8+** — published artifacts target Java 8 bytecode.
- **Testcontainers 2.x** — library targets `[2.0.0, 3.0.0)`; align consumer BOM to `2.0.4+` for Docker 29+.
- **JUnit 5** — typical; not strictly required.

## Installation

Coordinates: `by.macmonitor` / `2.0.0-SNAPSHOT`.

While the version is `-SNAPSHOT`, add the Central snapshots repository. Use **`test`** / **`testImplementation`** scope in normal apps (`src/main` + `src/test`). The [examples](examples/) use `implementation` only because those modules are test-only sample projects.

### Modules

| Artifact | Use when | Extra test dependency |
|----------|----------|----------------------|
| `preinit-testcontainers` | Generic images / custom containers | — |
| `preinit-testcontainers-jdbc` | Custom JDBC-backed modules | your JDBC driver |
| `preinit-testcontainers-mysql` | MySQL (`testcontainers-mysql`) | `com.mysql:mysql-connector-j:9.6.0` |
| `preinit-testcontainers-postgresql` | PostgreSQL | `org.postgresql:postgresql:42.7.5` |
| `preinit-testcontainers-clickhouse` | ClickHouse | `com.clickhouse:clickhouse-jdbc:0.9.8` |
| `preinit-testcontainers-redis` | Redis (`com.redis:testcontainers-redis`) | — |

Each DB module pulls its Testcontainers counterpart transitively via `api`.

### Maven

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-bom</artifactId>
      <version>2.0.4</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Pick one preinit module (see Modules table above) -->
  <dependency>
    <groupId>by.macmonitor</groupId>
    <artifactId>preinit-testcontainers-mysql</artifactId>
    <version>2.0.0-SNAPSHOT</version>
    <scope>test</scope>
  </dependency>
  <!-- JDBC driver for the DB module (test scope) -->
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.6.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

### Gradle

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    testImplementation platform("org.testcontainers:testcontainers-bom:2.0.4")
    // Pick one preinit module (see Modules table above)
    testImplementation "by.macmonitor:preinit-testcontainers-mysql:2.0.0-SNAPSHOT"
    testImplementation "com.mysql:mysql-connector-j:9.6.0"
    testImplementation "org.testcontainers:testcontainers-junit-jupiter"
    testImplementation "org.junit.jupiter:junit-jupiter"
}
```

## Quick start

- `preInitialized` defaults to `true` on commands.
- **First start** may be slow (image build); later starts reuse the end image.
- Init scripts live on the **test classpath** (e.g. `src/test/resources/`).

### MySQL (bundled module)

```java
import by.macmonitor.preinittestcontainers.PreInitStartCallback;
import by.macmonitor.preinittestcontainers.mysql.CreateMySQLContainerCommand;
import by.macmonitor.preinittestcontainers.mysql.MySQLContainerFactory;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

CreateMySQLContainerCommand command = CreateMySQLContainerCommand.builder()
    .withBaseImageName("mysql:8.0.45")
    .withInitScripts(List.of("mysql/init.tables.sql", "mysql/init.data.sql"))
    .withDbName("testdb")
    .withUsername("user")
    .withPassword("password")
    .withAfterPreInitStartCallback(PreInitStartCallback.of(
        "example-callback-v1",
        container -> {
            // Custom init during image build (not at test runtime).
        }))
    .build();

try (MySQLContainer container = MySQLContainerFactory.createMySQLContainer(command)) {
    container.start();
    // container.getJdbcUrl(), etc.
}
```

### Generic container (MongoDB)

Use the core [`preinit-testcontainers`](core/) artifact with [`GenericContainerFactory.createGenericContainer()`](core/src/main/java/by/macmonitor/preinittestcontainers/GenericContainerFactory.java) for images without a bundled module:

```groovy
testImplementation "by.macmonitor:preinit-testcontainers:2.0.0-SNAPSHOT"
testImplementation "org.testcontainers:testcontainers-junit-jupiter"
```

```java
import by.macmonitor.preinittestcontainers.CreateGenericContainerCommand;
import by.macmonitor.preinittestcontainers.GenericContainerFactory;
import by.macmonitor.preinittestcontainers.PreInitStartCallback;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

CreateGenericContainerCommand command = CreateGenericContainerCommand.builder()
    .withBaseImageName("mongo:7.0")
    .withExposedPorts(27017)
    .waitingFor(Wait.forListeningPort())
    .withAfterPreInitStartCallback(PreInitStartCallback.of(
        "mongo-seed-v1",
        container -> {
            container.execInContainer("mongosh", "--eval",
                "db.getSiblingDB('testdb').users.insertOne({name: 'alice'})");
        }))
    .build();

try (GenericContainer<?> container = GenericContainerFactory.createGenericContainer(command)) {
    container.start();
    // container.getHost(), container.getMappedPort(27017)
}
```

- Image metadata for `mongo` is resolved via [docker inspect fallback](#image-metadata) (no bundled `.metadata` file in core).

## Tips

- **Single-test runs:** Preinit is especially helpful when you run one test (e.g. from your IDE) — container startup often dominates runtime, and preinit reduces `start()` time significantly once the end image exists (see [Performance](#performance)).
- **First-run cost:** The initial start builds and commits the end image. Steady-state `start()` times match the [Performance](#performance) "Preinit" rows.
- **Parallel CI:** Cross-process locking via [`ImageCreationLockService`](core/src/main/java/by/macmonitor/preinittestcontainers/ImageCreationLockService.java) is built-in; tune via [`ImageCreationLockOption`](core/src/main/java/by/macmonitor/preinittestcontainers/ImageCreationLockOption.java) if needed (see [Extension points](#extension-points)).
- **Disable pre-init:** `.withPreInitialized(false)` for stock Testcontainers behavior.
- **Spring Boot:** See [examples/spring-boot-mysql](examples/spring-boot-mysql) (BOM import, exclude Testcontainers from `spring-boot-starter-test`).
- **Building from source:** `./gradlew build` (Java 21 toolchain for dev; published JAR is Java 8).

## Performance

Startup time for `container.start()` until ready (measured by `TimedContainerStart`). Workload: **100 tables** with **20 rows per table** (2,000 inserts), or empty DB. Five repetitions per database; **median** reported below. Results from this repo's dev setup (WSL2/Docker); absolute numbers vary — relative speedups are the takeaway.

All scenarios use **tmpfs on the DB data directory**, comparing vanilla Testcontainers (init at startup) vs preinit (pre-baked init + tmpfs restore).

| Scenario | MySQL | PostgreSQL | ClickHouse |
|----------|------:|-----------:|-----------:|
| Vanilla + tmpfs, 100 tables | 13,613 | 3,663 | 14,256 |
| **Preinit + tmpfs, 100 tables** | **1,389** | **551** | **3,403** |
| Vanilla + tmpfs, empty | 8,593 | 1,325 | 5,576 |
| **Preinit + tmpfs, empty** | **1,445** | **451** | **2,388** |

Images: `mysql:8.0.45` (`/var/lib/mysql`), `postgres:17` (`/var/lib/postgresql/data`), `clickhouse/clickhouse-server:26.3.4.11` (`/var/lib/clickhouse`).

**Speedups (preinit vs vanilla + tmpfs):**

- **100 tables:** ~10× MySQL, ~7× PostgreSQL, ~4× ClickHouse.
- **Empty:** ~6× MySQL, ~3× PostgreSQL, ~2× ClickHouse.

Init-heavy workloads show the largest gains; empty DB still benefits from pre-baked image + tmpfs restore.

## Internals

The following sections describe what happens under `create*Container()` — useful for debugging, custom images, and module authors. If you use a bundled DB module with a supported image tag, [Quick start](#quick-start) and [Tips](#tips) are usually enough.

<a id="image-metadata"></a>

### Image metadata (entrypoint & data dirs)

**Why this exists:** Preinit wraps the upstream image entrypoint with [`testcontainer-entrypoint.sh`](core/src/main/resources/docker/testcontainer-entrypoint.sh) and mounts tmpfs on data-directory `VOLUMES` for snapshot/restore (see [How it works](#how-it-works)). The factory needs each image's `ENTRYPOINT`, `CMD`, and `VOLUMES` to wire that correctly. **You can skip this** if you use a bundled DB module and a supported image tag.

This is upstream Docker image invocation data — not Maven or project metadata.

The value type is [`ContainerMetadata`](core/src/main/java/by/macmonitor/preinittestcontainers/ContainerMetadata.java): `entrypoint`, `entrypointPath`, `cmd`, and `volumes`. [`getTmpFs()`](core/src/main/java/by/macmonitor/preinittestcontainers/ContainerMetadata.java) derives default tmpfs mounts from `volumes` (e.g. `/var/lib/mysql` for MySQL).

#### Bundled files

Database modules ship version-ranged properties files on the classpath, e.g. [`mysql.metadata`](modules/mysql/src/main/resources/preinit-testcontainers/metadata/mysql.metadata) at `preinit-testcontainers/metadata/{repo-last-segment}.metadata` (`mysql:8.0.45` → `mysql.metadata`).

```properties
record.0.startVersion=5.5
record.0.endVersion=9.7
record.0.entrypointPath=/usr/local/bin/docker-entrypoint.sh
record.0.entrypoint=docker-entrypoint.sh
record.0.cmd=mysqld
record.0.volumes=/var/lib/mysql
```

#### Resolution order

[`GenericContainerFactory.resolveMetadata`](core/src/main/java/by/macmonitor/preinittestcontainers/GenericContainerFactory.java) picks metadata in this order:

1. Explicit [`withMetadata(...)`](core/src/main/java/by/macmonitor/preinittestcontainers/CreateContainerCommandBuilder.java) on the command
2. [`ContainerMetadataRegistry.find`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/ContainerMetadataRegistry.java) — default [`FileBasedContainerMetadataRegistry`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/FileBasedContainerMetadataRegistry.java) loads bundled `.metadata` files
3. [`DockerImageMetadataInspector.inspect`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/DockerImageMetadataInspector.java) — live `docker inspect` when no bundled file matches

```mermaid
flowchart TD
    cmd["CreateContainerCommand"]
    cmd --> q1{"Explicit metadata?"}
    q1 -->|Yes| meta["ContainerMetadata"]
    q1 -->|No| reg["ContainerMetadataRegistry.find"]
    reg --> q2{"Bundled .metadata?"}
    q2 -->|Yes| meta
    q2 -->|No| inspect["Docker inspect fallback"]
    inspect --> meta
    meta --> wrap["Wrap with testcontainer-entrypoint.sh"]
```

Version matching ([`MetadataFile.resolve`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/MetadataFile.java)): `latest` or empty tag → highest `endVersion` record; in-range tag → matching record; tag newer than max → max record; tag older than min → inspect fallback.

#### Override

For custom or unsupported images without a bundled `.metadata` file, set metadata explicitly via [`CreateContainerCommandBuilder.withMetadata`](core/src/main/java/by/macmonitor/preinittestcontainers/CreateContainerCommandBuilder.java).

### End image naming

The committed local image name is **deterministic** so identical configuration reuses the cache. Any input that changes image contents must affect the hash.

Resolution ([`GenericContainerFactory.createPreinitialized`](core/src/main/java/by/macmonitor/preinittestcontainers/GenericContainerFactory.java)):

1. Explicit [`withEndImageName(...)`](core/src/main/java/by/macmonitor/preinittestcontainers/CreateContainerCommandBuilder.java) wins
2. Otherwise [`ContainerEndImageNameCalculator.calculate`](core/src/main/java/by/macmonitor/preinittestcontainers/endimagename/ContainerEndImageNameCalculator.java) — default [`GenericContainerEndImageNameCalculator`](core/src/main/java/by/macmonitor/preinittestcontainers/endimagename/GenericContainerEndImageNameCalculator.java)

#### Default hash algorithm

**String inputs** (in order): `cmdParameters`, environment variables (`key=value`, keys sorted), `privileged=...`, `callback.uniqueKey()` when a [`PreInitStartCallback`](core/src/main/java/by/macmonitor/preinittestcontainers/PreInitStartCallback.java) is set.

**File inputs** (in order): [`docker/testcontainer-entrypoint.sh`](core/src/main/resources/docker/testcontainer-entrypoint.sh), then each `classpathResourceMapping` path — for each path, hash path bytes plus raw classpath file bytes.

**Digest:** MD5 over concatenated inputs (no delimiters between string params; `null` → literal `"null"`).

**Format:** `{prefix}-{baseImageName}.{first8HexChars}` — e.g. `test-mysql:8.0.45.a1b2c3d4` (prefix `"test"`).

#### JDBC modules

[`JdbcEndImageNameCalculator`](modules/jdbc/src/main/java/by/macmonitor/preinittestcontainers/endimagename/JdbcEndImageNameCalculator.java) extends the default: prefix = `dbName`; extra string hash of `dbName`, `username`, `password`; extra file hash of `initScripts`.

Changing init scripts, env vars, credentials, or classpath mappings produces a new image name. `preInitialized=false` does **not** affect the hash (stock path skips commit).

## Extension points

| Interface | When to use |
|-----------|-------------|
| [`ContainerFactory`](core/src/main/java/by/macmonitor/preinittestcontainers/ContainerFactory.java) | Entry point: `create(command)` |
| [`CreateContainerCommand`](core/src/main/java/by/macmonitor/preinittestcontainers/CreateContainerCommand.java) | Read-side command contract |
| [`CreateContainerCommandBuilder`](core/src/main/java/by/macmonitor/preinittestcontainers/CreateContainerCommandBuilder.java) | Fluent builder (`withBaseImageName`, `withMetadata`, `withEndImageName`, …) |
| [`PreInitStartCallback`](core/src/main/java/by/macmonitor/preinittestcontainers/PreInitStartCallback.java) | Custom init during image build |
| [`ContainerEndImageNameCalculator`](core/src/main/java/by/macmonitor/preinittestcontainers/endimagename/ContainerEndImageNameCalculator.java) | Custom hashing for new container modules |
| [`ContainerMetadataRegistry`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/ContainerMetadataRegistry.java) | Custom metadata lookup |
| [`DockerImageMetadataInspector`](core/src/main/java/by/macmonitor/preinittestcontainers/metadata/DockerImageMetadataInspector.java) | Replace live Docker inspect fallback |
| [`ImageCreationLockService`](core/src/main/java/by/macmonitor/preinittestcontainers/ImageCreationLockService.java) | Customize cross-process build locking |

Module extension is typically done by subclassing [`GenericContainerFactory`](core/src/main/java/by/macmonitor/preinittestcontainers/GenericContainerFactory.java) and [`GenericContainerEndImageNameCalculator`](core/src/main/java/by/macmonitor/preinittestcontainers/endimagename/GenericContainerEndImageNameCalculator.java) rather than adding new interfaces under `modules/`. For image metadata details, see [Image metadata](#image-metadata).

## Examples

Runnable examples live under [examples/](examples/). From that directory:

```bash
./gradlew :example-preinit-testcontainers:test
./gradlew :example-preinit-testcontainers-mysql:test
```

## License

Licensed under [MIT](LICENSE).
