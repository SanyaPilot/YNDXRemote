package com.sanyapilot.yandexstation_controller.api

import android.util.Log
import org.json.JSONObject

data class DisplayName(
    val name: String,
    val firstname: String,
    val lastname: String
)

enum class UserDataErrors {
    NOT_LOADED
}

class DataMissingException(message: String): Exception(message)

object UserData {
    private lateinit var userData: JSONObject
    fun updateUserData() {
        val result = Session.get("https://api.passport.yandex.ru/all_accounts")
        assert(result.ok)
        val body = result.response!!.body!!.string()
        Log.d(TAG, body)
        userData = JSONObject(body).getJSONArray("accounts").getJSONObject(0)
    }
    fun getDisplayName(): DisplayName {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        val dispName = userData.getJSONObject("displayName")
        return DisplayName(dispName.getString("name"), dispName.getString("firstname"),
                dispName.getString("lastname")
            )
    }
    fun getAvatarURL(): String {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        val avatar = userData.getJSONObject("displayName").getString("default_avatar")
        return "https://avatars.mds.yandex.net/get-yapic/$avatar/islands-200"
    }
    fun getEmail(): String {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        return userData.getString("defaultEmail")
    }
}