#!/bin/bash
set -euo pipefail

online_ios_root="$(cd "$(dirname "$0")/.." && pwd)"
online_sources="$online_ios_root/TraidoresIOS/TraidoresIOS/Platform/Online"
online_checks_dir="$(mktemp -d "${TMPDIR:-/tmp}/traidores-online-contracts.XXXXXX")"
trap 'rm -rf "$online_checks_dir"' EXIT

# Does not edit project.pbxproj, resolve Firebase packages, or access an online project.
xcrun swiftc -swift-version 5 -strict-concurrency=complete -warnings-as-errors \
    "$online_sources/OnlineModels.swift" \
    "$online_sources/OnlineServices.swift" \
    "$online_sources/OnlineContract.swift" \
    "$online_ios_root/Tests/OnlineContractsChecks.swift" \
    -o "$online_checks_dir/checks"
"$online_checks_dir/checks"
