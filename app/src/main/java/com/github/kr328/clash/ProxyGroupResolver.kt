package com.github.kr328.clash

import com.github.kr328.clash.core.model.TunnelState

internal object ProxyGroupResolver {
    fun managedGroupNames(
        mode: TunnelState.Mode,
        availableNames: List<String>,
    ): List<String> {
        val preferred = when (mode) {
            TunnelState.Mode.Global -> availableNames.firstOrNull {
                it.equals("GLOBAL", ignoreCase = true)
            }
            TunnelState.Mode.Rule -> availableNames.firstOrNull {
                it.trim() == "节点选择"
            } ?: availableNames.firstOrNull {
                it.contains("节点选择")
            } ?: availableNames.firstOrNull {
                it.contains("节点") && it.contains("选择")
            }
            else -> null
        }

        return preferred?.let(::listOf).orEmpty()
    }
}
