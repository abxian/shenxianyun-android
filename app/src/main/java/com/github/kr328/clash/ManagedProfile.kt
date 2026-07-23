package com.github.kr328.clash

import android.content.Context
import java.util.UUID

internal const val ACTIVATION_STORE_NAME = "jc116_activation"
internal const val MANAGED_PROFILE_UUID_KEY = "profile_uuid"
internal const val MANAGED_PROFILE_NAME = "神仙云订阅"
internal const val SUBSCRIPTION_NETWORK_ATTEMPTS = 4
internal const val SUBSCRIPTION_NETWORK_RETRIES = SUBSCRIPTION_NETWORK_ATTEMPTS - 1

internal fun Context.managedProfileUuid(): UUID? =
    getSharedPreferences(ACTIVATION_STORE_NAME, Context.MODE_PRIVATE)
        .getString(MANAGED_PROFILE_UUID_KEY, null)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

internal fun Context.isManagedProfile(uuid: UUID): Boolean =
    managedProfileUuid() == uuid

internal fun subscriptionRetryDelayMillis(retryNumber: Int): Long =
    (1_200L shl (retryNumber - 1).coerceAtLeast(0)).coerceAtMost(4_800L)
