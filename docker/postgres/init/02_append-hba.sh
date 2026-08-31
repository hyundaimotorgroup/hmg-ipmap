#!/bin/bash
set -e
# Append replication & host access rules
cat >> "$PGDATA/pg_hba.conf" <<'EOF'
host    all           all          0.0.0.0/0        scram-sha-256
EOF
# No need to reload here; the final server start will read this file.
