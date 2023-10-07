package com.sanyapilot.yandexstation_controller.device.settings

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.SettingsErrors
import com.sanyapilot.yandexstation_controller.ui.theme.AppTheme

class SettingsFragment : Fragment() {
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaController = MediaControllerCompat.getMediaController(requireActivity())
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                requireArguments().getString("deviceId")!!,
                mediaController
            )
        )[SettingsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    Surface {
                        SettingsLayout(viewModel)
                    }
                }
            }
        }
    }
    companion object {
        fun instance(deviceId: String): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            args.putString("deviceId", deviceId)
            fragment.arguments = args
            return fragment
        }
    }
}

@Composable
fun SettingsLayout(viewModel: SettingsViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Unlink dialog
    val showUnlinkDialog = remember { mutableStateOf(false) }
    if (showUnlinkDialog.value) {
        AlertDialog(
            title = { Text(text = stringResource(id = R.string.unlinkQuestion)) },
            text = { Text(text = stringResource(id = R.string.unlinkDescription)) },
            onDismissRequest = { showUnlinkDialog.value = false },
            confirmButton = {
                TextButton(onClick = {viewModel.unlinkDevice()}) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val netStatus = viewModel.netStatus.collectAsState()
        if (!netStatus.value.ok) {
            LaunchedEffect(snackbarHostState) {
                snackbarHostState.showSnackbar(
                    message = when(netStatus.value.error) {
                        SettingsErrors.NOT_LINKED -> context.resources.getString(R.string.deviceNotLinked)
                        SettingsErrors.UNAUTHORIZED -> context.resources.getString(R.string.unauthorizedError)
                        SettingsErrors.TIMEOUT -> context.resources.getString(R.string.unknownError)
                        SettingsErrors.UNKNOWN -> context.resources.getString(R.string.unknownError)
                        else -> context.resources.getString(R.string.wtf)
                    },
                    duration = SnackbarDuration.Long
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                val jingleEnabled by viewModel.jingleEnabled.collectAsState()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.jingleLabel)) },
                    trailingContent = { Switch(checked = jingleEnabled, onCheckedChange = { viewModel.toggleJingle() }) },
                    modifier = Modifier.clickable { viewModel.toggleJingle() }
                )

                val ssImages by viewModel.ssImages.collectAsState()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ssLabel)) },
                    supportingContent = {
                        Text(stringResource(R.string.ssSupportText) + " " +
                                if (ssImages) stringResource(R.string.images) else stringResource(R.string.video)
                        )
                    },
                    trailingContent = { Switch(checked = ssImages, onCheckedChange = { viewModel.toggleSSType() }) },
                    modifier = Modifier.clickable { viewModel.toggleSSType() }
                )
            }
            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = {showUnlinkDialog.value = true}
            ) {
                Text(stringResource(R.string.unlinkLabel))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    AppTheme {
        Surface {
            SettingsLayout(viewModel(factory = SettingsViewModelFactory("deaddeaddeaddeaddead", null)))
        }
    }
}
