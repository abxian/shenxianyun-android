package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import androidx.lifecycle.lifecycleScope
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::scanResultHandler)

    override suspend fun main() {
        val design = ProfilesDesign(this, managedProfileUuid())

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        is ProfilesDesign.Request.SaveSubscription ->
                            design.saveSubscription(it.url)
                        ProfilesDesign.Request.Scan ->
                            scanLauncher.launch(null)
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            if (isManagedProfile(it.profile.uuid)) {
                                withProfile { update(it.profile.uuid) }
                            } else {
                                startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                            }
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    design.requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    // 保存订阅链接（顶部输入框或扫码）：创建 URL 配置、下载并设为当前激活配置。
    // 无需提取码，纯订阅链接即可。
    private suspend fun ProfilesDesign.saveSubscription(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) {
            return
        }
        val name = runCatching { Uri.parse(url).host }
            .getOrNull()?.takeIf { h -> h.isNotBlank() }
            ?: getString(R.string.new_profile)
        try {
            withProfile {
                val uuid = create(Profile.Type.Url, name)
                try {
                    patch(uuid, name, url, 0)
                    commit(uuid, null)
                    queryByUUID(uuid)?.let { p -> setActive(p) }
                } catch (e: Exception) {
                    runCatching { delete(uuid) }
                    throw e
                }
            }
            fetch()
            showToast(R.string.import_code_success, ToastDuration.Long)
        } catch (e: Exception) {
            showExceptionToast(e)
        }
    }

    private fun scanResultHandler(result: QRResult) {
        lifecycleScope.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    design?.saveSubscription(url)
                }
                QRResult.QRUserCanceled -> {}
                QRResult.QRMissingPermission ->
                    design?.showExceptionToast(getString(R.string.import_from_qr_no_permission))
                is QRResult.QRError ->
                    design?.showExceptionToast(getString(R.string.import_from_qr_exception))
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        // 隐藏到期占位配置（提取码到期时自动切换用的不可上网配置），列表只显示用户
        // 自己导入/新建的配置文件。提取码导入的托管订阅固定置顶，其余配置维持原顺序。
        val hiddenUuid = getSharedPreferences("jc116_activation", Context.MODE_PRIVATE)
            .getString("expired_profile_uuid", null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val managedUuid = managedProfileUuid()
        withProfile {
            val visibleProfiles = queryAll().filter { it.uuid != hiddenUuid }
            patchProfiles(
                visibleProfiles.filter { it.uuid == managedUuid } +
                    visibleProfiles.filter { it.uuid != managedUuid }
            )
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                if (isManagedProfile(uuid)) {
                    getString(R.string.managed_profile_update_failed)
                } else {
                    getString(R.string.toast_profile_updated_failed, name, reason)
                },
                ToastDuration.Long
            ){
                if (isManagedProfile(uuid)) {
                    setAction(R.string.update) {
                        launch { withProfile { update(uuid) } }
                    }
                } else {
                    setAction(R.string.edit) {
                        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                    }
                }
            }
        }
    }
}
