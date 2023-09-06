package com.sanyapilot.yandexstation_controller.main_screen

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import kotlin.concurrent.thread

class DevicesFragment : Fragment() {
    private lateinit var recycler: RecyclerView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val appBarTitle = requireActivity().findViewById<TextView>(R.id.mainAppBarTitle)
        appBarTitle.text = getString(R.string.deviceList)
        recycler = requireView().findViewById(R.id.devicesRecycler)

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swiperefresh)
        swipeRefresh.setOnRefreshListener {
            updateDeviceList(true)
            swipeRefresh.isRefreshing = false
        }
    }
    override fun onStart() {
        super.onStart()
        updateDeviceList(false)
    }
    private fun updateDeviceList(fetch: Boolean) {
        val activity = requireActivity() as MainActivity

        if (fetch)
            thread(start = true) { activity.fetchDevices() }

        val devices = FuckedQuasarClient.getDevices()
        if (devices.isEmpty()) {
            val noDevicesImage = requireView().findViewById<ImageView>(R.id.noDevicesImage)
            val noDevicesText = requireView().findViewById<TextView>(R.id.noDevicesText)

            noDevicesImage.visibility = View.VISIBLE
            noDevicesText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            recycler.layoutManager = LinearLayoutManager(requireView().context)
            recycler.adapter = DevicesRecyclerAdapter(requireActivity(), devices)
        }
    }
}