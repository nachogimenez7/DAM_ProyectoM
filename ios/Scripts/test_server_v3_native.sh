#!/bin/bash
set -euo pipefail

# Requires the isolated local emulators + server_v3_native_harness.cjs from the handoff.
# App actions use the real Firebase SDK, security rules and V3 callables, never Cloud.
v3_repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
v3_simulator="${1:-F9FA9920-CBD7-4F13-AABE-1D1A410D6818}"
v3_build_root="$v3_repo_root/output/ios-server-v3-native"
v3_results="$v3_repo_root/output/ios-server-v3-ui-$(date +%Y%m%d-%H%M%S).xcresult"
cd "$v3_repo_root"

python3 - <<'PY'
import json, urllib.request
try:
    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:29888/health', data=b'{}'), timeout=3) as response:
        health = json.load(response)
    if health != {'emulatorsOnly': True, 'projectId': 'traidores'}:
        raise SystemExit('The fixture controller must target isolated emulators only.')
except OSError:
    raise SystemExit('Start the isolated emulators and ios/Scripts/server_v3_native_harness.cjs first.')
PY

xcodebuild build-for-testing \
    -project ios/TraidoresIOS/TraidoresIOS.xcodeproj -scheme TraidoresIOS \
    -configuration Debug -destination "platform=iOS Simulator,id=$v3_simulator" \
    -derivedDataPath "$v3_build_root" -disableAutomaticPackageResolution

# xcodebuild's parent environment does not propagate this switch to XCTest. Keep the
# new .xctestrun beside the generated one so __TESTROOT__ resolves the same products.
python3 - "$v3_build_root/Build/Products" <<'PY'
import pathlib, plistlib, sys
products = pathlib.Path(sys.argv[1])
original = next(products.glob('TraidoresIOS_*.xctestrun'))
settings = plistlib.loads(original.read_bytes())
for key in ('EnvironmentVariables', 'TestingEnvironmentVariables'):
    settings['TraidoresIOSUITests'].setdefault(key, {})['TRAIDORES_IOS_V3_NATIVE_TEST'] = '1'
(products / 'NativeV3.xctestrun').write_bytes(plistlib.dumps(settings))
PY

xcodebuild test-without-building \
    -xctestrun "$v3_build_root/Build/Products/NativeV3.xctestrun" \
    -destination "platform=iOS Simulator,id=$v3_simulator" -parallel-testing-enabled NO -collect-test-diagnostics never \
    -only-testing:TraidoresIOSUITests/OnlineFlowUITests/testServerChatVoteAndSingleNightAnnouncement \
    -only-testing:TraidoresIOSUITests/OnlineFlowUITests/testServerDeserterInitialRoundFourAndFinalWindow \
    -only-testing:TraidoresIOSUITests/OnlineFlowUITests/testServerPrivateChatsAndDeadAbandonment \
    -only-testing:TraidoresIOSUITests/OnlineFlowUITests/testServerRematchAndReentryAfterResult \
    -only-testing:TraidoresIOSUITests/OnlineFlowUITests/testServerDawnUsesPublicRoleAndEventsDoNotReplay \
    -resultBundlePath "$v3_results"
