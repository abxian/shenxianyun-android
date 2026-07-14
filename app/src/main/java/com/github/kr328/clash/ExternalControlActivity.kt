package com.github.kr328.clash

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    private companion object {
        const val ACTIVATION_STORE = "jc116_activation"
        const val KEY_CODE = "code"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_PROFILE_UUID = "profile_uuid"
        const val KEY_UPDATE_VERSION = "update_version"
        const val KEY_CLIENT_ID = "client_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        when(intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()
                val url = uri.getQueryParameter("url") ?: return finish()

                launch {
                    try {
                        importShenxianyunSubscription(uri, url)
                        Toast.makeText(this@ExternalControlActivity, R.string.import_code_success, Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@ExternalControlActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    } catch (e: Exception) {
                        Toast.makeText(this@ExternalControlActivity, e.message ?: getString(R.string.import_code_invalid), Toast.LENGTH_LONG).show()
                    } finally {
                        finish()
                    }
                }
                return
            }

            Intents.ACTION_TOGGLE_CLASH -> if(Remote.broadcasts.clashRunning) {
                stopClash()
            }
            else {
                startClash()
            }

            Intents.ACTION_START_CLASH -> if(!Remote.broadcasts.clashRunning) {
                startClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
            }

            Intents.ACTION_STOP_CLASH -> if(Remote.broadcasts.clashRunning) {
                stopClash()
            }
            else {
                Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
            }
        }
        return finish()
    }

    private suspend fun importShenxianyunSubscription(uri: Uri, url: String) {
        val code = extractCodeFromSubscriptionUrl(url)
        if (code.isBlank()) {
            importExternalSubscription(uri, url)
            return
        }

        val verify = verifyActivationCode(code)
        val secureUrl = verify.optString("subscription_url", "").trim()
        if (secureUrl.isBlank()) {
            throw IllegalStateException("Web backend did not return subscription_url")
        }
        val name = uri.getQueryParameter("name") ?: "Code $code"
        val uuid = withProfile {
            val savedUuid = activationStore()
                .getString(KEY_PROFILE_UUID, null)
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val profileUuid = if (savedUuid != null && queryByUUID(savedUuid) != null) {
                savedUuid
            } else {
                create(Profile.Type.Url, name)
            }
            patch(profileUuid, name, secureUrl, 0)
            commit(profileUuid, null)
            queryByUUID(profileUuid)?.let {
                setActive(it)
            }
            profileUuid
        }

        val expiresAt = verify.optString("expires_at", "")
        val updateVersion = verify.optLong("update_version", 0L)
        activationStore().edit()
            .putString(KEY_CODE, code)
            .putString(KEY_PROFILE_UUID, uuid.toString())
            .putString(KEY_EXPIRES_AT, expiresAt)
            .putLong(KEY_UPDATE_VERSION, updateVersion)
            .apply()
    }

    private suspend fun importExternalSubscription(uri: Uri, url: String) {
        withProfile {
            val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                "file" -> Profile.Type.File
                else -> Profile.Type.Url
            }
            val name = uri.getQueryParameter("name") ?: getString(R.string.new_profile)
            val uuid = create(type, name)
            patch(uuid, name, url, 0)
            commit(uuid, null)
            queryByUUID(uuid)?.let {
                setActive(it)
            }
        }
    }

    private fun extractCodeFromSubscriptionUrl(value: String): String {
        val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return ""
        parsed.getQueryParameter("code")?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val segments = parsed.pathSegments
        val subIndex = segments.indexOf("sub")
        if (subIndex >= 0 && subIndex + 1 < segments.size) {
            return segments[subIndex + 1].trim()
        }
        return ""
    }

    private suspend fun verifyActivationCode(code: String): JSONObject =
        try {
            verifyActivationCodeOnce(code, useBootstrap = false)
        } catch (e: java.io.IOException) {
            // 当前 API 线路失联：切到下一条可用线路重试。
            EndpointResolver.rotate()
            try {
                verifyActivationCodeOnce(code, useBootstrap = false)
            } catch (e2: java.io.IOException) {
                // 最后一层：经后台下发的兜底代理去验证。
                verifyActivationCodeOnce(code, useBootstrap = true)
            }
        }

    private suspend fun verifyActivationCodeOnce(code: String, useBootstrap: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val clientId = stableClientId()
        val url = "${EndpointResolver.apiBase()}/api/verify/$encoded?import=1&client_id=${URLEncoder.encode(clientId, "UTF-8")}"
        val connection = (if (useBootstrap) EndpointResolver.openViaBootstrap(url, 8000) else null)
            ?: (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
        connection.setRequestProperty("User-Agent", "Shenxianyun-Android/ExternalImport")
        connection.setRequestProperty("X-Client-Id", clientId)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(getString(R.string.import_code_invalid))
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("message", getString(R.string.import_code_invalid)))
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun stableClientId(): String {
        val store = activationStore()
        val saved = store.getString(KEY_CLIENT_ID, "")?.trim().orEmpty()
        if (saved.isNotBlank()) {
            return saved
        }
        val generated = UUID.randomUUID().toString()
        store.edit().putString(KEY_CLIENT_ID, generated).apply()
        return generated
    }

    private fun activationStore() =
        getSharedPreferences(ACTIVATION_STORE, Context.MODE_PRIVATE)

    private fun startClash() {
//        if (currentProfile == null) {
//            Toast.makeText(this, R.string.no_profile_selected, Toast.LENGTH_LONG).show()
//            return
//        }
        val vpnRequest = startClashService()
        if (vpnRequest != null) {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
