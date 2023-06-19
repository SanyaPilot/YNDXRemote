package com.sanyapilot.yandexstation_controller.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sanyapilot.yandexstation_controller.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Speaker

class DevicesRecyclerAdapter(private val dataSet: List<Any>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView
        val type: TextView
        val image: ImageView
        val card: MaterialCardView

        init {
            // Define click listener for the ViewHolder's View.
            name = view.findViewById(R.id.deviceName)
            type = view.findViewById(R.id.deviceType)
            image = view.findViewById(R.id.deviceImage)
            card = view.findViewById(R.id.deviceCard)
        }
    }
    private inner class TitleHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView
        init {
            textView = view.findViewById(R.id.deviceTitle)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Create a new view, which defines the UI of the list item
        if (viewType == 0) {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.device_element, viewGroup, false)

            return ViewHolder(view)
        } else {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.device_element_title, viewGroup, false)

            return TitleHolder(view)
        }
    }
    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {

        if (viewHolder.itemViewType == 0) {
            val curViewHolder = viewHolder as ViewHolder
            val curDevice = dataSet[position]
            if (curDevice is Speaker) {
                curViewHolder.image.setImageResource(R.drawable.station_icon)
                curViewHolder.name.text = curDevice.name
                curViewHolder.type.text = curDevice.platform
                curViewHolder.card.setOnClickListener {
                    val intent = Intent(it.context, DeviceActivity::class.java).apply {
                        putExtra("deviceId", curDevice.id)
                        putExtra("deviceName", curDevice.name)
                    }
                    it.context.startActivity(intent)
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
