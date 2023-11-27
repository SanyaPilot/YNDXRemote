package com.sanyapilot.yandexstation_controller.main_screen.user_settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.composables.ExpandingListItem
import com.sanyapilot.yandexstation_controller.composables.NetStatusSnack
import com.sanyapilot.yandexstation_controller.ui.theme.AppTheme

class UserSettingsFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appBarTitle = requireActivity().findViewById<TextView>(R.id.mainAppBarTitle)
        appBarTitle.text = getString(R.string.userSettingsTitle)
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
                        UserSettingsLayout()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsLayout(
    viewModel: UserSettingsViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val netStatus = viewModel.netStatus.collectAsState()
        NetStatusSnack(
            context = context,
            netStatus = netStatus.value,
            snackbarHostState = snackbarHostState
        )

        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            // Timezone
            val tzOpened = rememberSaveable { mutableStateOf(false) }
            ExpandingListItem(
                expanded = tzOpened,
                headlineContent = { Text(text = stringResource(id = R.string.timezoneLabel)) },
                supportingContent = { Text(text = stringResource(id = R.string.timezoneDescription)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.round_access_time_24),
                        contentDescription = null
                    )
                }
            )
            AnimatedVisibility(visible = tzOpened.value) {
                val tzMenuOpened = remember { mutableStateOf(false) }
                val curTimezoneName by viewModel.curTimezoneName.collectAsState()
                ExposedDropdownMenuBox(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    expanded = tzMenuOpened.value,
                    onExpandedChange = {
                        tzMenuOpened.value = !tzMenuOpened.value
                    }
                ) {
                    OutlinedTextField(
                        value = curTimezoneName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(id = R.string.timezoneLabel)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = tzMenuOpened.value
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = tzMenuOpened.value,
                        onDismissRequest = { tzMenuOpened.value = false }
                    ) {
                        TIMEZONES.forEach {
                            DropdownMenuItem(
                                text = { Text(text = it.name) },
                                onClick = {
                                    viewModel.updateTimezone(it.value)
                                    tzMenuOpened.value = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LayoutPreview() {
    AppTheme {
        Surface {
            UserSettingsLayout()
        }
    }
}