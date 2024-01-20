package com.sanyapilot.yandexstation_controller.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Accounts (
    val accounts: List<Account>
)

@Serializable
data class Account (
    val displayName: DisplayName,
    val defaultEmail: String = ""
)

@Serializable
data class DisplayName(
    val name: String = "",
    val firstname: String? = null,
    val lastname: String? = null,
    val default_avatar: String? = null
)

object UserData {
    private val json = Json { ignoreUnknownKeys = true }
    fun getUserData(): Account {
        val result = Session.get("https://api.passport.yandex.ru/all_accounts")
        if (!result.ok || result.response == null) {
            return Account(DisplayName())
        }
        return json.decodeFromString<Accounts>(result.response.body.string()).accounts[0]
    }
}
