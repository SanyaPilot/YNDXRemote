package com.sanyapilot.yandexstation_controller.fragments

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sanyapilot.yandexstation_controller.api.StationState

class DeviceViewModel : ViewModel() {
    val isReady: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val playerActive: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val isPlaying: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val trackName: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val trackArtist: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prevTrackName: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val prevTrackArtist: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val volume: MutableLiveData<Float> by lazy { MutableLiveData<Float>() }
    val progressMax: MutableLiveData<Int> by lazy { MutableLiveData<Int>() }
    val progress: MutableLiveData<Int> by lazy { MutableLiveData<Int>() }
    val type: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val coverBitmap: MutableLiveData<Bitmap> by lazy { MutableLiveData<Bitmap>() }
    val coverURL: MutableLiveData<String?> by lazy { MutableLiveData<String?>() }
    val prevCoverURL: MutableLiveData<String?> by lazy { MutableLiveData<String?>() }
    val hasProgressBar: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val hasNext: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val hasPrev: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val playlistId: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val shuffleSupported: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false)}
    val likeSupported: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>(false) }

    val seekTime = MutableLiveData<Int?>(null)

    fun update(data: StationState) {
        isPlaying.value = data.playing
        volume.value = data.volume
        val active = data.playerState != null && data.playerState.title != ""
        playerActive.value = active
        if (!active)
            return

        trackName.value = data.playerState!!.title
        trackArtist.value = data.playerState.subtitle
        progressMax.value = data.playerState.duration.toInt()
        type.value = data.playerState.playlistType
        coverURL.value = data.playerState.extra?.coverURI
        hasProgressBar.value = data.playerState.hasProgressBar
        hasNext.value = data.playerState.hasNext
        hasPrev.value = data.playerState.hasPrev
        playlistId.value = data.playerState.playlistId

        val curProgress = data.playerState.progress.toInt()
        if (seekTime.value != null) {
            if (curProgress == seekTime.value!!) {
                progress.value = curProgress
                seekTime.value = null
            }
        } else {
            progress.value = curProgress
        }
    }

    fun removeObservers(owner: LifecycleOwner) {
        isReady.removeObservers(owner)
        playerActive.removeObservers(owner)
        isPlaying.removeObservers(owner)
        trackName.removeObservers(owner)
        trackArtist.removeObservers(owner)
        volume.removeObservers(owner)
        progressMax.removeObservers(owner)
        progress.removeObservers(owner)
        type.removeObservers(owner)
        coverBitmap.removeObservers(owner)
        coverURL.removeObservers(owner)
        hasProgressBar.removeObservers(owner)
        hasNext.removeObservers(owner)
        hasPrev.removeObservers(owner)
        playlistId.removeObservers(owner)
    }
}