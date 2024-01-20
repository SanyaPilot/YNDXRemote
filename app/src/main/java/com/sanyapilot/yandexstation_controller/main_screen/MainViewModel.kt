package com.sanyapilot.yandexstation_controller.main_screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sanyapilot.yandexstation_controller.api.UserData
import kotlin.concurrent.thread

class MainViewModel : ViewModel() {
    private val userName: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    private val userAvatar: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    private val loggedIn: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }
    fun updateUserData() {
        thread {
            val userData = UserData.getUserData()
            userName.postValue(userData.displayName.name)
            userAvatar.postValue("https://avatars.mds.yandex.net/get-yapic/${userData.displayName.default_avatar}/islands-200")
        }
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
