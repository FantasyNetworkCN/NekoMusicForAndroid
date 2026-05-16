package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VipPricingItem(
    val id: Int,
    val months: Int,
    val days: Int,
    val priceYuan: Double,
    val sortOrder: Int = 0,
    val updatedAt: String = ""
)

@Serializable
data class VipPricingEnvelope(
    val success: Boolean,
    val message: String = "",
    val data: List<VipPricingItem> = emptyList()
)

@Serializable
data class VipPayCreateRequest(
    val pricingId: Int,
    val payType: String
)

@Serializable
data class VipPayCreateData(
    val outTradeNo: String = "",
    @SerialName("payurl") val payUrl: String? = null,
    @SerialName("payurl2") val payUrl2: String? = null,
    @SerialName("qrcode") val qrCode: String? = null,
    val img: String? = null
)

@Serializable
data class VipPayCreateEnvelope(
    val success: Boolean,
    val message: String? = null,
    val data: VipPayCreateData? = null
)

class VipApi(private val bearerToken: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        engine { config { preferHttp2AlpnOverHttp1() } }
        install(ContentNegotiation) { json(json) }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    private val baseUrl = UrlConfig.getBaseUrl()

    private fun authHeader(): String {
        val t = bearerToken.trim()
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    suspend fun fetchPricing(): VipPricingEnvelope {
        return try {
            val response = client.get("$baseUrl/api/vip/pricing")
            response.body()
        } catch (e: Exception) {
            Log.e("VipApi", "获取价目失败${e.protocolLogSuffixOrEmpty()}", e)
            VipPricingEnvelope(success = false, message = e.message ?: "网络错误")
        }
    }

    suspend fun createPayOrder(pricingId: Int, payType: String): VipPayCreateEnvelope {
        return try {
            val response = client.post("$baseUrl/api/vip/pay/create") {
                contentType(ContentType.Application.Json)
                header("Authorization", authHeader())
                setBody(VipPayCreateRequest(pricingId = pricingId, payType = payType))
            }
            val text = response.bodyAsText()
            json.decodeFromString<VipPayCreateEnvelope>(text)
        } catch (e: Exception) {
            Log.e("VipApi", "创建支付订单失败${e.protocolLogSuffixOrEmpty()}", e)
            VipPayCreateEnvelope(success = false, message = e.message ?: "网络错误", data = null)
        }
    }
}
