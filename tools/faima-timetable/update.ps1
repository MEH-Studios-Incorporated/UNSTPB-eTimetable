# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Windows version of update.sh: downloads FAIMA's timetable workbooks from
# Google Sheets and converts them for the development server
# (server/build/faima-timetable.csv, imported on its next start).
# Development aid only. Keep the two scripts doing the same thing.
#
# Usage: tools\faima-timetable\update.ps1 [SHEET_ID ...]
# Without arguments the sheet ids are read from tools\faima-timetable\sheets.local,
# one per line. That file is not committed.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$here = $PSScriptRoot
$root = (Resolve-Path (Join-Path $here '..\..')).Path
$work = Join-Path $root 'server\build\faima-sheets'
$venv = Join-Path $here '.venv'
$python = Join-Path $venv 'Scripts\python.exe'

$ids = $args
if (-not $ids) {
    $list = Join-Path $here 'sheets.local'
    if (-not (Test-Path $list)) { throw "No sheet ids given and no $list" }
    $ids = Get-Content $list | Where-Object { $_ -notmatch '^#' } | ForEach-Object { -split $_ } | Where-Object { $_ }
}

if (-not (Test-Path $python)) {
    # the py launcher comes with python.org installs; the Store version only has python
    if (Get-Command py -ErrorAction SilentlyContinue) { py -3 -m venv $venv } else { python -m venv $venv }
    if ($LASTEXITCODE) { throw 'Could not create the Python environment' }
    & $python -m pip install -q -r (Join-Path $here 'requirements.txt')
    if ($LASTEXITCODE) { throw 'Could not install the requirements' }
}

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $work | Out-Null
foreach ($id in $ids) {
    $url = "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile (Join-Path $work "$id.xlsx")
    Write-Output "downloaded $id"
}

$sheets = (Get-ChildItem $work -Filter *.xlsx).FullName
& $python -I (Join-Path $here 'faima_to_csv.py') (Join-Path $root 'server\build\faima-timetable.csv') @sheets
if ($LASTEXITCODE) { exit $LASTEXITCODE }
Write-Output 'Restart the development server (tools\dev-server.ps1) to load it.'
