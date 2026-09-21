// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TraidoresCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "TraidoresCore", targets: ["TraidoresCore"])],
    targets: [
        .target(name: "TraidoresCore"),
        .testTarget(
            name: "TraidoresCoreTests",
            dependencies: ["TraidoresCore"],
            resources: [.copy("Fixtures")]
        )
    ]
)
