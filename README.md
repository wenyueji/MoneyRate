# MoneyRate —— 🇲🇾 MYR-CNY Exchange Rate Monitor (Android)
一款专为大马华人/留学生设计的汇率监控工具。自动抓取 Money Master 和 Jags Money 实时数据，支持全天候后台监控与双向波动提醒。

---

## 🌟 核心功能 (Features)
* **双源同步 (Dual Source)**：同时支持 Money Master (Jsoup 直接抓取) 和 Jags Money (WebView 渲染抓取)。
* **双向波动提醒 (Bidirectional Alerts)**：汇率上涨或下跌均会触发通知，不再错过最佳换汇时机。
* **昨日目标锁死 (Baseline Locking)**：每日凌晨自动将前一日收盘价锁死为今日基准，提供最精准的日内波动参考。
* **高精度显示**：支持 100 CNY ↔ MYR 以及 1 MYR ↔ CNY 的双向换算，精度精确至小数点后 4 位。
* **后台静默运行**：基于 `WorkManager` 实现，即便 App 退出也能在后台持续监控。

## 🛠️ 技术栈 (Tech Stack)
* **Kotlin** + **Coroutine**：异步任务处理，代码简洁高效。
* **WorkManager**：可靠的后台定时任务调度。
* **Jsoup**：快速解析 HTML 静态数据。
* **WebView + evaluateJavascript**：处理 Jags Money 等需要 JS 渲染的动态网页。
* **ViewBinding**：类型安全的视图操作。

## 📸 运行截图 (Screenshots)
<img src="https://github.com/user-attachments/assets/6e17ce1b-b541-4222-b9ee-ab116d6fe54f" width="25%" alt="App运行截图-主界面" />
<img src="https://github.com/user-attachments/assets/9c18eb38-190a-4f62-a1b4-4d1fb3619510" width="40%" alt="App运行截图-通知栏" />


## 🚀 核心逻辑说明 (Core Logic)
本项目最核心的逻辑在于 `checkRateAndNotify`。它不是简单地对比上一次数据，而是：
1.  **凌晨切换**：发现日期更新时，将 `last_live_rate`（昨晚最后价格）存入 `yesterday_target`。
2.  **全天对比**：今天的所有刷新都去挑战这个 `yesterday_target`，只要偏离超过 `0.0001` 即触发通知。

## 📝 开源协议 (License)
本项目采用 [MIT License](LICENSE) 开源。你可以自由地分发、修改和商用，但请保留原作者版权声明。

---

### ⚠️ 免责声明 (Disclaimer)
本项目抓取的数据仅供参考，实际换汇请以当地柜台或官方牌价为准。作者不对因汇率波动导致的任何财务损失负责。
