# gmdb

Spring Boot + Liquibase project targeting PostgreSQL.

## Run

Set the database password using an environment variable or JVM argument:

```sh
# Environment variable
export GMDB_DB_PASSWORD='your-password'

# Or JVM arg
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DGMDB_DB_PASSWORD=your-password"
```

If not set, the application uses the default placeholder `changeme` from `src/main/resources/application.yml`.

## Liquibase changelogs

Changelogs are stored as XML files under `src/main/resources/db/changelog/` and referenced from `src/main/resources/application.yml`.
