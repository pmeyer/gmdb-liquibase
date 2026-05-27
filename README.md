# Guitar Transcription Catalog Database: gmdb

Spring Boot + Liquibase project containing bootstrapping and migrations for the database storage of the 
Guitar Transcription Catalog — a decades-spanning collection of guitar magazines and song books containing 
guitar tablature song transcriptions.

Targets a PostgreSQL database.

## Setup and Operation
Database development was done with a containerized version of PostgreSQL version 17+ that included extensions supporting 
JSON schema.  

This container image can be built using the [Dockerfile](docker/postgres/Dockerfile) in the `docker/postgres` directory.  The image built
includes PostgreSQL and the prebuilt [pg_jsonschema](https://github.com/supabase/pg_jsonschema) extensions.  Additionally, 
two initialization scripts are pulled into the image that:
 - Create the pg_jsonschema extension in the database
 - Create a specific schema named `liquibase` used to store liquibase operational tables.

Initialization scripts are executed the first time the container and database is started.  See official
[PostgreSQL docs](https://hub.docker.com/_/postgres#initialization-scripts) for more info.

## Resources Artifact
The Maven build publishes an attached `resources` classifier artifact for integration-test and runtime consumers that
need the Liquibase changelogs, profile configuration, and Docker build context without depending on the executable
Spring Boot jar:

```xml
<dependency>
  <groupId>com.yellowmoonsoftware.gmcatalog</groupId>
  <artifactId>gmdb-liquibase</artifactId>
  <version>1.3.0</version> <!-- x-release-please-version -->
  <classifier>resources</classifier>
  <scope>test</scope>
</dependency>
```

The classifier jar exposes the following paths at normal classpath roots:

- `application.yml`
- `application-bootstrap.yml`
- `application-migrate.yml`
- `db/changelog/bootstrap-changelog.xml`
- `db/changelog/db-changelog.xml`
- `db/changelog/changes/...`
- `gmdb-liquibase/docker/Dockerfile`
- `gmdb-liquibase/docker/docker-entrypoint-initdb.d/000_enable_pg_jsonschema.sql`
- `gmdb-liquibase/docker/docker-entrypoint-initdb.d/010_create_liquibase_schema.sql`

## Initialization
Get the database container up and running.  Refer to the docker image documentation for details about storage of the
database data files, the database name, the superuser name and password (you'll need these later). 

__Important__: as far as the database name is concerned, this liquibase app assumes the database is named __gmdb__.  So 
you'll at least need to specify that using the `POSTGRES_DB` environment variable when starting/running the container.

The remainder of database setup and initialization is handled by this Spring Boot + Liquibase app.\\

## Bootstrapping the Database
Run this application using the `bootstrap` profile.  

### Environment Variables
The following must be specified as environment variables or JVM arguments when running the `bootstrap` profile, unless otherwise specified:

| Variable                  | Description                                                                                                                                                                                                 |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GMDB_SUPERUSER`          | The database super user.  If running a container, this is the value specified for `POSTGRES_USER` or its default.                                                                                           |
| `GMDB_SUPERUSER_PASSWORD` | The database super user password.  If running a container, this is the value specified for `POSTGRES_PASSWORD` (which is required).                                                                         |
| `GMDB_ADMIN_PASSWORD`     | This is the password that should be used when creating the `gmdb_admin` user.  This is the user that this app uses when running the `migrate` profile.                                                      |
| `GMDB_APP_PASSWORD`       | This is the password that should be used when creating the `gmdb_app_user` user.  This is the user that the backend application (the API) uses to perform conenct and execute queries against the database. |
| `PGSQL_HOST`              | This is the host name running your database. __Note:__ if you include the `local` profile, then this value will be ignored and `localhost` will be used.                                                    |
| `PGSQL_PORT`              | _(Optional)_ This is the port number for the database server. Defaults to 5432.                                                                                                                             |

### Results
After running the application using the `bootstrap` profile, the following will have occurred in the `gmdb` database:
 - A new schema named `gmdb` will have been created to store the application's tables, functions, etc.
 - The following roles will have been created:
   - `gmdb_readonly`: read-only access on the `gmdb` schema only.
   - `gmdb_readwrite`: read/write access on the `gmdb` schema only.
   - `gmdb_owner`: set as the owner of the `gmdb` and `liquibase` schemas; full access to all objects in both schemas.
 - The following users will have been created:
   - `gmdb_admin`: granted `gmdb_owner` role and specific access to the `liquibase` tables.  This user will have the password specified in the `GMDB_ADMIN_PASSWORD` environment variable.
   - `gmdb_app_user`: granted read/write access to the `gmdb` schema.  This user will have the password specified in the `GMDB_APP_PASSWORD` environment variable.
 - The `liquibase` schema will contain the liquibase lock and changelog tables.
 - The schema search path will be set to list `gmdb` first for both `gmdb_admin` and `gmdb_app_user` users.

## Running Database Migrations
Database migrations are run using the `migrate` profile.  After successfully [bootstrapping the database](#bootstrapping-the-database), 
run the application using the `migrate` profile to create all database objects used for standard operations.  

Subsequently, run the application using the `migrate` profile whenever any changes need to applied to the database per standard
liquibase practices.

The following must be specified as environment variables or JVM arguments when running the `migrate` profile, unless otherwise specified:

| Variable                  | Description                                                                                                                                                                                                 |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GMDB_ADMIN_PASSWORD`     | This is the password that should be used when creating the `gmdb_admin` user.  This is the user that this app uses when running the `migrate` profile.                                                      |
| `PGSQL_HOST`              | This is the host name running your database. __Note:__ if you include the `local` profile, then this value will be ignored and `localhost` will be used.                                                    |
| `PGSQL_PORT`              | _(Optional)_ This is the port number for the database server. Defaults to 5432.                                                                                                                             |


Set the database password using an environment variable or JVM argument:

## Example: Running the Application

### Bootstrap

```sh
# Required environment variables for bootstrapping the database
export GMDB_SUPERUSER='the-pg-super-user'
export GMDB_SUPERUSER_PASSWORD='super-user-password'
export GMDB_ADMIN_PASSWORD='password-for-gmdb-admin-user'
export GMDB_APP_PASSWORD='password-for-gmdb-app-user'
export PGSQL_HOST='my-gmdb-host'
export PGSQL_PORT=12345

mvn spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

### Migrate

```sh
# Required environment variables for bootstrapping the database
export GMDB_ADMIN_PASSWORD='password-for-gmdb-admin-user'
export PGSQL_HOST='my-gmdb-host'
export PGSQL_PORT=12345

mvn spring-boot:run -Dspring-boot.run.profiles=migrate
```


If not set, the application uses the default placeholder `changeme` which will likely cause quick failure!

## Running the Application Container
Release builds publish a Spring Boot application image to GitHub Container Registry:

```sh
docker pull ghcr.io/pmeyer/gmdb-liquibase:latest
```

The image defaults to the `migrate` Spring profile. Provide the same environment variables used by the Maven-based
application run:

```sh
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=migrate \
  -e GMDB_ADMIN_PASSWORD='password-for-gmdb-admin-user' \
  -e PGSQL_HOST='my-gmdb-host' \
  -e PGSQL_PORT=12345 \
  ghcr.io/pmeyer/gmdb-liquibase:latest
```

For first-time database setup, run the same image with the `bootstrap` profile and the bootstrap environment variables:

```sh
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=bootstrap \
  -e GMDB_SUPERUSER='the-pg-super-user' \
  -e GMDB_SUPERUSER_PASSWORD='super-user-password' \
  -e GMDB_ADMIN_PASSWORD='password-for-gmdb-admin-user' \
  -e GMDB_APP_PASSWORD='password-for-gmdb-app-user' \
  -e PGSQL_HOST='my-gmdb-host' \
  -e PGSQL_PORT=12345 \
  ghcr.io/pmeyer/gmdb-liquibase:latest
```

## Integration Tests
Integration tests use [Testcontainers](https://testcontainers.com/) to run PostgreSQL from the same
[Dockerfile](docker/postgres/Dockerfile) used for local database development.

The test container sets `POSTGRES_DB=gmdb`, along with a generated PostgreSQL superuser and password, so the database
name and initialization behavior match the assumptions used by the Spring Boot + Liquibase profiles. The current
bootstrap integration tests start the application with the `bootstrap` profile and verify the structures created by
`bootstrap-changelog.xml`, including schemas, roles, role memberships, Liquibase metadata tables, and access for the
migration user.

Run the integration tests with Docker running:

```sh
mvn test
```

The full Maven verification lifecycle also runs these tests:

```sh
mvn verify
```

## Releases
Releases are managed by release-please from conventional commits on `main`. The Maven project remains on a
`-SNAPSHOT` development version between releases.

When release-please creates a GitHub release, it also creates the corresponding Git tag. GitHub Actions then checks out
that release tag and publishes the non-snapshot Maven artifact to GitHub Packages:

```xml
<dependency>
  <groupId>com.yellowmoonsoftware.gmcatalog</groupId>
  <artifactId>gmdb-liquibase</artifactId>
  <version>1.3.0</version> <!-- x-release-please-version -->
</dependency>
```

The same release workflow also builds and publishes the application container image from
[docker/app/Dockerfile](docker/app/Dockerfile) to GitHub Container Registry as `ghcr.io/pmeyer/gmdb-liquibase`.
The image is assembled from the release jar produced by the Maven deploy step. Release images are tagged with the full
version, the major/minor version, and `latest`.

Snapshot versions may be built locally, but they are not published by the release workflow. The workflow verifies that
the Maven project version does not end in `-SNAPSHOT` before publishing Maven artifacts or container images.
