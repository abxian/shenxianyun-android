package com.github.kr328.clash

internal fun isOtherSubscriptionCommand(value: String): Boolean =
    value.trim().equals("other", ignoreCase = true)

/** Only structured service codes enter user-facing messages. Never print remote details. */
internal class ManagedImportRejected(val reason: String) : IllegalStateException()

internal fun importRejectionReason(status: Int, code: String): String = when {
    code.isNotBlank() -> code
    status == 410 -> "ticket_expired"
    status == 429 || status >= 500 -> "busy"
    else -> "unavailable"
}

internal enum class CodeImportStage(val label: String, val failure: String) {
    Verify("验证提取码", "暂时无法验证提取码，请稍后重试"),
    Ticket("申请导入票据", "提取码有效，但暂时无法申请导入票据，请稍后重试"),
    Authorize("设备授权", "提取码已通过验证，但设备授权失败，请重试"),
    Download("下载配置", "设备已通过验证，但暂时无法获取配置，请稍后重试"),
    Install("配置校验和安装", "配置已下载，但校验或安装失败，请联系管理员检查订阅配置"),
    Save("保存设备凭据", "配置已安装，但无法保存设备凭据，请重试"),
}

internal fun codeImportFailureMessage(
    reason: String? = null,
    networkFailure: Boolean = false,
    stage: CodeImportStage? = null,
): String =
    when (reason) {
        "invalid_code" -> "提取码错误或不存在，请检查后重新输入"
        "expired" -> "提取码已过期，请续费后重试，或输入其他提取码"
        "code_disabled" -> "提取码已停用，请联系管理员"
        "traffic_limit" -> "流量额度已用尽，请续费或等待额度恢复"
        "device_limit" -> "同时在线设备已达上限，请断开其他设备后重试"
        "bound_limit" -> "绑定设备已达上限，请联系管理员解绑旧设备"
        "device_identity_conflict" -> "设备绑定信息不一致，请联系管理员核对"
        "credential_rejected" -> "下载配置时设备凭据被拒绝，请重新导入提取码"
        "ticket_expired" -> "验证凭证已失效，请重新点击导入"
        "busy" -> "服务暂忙，请稍后重试"
        else -> when {
            networkFailure && stage != null -> "${stage.label}时网络连接失败或超时，请检查网络后重试"
            networkFailure -> "网络连接失败或超时，请检查网络后重试；原配置已保留"
            stage != null -> stage.failure
            else -> "配置未导入，请稍后重试"
        }
    }
