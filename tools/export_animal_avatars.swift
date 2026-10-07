import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Package the approved artwork; never redraw or mirror Mamona's asymmetric markings.
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let source = root.appendingPathComponent("assets/avatars_animales/atlas_animales_v2.png")
let input = CGImageSourceCreateWithURL(source as CFURL, nil)!
let board = CGImageSourceCreateImageAtIndex(input, 0, nil)!
let names = ["carpincho", "buho", "cuervo", "lobo", "mamona", "liebre", "puma", "zorzal", "calandria", "hornero", "zorro", "yaguarete", "nandu", "yacare"]
for (index, name) in names.enumerated() {
    let x = (index % 4) * board.width / 4
    let y = (index / 4) * board.height / 4
    let right = ((index % 4) + 1) * board.width / 4
    let bottom = ((index / 4) + 1) * board.height / 4
    let rect = CGRect(x: x, y: y, width: right - x, height: bottom - y)
    let image = board.cropping(to: rect)!
    let filename = "avatar_\(name).png"
    let assetDir = root.appendingPathComponent("ios/TraidoresIOS/TraidoresIOS/Resources/Assets.xcassets/avatar_\(name).imageset")
    try FileManager.default.createDirectory(at: assetDir, withIntermediateDirectories: true)
    let outputs = [root.appendingPathComponent("assets/avatars_animales/\(filename)"), root.appendingPathComponent("app/src/main/res/drawable-nodpi/\(filename)"), assetDir.appendingPathComponent(filename)]
    for output in outputs {
        let dest = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, image, nil)
        precondition(CGImageDestinationFinalize(dest))
    }
    let contents = "{\"images\":[{\"filename\":\"\(filename)\",\"idiom\":\"universal\"}],\"info\":{\"author\":\"xcode\",\"version\":1}}"
    try contents.write(to: assetDir.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
}

// The fifteenth portrait is a standalone source, preserving the approved atlas unchanged.
let dogSource = root.appendingPathComponent("assets/avatars_animales/avatar_border_collie.png")
if FileManager.default.fileExists(atPath: dogSource.path) {
    let directory = root.appendingPathComponent("ios/TraidoresIOS/TraidoresIOS/Resources/Assets.xcassets/avatar_border_collie.imageset")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    for output in [root.appendingPathComponent("app/src/main/res/drawable-nodpi/avatar_border_collie.png"),
                   directory.appendingPathComponent("avatar_border_collie.png")] {
        try Data(contentsOf: dogSource).write(to: output)
    }
    let contents = #"{"images":[{"filename":"avatar_border_collie.png","idiom":"universal"}],"info":{"author":"xcode","version":1}}"#
    try contents.write(to: directory.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
}
