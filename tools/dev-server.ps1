# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Windows version of tools/dev-server.sh: runs the development server on
# port 8080 and lets every connected Android emulator or phone reach it as
# 127.0.0.1:8080 through `adb reverse`. Devices connected later are picked
# up within a few seconds. Keep the two scripts doing the same thing.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$sdk = $env:ANDROID_HOME
if (-not $sdk -and (Test-Path local.properties)) {
    $line = Select-String -Path local.properties -Pattern '^sdk\.dir=(.*)' | Select-Object -First 1
    # local.properties escapes the colon and backslashes: C\:\\Users\\...
    if ($line) { $sdk = $line.Matches[0].Groups[1].Value -replace '\\(.)', '$1' }
}
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'

$reverse = $null
if (Test-Path $adb) {
    $reverse = Start-Job -ArgumentList $adb -ScriptBlock {
        param($adb)
        while ($true) {
            & $adb devices | Select-Object -Skip 1 | ForEach-Object {
                $serial, $state = -split $_
                if ($state -eq 'device') { & $adb -s $serial reverse tcp:8080 tcp:8080 *> $null }
            }
            Start-Sleep -Seconds 3
        }
    }
} else {
    Write-Warning "adb not found at $adb; devices will not reach the server"
}

try {
    & .\gradlew.bat :server:runDev
    $code = $LASTEXITCODE
} finally {
    if ($reverse) { Remove-Job $reverse -Force }
}
exit $code
