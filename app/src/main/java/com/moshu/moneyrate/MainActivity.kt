package com.moshu.moneyrate

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.moshu.moneyrate.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var jagsScraper: JagsWebViewScraper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jagsScraper = JagsWebViewScraper(this)

        binding.btnRefresh.setOnClickListener {
            startExchangeUpdate()
        }

        // 初始化显示（展示上次保存的数据）
        displaySavedRates()

        // 启动后台任务
        setupDailyWork()
    }

    /**
     * 从存储中读取并显示汇率（当前 vs 昨日目标）
     */
    @SuppressLint("SetTextI18n")
    private fun displaySavedRates() {
        val prefs = getSharedPreferences("ExchangePrefs", Context.MODE_PRIVATE)

        // --- Money Master 处理 ---
        val mmTarget = prefs.getFloat("yesterday_target_Money Master", 0f).toDouble()
        val mmLast = prefs.getFloat("last_live_rate_Money Master", 0f).toDouble()

        // 计算反向汇率：100 MYR = 多少 CNY
        val mmCnyValue = if (mmLast > 0) 100.0 / mmLast else 0.0

        // 修改为 %.4f 以显示四位小数
        binding.tvMasterRate.text = "当前: ${format(mmLast)} (1马币 ≈ ${String.format("%.4f", mmCnyValue)}元)\n昨日目标: ${format(mmTarget)}"

        // --- Jags Money 处理 ---
        val jagsTarget = prefs.getFloat("yesterday_target_Jags Money", 0f).toDouble()
        val jagsLast = prefs.getFloat("last_live_rate_Jags Money", 0f).toDouble()

        val jagsCnyValue = if (jagsLast > 0) 100.0 / jagsLast else 0.0

        binding.tvJagsRate.text = "当前: ${format(jagsLast)} (1马币 ≈ ${String.format("%.4f", jagsCnyValue)}元)\n昨日目标: ${format(jagsTarget)}"
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun startExchangeUpdate() {
        binding.tvStatus.text = "状态：同步中..."
        binding.btnRefresh.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main) {
            // 1. Money Master 抓取 (IO 线程)
            val mmResult = withContext(Dispatchers.IO) {
                ExchangeScraper.fetchMoneyMaster("CNY")
            }
            val mmRate = mmResult.toDoubleOrNull() ?: 0.0
            if (mmRate > 0) updateRateStorage("Money Master", mmRate)

            // 2. Jags Money 抓取 (WebView 挂起)
            val jagsResult = jagsScraper.fetchJagsRateSync("CNY")
            val jagsRate = jagsResult.toDoubleOrNull() ?: 0.0
            if (jagsRate > 0) updateRateStorage("Jags Money", jagsRate)

            // 3. 刷新 UI 显示
            displaySavedRates()
            binding.tvStatus.text = "更新完毕: ${getCurrentTime()}"
            binding.btnRefresh.isEnabled = true
        }
    }

    /**
     * 仅更新数据存储，不发送通知（通知交给 Worker）
     */
    private fun updateRateStorage(source: String, currentRate: Double) {
        val prefs = getSharedPreferences("ExchangePrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        val savedDate = prefs.getString("recorded_date_$source", "")
        val lastLiveRate = prefs.getFloat("last_live_rate_$source", 0f).toDouble()
        val editor = prefs.edit()

        // 如果手动刷新时发现跨天了，同步更新昨日目标
        if (todayStr != savedDate) {
            editor.putFloat("yesterday_target_$source", lastLiveRate.toFloat())
            editor.putString("recorded_date_$source", todayStr)
        }

        editor.putFloat("last_live_rate_$source", currentRate.toFloat())
        editor.apply()
    }

    private fun format(value: Any): String = String.format("%.4f", value)

    private fun getCurrentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun setupDailyWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val repeatingRequest = PeriodicWorkRequestBuilder<ExchangeWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyExchangeCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
    }
}