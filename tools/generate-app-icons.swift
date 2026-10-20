import CoreGraphics
import Foundation
import ImageIO

// Deterministic platform sizing and masking of the approved artwork.
// Run from the repository root; the approved artwork and native icon layers stay unchanged.
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let masterURL = root.appendingPathComponent("artwork/app-icon/gatas-companion.png")
guard let source = CGImageSourceCreateWithURL(masterURL as CFURL, nil),
      let master = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("Cannot read the approved icon master")
}
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

func export(_ path: String, size: Int, artworkFraction: CGFloat = 1,
            round: Bool = false, cornerRadius: CGFloat = 0,
            transparent: Bool = false, source: CGImage = master) {
    let alpha = transparent || round || cornerRadius > 0
    let bitmapInfo = alpha ? CGImageAlphaInfo.premultipliedLast.rawValue : CGImageAlphaInfo.noneSkipLast.rawValue
    guard let context = CGContext(data: nil, width: size, height: size,
                                  bitsPerComponent: 8, bytesPerRow: 0,
                                  space: colorSpace, bitmapInfo: bitmapInfo) else {
        fatalError("Cannot create icon context")
    }
    let side = CGFloat(size)
    let bounds = CGRect(x: 0, y: 0, width: side, height: side)
    if round { context.addEllipse(in: bounds); context.clip() }
    if cornerRadius > 0 {
        context.addPath(CGPath(roundedRect: bounds, cornerWidth: side * cornerRadius,
                               cornerHeight: side * cornerRadius, transform: nil))
        context.clip()
    }
    if !transparent {
        context.setFillColor(CGColor(colorSpace: colorSpace, components: [11 / 255.0, 34 / 255.0, 57 / 255.0, 1])!)
        context.fill(bounds)
    }
    context.interpolationQuality = .high
    let width = side * artworkFraction
    let origin = (side - width) / 2
    context.draw(source, in: CGRect(x: origin, y: origin, width: width, height: width))
    let output = root.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let image = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(output as CFURL, "public.png" as CFString, 1, nil) else {
        fatalError("Cannot create \(path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    precondition(CGImageDestinationFinalize(destination), "Cannot save \(path)")
    print("\(path): \(size)x\(size)")
}

export("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png", size: 1024)
export("iosApp/GatasWatchApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png", size: 1024)
export("composeApp/src/androidMain/res/drawable-nodpi/gatas_app_icon.png", size: 512)
// 72 / 108 keeps the mark inside the system splash icon's circular mask.
export("composeApp/src/androidMain/res/drawable-nodpi/gatas_splash_icon.png", size: 512, artworkFraction: 72 / 108.0, transparent: true)
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    export("composeApp/src/androidMain/res/mipmap-\(density)/ic_launcher.png", size: size, cornerRadius: 0.22)
    export("composeApp/src/androidMain/res/mipmap-\(density)/ic_launcher_round.png", size: size, artworkFraction: 0.9, round: true)
}

// Android 13+ themed icons use the same transparent foreground contours as Apple.
// Rasterize the mask once to keep launcher rendering inexpensive.
let foregroundURL = root.appendingPathComponent("artwork/app-icon/AppIcon.icon/Assets/gatas-foreground.png")
let foregroundSource = CGImageSourceCreateWithURL(foregroundURL as CFURL, nil)!
let foreground = CGImageSourceCreateImageAtIndex(foregroundSource, 0, nil)!
let mask = CGContext(data: nil, width: foreground.width, height: foreground.height,
                     bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
                     bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
let maskBounds = CGRect(x: 0, y: 0, width: foreground.width, height: foreground.height)
mask.draw(foreground, in: maskBounds)
mask.setBlendMode(.sourceIn)
mask.setFillColor(CGColor(gray: 1, alpha: 1))
mask.fill(maskBounds)
export("composeApp/src/androidMain/res/drawable-nodpi/gatas_app_icon_monochrome.png",
       size: 512, artworkFraction: 72 / 108.0, transparent: true, source: mask.makeImage()!)
