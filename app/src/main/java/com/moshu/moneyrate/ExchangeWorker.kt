package com.moshu.moneyrate

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.*

class ExchangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val CHANNEL_ID = "rate_alert_channel"

    override suspend fun doWork(): Result {
        System.out.println("DEBUG_MONEY: >>> 开始双源监控任务 [${Date()}] <<<")

        // 1. 处理 Money Master (直接 Jsoup 抓取)
        try {
            val mmRaw = ExchangeScraper.fetchMoneyMaster("CNY")
            val mmRate = mmRaw.toDoubleOrNull() ?: 0.0
            if (mmRate > 0) {
                checkRateAndNotify("Money Master", mmRate)
            }
        } catch (e: Exception) {
            System.out.println("DEBUG_MONEY: Money Master 抓取失败: ${e.message}")
        }

        // 2. 处理 Jags Money (使用 WebView 挂起抓取)
        try {
            val jagsScraper = JagsWebViewScraper(applicationContext)
            val jagsRaw = jagsScraper.fetchJagsRateSync("CNY")
            val jagsRate = jagsRaw.toDoubleOrNull() ?: 0.0

            if (jagsRate > 0) {
                checkRateAndNotify("Jags Money", jagsRate)
            } else {
                System.out.println("DEBUG_MONEY: Jags 抓取结果无效: $jagsRaw")
            }
        } catch (e: Exception) {
            System.out.println("DEBUG_MONEY: Jags Money 抓取崩溃: ${e.message}")
        }

        return Result.success()
    }

    /**
     * 核心逻辑：对比昨日目标并实现双向通知
     */
    private fun checkRateAndNotify(source: String, currentRate: Double) {
        val prefs = applicationContext.getSharedPreferences("ExchangePrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        val savedDate = prefs.getString("recorded_date_$source", "")
        val yesterdayTarget = prefs.getFloat("yesterday_target_$source", 0f).toDouble()
        val lastLiveRate = prefs.getFloat("last_live_rate_$source", 0f).toDouble()

        val editor = prefs.edit()

        // --- 场景 A：今天第一次运行 (凌晨切换基准) ---
        if (todayStr != savedDate) {
            System.out.println("DEBUG_MONEY: [$source] 切换日期，昨日最终价 $lastLiveRate 锁死为今日目标")

            editor.putFloat("yesterday_target_$source", lastLiveRate.toFloat())
            editor.putString("recorded_date_$source", todayStr)
            editor.putFloat("last_live_rate_$source", currentRate.toFloat())
            editor.apply()

            // 每天第一跳发个开盘状态提醒
            if (lastLiveRate > 0) {
                sendTrendNotification(source, lastLiveRate, currentRate, isOpening = true)
            }
        }
        // --- 场景 B：今天的持续双向监控 ---
        else {
            // 只要偏离昨日目标（锁死的基准）超过微小阈值就通知
            if (yesterdayTarget > 0 && Math.abs(currentRate - yesterdayTarget) > 0.0001) {
                sendTrendNotification(source, yesterdayTarget, currentRate, isOpening = false)
            }

            // 更新今天的实时汇率，为明天做准备
            editor.putFloat("last_live_rate_$source", currentRate.toFloat())
            editor.apply()
        }
    }

    /**
     * 发送带方向箭头的通知
     */
    private fun sendTrendNotification(source: String, targetRate: Double, currentRate: Double, isOpening: Boolean) {
        val diff = currentRate - targetRate
        val trendIcon = if (diff > 0) "▲ 涨" else "▼ 跌"

        // 计算反向价值
        val cnyValue = if (currentRate > 0) 100.0 / currentRate else 0.0
        val formattedCny = String.format("%.4f", cnyValue)

        val title = if (isOpening) "[$source] 今日开盘" else "[$source] 波动提醒 ($trendIcon)"

        // 构建通知内容
        val content = "1 CNY = ${String.format("%.4f", currentRate)} MYR\n" +
                "100 MYR ≈ ${formattedCny} CNY\n" +
                "较昨日 $trendIcon ${String.format("%.4f", Math.abs(diff))}"

        createNotificationChannel()

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content) // 这是折叠时的预览文字
            // --- 核心修复：添加下面这一行 ---
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            // -----------------------------
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(source.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            System.out.println("DEBUG_MONEY: 缺少通知权限")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "汇率提醒渠道"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}