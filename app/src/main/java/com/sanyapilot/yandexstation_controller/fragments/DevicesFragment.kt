package com.sanyapilot.yandexstation_controller.fragments

import android.os.Bundle
import android.transition.Fade
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import kotlin.concurrent.thread

class DevicesFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enterTransition = Fade(Fade.MODE_IN)
        //exitTransition = Fade(Fade.MODE_OUT)
    }
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

        val recycler = view.findViewById<RecyclerView>(R.id.devicesRecycler)

        thread(start = true) {
            requireActivity().runOnUiThread {
                recycler.layoutManager = LinearLayoutManager(view.context)
                recycler.adapter = DevicesRecyclerAdapter(FuckedQuasarClient.getDevices())
            }
        }
    }
}