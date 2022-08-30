package com.sanyapilot.yandexstation_controller.api

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.*
import okio.Buffer
import okio.IOException
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*


const val TAG = "YaStationController"

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
    INVALID_ACCOUNT, INVALID_PASSSWD, NEEDS_PHONE_CHALLENGE, TOKEN_AUTH_FAILED, XTOKEN_REQUIRED,
    TIMEOUT, BAD_REQUEST
}

enum class Methods {
    GET, POST, PUT
}

class SimpleCookieJar: CookieJar {
    private var storage: MutableList<Cookie> = ArrayList()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        storage.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesToSend: MutableList<Cookie> = ArrayList()
        for (cookie in storage){
            if (cookie.expiresAt < System.currentTimeMillis()) storage.remove(cookie)
            if (cookie.matches(url)){
                cookiesToSend.add(cookie)
            }
        }
        return cookiesToSend
    }
    fun getYandexCookies(): List<Cookie> {
        val cookiesToSend: MutableList<Cookie> = ArrayList()
        for (cookie in storage){
            if (cookie.domain.endsWith("yandex.ru")) cookiesToSend.add(cookie)
        }
        return cookiesToSend
    }
    fun getYandexCookiesString(): String {
        var data = ""
        for (cookie in getYandexCookies()){
            data += "${cookie.name}=${cookie.value}; "
        }
        data.dropLast(2)
        return data
    }
    fun clearCookies() {
        storage = ArrayList()
    }
}

object Session {
    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()
    private val unsafeClient = getUnsafeOkHttpClient()
    private var trackId: String = ""
    lateinit var xToken: String
    private var csrf: String? = null

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
        // Get CSRF token
        var csrf: String
        var request = Request.Builder()
            .url("https://passport.yandex.ru/am?app_platform=android")
            .build()

        client.newCall(request).execute().use { resp ->
            val reCSRF = Regex("\"csrf_token\" value=\"([^\"]+)\"")
            val body = resp.body!!.string()
            csrf = reCSRF.find(body)!!.value.split('=')[1].removeSurrounding("\"")
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
            resp.request.header("Cookie")?.let { Log.d(TAG, it) }
            val respBody = resp.body!!.string()
            Log.d(TAG, "BODY: $respBody")
            val parsed = JSONObject(respBody)
            if (parsed.has("can_register") && parsed.get("can_register") == true) return LoginResponse(false, Errors.INVALID_ACCOUNT)
            trackId = parsed.getString("track_id")
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
            val respBody = resp.body!!.string()
            val parsed = JSONObject(respBody)
            Log.d(TAG, "\n\n")
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            Log.d(TAG, buffer.readUtf8())
            Log.d(TAG, respBody)
            Log.d(TAG, "\n\n")
            if (parsed.getString("status") != "ok") {
                return LoginResponse(false, Errors.INVALID_PASSSWD)
            }
            if (parsed.has("redirect_url")) {
                return LoginResponse(false, Errors.NEEDS_PHONE_CHALLENGE)
            }
        }
        return LoginResponse(true)
    }
    fun loginCookies(): LoginResponse {
        // Logging in with cookies
        val cookieBody = FormBody.Builder()
            .add("client_id", "c0ebe342af7d48fbbbfcf2d2eedb8f9e")
            .add("client_secret", "ad0a908f0aa341a182a37ecd75bc319e")
            .build()

        Log.d(TAG, cookieJar.getYandexCookiesString())

        val request = Request.Builder()
            .url("https://mobileproxy.passport.yandex.net/1/bundle/oauth/token_by_sessionid")
            .post(cookieBody)
            .addHeader("Ya-Client-Host", "passport.yandex.ru")
            .addHeader("Ya-Client-Cookie", cookieJar.getYandexCookiesString())
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body!!.string()
            Log.d(TAG, "Cookie auth body $body")
            val headers = resp.request.headers
            for (name in headers.names()) {
                Log.d(TAG, name)
            }
            val parsed = JSONObject(body)
            xToken = parsed.getString("access_token")
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
            .addHeader("Ya-Consumer-Authorization", "OAuth $xToken")
            .build()

        val host: String
        val trackId: String
        client.newCall(request).execute().use { resp ->
            val parsed = JSONObject(resp.body!!.string())
            if (parsed.getString("status") != "ok") {
                return LoginResponse(false, Errors.TOKEN_AUTH_FAILED)
            }

            host = parsed.getString("passport_host")
            trackId = parsed.getString("track_id")
        }

        val noRedirectClient = client.newBuilder()
            .followRedirects(false) // override the previous/default settings used by commonClient
            .followSslRedirects(false)
            .build()

        request = Request.Builder()
            .url("$host/auth/session/?track_id=$trackId")
            .build()

        noRedirectClient.newCall(request).execute()
        return LoginResponse(true)
    }
    fun refreshCookies(): LoginResponse {
        if (!this::xToken.isInitialized) {
            return LoginResponse(false, Errors.XTOKEN_REQUIRED)
        }
        // Check if cookies are fine
        val request = Request.Builder()
            .url("https://yandex.ru/quasar?storage=1")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val parsed = JSONObject(resp.body!!.string())
                if (parsed.getJSONObject("storage").getJSONObject("user").getString("uid") != "") {
                    Log.i(TAG, "Cookies OK")
                    return LoginResponse(true)
                }
                Log.i(TAG, "Logging in via x-token!")
                return loginToken()
            }
        } catch (e: IOException) {
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
    fun wsConnect(url: String, listener: WebSocketListener) {
        val request = Request.Builder()
            .url(url)
            .build()

        unsafeClient.newWebSocket(request, listener)
    }
    private fun doRequest(method: Methods, url: String, body: RequestBody? = null): RequestResponse {
        val request = Request.Builder()
            .url(url)

        // All except get requires CSRF token
        if (method != Methods.GET) {
            if (csrf == null) {
                Log.d(TAG, "Updating CSRF...")
                val requestCSRF = Request.Builder()
                    .url("https://yandex.ru/quasar?storage=1")
                    .build()

                try {
                    client.newCall(requestCSRF).execute().use { resp ->
                        val parsed = JSONObject(resp.body!!.string())
                        csrf = parsed.getJSONObject("storage").getString("csrfToken2")
                    }
                } catch (e: IOException) {
                    return RequestResponse(false, Errors.TIMEOUT)
                }
            }
            request.addHeader("x-csrf-token", csrf!!)
        }
        if (method == Methods.POST) {
            request.post(body!!)
        } else if (method == Methods.PUT) {
            request.put(body!!)
        }

        try {
            val resp = client.newCall(request.build()).execute()
            if (resp.code == 400) {
                Log.e(TAG, "URL $url")
                Log.e(TAG, resp.body!!.string())
                return RequestResponse(false, Errors.BAD_REQUEST)
            } else if (resp.code == 401) {
                // No cookies, updating
                refreshCookies()
                return doRequest(method, url, body)
            }
            return RequestResponse(true, response = resp)
        } catch (e: IOException) {
            return RequestResponse(false, Errors.TIMEOUT)
        }
    }
    fun clearAllCookies() {
        cookieJar.clearCookies()
    }
}