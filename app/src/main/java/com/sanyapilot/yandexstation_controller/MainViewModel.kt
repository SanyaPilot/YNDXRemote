package com.sanyapilot.yandexstation_controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class UserDataObj(
    val nickname: String,
    val firstname: String,
    val lastName: String,
    val email: String,
    val avatarURL: String
)

class MainViewModel : ViewModel() {
    private val userData: MutableLiveData<UserDataObj> by lazy { MutableLiveData<UserDataObj>() }
    private val loggedIn: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    fun updateUserData(new: UserDataObj) {
        userData.value = new
    }
    fun getUserData(): LiveData<UserDataObj> {
        return userData
    }
    fun isLoggedIn(): Boolean? {
        return loggedIn.value
    }
    fun setLoggedIn(value: Boolean) {
        loggedIn.value = value
    }
}