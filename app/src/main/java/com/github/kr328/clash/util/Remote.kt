package com.github.kr328.clash.util

import android.os.DeadObjectException
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

// 绑定后台服务最长等待时间。正常绑定通常 <1s，超时多半意味着服务进程被系统杀掉或
// 绑定竞争失败。设一个上限避免 remote.get() 永久挂起把界面的事件循环卡死（点击失效）。
private val REMOTE_BIND_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(15)

private suspend fun awaitRemote() =
    withTimeoutOrNull(REMOTE_BIND_TIMEOUT_MILLIS) { Remote.service.remote.get() }
        ?: throw IllegalStateException("Clash 后台服务连接超时，请重试")

suspend fun <T> withClash(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IClashManager.() -> T
): T {
    while (true) {
        val remote = awaitRemote()
        val client = remote.clash()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)
        }
    }
}

suspend fun <T> withProfile(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IProfileManager.() -> T
): T {
    while (true) {
        val remote = awaitRemote()
        val client = remote.profile()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)
        }
    }
}
