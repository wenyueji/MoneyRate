package com.moshu.moneyrate
import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup

object ExchangeScraper {
//    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    fun fetchRates(code: String): String {
        val mm = fetchMoneyMaster(code)
//        return "Master: $mm | Jags: $jags"
        return "Master: $mm"
    }

     fun fetchMoneyMaster(code: String): String {
        return try {
            val doc = Jsoup.connect("http://www.mymoneymaster.com.my/Home/full_rate_board")
                .userAgent(USER_AGENT).timeout(10000).get()
            val tbody = doc.getElementById("ajaxmobval")
            val row = tbody?.select("tr:contains($code)")?.first()
            if (row != null) {
                val tds = row.select("td")
                val rawRate = tds[1].text().toDoubleOrNull() ?: 0.0 // 取 Buy 价
                val unitText = tds[0].text()
//                val finalRate = if (unitText.contains("100")) rawRate / 100 else rawRate
                val finalRate = rawRate
                String.format("%.4f", finalRate)
            } else "N/A"
        } catch (e: Exception) { "Err" }
    }


}