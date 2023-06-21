package com.sanyapilot.yandexstation_controller.fragments.devices

import android.content.Context
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.TAG
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel

class PlaybackInfoObservers (private val viewModel: DeviceViewModel, private val context: Context) {
    fun coverObserver(image: ImageView, url: String?) {
        if (url != null) {
            Log.d(TAG, "Img URL: $url")
            val curImageURL = if (url != "") "https://" + url.removeSuffix("%%") + "800x800" else "dummy"
            if (curImageURL != viewModel.prevCoverURL.value) {
                viewModel.prevCoverURL.value = curImageURL

                val fadeIn = AlphaAnimation(0f, 1f)
                fadeIn.interpolator = DecelerateInterpolator()
                fadeIn.duration = 200
                val fadeOut = AlphaAnimation(1f, 0f)
                fadeOut.interpolator = AccelerateInterpolator()
                fadeOut.duration = 200

                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        if (curImageURL != "dummy") {
                            val request = ImageRequest.Builder(context)
                                .data(curImageURL)
                                .target(image)
                                .listener { _, _ ->
                                    image.startAnimation(fadeIn)
                                }
                                .build()

                            context.imageLoader.enqueue(request)
                        } else {
                            image.setImageResource(R.drawable.ic_round_smart_display_24)
                            image.startAnimation(fadeIn)
                        }
                    }

                    override fun onAnimationRepeat(p0: Animation?) {
                    }
                })
                image.startAnimation(fadeOut)
            }
        }
    }

    fun trackArtistObserver(textView: TextView, data: String?) {
        if (data != viewModel.prevTrackArtist.value) {
            if (viewModel.prevTrackArtist.value == null) {
                textView.visibility = TextView.VISIBLE
            }
            viewModel.prevTrackArtist.value = data
            animateText(textView, data)
        }
    }

    fun trackNameObserver(textView: TextView, data: String?) {
        if (data != viewModel.prevTrackName.value) {
            if (viewModel.prevTrackName.value == null) {
                textView.visibility = TextView.VISIBLE
            }
            viewModel.prevTrackName.value = data
            animateText(textView, data)
        }
    }

    fun playerActiveObserver(image: ImageView, data: Boolean) {
        if (!data) {
            image.setImageResource(R.drawable.ic_round_pause_on_surface_24)
        }
    }

    fun isLocalObserver(trackName: TextView, trackArtist: TextView, coverImage: ImageView, data: Boolean) {
        if (!data) {
            trackName.visibility = TextView.INVISIBLE
            trackArtist.visibility = TextView.INVISIBLE
            coverImage.setImageResource(R.drawable.ic_baseline_cloud_24)
        }
    }

    private fun animateText(textView: TextView, value: String?) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator()
        fadeIn.duration = 200
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator()
        fadeOut.duration = 200

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
            }

            override fun onAnimationEnd(p0: Animation?) {
                textView.text = value
                textView.startAnimation(fadeIn)
            }

            override fun onAnimationRepeat(p0: Animation?) {
            }
        })
        textView.startAnimation(fadeOut)
    }
}