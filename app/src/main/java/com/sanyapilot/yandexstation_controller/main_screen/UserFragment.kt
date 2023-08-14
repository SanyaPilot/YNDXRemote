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
import com.sanyapilot.yandexstation_controller.api.UserData
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
        viewModel.getUserData().observe(viewLifecycleOwner) { userData ->
            val avatar = view.findViewById<ImageView>(R.id.userAvatar)
            val displayName = view.findViewById<TextView>(R.id.userName)
            val displayNickname = view.findViewById<TextView>(R.id.userNickname)

            if (userData.avatarURL != null) {
                avatar.load(userData.avatarURL) {
                    crossfade(100)
                    transformations(CircleCropTransformation())
                }
            } else {
                avatar.setImageResource(R.drawable.baseline_account_circle_24)
            }

            displayName.text = userData.displayName
            displayNickname.text = userData.nickname

            displayName.visibility = TextView.VISIBLE
            displayNickname.visibility = if (displayNickname != null) TextView.VISIBLE else TextView.GONE
        }

        if (viewModel.getUserData().value == null) {
            thread(start = true) {
                // Get data about user
                UserData.updateUserData()
                requireActivity().runOnUiThread {
                    viewModel.updateUserData(
                        UserDataObj(
                            UserData.getDisplayName(),
                            UserData.getNickname(),
                            UserData.getAvatarURL()
                        )
                    )
                }
            }
        }
    }
}