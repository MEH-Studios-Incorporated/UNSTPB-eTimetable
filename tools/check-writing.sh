#!/bin/sh
# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
#
# Checks tracked text files against the writing rules in AGENTS.md.
# Exits 1 and lists the offending lines if anything is found.
set -eu
export LC_ALL=C.UTF-8

cd "$(git rev-parse --show-toplevel)"
status=0

report() {
    echo "$1:" >&2
    printf '%s\n' "$2" >&2
    status=1
}

# git grep -P understands \x{...} only in a UTF-8 locale
dashes=$(git grep -n -I -P '[\x{2013}\x{2014}]' || true)
[ -z "$dashes" ] || report "em or en dashes" "$dashes"

quotes=$(git grep -n -I -P '[\x{2018}\x{2019}\x{201C}\x{201D}]' || true)
[ -z "$quotes" ] || report "curly quotes" "$quotes"

emoji=$(git grep -n -I -P '[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B50}\x{FE0F}]' || true)
[ -z "$emoji" ] || report "emojis" "$emoji"

# AGENTS.md lists these words, so it is skipped
words='delve|crucial|pivotal|seamless(ly)?|robust|leverag(e|es|ed|ing)|showcas(e|es|ed|ing)|testament|vibrant|landscape|realm|elevat(e|es|ed|ing)|empower(s|ed|ing)?|unlock(s|ed|ing)?|game-changer|cutting-edge|comprehensive|effortless(ly)?'
inflated=$(git grep -n -I -i -w -E "$words" -- ':!AGENTS.md' ':!tools/check-writing.sh' || true)
[ -z "$inflated" ] || report "inflated words" "$inflated"

exit "$status"
