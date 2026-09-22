package dev.zephyr.mobile.core

import dev.zephyr.mobile.data.ProxyItem

/** A local preview, available even before the VPN has started. No credentials are exposed. */
object ProfileConfig {
    data class Preview(val proxies: Map<String, ProxyItem>, val nodeCount: Int, val providerCount: Int)

    fun parse(text: String): Preview {
        val root = ConfigBuilder.parseToMap(text)
        ConfigBuilder.validateProviders(root)
        require(root["proxies"] is List<*> || root["proxy-providers"] is Map<*, *>) {
            "订阅缺少 proxies 或 proxy-providers 节点配置"
        }
        val result = linkedMapOf(
            "DIRECT" to ProxyItem(name = "DIRECT", type = "Direct"),
            "REJECT" to ProxyItem(name = "REJECT", type = "Reject"),
        )
        val nodes = (root["proxies"] as? List<*>).orEmpty().map { entry ->
            val node = entry as? Map<*, *> ?: error("节点配置格式错误")
            val name = (node["name"] as? String)?.takeIf { it.isNotBlank() } ?: error("节点缺少名称")
            val type = (node["type"] as? String)?.takeIf { it.isNotBlank() } ?: error("节点 $name 缺少协议类型")
            require(name !in result) { "节点名称重复：$name" }
            result[name] = ProxyItem(name = name, type = type, udp = node["udp"] == true)
            name
        }
        val providers = root["proxy-providers"] as? Map<*, *> ?: emptyMap<Any, Any>()
        require(nodes.isNotEmpty() || providers.isNotEmpty()) { "订阅没有可用节点或节点提供器" }
        val groupNames = (root["proxy-groups"] as? List<*>).orEmpty().map { entry ->
            val group = entry as? Map<*, *> ?: error("策略组格式错误")
            val name = group["name"] as? String ?: error("策略组缺少名称")
            require(name !in result) { "策略组名称重复：$name" }
            val members = (group["proxies"] as? List<*>)?.filterIsInstance<String>().orEmpty().toMutableList()
            if (group["include-all"] == true || group["include-all-proxies"] == true) members += nodes
            val type = when (val raw = group["type"] as? String ?: "select") {
                "select" -> "Selector"
                "url-test" -> "URLTest"
                "fallback" -> "Fallback"
                "load-balance" -> "LoadBalance"
                else -> raw
            }
            // Selection and provider expansion are authoritative only once the core runs.
            result[name] = ProxyItem(name = name, type = type, all = members.distinct())
            name
        }
        result["GLOBAL"] = ProxyItem(name = "GLOBAL", type = "Selector", all = (groupNames + nodes + "DIRECT").distinct())
        return Preview(result, nodes.size, providers.size)
    }
}
