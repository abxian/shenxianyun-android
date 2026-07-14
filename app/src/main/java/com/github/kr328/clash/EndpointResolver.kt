package com.github.kr328.clash

import android.content.Context
import com.github.kr328.clash.common.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 端点发现（去掉写死 sub.jc116.com）：
 * 启动时从「发现源」拉取 endpoints.json，得到 api_bases / sub_base / download_base，
 * 之后换域名 / 换内穿 / 切线路只改后台 + endpoints.json，客户端下次启动自动跟随。
 *
 * 发现失败 → 用本地缓存；再没有 → 用内置默认。任何一步都不阻塞启动、不抛异常。
 */
object EndpointResolver {
    /** 内置兜底默认值（发现源全挂时最后的锚点）。 */
    const val DEFAULT_API_BASE = "https://sub.jc116.com"

    // 静态发现锚点（互为备份）。sxnn.de 是 app 反代域名，走 /api/endpoints 动态兜底。
    private val DISCOVERY_URLS = listOf(
        "https://raw.githubusercontent.com/abxian/shenxianyun-config/main/endpoints.json",
        "http://114.80.36.225:5011/endpoints.json",
        "https://sxnn.de/api/endpoints",
    )

    private const val STORE = "jc116_endpoints"
    private const val KEY_API_BASES = "api_bases"
    private const val KEY_ACTIVE_BASE = "api_base_active"
    private const val KEY_SUB_BASE = "sub_base"
    private const val KEY_DOWNLOAD_BASE = "download_base"

    private const val DISCOVERY_TIMEOUT_MS = 8_000
    private const val PROBE_TIMEOUT_MS = 5_000

    private val prefs
        get() = Global.application.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun normalizeBase(value: String?): String {
        val v = value?.trim()?.trimEnd('/') ?: return ""
        return if (v.startsWith("http://") || v.startsWith("https://")) v else ""
    }

    private fun cachedBases(): List<String> =
        (prefs.getString(KEY_API_BASES, null) ?: "")
            .split('\n')
            .map { normalizeBase(it) }
            .filter { it.isNotEmpty() }

    /** 当前应使用的 API 基址：探测出的可用地址 > 缓存列表第一个 > 内置默认。同步、永不抛错。 */
    fun apiBase(): String {
        normalizeBase(prefs.getString(KEY_ACTIVE_BASE, null)).takeIf { it.isNotEmpty() }?.let { return it }
        return cachedBases().firstOrNull() ?: DEFAULT_API_BASE
    }

    fun downloadBase(): String = normalizeBase(prefs.getString(KEY_DOWNLOAD_BASE, null))

    private fun httpGet(url: String, timeoutMs: Int): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "JC116-Shenxianyun-Android")
        }
        return try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            code to body
        } finally {
            connection.disconnect()
        }
    }

    /** 拉取发现源（依次尝试），成功则写入缓存。全部失败静默返回（保留旧缓存）。 */
    private fun refresh() {
        for (url in DISCOVERY_URLS) {
            try {
                val (code, body) = httpGet(url, DISCOVERY_TIMEOUT_MS)
                if (code !in 200..299 || body.isBlank()) continue
                val json = JSONObject(body)
                val basesJson = json.optJSONArray("api_bases") ?: continue
                val bases = (0 until basesJson.length())
                    .map { normalizeBase(basesJson.optString(it)) }
                    .filter { it.isNotEmpty() }
                if (bases.isEmpty()) continue
                prefs.edit()
                    .putString(KEY_API_BASES, bases.joinToString("\n"))
                    .putString(KEY_SUB_BASE, normalizeBase(json.optString("sub_base")))
                    .putString(KEY_DOWNLOAD_BASE, normalizeBase(json.optString("download_base")))
                    .apply()
                return
            } catch (_: Exception) {
                // 下一个发现源
            }
        }
    }

    private fun probe(base: String): Boolean = try {
        httpGet("$base/api/app-version", PROBE_TIMEOUT_MS).first in 200..299
    } catch (_: Exception) {
        false
    }

    /** 逐个探测 api_bases，第一个通的设为 active。 */
    private fun pickApiBase() {
        val bases = cachedBases().ifEmpty { listOf(DEFAULT_API_BASE) }
        for (base in bases) {
            if (probe(base)) {
                prefs.edit().putString(KEY_ACTIVE_BASE, base).apply()
                return
            }
        }
    }

    /** 启动时后台调用一次：刷新发现源 + 探测可用基址。IO 线程执行，静默失败。 */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            refresh()
            pickApiBase()
        } catch (_: Exception) {
            // 发现失败不影响任何功能，继续用缓存/默认
        }
    }

    /** 请求失败时调用：把当前 active 基址作废并顺延到下一条可用线路。IO 线程执行。 */
    suspend fun rotate(): String = withContext(Dispatchers.IO) {
        val bad = apiBase()
        try {
            for (base in cachedBases().filter { it != bad }) {
                if (probe(base)) {
                    prefs.edit().putString(KEY_ACTIVE_BASE, base).apply()
                    return@withContext base
                }
            }
        } catch (_: Exception) {
            // 保持原地址
        }
        bad
    }
}
