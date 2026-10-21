# GATAS Companion app icon

`gatas-companion.png` is the approved full-color master: `GA` above `TAS`,
cyan eyes and separator, dark navy background, without a rectangular border.
This is the unmodified, uncropped original at 1254 × 1254 pixels, before
platform-specific resizing, padding, or masking. It was created with Imagegen
and selected for the Android, iPhone, and Watch app icons.

Regenerate the platform PNG exports on macOS from the repository root:

```sh
swift -module-cache-path /tmp/gatas-icon-module-cache tools/generate-app-icons.swift
```

The iPhone and Watch use opaque 1024-pixel exports. Android's adaptive
foreground uses the same artwork with safe-area insets. The Android legacy
round icons and system splash export include platform-specific padding.
The full loading screen continues to use the horizontal GATAS wordmark.

## System appearances

`AppIcon.icon` is the shared native Icon Composer document for the iPhone and
Watch targets. Its transparent foreground is traced from the approved master;
`gatas-foreground.svg` keeps those contours editable without the navy background.
The original PNG above remains unchanged.

The native document combines the white lettering and cyan accents with a navy
background and Liquid Glass material. Icon Composer supplies Default, Dark,
Clear Light/Dark and Tinted Light/Dark appearances for iOS. watchOS uses its
circular platform rendition. Edit the document in Apple's Icon Composer to
preview these appearances; Xcode compiles it for both targets and generates
compatible renditions for older OS versions.

Android 13+ uses `ic_launcher_monochrome.xml` for themed icons. Its transparent
mask includes the letters, eyes and separator, and lets the launcher
apply the user's wallpaper and light/dark colors. The command above regenerates
this mask from the native document's foreground alongside the full-color PNG
exports. Legacy square icons have transparent rounded corners; adaptive icons
use the launcher's own mask.
