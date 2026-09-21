#!/usr/bin/env python3
"""Reproducible Xcode project, standard library only; never edits Android."""
import hashlib
import json
import plistlib
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
BASE = IOS / "TraidoresIOS"
PROJECT = BASE / "TraidoresIOS.xcodeproj"
objects = {}


def uid(key):
    return hashlib.sha256(key.encode()).hexdigest()[:24].upper()


def add(key, isa, **values):
    identifier = uid(key)
    objects[identifier] = {"isa": isa, **values}
    return identifier


def ref(path, file_type):
    return add(path, "PBXFileReference", lastKnownFileType=file_type, path=path, sourceTree="<group>")


def build(ref_id):
    return add("build-" + ref_id, "PBXBuildFile", fileRef=ref_id)


sources = [ref(str(p.relative_to(BASE)), "sourcecode.swift") for p in sorted((BASE / "TraidoresIOS").rglob("*.swift"))]
resources = [ref(p, kind) for p, kind in [
    ("TraidoresIOS/Resources/Assets.xcassets", "folder.assetcatalog"),
    ("TraidoresIOS/Resources/bree_serif.ttf", "file"),
    ("TraidoresIOS/Resources/menu_music.mp3", "audio.mp3"),
    ("TraidoresIOS/PrivacyInfo.xcprivacy", "text.xml"),
]]
info = ref("TraidoresIOS/Info.plist", "text.plist.xml")
entitlements = ref("TraidoresIOS/TraidoresIOS.entitlements", "text.plist.entitlements")
configs = {name: ref(f"Configuration/{name}.xcconfig", "text.xcconfig") for name in ["Shared", "Debug", "Release"]}
local_example = ref("Configuration/Local.xcconfig.example", "text")
product = add("product", "PBXFileReference", explicitFileType="wrapper.application", includeInIndex=0,
              path="TraidoresIOS.app", sourceTree="BUILT_PRODUCTS_DIR")
products = add("products", "PBXGroup", children=[product], name="Products", sourceTree="<group>")
root_group = add("root-group", "PBXGroup", children=sources + resources + [info, entitlements] + list(configs.values()) + [local_example, products], sourceTree="<group>")
package = add("core-package", "XCLocalSwiftPackageReference", relativePath="../Packages/TraidoresCore")
dependency = add("core-product", "XCSwiftPackageProductDependency", package=package, productName="TraidoresCore")
core_build = add("core-build", "PBXBuildFile", productRef=dependency)
framework_phase = add("frameworks", "PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[core_build], runOnlyForDeploymentPostprocessing=0)
source_phase = add("sources", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=[build(r) for r in sources], runOnlyForDeploymentPostprocessing=0)
resource_phase = add("resources", "PBXResourcesBuildPhase", buildActionMask=2147483647, files=[build(r) for r in resources], runOnlyForDeploymentPostprocessing=0)


def config_list(scope):
    entries = []
    for name in ["Debug", "Release"]:
        values = {"name": name, "buildSettings": {}}
        if scope == "app":
            values["baseConfigurationReference"] = configs[name]
        entries.append(add(f"{scope}-{name}", "XCBuildConfiguration", **values))
    return add(f"{scope}-configs", "XCConfigurationList", buildConfigurations=entries, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")


app_target = add("app-target", "PBXNativeTarget", buildConfigurationList=config_list("app"),
                 buildPhases=[source_phase, framework_phase, resource_phase], buildRules=[], dependencies=[],
                 name="TraidoresIOS", packageProductDependencies=[dependency], productName="TraidoresIOS",
                 productReference=product, productType="com.apple.product-type.application")
root = add("project", "PBXProject", attributes={"BuildIndependentTargetsInParallel": "YES", "LastUpgradeCheck": "1600",
           "TargetAttributes": {app_target: {"CreatedOnToolsVersion": "16.0", "ProvisioningStyle": "Automatic"}}},
           buildConfigurationList=config_list("project"), compatibilityVersion="Xcode 14.0", developmentRegion="es",
           hasScannedForEncodings=0, knownRegions=["es", "en", "Base"], mainGroup=root_group,
           packageReferences=[package], productRefGroup=products, projectDirPath="", projectRoot="", targets=[app_target])


def encode(value, level=0):
    tab = "\t" * level
    if isinstance(value, dict):
        return "{\n" + "".join(f'{tab}\t{json.dumps(k)} = {encode(v, level + 1)};\n' for k, v in value.items()) + tab + "}"
    if isinstance(value, list):
        return "(" + ", ".join(encode(v, level) for v in value) + ")"
    return str(value) if isinstance(value, int) else json.dumps(value, ensure_ascii=False)


PROJECT.mkdir(parents=True, exist_ok=True)
(PROJECT / "project.pbxproj").write_text("// !$*UTF8*$!\n" + encode({"archiveVersion": 1, "classes": {}, "objectVersion": 56, "objects": objects, "rootObject": root}) + "\n")
scheme_dir = PROJECT / "xcshareddata/xcschemes"
scheme_dir.mkdir(parents=True, exist_ok=True)
reference = f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app_target}" BuildableName="TraidoresIOS.app" BlueprintName="TraidoresIOS" ReferencedContainer="container:TraidoresIOS.xcodeproj"/>'
(scheme_dir / "TraidoresIOS.xcscheme").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference}</BuildActionEntry></BuildActionEntries>
  </BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES">
    <BuildableProductRunnable runnableDebuggingMode="0">{reference}</BuildableProductRunnable>
  </LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference}</BuildableProductRunnable></ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
''')

plists = {
    "Info.plist": {
        "CFBundleDevelopmentRegion": "es", "CFBundleDisplayName": "Traidores",
        "CFBundleExecutable": "$(EXECUTABLE_NAME)", "CFBundleIdentifier": "$(PRODUCT_BUNDLE_IDENTIFIER)",
        "CFBundleInfoDictionaryVersion": "6.0", "CFBundleName": "$(PRODUCT_NAME)",
        "CFBundlePackageType": "APPL", "CFBundleShortVersionString": "$(MARKETING_VERSION)",
        "CFBundleVersion": "$(CURRENT_PROJECT_VERSION)", "LSRequiresIPhoneOS": True,
        "UIApplicationSceneManifest": {"UIApplicationSupportsMultipleScenes": False},
        "UILaunchScreen": {"UIColorName": "LaunchBackground"},
        "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait"],
        "UIAppFonts": ["bree_serif.ttf"],
    },
    "TraidoresIOS.entitlements": {},
    "PrivacyInfo.xcprivacy": {
        "NSPrivacyTracking": False, "NSPrivacyTrackingDomains": [], "NSPrivacyCollectedDataTypes": [],
        "NSPrivacyAccessedAPITypes": [{"NSPrivacyAccessedAPIType": "NSPrivacyAccessedAPICategoryUserDefaults",
                                       "NSPrivacyAccessedAPITypeReasons": ["CA92.1"]}],
    },
}
for filename, contents in plists.items():
    (BASE / "TraidoresIOS" / filename).write_bytes(plistlib.dumps(contents, sort_keys=False))
print(f"Generated project with {len(sources)} Swift sources and local TraidoresCore package.")
