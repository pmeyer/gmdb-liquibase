FROM postgres:17.9-bookworm

ARG PG_JSONSCHEMA_VERSION=0.3.4
ARG PG_JSONSCHEMA_SHA256=4c339c178a7396b4076fb1eb5ffe3430bafd3d5f92fb6c917ad49860237db6c9

# Install pg_jsonschema from the prebuilt .deb release
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl; \
    curl -fsSL -o /tmp/pg_jsonschema.deb \
      "https://github.com/supabase/pg_jsonschema/releases/download/v${PG_JSONSCHEMA_VERSION}/pg_jsonschema-v${PG_JSONSCHEMA_VERSION}-pg17-amd64-linux-gnu.deb"; \
    echo "${PG_JSONSCHEMA_SHA256}  /tmp/pg_jsonschema.deb" | sha256sum -c -; \
    dpkg -i /tmp/pg_jsonschema.deb; \
    rm -f /tmp/pg_jsonschema.deb; \
    apt-get purge -y --auto-remove curl; \
    rm -rf /var/lib/apt/lists/*

COPY docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/
