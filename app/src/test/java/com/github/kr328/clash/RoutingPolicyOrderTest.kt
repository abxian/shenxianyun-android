package com.github.kr328.clash

import com.github.kr328.clash.core.model.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingPolicyOrderTest {
    @Test
    fun pinsRoutingPoliciesWithoutReorderingOtherNodes() {
        fun proxy(name: String, type: Proxy.Type) = Proxy(name, name, "", type, 120)
        val first = proxy("普通节点 2", Proxy.Type.Shadowsocks)
        val sameName = proxy("负载均衡", Proxy.Type.Shadowsocks)
        val last = proxy("普通节点 1", Proxy.Type.Shadowsocks)
        val auto = proxy("♻️ 自动选择", Proxy.Type.URLTest)
        val balance = proxy("负载均衡", Proxy.Type.LoadBalance)
        val roundRobin = proxy("轮询负载均衡", Proxy.Type.LoadBalance)
        val fallback = proxy("故障转移", Proxy.Type.Fallback)
        assertEquals(
            listOf(auto, balance, roundRobin, fallback, first, sameName, last),
            ProxyGroupResolver.pinManagedRoutingPolicies(
                listOf(first, fallback, sameName, roundRobin, last, balance, auto)
            )
        )
    }
}
