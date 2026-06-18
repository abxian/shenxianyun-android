package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotalBytes
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class MainActivity : BaseActivity<MainDesign>() {
    private companion object {
        const val SUBSCRIPTION_BASE_URL = "https://sub.jc116.com"
        const val ACTIVATION_STORE = "jc116_activation"
        const val KEY_CODE = "code"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_PROFILE_UUID = "profile_uuid"
        const val KEY_UPDATE_VERSION = "update_version"
        const val KEY_CLIENT_ID = "client_id"
        const val UPDATE_CHECK_INTERVAL_MILLIS = 60_000L
        const val HEARTBEAT_INTERVAL_MILLIS = 30_000L
        const val TRAFFIC_REPORT_INTERVAL_MILLIS = 30_000L
        const val MAX_TRAFFIC_REPORT_DELTA_BYTES = 5L * 1024 * 1024 * 1024
    }

    override suspend fun main() {
        val design = MainDesign(this)
        stableClientId()

        setContentDesign(design)

        runCatching { ensureDefaultMetaFeatures() }
            .onFailure { design.showExceptionToast(it.asException()) }

        runCatching { design.fetch() }
            .onFailure { design.showExceptionToast(it.asException()) }
        runCatching { design.checkAppUpdate() }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))
        var lastSubscriptionUpdateCheck = 0L
        var lastHeartbeatAt = 0L
        var lastTrafficReportAt = 0L
        var lastReportedTrafficTotal: Long? = null

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        Event.ClashStart -> {
                            design.fetch()
                            launch { sendClientHeartbeat("online") }
                        }
                        Event.ClashStop -> {
                            design.fetch()
                            lastHeartbeatAt = 0L
                            lastTrafficReportAt = 0L
                            lastReportedTrafficTotal = null
                            launch { sendClientHeartbeat("offline") }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            runCatching {
                                if (clashRunning)
                                    stopClashService()
                                else
                                    design.startClash()
                            }.onFailure { design.showExceptionToast(it.asException()) }
                        }
                        MainDesign.Request.ImportByCode ->
                            design.showCodeImportDialog()
                        MainDesign.Request.UpdateSubscription -> {
                            runCatching {
                                val code = savedActivationCode()
                                if (code.isBlank()) {
                                    design.showCodeImportDialog()
                                } else {
                                    design.importSubscriptionCode(code, silent = true)
                                }
                            }.onFailure { design.showExceptionToast(it.asException()) }
                        }
                        MainDesign.Request.RenewCode ->
                            openCodeStorePage()
                        MainDesign.Request.SetRuleMode ->
                            runCatching { design.patchMode(TunnelState.Mode.Rule) }
                                .onFailure { design.showExceptionToast(it.asException()) }
                        MainDesign.Request.SetGlobalMode ->
                            runCatching { design.patchMode(TunnelState.Mode.Global) }
                                .onFailure { design.showExceptionToast(it.asException()) }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            design.showAbout(queryAppVersionName())
                    }
                }
                ticker.onReceive {
                    val now = System.currentTimeMillis()
                    if (clashRunning) {
                        val trafficTotal = runCatching { design.fetchTraffic() }
                            .getOrElse { lastReportedTrafficTotal ?: 0L }
                        if (lastReportedTrafficTotal == null) {
                            lastReportedTrafficTotal = trafficTotal
                        }
                        if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MILLIS) {
                            lastHeartbeatAt = now
                            launch { sendClientHeartbeat("online") }
                        }
                        if (now - lastTrafficReportAt >= TRAFFIC_REPORT_INTERVAL_MILLIS) {
                            val previous = lastReportedTrafficTotal ?: trafficTotal
                            val delta = (trafficTotal - previous).coerceAtLeast(0L)
                            lastTrafficReportAt = now
                            if (delta > 0L && delta <= MAX_TRAFFIC_REPORT_DELTA_BYTES) {
                                launch {
                                    if (sendClientTraffic(delta)) {
                                        lastReportedTrafficTotal = trafficTotal
                                    }
                                }
                            } else if (delta > MAX_TRAFFIC_REPORT_DELTA_BYTES) {
                                lastReportedTrafficTotal = trafficTotal
                            }
                        }
                    }
                    if (now - lastSubscriptionUpdateCheck >= UPDATE_CHECK_INTERVAL_MILLIS) {
                        lastSubscriptionUpdateCheck = now
                        launch {
                            runCatching { design.checkSubscriptionUpdate() }
                        }
                    }
                }
            }
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

    private suspend fun sendClientHeartbeat(status: String) = withContext(Dispatchers.IO) {
        val code = savedActivationCode()
        if (code.isBlank()) {
            return@withContext
        }
        val appVersion = queryAppVersionName().asHeaderValue()
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val path = if (status == "offline") "offline" else "heartbeat"
        val params = listOf(
            "client_id=${URLEncoder.encode(stableClientId(), "UTF-8")}",
            "platform=${URLEncoder.encode("安卓手机", "UTF-8")}",
            "app_name=${URLEncoder.encode("神仙云安卓端", "UTF-8")}",
            "app_version=${URLEncoder.encode(appVersion, "UTF-8")}",
            "device_name=${URLEncoder.encode("${Build.BRAND} ${Build.MODEL}", "UTF-8")}",
        ).joinToString("&")
        val connection = (URL("$SUBSCRIPTION_BASE_URL/api/client/$path/$encoded?$params").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Shenxianyun-Android/$appVersion")
            setRequestProperty("X-Client-Id", stableClientId())
        }
        try {
            connection.responseCode
        } catch (_: Exception) {
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun sendClientTraffic(deltaBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val code = savedActivationCode()
        if (code.isBlank() || deltaBytes <= 0L) {
            return@withContext false
        }
        val appVersion = queryAppVersionName().asHeaderValue()
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val body = JSONObject().apply {
            put("client_id", stableClientId())
            put("platform", "安卓手机")
            put("app_name", "神仙云安卓端")
            put("app_version", appVersion)
            put("device_name", "${Build.BRAND} ${Build.MODEL}")
            put("upload_bytes", 0)
            put("download_bytes", deltaBytes)
        }.toString().toByteArray(Charsets.UTF_8)
        val connection = (URL("$SUBSCRIPTION_BASE_URL/api/client/traffic/$encoded").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Shenxianyun-Android/$appVersion")
            setRequestProperty("X-Client-Id", stableClientId())
        }
        try {
            connection.outputStream.use { it.write(body) }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun String.asHeaderValue(): String {
        return lineSequence()
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun Throwable.asException(): Exception {
        return this as? Exception ?: RuntimeException(this)
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)
        setActivationCode(savedActivationCode().ifBlank { null })

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun ensureDefaultMetaFeatures() {
        withClash {
            val override = queryOverride(Clash.OverrideSlot.Persist)
            if (override.allowLan == null) {
                override.allowLan = false
            }
            if (override.unifiedDelay != true || override.tcpConcurrent != true) {
                override.unifiedDelay = true
                override.tcpConcurrent = true
                patchOverride(Clash.OverrideSlot.Persist, override)
            } else if (override.allowLan == false) {
                patchOverride(Clash.OverrideSlot.Persist, override)
            }
        }
    }

    private suspend fun MainDesign.fetchTraffic(): Long {
        return withClash {
            queryTrafficTotal().also { setForwarded(it) }.trafficTotalBytes()
        }
    }

    private suspend fun MainDesign.patchMode(mode: TunnelState.Mode) {
        withClash {
            val override = queryOverride(Clash.OverrideSlot.Session)
            override.mode = mode
            patchOverride(Clash.OverrideSlot.Session, override)
        }
        setMode(mode)
    }

    private fun MainDesign.showCodeImportDialog() {
        val input = EditText(this@MainActivity).apply {
            hint = getString(R.string.import_code_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(savedActivationCode())
            selectAll()
        }

        AlertDialog.Builder(this@MainActivity)
            .setTitle(R.string.import_code_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val code = input.text.toString().trim()

                if (code.isBlank()) {
                    launch {
                        showToast(R.string.import_code_empty, ToastDuration.Long)
                    }
                    return@setPositiveButton
                }

                launch {
                    importSubscriptionCode(code)
                }
            }
            .show()
    }

    private suspend fun MainDesign.importSubscriptionCode(code: String, silent: Boolean = false) {
        try {
            if (!silent) {
                showToast(R.string.import_code_fetching, ToastDuration.Long)
            }

            val verifyJson = verifyActivationCode(code, countImport = !silent)
            if (!verifyJson.optBoolean("ok", false)) {
                if (!silent) {
                    showToast(R.string.import_code_invalid, ToastDuration.Long)
                }
                return
            }
            val expiresAt = verifyJson.optString("expires_at", "")
            if (isExpired(expiresAt)) {
                if (!silent) {
                    showToast(R.string.import_code_expired, ToastDuration.Long)
                }
                return
            }
            val url = verifyJson.optString("subscription_url", "").trim()
            if (url.isBlank()) {
                throw IllegalStateException("Web backend did not return subscription_url")
            }
            val name = "Code $code"

            withProfile {
                val savedUuid = activationStore()
                    .getString(KEY_PROFILE_UUID, null)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val uuid = if (savedUuid != null && queryByUUID(savedUuid) != null) {
                    savedUuid
                } else {
                    create(Profile.Type.Url, name)
                }
                patch(uuid, name, url, 0)
                commit(uuid, null)
                queryByUUID(uuid)?.let {
                    setActive(it)
                }
                activationStore().edit()
                    .putString(KEY_CODE, code)
                    .putString(KEY_EXPIRES_AT, expiresAt)
                    .putString(KEY_PROFILE_UUID, uuid.toString())
                    .apply()
            }
            verifyJson.optLong("update_version", 0).takeIf { it > 0 }?.let { version ->
                activationStore().edit()
                    .putLong(KEY_UPDATE_VERSION, version)
                    .apply()
            }
            fetch()
            showToast(
                if (silent) R.string.subscription_updated else R.string.import_code_success,
                ToastDuration.Long
            )
        } catch (e: Exception) {
            if (!silent) {
                showExceptionToast(e)
            }
        }
    }

    private suspend fun MainDesign.startClash() {
        val code = savedActivationCode()
        if (code.isBlank()) {
            showToast(R.string.import_code_required, ToastDuration.Long)
            showCodeImportDialog()
            return
        }
        if (!isActivationStillValid(code)) {
            showToast(R.string.import_code_expired, ToastDuration.Long)
            return
        }

        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.import_by_code) {
                    showCodeImportDialog()
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private fun activationStore() =
        getSharedPreferences(ACTIVATION_STORE, Context.MODE_PRIVATE)

    private fun savedActivationCode(): String =
        activationStore().getString(KEY_CODE, "")?.trim().orEmpty()

    private fun openCodeStorePage() {
        val code = savedActivationCode()
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val url = if (code.isBlank()) {
            "$SUBSCRIPTION_BASE_URL/pay?action=new"
        } else {
            "$SUBSCRIPTION_BASE_URL/pay?action=renew&code=$encoded"
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private suspend fun verifyActivationCode(code: String, countImport: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val importParams = if (countImport) {
            "?import=1&client_id=${URLEncoder.encode(stableClientId(), "UTF-8")}"
        } else {
            ""
        }
        val connection = (URL("$SUBSCRIPTION_BASE_URL/api/verify/$encoded$importParams").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("X-Client-Id", stableClientId())
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext JSONObject().put("ok", false)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val ok = json.optBoolean("ok", false)
            val expiresAt = json.optString("expires_at", "")

            activationStore().edit()
                .putString(KEY_EXPIRES_AT, expiresAt)
                .apply()

            if (!ok || isExpired(expiresAt)) {
                json.put("ok", false)
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun queryUpdateVersion(code: String): Long? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        val clientId = URLEncoder.encode(stableClientId(), "UTF-8")
        val connection = (URL("$SUBSCRIPTION_BASE_URL/api/update-state/$encoded?client_id=$clientId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("X-Client-Id", stableClientId())
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return@withContext null
            }

            json.optLong("update_version", 0L).takeIf { it > 0L }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun MainDesign.checkAppUpdate() {
        val update = withContext(Dispatchers.IO) {
            val connection = (URL("$SUBSCRIPTION_BASE_URL/api/app-version").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    return@withContext null
                }

                val latestCode = json.optLong("latest_version_code", 0L)
                val apkUrl = json.optString("apk_url", "")
                val versionName = json.optString("latest_version_name", "").ifBlank { latestCode.toString() }
                val currentCode = packageManager.getPackageInfo(packageName, 0).versionCodeCompat

                if (latestCode > currentCode && apkUrl.isNotBlank()) {
                    Pair(versionName, apkUrl)
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } ?: return

        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.app_update_title)
                .setMessage(getString(R.string.app_update_message, update.first))
                .setPositiveButton(R.string.app_update_now) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.second)))
                }
                .setNegativeButton(R.string.app_update_later, null)
                .show()
        }
    }

    private suspend fun MainDesign.checkSubscriptionUpdate() {
        if (clashRunning) {
            return
        }

        val code = savedActivationCode()
        if (code.isBlank()) {
            return
        }

        val remoteVersion = try {
            queryUpdateVersion(code)
        } catch (_: Exception) {
            null
        } ?: return

        val localVersion = activationStore().getLong(KEY_UPDATE_VERSION, 0L)
        if (localVersion > 0L && remoteVersion <= localVersion) {
            return
        }

        importSubscriptionCode(code, silent = true)
    }

    private suspend fun isActivationStillValid(code: String): Boolean {
        val expiresAt = activationStore().getString(KEY_EXPIRES_AT, "").orEmpty()
        if (expiresAt.isNotBlank() && expiresAt != "null") {
            return !isExpired(expiresAt)
        }

        return code.isNotBlank()
    }

    private fun isExpired(value: String): Boolean {
        if (value.isBlank() || value == "null") {
            return false
        }

        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
        val expiry = patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value.take(19))
            }.getOrNull()
        } ?: return false

        return expiry.before(Date())
    }

    private suspend fun MainDesign.showNodeSelector() {
        if (!clashRunning) {
            showToast(R.string.select_node_start_first, ToastDuration.Long)
            return
        }

        try {
            val (groupName, nodes, selected) = withClash {
                val names = queryProxyGroupNames(true)
                val groups = names.map { name ->
                    name to queryProxyGroup(name, ProxySort.Delay)
                }
                val preferred = groups.firstOrNull { (name, group) ->
                    val lower = name.lowercase(Locale.ROOT)
                    group.type == Proxy.Type.Selector &&
                            group.proxies.any { !it.type.group && it.type != Proxy.Type.Direct && it.type != Proxy.Type.Reject } &&
                            (name.contains("节点") ||
                                    name.contains("选择") ||
                                    lower.contains("proxy") ||
                                    lower.contains("select"))
                } ?: groups.firstOrNull { (_, group) ->
                    group.type == Proxy.Type.Selector &&
                            group.proxies.any { !it.type.group && it.type != Proxy.Type.Direct && it.type != Proxy.Type.Reject }
                }

                if (preferred == null) {
                    Triple("", emptyList<Proxy>(), "")
                } else {
                    val (preferredName, group) = preferred
                    Triple(
                        preferredName,
                        group.proxies.filter { !it.type.group && it.type != Proxy.Type.Direct && it.type != Proxy.Type.Reject },
                        group.now
                    )
                }
            }

            if (groupName.isBlank() || nodes.isEmpty()) {
                showToast(R.string.select_node_empty, ToastDuration.Long)
                return
            }

            val labels = nodes.map {
                val delay = if (it.delay > 0) " - ${it.delay}ms" else ""
                "${it.title.ifBlank { it.name }}$delay"
            }.toTypedArray()
            val checked = nodes.indexOfFirst { it.name == selected }

            withContext(Dispatchers.Main) {
                var pending = checked.coerceAtLeast(0)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.select_node)
                    .setSingleChoiceItems(labels, checked) { _, which ->
                        pending = which
                    }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        launch {
                            val applied = withClash {
                                patchSelector(groupName, nodes[pending].name)
                            }
                            showToast(
                                if (applied) R.string.select_node_applied else R.string.select_node_failed,
                                ToastDuration.Long
                            )
                            fetch()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } catch (e: Exception) {
            showExceptionToast(e)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
