package com.sanyapilot.yandexstation_controller.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.*
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.api.UserData
import kotlin.concurrent.thread

class UserFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = Fade(Fade.MODE_IN)
        exitTransition = Fade(Fade.MODE_OUT)
    }

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



        // Register observer
        viewModel.getUserData().observe(viewLifecycleOwner) { userData ->
            val avatar = view.findViewById<ImageView>(R.id.userAvatar)
            val displayName = view.findViewById<TextView>(R.id.userName)
            val displayEmail = view.findViewById<TextView>(R.id.userEmail)

            avatar.load(userData.avatarURL) {
                crossfade(100)
                transformations(CircleCropTransformation())
            }

            displayName.text = userData.nickname
            displayEmail.text = userData.email

            displayName.visibility = TextView.VISIBLE
            displayEmail.visibility = TextView.VISIBLE
        }

        //requireActivity().findViewById<Button>(R.id.logOutButton).setOnClickListener { logOut() }
        //val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        Log.e(TAG, viewModel.isLoggedIn()!!.toString())
        if (viewModel.getUserData().value == null) {
            Log.e(TAG, "REFRESHING")
            thread(start = true) {
                // Get data about user
                UserData.updateUserData()
                requireActivity().runOnUiThread {
                    val displayNames = UserData.getDisplayName()
                    viewModel.updateUserData(
                        UserDataObj(
                            displayNames.name, displayNames.firstname,
                            displayNames.lastname, UserData.getEmail(), UserData.getAvatarURL()
                        )
                    )
                }
            }
        }
    }
}