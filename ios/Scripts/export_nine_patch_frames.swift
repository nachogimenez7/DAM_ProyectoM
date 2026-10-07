// Exports Android's event-frame 9-patches (ui_frame_event_*.9.png) as plain @3x PNGs
// for the iOS asset catalog and prints their stretch and padding regions, which
// `RevealFrameArt` in DawnRevealViews.swift hardcodes. Android sources are untouched.
//
//   swift ios/Scripts/export_nine_patch_frames.swift
import AppKit

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let source = root.appendingPathComponent("app/src/main/res/drawable-xxhdpi")
let catalog = root.appendingPathComponent("ios/TraidoresIOS/TraidoresIOS/Resources/Assets.xcassets")

func segments(_ marks: [Bool]) -> [(Int, Int)] {
    var result: [(Int, Int)] = []
    var start: Int?
    for (index, marked) in marks.enumerated() {
        if marked, start == nil { start = index }
        if !marked, let begin = start { result.append((begin, index)); start = nil }
    }
    if let begin = start { result.append((begin, marks.count)) }
    return result
}

for map in ["pampa", "grecia", "medieval"] {
    let name = "ui_frame_event_\(map)"
    let data = try Data(contentsOf: source.appendingPathComponent("\(name).9.png"))
    guard let rep = NSBitmapImageRep(data: data), let cgImage = rep.cgImage else { fatalError(name) }
    let width = rep.pixelsWide, height = rep.pixelsHigh
    func isMark(_ x: Int, _ y: Int) -> Bool {
        guard let color = rep.colorAt(x: x, y: y)?.usingColorSpace(.deviceRGB) else { return false }
        return color.alphaComponent > 0.8 && color.redComponent < 0.1
            && color.greenComponent < 0.1 && color.blueComponent < 0.1
    }
    let stretchX = segments((1..<width - 1).map { isMark($0, 0) })
    let stretchY = segments((1..<height - 1).map { isMark(0, $0) })
    let paddingX = segments((1..<width - 1).map { isMark($0, height - 1) })
    let paddingY = segments((1..<height - 1).map { isMark(width - 1, $0) })
    print(name, "size", width - 2, height - 2, "stretchX", stretchX, "stretchY", stretchY,
          "paddingX", paddingX, "paddingY", paddingY)

    guard let cropped = cgImage.cropping(to: CGRect(x: 1, y: 1, width: width - 2, height: height - 2)) else {
        fatalError(name)
    }
    let output = NSBitmapImageRep(cgImage: cropped)
    let imageset = catalog.appendingPathComponent("\(name).imageset")
    try FileManager.default.createDirectory(at: imageset, withIntermediateDirectories: true)
    try output.representation(using: .png, properties: [:])!
        .write(to: imageset.appendingPathComponent("\(name)@3x.png"))
    let contents = """
    {
      "images" : [
        { "filename" : "\(name)@3x.png", "idiom" : "universal", "scale" : "3x" }
      ],
      "info" : { "author" : "xcode", "version" : 1 }
    }

    """
    try contents.write(to: imageset.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
}
