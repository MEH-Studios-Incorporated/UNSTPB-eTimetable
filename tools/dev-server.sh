#!/bin/sh
# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Runs the development server on port 8080 and lets every connected Android
# emulator or phone reach it as 127.0.0.1:8080 through `adb reverse`. The
# emulator's Wi-Fi network cannot reach the host at 10.0.2.2, and a phone
# on USB cannot reach it at all without this. Devices connected later are
# picked up within a few seconds.
set -eu
cd "$(dirname "$0")/.."

sdk=${ANDROID_HOME:-}
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk=$(sed -n 's/^sdk\.dir=//p' local.properties)
fi
adb=${sdk:-$HOME/Android/Sdk}/platform-tools/adb

if [ -x "$adb" ]; then
    (
        while :; do
            for serial in $("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
                "$adb" -s "$serial" reverse tcp:8080 tcp:8080 >/dev/null 2>&1 || true
            done
            sleep 3
        done
    ) &
    reverse_loop=$!
    trap 'kill "$reverse_loop" 2>/dev/null' EXIT INT TERM
else
    echo "adb not found at $adb; devices will not reach the server" >&2
fi

./gradlew :server:runDev
