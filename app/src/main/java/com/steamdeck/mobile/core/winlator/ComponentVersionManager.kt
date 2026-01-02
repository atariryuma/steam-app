package com.steamdeck.mobile.core.winlator

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component Version Manager
 *
 * Manages versions of all Winlator components (Wine, Box64, DXVK, etc.)
 * by reading from a JSON configuration file instead of hardcoded constants.
 *
 * Benefits:
 * - Easy version updates without code changes
 * - Single source of truth for component versions
 * - Supports future dynamic version checking/upgrading
 * - Simple and maintainable
 *
 * Usage:
 * ```kotlin
 * val versionManager = ComponentVersionManager(context)
 * val wineVersion = versionManager.getWineVersion()
 * val box64AssetPath = versionManager.getBox64AssetPath()
 * ```
 */
@Singleton
class ComponentVersionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ComponentVersionManager"
        private const val CONFIG_FILE = "winlator/component_versions.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // CRITICAL: @Volatile ensures thread-safe visibility across threads
    // Without this, config writes in onCreate() may not be visible in IO threads
    @Volatile
    private var config: ComponentVersionsConfig? = null

    /**
     * Load component versions from JSON configuration file
     */
    fun loadConfig(): Result<ComponentVersionsConfig> {
        return try {
            if (config != null) {
                return Result.success(config!!)
            }

            val configText = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
            val loadedConfig = json.decodeFromString<ComponentVersionsConfig>(configText)
            config = loadedConfig

            AppLogger.i(TAG, "Component versions loaded successfully")
            AppLogger.d(TAG, "  Wine: ${loadedConfig.wine.version}")
            AppLogger.d(TAG, "  Box64: ${loadedConfig.box64.version}")

            Result.success(loadedConfig)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Failed to load component versions config", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse component versions config", e)
            Result.failure(e)
        }
    }

    /**
     * Get Wine version
     */
    fun getWineVersion(): String {
        return config?.wine?.version ?: "10.10"
    }

    /**
     * Get Wine asset path
     */
    fun getWineAssetPath(): String {
        return config?.wine?.assetPath ?: "rootfs.tar.xz"
    }

    /**
     * Get Box64 version
     */
    fun getBox64Version(): String {
        return config?.box64?.version ?: "0.3.6"
    }

    /**
     * Get Box64 asset path
     */
    fun getBox64AssetPath(): String {
        return config?.box64?.assetPath ?: "box64/box64-0.3.6.tar.xz"
    }

    /**
     * Get PRoot asset path
     */
    fun getProotAssetPath(): String {
        return config?.proot?.assetPath ?: "proot/proot-v5.3.0-aarch64"
    }

    /**
     * Get all environment variable overrides for Mesa/Zink
     */
    fun getMesaZinkEnvironmentVars(): Map<String, String> {
        return config?.mesaZink?.environmentVars ?: mapOf(
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "ZINK_DESCRIPTORS" to "lazy"
        )
    }

    /**
     * Get Proton version
     */
    fun getProtonVersion(): String {
        return config?.proton?.version ?: "10.0"
    }

    /**
     * Get Proton asset path
     */
    fun getProtonAssetPath(): String {
        return config?.proton?.assetPath ?: "proton/proton-10-arm64ec.tar.xz"
    }

    /**
     * Check if Proton is enabled in configuration
     */
    fun isProtonEnabled(): Boolean {
        return config?.proton?.enabled ?: false
    }

    /**
     * Get Proton prefix pack filename (relative to rootfs extraction)
     * Returns null if not defined or Proton not enabled
     */
    fun getProtonPrefixPack(): String? {
        return if (isProtonEnabled()) {
            config?.proton?.prefixPack
        } else {
            null
        }
    }

    /**
     * Check if Wine is enabled in configuration
     */
    fun isWineEnabled(): Boolean {
        return config?.wine?.enabled ?: true
    }

    /**
     * Get the active Wine/Proton rootfs path
     * Returns Proton if enabled, otherwise Wine
     */
    fun getActiveRootfsPath(): String {
        return if (isProtonEnabled()) {
            getProtonAssetPath()
        } else {
            getWineAssetPath()
        }
    }

    /**
     * Get the active Wine/Proton version string
     */
    fun getActiveVersion(): String {
        return if (isProtonEnabled()) {
            "Proton ${getProtonVersion()}"
        } else {
            "Wine ${getWineVersion()}"
        }
    }

    /**
     * Get component information for logging/debugging
     */
    fun getComponentInfo(): String {
        val cfg = config ?: return "Component versions not loaded"
        return buildString {
            appendLine("=== Winlator Component Versions ===")
            appendLine("Active: ${getActiveVersion()}")
            appendLine("Wine: ${cfg.wine.version} (${cfg.wine.source}) [${if (cfg.wine.enabled == true) "Enabled" else "Disabled"}]")
            cfg.proton?.let {
                appendLine("Proton: ${it.version} (${it.source}) [${if (it.enabled == true) "Enabled" else "Disabled"}]")
            }
            appendLine("Box64: ${cfg.box64.version} (${cfg.box64.source})")
            appendLine("DXVK: ${cfg.dxvk.version}")
            appendLine("VKD3D: ${cfg.vkd3d.version}")
            appendLine("PRoot: ${cfg.proot.version}")
            appendLine("================================")
        }
    }
}

/**
 * Component Versions Configuration (JSON schema)
 */
@Serializable
data class ComponentVersionsConfig(
    val wine: WineConfig,
    val proton: ProtonConfig? = null,
    val box64: Box64Config,
    val dxvk: DxvkConfig,
    val vkd3d: Vkd3dConfig,
    @kotlinx.serialization.SerialName("mesa_zink")
    @Serializable(with = MesaZinkConfigSerializer::class)
    val mesaZink: MesaZinkConfig,
    val proot: ProotConfig
)

@Serializable
data class WineConfig(
    val version: String,
    val architecture: String,
    @kotlinx.serialization.SerialName("asset_path")
    val assetPath: String,
    val description: String,
    val source: String,
    val notes: String? = null,
    val enabled: Boolean? = null
)

@Serializable
data class ProtonConfig(
    val version: String,
    val architecture: String,
    @kotlinx.serialization.SerialName("asset_path")
    val assetPath: String,
    val description: String,
    val source: String,
    val notes: String? = null,
    val enabled: Boolean? = null,
    val features: List<String>? = null,
    @kotlinx.serialization.SerialName("prefix_pack")
    val prefixPack: String? = null
)

@Serializable
data class Box64Config(
    val version: String,
    @kotlinx.serialization.SerialName("asset_path")
    val assetPath: String,
    val description: String,
    val source: String,
    val features: List<String>? = null
)

@Serializable
data class DxvkConfig(
    val version: String,
    val description: String,
    @kotlinx.serialization.SerialName("integrated_in")
    val integratedIn: String,
    val notes: String? = null
)

@Serializable
data class Vkd3dConfig(
    val version: String,
    val description: String,
    @kotlinx.serialization.SerialName("integrated_in")
    val integratedIn: String,
    val notes: String? = null
)

@Serializable
data class MesaZinkConfig(
    val version: String,
    val description: String,
    val environmentVars: Map<String, String>
)

@Serializable
data class ProotConfig(
    val version: String,
    val architecture: String,
    @kotlinx.serialization.SerialName("asset_path")
    val assetPath: String,
    val description: String,
    val source: String
)

/**
 * Custom serializer for MesaZinkConfig to handle snake_case JSON keys
 */
object MesaZinkConfigSerializer : KSerializer<MesaZinkConfig> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MesaZinkConfig")

    override fun deserialize(decoder: Decoder): MesaZinkConfig {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject

        val version = element["version"]?.jsonPrimitive?.content ?: "unknown"
        val description = element["description"]?.jsonPrimitive?.content ?: ""
        val envVarsObj = element["environment_vars"]?.jsonObject
        val environmentVars = envVarsObj?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

        return MesaZinkConfig(version, description, environmentVars)
    }

    override fun serialize(encoder: Encoder, value: MesaZinkConfig) {
        throw NotImplementedError("Serialization not implemented")
    }
}
