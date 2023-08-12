package com.sanyapilot.yandexstation_controller.device

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.main_screen.TAG

class PlaybackInfoObservers (
    private val viewModel: DeviceViewModel,
    private val context: Context
    ) {
    fun coverObserver(image: ImageView, bitmap: Bitmap?, curImageURL: String?) {
        if (bitmap != null) {
            if (curImageURL != viewModel.prevCoverURL.value) {
                Log.d(TAG, "Update cover!")
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
                            image.setImageBitmap(bitmap)
                        } else {
                            image.setImageResource(R.drawable.ic_round_smart_display_24)
                        }
                        image.startAnimation(fadeIn)
                    }

                    override fun onAnimationRepeat(p0: Animation?) {
                    }
                })
                image.startAnimation(fadeOut)
            }
        } else {
            image.setImageResource(R.drawable.ic_round_pause_on_surface_24)
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