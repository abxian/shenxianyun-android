package com.github.kr328.clash

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState

internal object ProxyGroupResolver {
    fun pinManagedRoutingPolicies(proxies: List<Proxy>): List<Proxy> =
        proxies.sortedBy { proxy ->
            when {
                proxy.type == Proxy.Type.URLTest && "自动选择" in proxy.name -> 0
                proxy.type == Proxy.Type.LoadBalance && proxy.name == "负载均衡" -> 1
                proxy.type == Proxy.Type.LoadBalance && proxy.name == "轮询负载均衡" -> 2
                proxy.type == Proxy.Type.Fallback && proxy.name == "故障转移" -> 3
                else -> 4
            }
        }

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
