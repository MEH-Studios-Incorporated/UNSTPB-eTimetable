#!/bin/sh
# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Downloads FAIMA's timetable workbooks from Google Sheets and converts them
# for the development server (server/build/faima-timetable.csv, imported on
# its next start). Development aid only; in production an administrator
# imports the timetable through the dashboard.
#
# Usage: tools/faima-timetable/update.sh [SHEET_ID ...]
# Without arguments the sheet ids are read from tools/faima-timetable/sheets.local,
# one per line. That file is not committed.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
work="$root/server/build/faima-sheets"
venv="$here/.venv"

if [ "$#" -eq 0 ]; then
    [ -f "$here/sheets.local" ] || { echo "No sheet ids given and no $here/sheets.local" >&2; exit 1; }
    # one id per line, so word splitting is what we want
    # shellcheck disable=SC2046
    set -- $(grep -v '^#' "$here/sheets.local")
fi

[ -x "$venv/bin/python" ] || { python3 -m venv "$venv" && "$venv/bin/pip" install -q -r "$here/requirements.txt"; }

rm -rf "$work"
mkdir -p "$work"
for id in "$@"; do
    curl -fsSL -o "$work/$id.xlsx" "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
    echo "downloaded $id"
done

"$venv/bin/python" -I "$here/faima_to_csv.py" "$root/server/build/faima-timetable.csv" "$work"/*.xlsx
echo "Restart the development server (tools/dev-server.sh) to load it."
