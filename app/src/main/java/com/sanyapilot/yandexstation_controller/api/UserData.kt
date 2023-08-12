package com.sanyapilot.yandexstation_controller.api

import org.json.JSONObject

class DataMissingException(message: String): Exception(message)

object UserData {
    private lateinit var userData: JSONObject
    fun updateUserData() {
        val result = Session.get("$FQ_BACKEND_URL/user_info")
        if (result.ok) {
            val body = result.response!!.body.string()
            userData = JSONObject(body)
        }
    }
    fun getDisplayName(): String {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        return userData.getString("name")
    }
    // TODO: Update FQ to save extended user data
    fun getAvatarURL(): String? {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        return null
    }
    fun getNickname(): String? {
        if (!this::userData.isInitialized) throw DataMissingException("User data is not loaded!")
        return null
    }
}