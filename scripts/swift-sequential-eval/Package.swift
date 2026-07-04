// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SwiftSequentialEval",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "swift-sequential-eval", targets: ["SwiftSequentialEval"]),
    ],
    dependencies: [
        .package(path: "../../clones/AzooKeyKanaKanjiConverter"),
    ],
    targets: [
        .executableTarget(
            name: "SwiftSequentialEval",
            dependencies: [
                .product(name: "KanaKanjiConverterModuleWithDefaultDictionary", package: "AzooKeyKanaKanjiConverter"),
            ]
        ),
    ]
)
