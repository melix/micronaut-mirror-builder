#!/bin/bash
# Courtesy of Daniel Dietrich
# https://gist.github.com/danieldietrich/76e480f3fb903bdeaac5b1fb007ab5ac
#
# This script copies cached Gradle dependencies to a local Maven repository
# (modulo different hashes for same coordinates).
#

function mavenize {
    IFS='/' read -r -a PATHS <<< "$1"
    GROUP_ID=$(echo "${PATHS[1]}" | tr . /)
    ARTIFACT_ID="${PATHS[2]}"
    VERSION="${PATHS[3]}"
    echo "$GROUP_ID/$ARTIFACT_ID/$VERSION"
}

GRADLE_DEPENDENCY_CACHE=/root/.gradle/caches/modules-2/files-2.1
MAVEN_REPO=/export
mkdir -p $MAVEN_REPO
cd "$GRADLE_DEPENDENCY_CACHE" || exit

find . -type f -print0 | while IFS= read -r -d '' file; do
    FILE_NAME=$(basename "$file")
    SOURCE_DIR=$(dirname "$file")
    TARGET_DIR="$MAVEN_REPO/$(mavenize "$SOURCE_DIR")"
    # echo "$SOURCE_DIR/$FILE_NAME -> $TARGET_DIR/$FILE_NAME"
    mkdir -p "$TARGET_DIR" && cp "$SOURCE_DIR/$FILE_NAME" "$TARGET_DIR/"
done
