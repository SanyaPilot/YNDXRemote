package com.sanyapilot.yandexstation_controller.main_screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val userName: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    private val userAvatar: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    private val loggedIn: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    fun updateUserData(name: String) {
        userName.value = name
        userAvatar.value = null
    }
    fun getUserName(): LiveData<String> {
        return userName
    }
    fun getUserAvatar(): LiveData<String?> {
        return userAvatar
    }
    fun isLoggedIn(): Boolean {
        return if (loggedIn.value != null) loggedIn.value!! else false
    }
    fun setLoggedIn(value: Boolean) {
        loggedIn.value = value
    }
}
