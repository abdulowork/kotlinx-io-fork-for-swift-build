// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "kotlinx_io",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [
        .library(name: "kotlinx_io", targets: ["kotlinx_io", "kotlinx_io_bytestring", "kotlinx_io_core", "kotlinx_io_okio"]),
    ],
 dependencies: [ .package(url : "https://github.com/berkaybozkurt-jb/okio-fork-for-swift-build", exact : "1.0.1") ],
    targets: [
        .target(
            name: "kotlinx_io",
            dependencies: [],
            path: "Sources/kotlinx_io"            , cSettings: [.define( "KOTLIN", to: "-Xfragments=iosSimulatorArm64Main,iosMain,appleMain,nativeNonAndroidMain,nativeMain,commonMain -Xfragment-refines=appleMain:nativeNonAndroidMain,iosMain:appleMain,iosSimulatorArm64Main:iosMain,nativeMain:commonMain,nativeNonAndroidMain:nativeMain -Xfragment-sources=commonMain:__PACKAGE_DIR__/Sources/kotlinx_io/commonMain/Stub.kt -Xmulti-platform")]
        ),
        .target(
            name: "kotlinx_io_bytestring",
            dependencies: [],
            path: "Sources/kotlinx_io_bytestring"            , cSettings: [.define( "KOTLIN", to: "-Xfragments=iosSimulatorArm64Main,iosMain,appleMain,nativeNonAndroidMain,nativeMain,commonMain -Xfragment-refines=appleMain:nativeNonAndroidMain,iosMain:appleMain,iosSimulatorArm64Main:iosMain,nativeMain:commonMain,nativeNonAndroidMain:nativeMain -Xfragment-sources=appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/appleMain/ByteStringApple.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/-Platform.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/Annotations.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/Base64.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/ByteString.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/ByteStringBuilder.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/Hex.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/commonMain/UnsafeByteStringOperations.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_bytestring/nativeMain/-PlatformNative.kt -Xmulti-platform")]
        ),
        .target(
            name: "kotlinx_io_core",
            dependencies: ["kotlinx_io_bytestring"],
            path: "Sources/kotlinx_io_core"            , cSettings: [.define( "KOTLIN", to: "-Xfragments=iosSimulatorArm64Main,iosMain,appleMain,nativeNonAndroidMain,nativeMain,commonMain -Xfragment-refines=appleMain:nativeNonAndroidMain,iosMain:appleMain,iosSimulatorArm64Main:iosMain,nativeMain:commonMain,nativeNonAndroidMain:nativeMain -Xfragment-sources=appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/-Util.kt,appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/AppleCore.kt,appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/BuffersApple.kt,appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/FileSystemApple.kt,appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/SinksApple.kt,appleMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/appleMain/SourcesApple.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/-CommonPlatform.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/-Utf8.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/-Util.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Annotations.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Buffer.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Buffers.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/ByteStrings.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Core.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/FileSystem.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Paths.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/PeekSource.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/RawSink.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/RawSource.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/RealSink.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/RealSource.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Segment.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/SegmentPool.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Sink.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Sinks.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Source.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Sources.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/UnsafeBufferOperations.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/commonMain/Utf8.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/-NonJvmPlatform.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/FileSystemNative.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/PathsNative.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/RawSink.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/SegmentPool.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeMain/UtilsNative.kt,nativeNonAndroidMain:__PACKAGE_DIR__/Sources/kotlinx_io_core/nativeNonAndroidMain/FileSystemNativeNonAndroid.kt -Xmulti-platform")]
        ),
        .target(
            name: "kotlinx_io_okio",
            dependencies: ["kotlinx_io_bytestring", "kotlinx_io_core", .product(name : "okio", package : "okio-fork-for-swift-build")],
            path: "Sources/kotlinx_io_okio"            , cSettings: [.define( "KOTLIN", to: "-Xfragments=iosSimulatorArm64Main,iosMain,appleMain,nativeNonAndroidMain,nativeMain,commonMain -Xfragment-refines=appleMain:nativeNonAndroidMain,iosMain:appleMain,iosSimulatorArm64Main:iosMain,nativeMain:commonMain,nativeNonAndroidMain:nativeMain -Xfragment-sources=commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_okio/commonMain/ExceptionUtils.kt,commonMain:__PACKAGE_DIR__/Sources/kotlinx_io_okio/commonMain/OkioAdapters.kt,nativeMain:__PACKAGE_DIR__/Sources/kotlinx_io_okio/nativeMain/ExceptionUtilsNative.kt -Xmulti-platform")]
        ),
    ]
)
