package com.sanyapilot.yandexstation_controller.device.settings

import android.support.v4.media.session.MediaControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.SettingsErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

data class NetStatus(
    val ok: Boolean,
    val error: SettingsErrors? = null
)

class SettingsViewModel(
    private val deviceId: String,
    private val mediaController: MediaControllerCompat?
) : ViewModel() {
    private val _jingleEnabled = MutableStateFlow(false)
    private val _ssImages = MutableStateFlow(false)
    private val _netStatus = MutableStateFlow(NetStatus(true))
    val jingleEnabled: StateFlow<Boolean>
        get() = _jingleEnabled
    val ssImages: StateFlow<Boolean>
        get() = _ssImages
    val netStatus: StateFlow<NetStatus>
        get() = _netStatus

    init {
        thread {
            val jingleRes = FuckedQuasarClient.getJingleStatus(deviceId)
            if (!jingleRes.ok) {
                _netStatus.value = NetStatus(false, jingleRes.error)
                return@thread
            }
            _jingleEnabled.value = jingleRes.enabled!!

            val ssRes = FuckedQuasarClient.getSSType(deviceId)
            if (!ssRes.ok) {
                _netStatus.value = NetStatus(false, ssRes.error)
                return@thread
            }
            _ssImages.value = ssRes.type == "image"
        }
    }

    fun toggleJingle() {
        thread {
            val res = FuckedQuasarClient.setJingleStatus(deviceId, !_jingleEnabled.value)
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _jingleEnabled.value = !_jingleEnabled.value
            }
        }
    }

    fun toggleSSType() {
        thread {
            val res = FuckedQuasarClient.setSSType(deviceId, if (_ssImages.value) "video" else "image")
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _ssImages.value = !ssImages.value
            }
        }
    }

    fun unlinkDevice() {
        thread {
            val res = FuckedQuasarClient.unlinkDevice(deviceId)
            if (res.ok) {
                // Stop service and finish activity
                FuckedQuasarClient.fetchDevices()
                mediaController?.transportControls?.stop()
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
}

class SettingsViewModelFactory(
    private val deviceId: String,
    private val mediaController: MediaControllerCompat?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(deviceId, mediaController) as T
    }
}
