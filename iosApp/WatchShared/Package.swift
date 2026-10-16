// swift-tools-version: 5.9
import PackageDescription

// A host-only test harness for the same wire types used by the Apple targets.
// It does not replace watchOS/iOS builds or a paired-device acceptance test.
let package = Package(
    name: "WatchProtocol",
    platforms: [.macOS(.v13)],
    targets: [
        .target(name: "WatchProtocol", path: ".", exclude: ["Tests", "Package.swift"], sources: ["WatchProtocol.swift", "CompanionHandoff.swift"]),
        .testTarget(name: "WatchProtocolTests", dependencies: ["WatchProtocol"], path: "Tests"),
    ]
)
