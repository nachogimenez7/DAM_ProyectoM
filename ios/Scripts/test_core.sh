#!/bin/bash
set -euo pipefail

ios_root="$(cd "$(dirname "$0")/.." && pwd)"
developer_dir="$(xcode-select -p)"
test_flags=()

# Standalone CLT has Testing.framework but SwiftPM doesn't discover its paths.
# Xcode supplies these automatically. Keep this workaround outside Package.swift.
if [[ "$developer_dir" == */CommandLineTools ]]; then
    testing_frameworks="$developer_dir/Library/Developer/Frameworks"
    testing_libraries="$developer_dir/Library/Developer/usr/lib"
    test_flags=(
        -Xswiftc "-F$testing_frameworks"
        -Xlinker "-F$testing_frameworks"
        -Xlinker -rpath -Xlinker "$testing_frameworks"
        -Xlinker -rpath -Xlinker "$testing_libraries"
    )
fi

exec xcrun swift test --disable-xctest \
    --package-path "$ios_root/Packages/TraidoresCore" ${test_flags[@]+"${test_flags[@]}"}
