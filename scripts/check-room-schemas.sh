#!/usr/bin/env bash
# Fails if the checked-in Room schemas don't match what the entities generate:
# either the schema for the declared database version is missing, or a schema
# file changed and wasn't committed. Run `:core:assembleDebug` first so Room
# (KSP) regenerates the schemas, e.g. via the build CI job.
set -euo pipefail

DB="core/src/main/java/com/github/karczews/publictransportviewer/data/db/GtfsDatabase.kt"
SCHEMA_DIR="core/schemas/com.github.karczews.publictransportviewer.data.db.GtfsDatabase"

version=$(grep -oE 'version = [0-9]+' "$DB" | grep -oE '[0-9]+')
schema="${SCHEMA_DIR}/${version}.json"

if [ ! -f "$schema" ]; then
  echo "::error::Missing Room schema for GtfsDatabase version $version (expected $schema)."
  exit 1
fi

if [ -n "$(git status --porcelain -- core/schemas)" ]; then
  echo "::error::Room schemas in core/schemas/ are out of date."
  git status --porcelain -- core/schemas
  echo "Run './gradlew :core:assembleDebug' locally and commit the updated schemas."
  exit 1
fi
