// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "ScaScanKit",
    platforms: [
        .iOS(.v26)
    ],
    products: [
        .library(name: "ScaScanKit", targets: ["ScaScanKit"])
    ],
    targets: [
        .target(
            name: "ScaScanKit",
            swiftSettings: [.swiftLanguageMode(.v6)]
        )
    ]
)
