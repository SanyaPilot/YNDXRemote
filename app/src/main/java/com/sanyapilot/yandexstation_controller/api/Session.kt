@file:Suppress("PropertyName")  // Models require underscores

package com.sanyapilot.yandexstation_controller.api

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.IOException
import java.net.ConnectException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class LoginResponse(
    val ok: Boolean,
    val errorId: Errors? = null
)

data class RequestResponse(
    val ok: Boolean,
    val errorId: Errors? = null,
    val response: Response?
)

enum class Errors {
    INVALID_ACCOUNT, INVALID_PASSSWD, INVALID_TOKEN,
    NEEDS_PHONE_CHALLENGE, TIMEOUT, BAD_REQUEST,
    INTERNAL_SERVER_ERROR, CONNECTION_ERROR, UNKNOWN
}

enum class Methods {
    GET, POST, PUT, DELETE
}

@Serializable
data class TrackIDResponse (
    val status: String,
    val can_register: Boolean? = false,
    val track_id: String? = null
)

@Serializable
data class CommitPasswordResponse (
    val status: String,
    val redirect_url: String? = null
)

@Serializable
data class TokenBySessionIDResponse (
    val status: String,
    val access_token: String? = null
)

@Serializable
data class AuthXTokenResponse (
    val status: String,
    val passport_host: String? = null,
    val track_id: String? = null
)

@Serializable
data class QuasarStorageResponse (
    val storage: S
) {
    @Serializable
    data class S (
        val csrfToken2: String,
        val user: U
    ) {
        @Serializable
        data class U (
            val uid: String
        )
    }
}

@Serializable
data class PersistentCookie (
    val domain: String,
    val path: String,
    val cookie: String
)

class YandexCookieJar(cookies: List<PersistentCookie>): CookieJar {
    private var storage = mutableListOf<Cookie>()

    init {
        // Parse cookies
        for (c in cookies) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(c.domain)
                .encodedPath(c.path)
                .build()
            val parsed = Cookie.parse(url, c.cookie)
            if (parsed != null) {
                storage.add(parsed)
            } else {
                Log.e("YandexCookieJar", "Failed to parse cookie from ${c.domain}!")
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        storage.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesToSend = mutableListOf<Cookie>()
        for (cookie in storage){
            if (cookie.expiresAt < System.currentTimeMillis()) {
                storage.remove(cookie)
            } else if (cookie.matches(url)){
                cookiesToSend.add(cookie)
            }
        }
        return cookiesToSend
    }
    fun getYandexCookiesString(): String {
        var data = ""
        for (cookie in storage){
            if (cookie.domain.endsWith("yandex.ru")) {
                data += "${cookie.name}=${cookie.value}; "
            }
        }
        data.dropLast(2)
        return data
    }
    fun clearCookies() {
        storage.clear()
    }
    fun dumpCookies(): List<PersistentCookie> {
        val result = mutableListOf<PersistentCookie>()
        for (cookie in storage) {
            if (cookie.expiresAt < System.currentTimeMillis()) {
                storage.remove(cookie)
                continue
            }
            result.add(PersistentCookie(
                domain = cookie.domain,
                path = cookie.path,
                cookie = cookie.toString()
            ))
        }
        return result
    }
}

object Session {
    private const val TAG = "QSession"
    private lateinit var cookieJar: YandexCookieJar
    private lateinit var cookieDumpCallback: (String) -> Unit
    private lateinit var client: OkHttpClient
    private val unsafeClient = getUnsafeOkHttpClient()
    private lateinit var trackId: String
    private lateinit var csrf: String
    private lateinit var _xToken: String
    val xToken: String get() = _xToken

    private val json = Json { ignoreUnknownKeys = true }

    fun init(token: String?, cookies: String?, dumpCallback: (String) -> Unit) {
        token?.let { _xToken = it }
        cookieJar = if (cookies != null) {
            val parsed = json.decodeFromString<List<PersistentCookie>>(cookies)
            YandexCookieJar(parsed)
        } else {
            YandexCookieJar(listOf())
        }
        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        cookieDumpCallback = dumpCallback
    }

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

    fun login(username: String, password: String): LoginResponse {
        try {
            // Get CSRF token
            var csrf: String
            var request = Request.Builder()
                .url("https://passport.yandex.ru/am?app_platform=android")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.code != 200) {
                    return LoginResponse(false, errorId = Errors.UNKNOWN)
                }
                val reCSRF = Regex("\"csrf_token\" value=\"([^\"]+)\"")
                val body = resp.body.string()
                val reRes = reCSRF.find(body) ?: return LoginResponse(false, errorId = Errors.UNKNOWN)
                csrf = reRes.value.split('=')[1].removeSurrounding("\"")
                Log.d(TAG, "CSRF $csrf")
            }

            // Get track ID
            val body = FormBody.Builder()
                .add("csrf_token", csrf)
                .add("login", username)

            request = Request.Builder()
                .url("https://passport.yandex.ru/registration-validations/auth/multi_step/start")
                .post(body.build())
                .build()

            client.newCall(request).execute().use { resp ->
                val parsed = json.decodeFromString<TrackIDResponse>(resp.body.string())
                if (parsed.can_register == true) return LoginResponse(false, Errors.INVALID_ACCOUNT)
                trackId = parsed.track_id!!
                Log.d(TAG, "Received track ID $trackId")
            }

            // Try to login with password
            body.add("track_id", trackId)
                .add("password", password)
                .add("retpath", "https://passport.yandex.ru/am/finish?status=ok&from=Login")

            val built = body.build()
            request = Request.Builder()
                .url("https://passport.yandex.ru/registration-validations/auth/multi_step/commit_password")
                .post(built)
                .build()

            client.newCall(request).execute().use { resp ->
                val parsed = json.decodeFromString<CommitPasswordResponse>(resp.body.string())
                if (parsed.status != "ok") {
                    return LoginResponse(false, Errors.INVALID_PASSSWD)
                }
                if (parsed.redirect_url != null) {
                    return LoginResponse(false, Errors.NEEDS_PHONE_CHALLENGE)
                }
            }

            // Login with cookies
            return loginCookies()
        } catch (e: ConnectException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.CONNECTION_ERROR)
        } catch (e: IOException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.TIMEOUT)
        }
    }
    private fun loginCookies(): LoginResponse {
        // Logging in with cookies
        val cookieBody = FormBody.Builder()
            .add("client_id", "c0ebe342af7d48fbbbfcf2d2eedb8f9e")
            .add("client_secret", "ad0a908f0aa341a182a37ecd75bc319e")
            .build()

        val request = Request.Builder()
            .url("https://mobileproxy.passport.yandex.net/1/bundle/oauth/token_by_sessionid")
            .post(cookieBody)
            .addHeader("Ya-Client-Host", "passport.yandex.ru")
            .addHeader("Ya-Client-Cookie", cookieJar.getYandexCookiesString())
            .build()

        client.newCall(request).execute().use { resp ->
            val parsed = json.decodeFromString<TokenBySessionIDResponse>(resp.body.string())
            if (parsed.status == "ok") {
                _xToken = parsed.access_token!!
            } else {
                return LoginResponse(false, Errors.UNKNOWN)
            }
        }
        return LoginResponse(true)
    }
    private fun loginToken(): LoginResponse {
        Log.i(TAG, "Start logging with token")

        val body = FormBody.Builder()
            .add("type", "x-token")
            .add("retpath", "https://www.yandex.ru")
            .build()

        var request = Request.Builder()
            .url("https://mobileproxy.passport.yandex.net/1/bundle/auth/x_token/")
            .post(body)
            .addHeader("Ya-Consumer-Authorization", "OAuth $_xToken")
            .build()

        try {
            val host: String
            val trackId: String
            client.newCall(request).execute().use { resp ->
                val parsed = json.decodeFromString<AuthXTokenResponse>(resp.body.string())
                if (parsed.status != "ok") {
                    return LoginResponse(false, Errors.INVALID_TOKEN)
                }
                host = parsed.passport_host!!
                trackId = parsed.track_id!!
            }

            val noRedirectClient = client.newBuilder()
                .followRedirects(false) // override the previous/default settings used by commonClient
                .followSslRedirects(false)
                .build()

            request = Request.Builder()
                .url("$host/auth/session/?track_id=$trackId")
                .build()

            noRedirectClient.newCall(request).execute()
            // Save cookies for further auth
            cookieDumpCallback(json.encodeToString(cookieJar.dumpCookies()))
            return LoginResponse(true)
        } catch (e: ConnectException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.CONNECTION_ERROR)
        } catch (e: IOException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.TIMEOUT)
        }
    }
    private fun refreshCookies(): LoginResponse {
        if (!this::_xToken.isInitialized) {
            throw UninitializedPropertyAccessException("xToken is required!")
        }
        // Check if cookies are fine
        val request = Request.Builder()
            .url("https://yandex.ru/quasar?storage=1")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val parsed = json.decodeFromString<QuasarStorageResponse>(resp.body.string())
                if (parsed.storage.user.uid != "") {
                    Log.i(TAG, "Cookies OK")
                    return LoginResponse(true)
                }
                Log.i(TAG, "Logging in via x-token!")
                return loginToken()
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.CONNECTION_ERROR)
        } catch (e: IOException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return LoginResponse(false, Errors.TIMEOUT)
        }
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
    fun delete(url: String): RequestResponse {
        return doRequest(Methods.DELETE, url)
    }
    fun wsConnect(url: String, listener: WebSocketListener) {
        val request = Request.Builder()
            .url(url)
            .build()

        unsafeClient.newWebSocket(request, listener)
    }
    private fun doRequest(method: Methods, url: String, body: RequestBody? = null, retry: Int = 2): RequestResponse {
        val request = Request.Builder()
            .url(url)

        Log.d(TAG, "$method $url")

        // All except get requires CSRF token
        if (method != Methods.GET) {
            if (!this::csrf.isInitialized) {
                Log.d(TAG, "Updating CSRF...")
                val requestCSRF = Request.Builder()
                    .url("https://yandex.ru/quasar?storage=1")
                    .build()

                try {
                    client.newCall(requestCSRF).execute().use { resp ->
                        val parsed = json.decodeFromString<QuasarStorageResponse>(resp.body.string())
                        csrf = parsed.storage.csrfToken2
                    }
                } catch (e: ConnectException) {
                    Log.e(TAG, "Error performing request!\n${e.message}")
                    return RequestResponse(false, Errors.CONNECTION_ERROR, null)
                } catch (e: IOException) {
                    Log.e(TAG, "Error performing request!\n${e.message}")
                    return RequestResponse(false, Errors.TIMEOUT, null)
                }
            }
            request.addHeader("x-csrf-token", csrf)
        }

        when (method) {
            Methods.GET -> request.get()
            Methods.POST -> request.post(body!!)
            Methods.PUT -> request.put(body!!)
            Methods.DELETE -> request.delete()
        }

        try {
            val resp = client.newCall(request.build()).execute()
            if (resp.code != 200) {
                Log.w(TAG, "Error while performing request to URL $url!\nCode: ${resp.code}")
            }
            return when (resp.code) {
                400 -> {
                    // Invalid device?
                    RequestResponse(false, Errors.BAD_REQUEST, resp)
                }
                401 -> {
                    if (retry > 0) {
                        // Cookies are dead, refreshing
                        refreshCookies()
                        doRequest(method, url, body, retry = retry - 1)
                    } else {
                        RequestResponse(false, Errors.INVALID_TOKEN, resp)
                    }
                }
                500 -> {
                    RequestResponse(false, Errors.INTERNAL_SERVER_ERROR, resp)
                }
                else -> RequestResponse(true, response = resp)
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return RequestResponse(false, Errors.CONNECTION_ERROR, null)
        } catch (e: IOException) {
            Log.e(TAG, "Error performing request!\n${e.message}")
            return RequestResponse(false, Errors.TIMEOUT, null)
        }
    }
    fun clearAllCookies() {
        cookieJar.clearCookies()
    }
}
