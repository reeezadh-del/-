package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiCoreEngine"

enum class AiExpertPersona(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val modelName: String,
    val systemInstruction: String
) {
    VPN_OPTIMIZER(
        id = "vpn_optimizer",
        titleFa = "هسته هوشمند VPN و خودارتقایی",
        subtitleFa = "تحلیل فیلترینگ، یافتن مسیرهای عبور، تنظیم Fragment و بهینه‌سازی سرورها",
        modelName = "gemini-3.8-flash",
        systemInstruction = "تو هسته هوش مصنوعی داخلی اپلیکیشن سپر نت هستی. به فارسی دقیق، فنی و کاربردی درباره پروتکل‌های VLESS Reality، Hysteria2، TUIC، ShadowTLS، WireGuard WARP، تکه‌تکه‌سازی TLS و DoH راهنمایی و تحلیل ارائه بده."
    ),
    GENERAL_AI(
        id = "general",
        titleFa = "دستیار جامع هوش مصنوعی",
        subtitleFa = "پاسخ‌گویی سریع، تحقیق، خلاصه‌سازی و ترجمه چندزبانه",
        modelName = "gemini-3.8-flash",
        systemInstruction = "تو یک دستیار هوشمند جامع، دقیق و چندزبانه هستی. پاسخ‌ها را شفاف، منظم و به زبان پرسش کاربر (ترجیحاً فارسی روان) ارائه بده."
    ),
    CODING_AI(
        id = "coding",
        titleFa = "دستیار تخصصی برنامه‌نویسی",
        subtitleFa = "تحلیل کد، شبکه، اندروید، پایتون و ساخت اسکریپت‌های سرور",
        modelName = "gemini-3.8-flash",
        systemInstruction = "تو یک مهندس ارشد نرم‌افزار و شبکه هستی. کدهای تمیز، امن و قابل اجرا بنویس و نکات فنی را کوتاه و دقیق توضیح بده."
    ),
    FAST_LITE(
        id = "fast_lite",
        titleFa = "پاسخ‌دهنده فوق‌سریع (Flash Lite)",
        subtitleFa = "پاسخ‌های آنی با کمترین مصرف ترافیک و تأخیر",
        modelName = "gemini-3.5-flash-lite",
        systemInstruction = "پاسخ‌ها را بسیار کوتاه، سریع و دقیق بده."
    )
}

data class AiUpgradePlan(
    val diagnosisFa: String,
    val recommendedMode: String,
    val recommendedFragmentSize: Int,
    val recommendedFragmentDelayMs: Int,
    val recommendedMultiplex: Boolean,
    val recommendedDnsId: String,
    val upgradeActionsFa: List<String>
)

data class AiServicePortal(
    val id: String,
    val name: String,
    val subtitleFa: String,
    val url: String
)

object AiCoreEngine {

    val GLOBAL_AI_PORTALS = listOf(
        AiServicePortal("chatgpt", "ChatGPT (OpenAI)", "دسترسی مستقیم از طریق تونل امن", "https://chatgpt.com/"),
        AiServicePortal("gemini", "Google Gemini", "هوش مصنوعی گوگل", "https://gemini.google.com/"),
        AiServicePortal("claude", "Anthropic Claude", "مدل تحلیلی و کدنویسی کلود", "https://claude.ai/"),
        AiServicePortal("perplexity", "Perplexity AI", "موتور جستجوی هوشمند با منبع", "https://www.perplexity.ai/"),
        AiServicePortal("deepseek", "DeepSeek AI", "مدل استدلال و برنامه‌نویسی", "https://chat.deepseek.com/"),
        AiServicePortal("grok", "xAI Grok", "دستیار هوش مصنوعی ایکس", "https://grok.com/")
    )

    private val chatModelFlash by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val chatModelLite by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val upgradeSchema = Schema.obj(
        properties = mapOf(
            "diagnosisFa" to Schema.string(description = "Concise Persian diagnosis of network censorship conditions"),
            "recommendedMode" to Schema.enumeration(
                values = listOf("AI_AUTO_DISCOVERY", "PROXY_CHAIN", "AUTO_BEST_SERVER", "DOUBLE_WARP_CHAIN", "DPI_FRAGMENT_DOH"),
                description = "Best VPN connection mode for the current network"
            ),
            "recommendedFragmentSize" to Schema.integer(description = "Optimal TLS fragment chunk size in bytes between 10 and 64"),
            "recommendedFragmentDelayMs" to Schema.integer(description = "Optimal delay between TLS fragments in ms between 4 and 35"),
            "recommendedMultiplex" to Schema.boolean(description = "Whether H2Mux padding should be enabled"),
            "recommendedDnsId" to Schema.enumeration(
                values = listOf("cloudflare", "google", "quad9", "adguard"),
                description = "Best DoH preset id"
            ),
            "upgradeActionsFa" to Schema.array(
                items = Schema.string(),
                description = "List of 2 to 4 concise Persian upgrade actions applied"
            )
        )
    )

    private val optimizerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = upgradeSchema
            }
        )
    }

    fun streamChat(
        persona: AiExpertPersona,
        historyPairs: List<Pair<Boolean, String>>,
        userPrompt: String
    ): Flow<String> = flow {
        val model = if (persona == AiExpertPersona.FAST_LITE) chatModelLite else chatModelFlash
        val historyContents = buildList {
            add(content("user") { text("دستورالعمل سیستمی: ${persona.systemInstruction}") })
            add(content("model") { text("تأیید شد. آماده‌ام.") })
            historyPairs.takeLast(8).forEach { (isUser, msg) ->
                add(content(if (isUser) "user" else "model") { text(msg) })
            }
        }
        val chat = model.startChat(history = historyContents)
        withTimeout(60_000L) {
            chat.sendMessageStream(userPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    emit(part)
                }
            }
        }
    }

    suspend fun generateSelfUpgradePlan(telemetryPrompt: String): Result<AiUpgradePlan> {
        return try {
            val response = withTimeout(45_000L) {
                optimizerModel.generateContent(telemetryPrompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("پاسخی از هسته هوش مصنوعی دریافت نشد.")
            val json = JSONObject(rawJson)
            val actionsArray = json.optJSONArray("upgradeActionsFa")
            val actions = if (actionsArray != null) {
                (0 until actionsArray.length()).mapNotNull { actionsArray.optString(it).takeIf { s -> s.isNotBlank() } }
            } else {
                emptyList()
            }
            Result.success(
                AiUpgradePlan(
                    diagnosisFa = json.optString("diagnosisFa", "پیکربندی شبکه تحلیل و ارتقا یافت."),
                    recommendedMode = json.optString("recommendedMode", "AI_AUTO_DISCOVERY"),
                    recommendedFragmentSize = json.optInt("recommendedFragmentSize", 16).coerceIn(10, 64),
                    recommendedFragmentDelayMs = json.optInt("recommendedFragmentDelayMs", 12).coerceIn(4, 35),
                    recommendedMultiplex = json.optBoolean("recommendedMultiplex", true),
                    recommendedDnsId = json.optString("recommendedDnsId", "cloudflare"),
                    upgradeActionsFa = actions
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI self-upgrade analysis timed out", e)
            Result.failure(IllegalStateException("زمان تحلیل هوش مصنوعی به پایان رسید. دوباره تلاش کنید."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI self-upgrade analysis failed", e)
            Result.failure(e)
        }
    }
}
