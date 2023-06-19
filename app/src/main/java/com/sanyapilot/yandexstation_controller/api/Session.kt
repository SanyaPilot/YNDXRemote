package com.sanyapilot.yandexstation_controller.api

import android.annotation.SuppressLint
import android.util.Log
import com.sanyapilot.yandexstation_controller.TAG
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

data class LoginResponse(
    val ok: Boolean,
    val errorId: Errors? = null
)

data class RequestResponse(
    val ok: Boolean,
    val errorId: Errors? = null,
    val response: Response? = null
)

enum class Errors {
    INVALID_CODE, INVALID_TOKEN, TIMEOUT, BAD_REQUEST, INTERNAL_SERVER_ERROR
}

enum class Methods {
    GET, POST, PUT
}

object Session {
    val client = OkHttpClient.Builder()
        .build()
    private val unsafeClient = getUnsafeOkHttpClient()
    lateinit var accessToken: String

    // https://stackoverflow.com/questions/50961123/how-to-ignore-ssl-error-in-okhttp
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts: Array<TrustManager> = arrayOf(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate?> {
                        return arrayOfNulls(0)
                    }
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun login(code: Int): LoginResponse {
        val request = Request.Builder()
            .url("https://testing.yndxfuck.ru/submit_auth_code")
            .post("{\"code\": \"$code\"}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use {
            if (it.code == 400) {
                return LoginResponse(false, Errors.INVALID_CODE)
            } else if (it.code == 500) {
                return LoginResponse(false, Errors.INTERNAL_SERVER_ERROR)
            }
            val parsed = JSONObject(it.body.string())
            accessToken = parsed.getString("token")
        }
        return LoginResponse(true)
    }
    fun get(url: String): RequestResponse {
        return doRequest(Methods.GET, url)
    }
    fun post(url: String, body: RequestBody?): RequestResponse {
        return doRequest(Methods.POST, url, body)
    }
    fun put(url: String, body: RequestBody?): RequestResponse {
        return doRequest(Methods.PUT, url, body)
    }
    fun wsConnect(url: String, listener: WebSocketListener) {
        val request = Request.Builder()
            .url(url)
            .build()

        unsafeClient.newWebSocket(request, listener)
    }
    private fun doRequest(method: Methods, url: String, body: RequestBody? = null): RequestResponse {
        val request = Request.Builder()
            .url(url)

        // All requires accessToken
        request.addHeader("yf-auth-token", accessToken)

        if (method == Methods.POST) {
            request.post(body!!)
        } else if (method == Methods.PUT) {
            request.put(body!!)
        }

        try {
            client.newCall(request.build()).execute().use {
                when (it.code) {
                    400 -> {
                        // Invalid device?
                        return RequestResponse(false, Errors.BAD_REQUEST)
                    }
                    401 -> {
                        // Token is dead, no refresh flow for now
                        return RequestResponse(false, Errors.INVALID_TOKEN)
                    }
                    500 -> {
                        return RequestResponse(false, Errors.INTERNAL_SERVER_ERROR)
                    }
                    else -> return RequestResponse(true, response = it)
                }
            }
        } catch (e: IOException) {
            return RequestResponse(false, Errors.TIMEOUT)
        }
    }
}