# backend/

Spring Boot 3.x + Java 21 Maven project for the Societix platform (modular monolith).

The project is bootstrapped: `pom.xml` (pinned to Spring Boot 3.4.5 on Java 21), a Maven
wrapper (`mvnw` / `mvnw.cmd`), the `com.societix` module package skeleton, the
`SocietixApplication` entry point, and `application.yml` with `local` / `test` / `prod`
profiles. Flyway migrations, datasource wiring, error handling, logging, actuator/metrics,
and OpenAPI configuration are added in subsequent tasks.

## Build

```
./mvnw -DskipTests compile      # compile (downloads deps on first run)
./mvnw test                     # run unit + property tests
./mvnw spring-boot:run          # run locally (needs the `local` profile + a Postgres)
```

Requires JDK 21 on `JAVA_HOME`. The active Spring profile is selected via
`SPRING_PROFILES_ACTIVE` (e.g. `local`, `test`, `prod`). In `prod`, the datasource URL and
credentials are supplied through the `SOCIETIX_DB_URL`, `SOCIETIX_DB_USERNAME`, and
`SOCIETIX_DB_PASSWORD` environment variables — no secrets live in `application.yml`.

## Layout

```
backend/
├── pom.xml
└── src/
    ├── main/java/com/societix/
    │   ├── tenancy/ identity/ authorization/ audit/ notification/
    │   ├── filestorage/ societyproperty/ resident/ communication/
    │   ├── maintenance/ billing/ payments/ visitor/ facilitybooking/
    │   ├── platformadmin/ reporting/
    │   └── common/          # error envelope, base DTO, pagination, config
    ├── main/resources/
    │   ├── application.yml  # profiles: local, test, prod
    │   └── db/migration/    # Flyway V__*.sql
    └── test/java/com/societix/...   # unit, integration (Testcontainers), arch
```

Each module is organized package-by-module (`com.societix.<module>`), exposing a public
`api` package (service interfaces + DTOs) and keeping entities/repositories in an
`internal` package. ArchUnit tests enforce module boundaries.
