#!/bin/sh
# Copyright (c) 2026 Mihnea Gîrbăcică (Mihnea8848)
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Prints the CHANGELOG.md section of a version as release notes.
# Usage: tools/release-notes.sh vX.Y.Z
set -eu

version=${1:?usage: release-notes.sh vX.Y.Z}
changelog=${CHANGELOG:-CHANGELOG.md}

notes=$(awk -v v="$version" '
    /^## / { if (found) exit; found = ($2 == v); next }
    found
' "$changelog")

if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
    echo "release-notes.sh: no section \"## $version\" in $changelog" >&2
    exit 1
fi

# drop the blank lines left between the heading and the summary
printf '%s\n' "$notes" | sed '/./,$!d'
