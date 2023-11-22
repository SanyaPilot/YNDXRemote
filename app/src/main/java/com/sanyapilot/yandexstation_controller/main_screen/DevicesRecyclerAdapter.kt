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
import com.sanyapilot.yandexstation_controller.device.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Speaker
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.misc.stationIcons
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
        val udid: TextView
        val image: ImageView
        val card: MaterialCardView
        val offlineImage: ImageView

        init {
            // Define click listener for the ViewHolder's View.
            name = view.findViewById(R.id.deviceName)
            type = view.findViewById(R.id.deviceType)
            udid = view.findViewById(R.id.deviceUDID)
            image = view.findViewById(R.id.deviceImage)
            card = view.findViewById(R.id.deviceCard)
            offlineImage = view.findViewById(R.id.offlineImage)
        }
        fun goOffline() {
            offlineImage.visibility = View.VISIBLE
        }
        fun goOnline() {
            offlineImage.visibility = View.INVISIBLE
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
                curViewHolder.image.setImageResource(stationIcons.getOrDefault(curDevice.platform, R.drawable.station_icon))
                curViewHolder.name.text = curDevice.name
                curViewHolder.type.text = curDevice.platform
                curViewHolder.udid.text = curDevice.id

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
                    activity.runOnUiThread { curViewHolder.goOnline() }
                }
                // Device is offline again
                mDNSWorker.addOnLostListener(curDevice.id) {
                    activity.runOnUiThread { curViewHolder.goOffline() }
                }

                if (mDNSWorker.deviceExists(curDevice.id))
                    curViewHolder.goOnline()
                else
                    curViewHolder.goOffline()

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
