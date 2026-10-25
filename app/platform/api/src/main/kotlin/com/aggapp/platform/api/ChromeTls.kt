package com.aggapp.platform.api

import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion

/**
 * TLS 指纹一致性（Chrome 移动端对齐）。
 *
 * 背景：请求头已带真实 Chrome 移动 UA（见 [DeviceUa]），但 OkHttp 默认 ConnectionSpec
 * 的 ClientHello（密码套件列表/顺序）是 Java/Conscrypt 形态——风控做 UA↔TLS 指纹交叉
 * 比对即可识破"浏览器 UA + 非浏览器握手"。本对象把各平台客户端的密码套件与
 * TLS 版本对齐 Chrome 移动端 ClientHello（顺序一致）。
 *
 * 局限（如实）：Conscrypt 不暴露 ClientHello 扩展顺序/GREASE/签名算法等控制，
 * 严格 JA3/JA4 全量比对仍可区分——本改动消除的是「默认 OkHttp 指纹」这一最粗粒度
 * 特征，让识别成本从"一眼默认值"提到"需细粒度指纹比对"，不是完全伪装成 Chrome。
 * 彻底方案需换 Cronet（Chromium 网络栈，真 Chrome 指纹），架构级改动暂不做。
 */
object ChromeTls {

    /** Chrome 移动端 ClientHello 密码套件（顺序对齐；OkHttp 会静默滤掉平台不支持的项） */
    private val CHROME_SUITES = listOf(
        CipherSuite.TLS_AES_128_GCM_SHA256,
        CipherSuite.TLS_AES_256_GCM_SHA384,
        CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
    )

    val connectionSpec: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .cipherSuites(*CHROME_SUITES.toTypedArray())
        .build()

    /**
     * debug 诊断：请求 tls.peet.ws 回显本进程的 TLS 指纹（JA3/JA4/协商版本），
     * 写 AppLog 验证 ConnectionSpec 生效。仅 debug 包由 AggApp 调用，release 不触发；
     * 诊断客户端与平台客户端同规格（同 connectionSpecs + 同 UA 拦截器）。
     */
    fun logFingerprintDiagnostic() {
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectionSpecs(listOf(connectionSpec))
                    .addInterceptor(DeviceUa.interceptor)
                    .build()
                client.newCall(Request.Builder().url("https://tls.peet.ws/api/all").build())
                    .execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val field = { name: String ->
                            Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1).orEmpty()
                        }
                        AppLog.d(
                            "TlsDiag",
                            "tls=${field("tls_version")} h2=${field("http_version")} " +
                                "JA3=${field("ja3_hash")} JA4=${field("ja4")} ua=${DeviceUa.chromeMobile?.take(50)}",
                        )
                    }
            } catch (e: Exception) {
                AppLog.w("TlsDiag", "指纹诊断失败: ${e.message}")
            }
            // 本地 ClientHello 校验通道：adb reverse tcp:18443 tcp:18443 后连 PC 抓包解析
            // （国内网络 peet.ws 常被重置；证书校验失败属预期，PC 侧只看 ClientHello 字节）
            try {
                OkHttpClient.Builder()
                    .connectionSpecs(listOf(connectionSpec))
                    .build()
                    .newCall(Request.Builder().url("https://127.0.0.1:18443/api/all").build())
                    .execute().use { }
                AppLog.d("TlsDiag", "本地诊断端口连接结束")
            } catch (e: Exception) {
                AppLog.d("TlsDiag", "本地诊断端口连接已发出（${e.javaClass.simpleName}，ClientHello 由 PC 侧解析）")
            }
        }.start()
    }
}
