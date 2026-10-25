package com.aggapp.platform.api

import okhttp3.Interceptor

/**
 * 设备真实 UA（风控一致性）。
 *
 * 各平台请求原先带写死的固定 UA（如 Nexus 5/Android 6 + Chrome 128 的不可能组合、
 * 手机 App 发 Windows 桌面 UA），与设备实际形态矛盾且全网用户同串，是平台风控的
 * 显著识别特征。App 启动时（AggApp.onCreate，主线程）用 WebSettings.getDefaultUserAgent
 * 采集本机 WebView UA，清洗为本机标准移动 Chrome 形态后供各适配器取用。
 *
 * 清洗规则与 WebViewLoginDialog 的实测结论一致：去掉 "; wv" 标记（京东 jcap/拼多多
 * 风控识别 WebView 环境），再去掉 WebView 特有的 " Version/4.0"。
 */
object DeviceUa {

    /** 本机 WebView 原始 UA（AggApp 启动时采集） */
    @Volatile
    var rawWebViewUa: String? = null

    /** 本机标准移动 Chrome UA；未采集/清洗后为空返回 null（调用方回退各自实测可用常量） */
    val chromeMobile: String?
        get() = rawWebViewUa?.let { raw ->
            raw.replace("; wv", "").replace(" Version/4.0", "").trim().ifEmpty { null }
        }

    /** 微信内置浏览器形态（微信登录链路）：本机移动 Chrome UA + MicroMessenger 后缀 */
    val wechatMobile: String?
        get() = chromeMobile?.plus(" MicroMessenger/8.0.49")

    /** 兜底 UA（DeviceUa 未初始化时拦截器取值；各适配器显式 UA 自带实测兜底常量） */
    private const val FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /**
     * 默认 UA 拦截器：请求未显式携带浏览器形态 UA（缺失或 OkHttp 默认 okhttp/x.x）时补
     * 本机移动 Chrome UA——顺丰驿收发/如意邻里等未显式设 UA 的请求原先暴露 okhttp/x.x，
     * 是最直白的非浏览器特征。适配器显式设置的 UA（微信登录链路等）不受影响。
     * 各平台 OkHttpClient 构建时挂载（同 NetDebug.interceptor 用法）。
     */
    val interceptor: Interceptor = Interceptor { chain ->
        val req = chain.request()
        val cur = req.header("User-Agent").orEmpty()
        val newReq = if (cur.isBlank() || cur.startsWith("okhttp", ignoreCase = true)) {
            req.newBuilder().header("User-Agent", chromeMobile ?: FALLBACK_UA).build()
        } else {
            req
        }
        chain.proceed(newReq)
    }
}
