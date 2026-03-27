package com.moshu.moneyrate

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Jags Money 网页抓取器
 * 适配 WorkManager 协程环境，支持异步转同步挂起
 */
class JagsWebViewScraper(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 核心挂起函数：在后台任务中直接调用 val rate = fetchJagsRateSync("CNY")
     */
    suspend fun fetchJagsRateSync(code: String = "CNY"): String = suspendCoroutine { continuation ->
        // WebView 必须在主线程创建和操作
        handler.post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true

            // 状态锁，防止多次回调导致协程崩溃
            var isCallbackDone = false

            // 内部清理与返回函数
            fun safeFinish(result: String) {
                if (!isCallbackDone) {
                    isCallbackDone = true
                    handler.removeCallbacksAndMessages(null) // 移除所有延时任务
                    webView.stopLoading()
                    webView.destroy()
                    continuation.resume(result) // 恢复协程并返回数据
                }
            }

            // 1. 设置 25 秒总超时限制
            handler.postDelayed({ safeFinish("请求超时") }, 25000)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 2. 网页加载完后，留出 5 秒给 JS 渲染汇率表
                    handler.postDelayed({
                        view?.evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { html ->
                            val parsedRate = parseHtml(html, code)
                            safeFinish(parsedRate)
                        }
                    }, 5000)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // 只有主页加载失败才算错，忽略一些小的资源加载错误
                    if (request?.isForMainFrame == true) {
                        safeFinish("网络连接错误")
                    }
                }
            }

            // 3. 开始加载
            webView.loadUrl("https://www.jagsmoney.com/daily-rates/")
        }
    }

    /**
     * HTML 解析逻辑
     */
    private fun parseHtml(rawHtml: String?, code: String): String {
        if (rawHtml == null || rawHtml == "null" || rawHtml.isEmpty()) return "0.0"

        // 清理 WebView 返回的转义字符
        val cleanHtml = rawHtml.replace("\\u003C", "<")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        return try {
            val doc = Jsoup.parse(cleanHtml)

            // 寻找包含特定货币代码（如 CNY）的行
            val row = doc.select("tr").find {
                it.text().contains(code, ignoreCase = true)
            }

            if (row != null) {
                val rateText = row.select("td.curr_val").first()?.text()
                val unitText = row.select("li.curr_unit").text() ?: ""

                val rawRate = rateText?.toDoubleOrNull() ?: 0.0

                // 关键修正：Jags 通常对 CNY 使用 100 单位（例如显示 61.20）
                // 我们需要将其换算为 1 单位（0.6120）以便和 Money Master 对齐
                val unitDivisor = if (unitText.contains("100")) 100.0 else 1.0

                if (rawRate > 0) {
//                    String.format(Locale.US, "%.4f", rawRate / unitDivisor)
                    String.format(Locale.US, "%.4f", rawRate)
                } else {
                    "0.0"
                }
            } else {
                "0.0" // 未找到该货币数据
            }
        } catch (e: Exception) {
            "0.0" // 解析异常
        }
    }
}