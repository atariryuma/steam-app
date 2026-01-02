import java.net.URL
import java.security.MessageDigest

/**
 * Download large asset files during build
 *
 * These files are too large for Git (>100MB GitHub limit):
 * - Wine 10.10 rootfs (53MB)
 * - Box64 0.3.6 (3MB)
 * - Proton 10 ARM64EC (196MB)
 */

data class AssetFile(
    val url: String,
    val outputPath: String,
    val sha256: String,
    val description: String
)

val assets = listOf(
    AssetFile(
        url = "https://github.com/Winlator/winlator/releases/download/v11.0-beta/rootfs.tar.xz",
        outputPath = "app/src/main/assets/rootfs.tar.xz",
        sha256 = "REPLACE_WITH_ACTUAL_SHA256",
        description = "Wine 10.10 rootfs (53MB)"
    ),
    AssetFile(
        url = "https://github.com/Winlator/winlator/releases/download/v11.0-beta/box64-0.3.6.tar.xz",
        outputPath = "app/src/main/assets/box64/box64-0.3.6.tar.xz",
        sha256 = "REPLACE_WITH_ACTUAL_SHA256",
        description = "Box64 0.3.6 (3MB)"
    ),
    AssetFile(
        url = "https://github.com/K11MCH1/Winlator/releases/download/v13.1.1/proton-10-arm64ec.tar.xz",
        outputPath = "app/src/main/assets/proton/proton-10-arm64ec.tar.xz",
        sha256 = "REPLACE_WITH_ACTUAL_SHA256",
        description = "Proton 10 ARM64EC (196MB)"
    )
)

tasks.register("downloadAssets") {
    group = "build setup"
    description = "Download large asset files not in Git"

    doLast {
        assets.forEach { asset ->
            val outputFile = file(asset.outputPath)

            if (outputFile.exists()) {
                println("✓ ${asset.description} already exists: ${asset.outputPath}")
                return@forEach
            }

            println("⬇ Downloading ${asset.description}...")
            println("  URL: ${asset.url}")
            println("  Output: ${asset.outputPath}")

            outputFile.parentFile.mkdirs()

            try {
                URL(asset.url).openStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                println("✓ Downloaded successfully: ${outputFile.length() / 1024 / 1024}MB")

                // Verify SHA256 if provided
                if (asset.sha256 != "REPLACE_WITH_ACTUAL_SHA256") {
                    val sha256 = MessageDigest.getInstance("SHA-256")
                        .digest(outputFile.readBytes())
                        .joinToString("") { "%02x".format(it) }

                    if (sha256 == asset.sha256) {
                        println("✓ SHA256 verified")
                    } else {
                        throw GradleException("SHA256 mismatch for ${asset.description}")
                    }
                }
            } catch (e: Exception) {
                println("✗ Download failed: ${e.message}")
                println("  Please download manually from: ${asset.url}")
                throw e
            }
        }
    }
}

// Auto-download before preBuild
tasks.named("preBuild") {
    dependsOn("downloadAssets")
}
