package com.sanyapilot.yandexstation_controller.main_screen

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import coil.load
import coil.transform.CircleCropTransformation
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import kotlin.concurrent.thread

class UserFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_user, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel: MainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val appBarTitle = requireActivity().findViewById<TextView>(R.id.mainAppBarTitle)
        appBarTitle.text = getString(R.string.aboutAccount)

        val withLoveText = requireActivity().findViewById<TextView>(R.id.withLove)
        val credits = requireActivity().findViewById<TextView>(R.id.creditsText)
        withLoveText.movementMethod = LinkMovementMethod.getInstance()
        credits.movementMethod = LinkMovementMethod.getInstance()

        // Register observer
        viewModel.getUserName().observe(viewLifecycleOwner) {
            val displayName = view.findViewById<TextView>(R.id.userName)
            //val displayNickname = view.findViewById<TextView>(R.id.userNickname)

            displayName.text = it
            //displayNickname.text = userData.nickname

            displayName.visibility = TextView.VISIBLE
            //displayNickname.visibility = if (displayNickname != null) TextView.VISIBLE else TextView.GONE
        }
        viewModel.getUserAvatar().observe(viewLifecycleOwner) {
            val avatar = view.findViewById<ImageView>(R.id.userAvatar)
            if (it != null) {
                avatar.load(it) {
                    crossfade(100)
                    transformations(CircleCropTransformation())
                }
            } else {
                avatar.setImageResource(R.drawable.baseline_account_circle_24)
            }
        }

        if (viewModel.getUserName().value == null) {
            thread {
                val res = FuckedQuasarClient.getUserInfo()
                if (res.ok) {
                    requireActivity().runOnUiThread {
                        viewModel.updateUserData(res.data!!.name!!)
                    }
                }
            }
        }
    }
}