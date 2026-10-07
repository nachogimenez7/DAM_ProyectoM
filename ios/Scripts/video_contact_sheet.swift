// Contact sheet of video frames, to review animations recorded with
// `xcrun simctl io booted recordVideo --codec h264 out.mov` (stop with Ctrl-C / SIGINT).
//   swift ios/Scripts/video_contact_sheet.swift <video> <outPrefix> <startS> <endS> <stepS> [columns] [thumbWidth]
import AVFoundation
import AppKit
let a = CommandLine.arguments
let url = URL(fileURLWithPath: a[1]); let prefix = a[2]
let start = Double(a[3])!, end = Double(a[4])!, step = Double(a[5])!
let cols = a.count > 6 ? Int(a[6])! : 6
let tw = a.count > 7 ? CGFloat(Double(a[7])!) : 260
let asset = AVURLAsset(url: url)
let gen = AVAssetImageGenerator(asset: asset)
gen.appliesPreferredTrackTransform = true
gen.requestedTimeToleranceBefore = .zero; gen.requestedTimeToleranceAfter = .zero
let dur = CMTimeGetSeconds(asset.duration) // deprecated but synchronous; fine for a script
print("duration", dur)
var times: [Double] = []; var t = start
while t <= min(end, dur - 0.05) { times.append(t); t += step }
var imgs: [(Double, CGImage)] = []
for t in times { if let im = try? gen.copyCGImage(at: CMTime(seconds: t, preferredTimescale: 600), actualTime: nil) { imgs.append((t, im)) } }
guard let first = imgs.first?.1 else { exit(1) }
let th = tw * CGFloat(first.height) / CGFloat(first.width)
let rows = (imgs.count + cols - 1) / cols
let W = Int(tw) * cols, H = Int(th + 18) * rows
let ctx = CGContext(data: nil, width: W, height: H, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.setFillColor(NSColor.black.cgColor); ctx.fill(CGRect(x: 0, y: 0, width: W, height: H))
let ns = NSGraphicsContext(cgContext: ctx, flipped: false); NSGraphicsContext.current = ns
for (i, (t, im)) in imgs.enumerated() {
  let c = i % cols, r = i / cols
  let x = CGFloat(c) * tw, y = CGFloat(H) - CGFloat(r + 1) * (th + 18)
  ctx.draw(im, in: CGRect(x: x, y: y, width: tw, height: th))
  let s = NSAttributedString(string: String(format: "%.1fs", t), attributes: [.font: NSFont.boldSystemFont(ofSize: 13), .foregroundColor: NSColor.yellow])
  s.draw(at: NSPoint(x: x + 4, y: y + th + 2))
}
let out = ctx.makeImage()!
let rep = NSBitmapImageRep(cgImage: out)
try! rep.representation(using: .jpeg, properties: [.compressionFactor: 0.8])!.write(to: URL(fileURLWithPath: prefix + ".jpg"))
print("wrote", prefix + ".jpg", imgs.count)
