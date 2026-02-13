FROM postgres:17.1-bookworm

# Install pg_jsonschema from the prebuilt .deb release
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl; \
    curl -sSL -o /tmp/pg_jsonschema.deb \
      "https://github.com/supabase/pg_jsonschema/releases/download/v0.3.3/pg_jsonschema-v0.3.3-pg17-amd64-linux-gnu.deb"; \
    dpkg -i /tmp/pg_jsonschema.deb; \
    rm -f /tmp/pg_jsonschema.deb; \
    apt-get purge -y --auto-remove curl; \
    rm -rf /var/lib/apt/lists/*

COPY docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/