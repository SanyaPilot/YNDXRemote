package com.sanyapilot.yandexstation_controller.main_screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.sanyapilot.yandexstation_controller.device.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Speaker
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.misc.stationIcons
import com.sanyapilot.yandexstation_controller.misc.stationNames
import com.sanyapilot.yandexstation_controller.service.DEVICE_ID
import com.sanyapilot.yandexstation_controller.service.DEVICE_NAME
import com.sanyapilot.yandexstation_controller.service.DEVICE_PLATFORM

class DevicesRecyclerAdapter(
    private val activity: Activity,
    private val dataSet: List<Any>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView
        val type: TextView
        val onlineText: TextView
        val image: ImageView
        val card: MaterialCardView

        init {
            // Define click listener for the ViewHolder's View.
            name = view.findViewById(R.id.deviceName)
            type = view.findViewById(R.id.deviceType)
            onlineText = view.findViewById(R.id.deviceOnlineText)
            image = view.findViewById(R.id.deviceImage)
            card = view.findViewById(R.id.deviceCard)
        }
        fun goOffline(animate: Boolean) {
            if (animate) {
                card.animate().apply {
                    duration = 200
                    alpha(0.38f)
                    withEndAction {
                        name.setTextColor(MaterialColors.getColor(card, android.R.attr.textColorPrimary))
                    }
                    start()
                }
            } else {
                card.alpha = 0.38f
                name.setTextColor(MaterialColors.getColor(card, android.R.attr.textColorPrimary))
            }
            onlineText.setText(R.string.offline)
        }
        fun goOnline(animate: Boolean) {
            if (animate) {
                card.animate().apply {
                    duration = 200
                    alpha(1f)
                    withEndAction {
                        name.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimaryContainer))
                    }
                    start()
                }
            } else {
                card.alpha = 1f
                name.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimaryContainer))
            }
            onlineText.setText(R.string.online)
        }
    }
    private inner class TitleHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView
        init {
            textView = view.findViewById(R.id.deviceTitle)
        }
    }
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Create a new view, which defines the UI of the list item
        return if (viewType == 0) {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.device_element, viewGroup, false)

            ViewHolder(view)
        } else {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.device_element_title, viewGroup, false)

            TitleHolder(view)
        }
    }
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {

        if (viewHolder.itemViewType == 0) {
            val curViewHolder = viewHolder as ViewHolder
            val curDevice = dataSet[position]
            if (curDevice is Speaker) {
                curViewHolder.image.setImageResource(stationIcons.getOrDefault(curDevice.platform, R.drawable.station_unknown))
                curViewHolder.name.text = curDevice.name
                curViewHolder.type.setText(stationNames.getOrDefault(curDevice.platform, R.string.station_unknown))

                curViewHolder.card.setOnClickListener {
                    val intent = Intent(it.context, DeviceActivity::class.java).apply {
                        putExtra(DEVICE_ID, curDevice.id)
                        putExtra(DEVICE_NAME, curDevice.name)
                        putExtra(DEVICE_PLATFORM, curDevice.platform)
                    }
                    it.context.startActivity(intent)
                }

                // Device became online
                mDNSWorker.addListener(curDevice.id) {
                    activity.runOnUiThread { curViewHolder.goOnline(true) }
                }
                // Device is offline again
                mDNSWorker.addOnLostListener(curDevice.id) {
                    activity.runOnUiThread { curViewHolder.goOffline(true) }
                }

                if (mDNSWorker.deviceExists(curDevice.id))
                    curViewHolder.goOnline(false)
                else
                    curViewHolder.goOffline(false)

                curViewHolder.card.setOnLongClickListener {
                    val clipboard: ClipboardManager = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("UDID", curDevice.id)
                    clipboard.setPrimaryClip(clip)
                    // Display toast
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast(it.context).apply {
                            duration = Toast.LENGTH_SHORT
                            setText("UDID скопирован!")
                            show()
                        }
                    }
                    return@setOnLongClickListener true
                }
            }
        } else {
            val curViewHolder = viewHolder as TitleHolder
            val curTitle = dataSet[position]

            if (curTitle is String)
                curViewHolder.textView.text = curTitle
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}
