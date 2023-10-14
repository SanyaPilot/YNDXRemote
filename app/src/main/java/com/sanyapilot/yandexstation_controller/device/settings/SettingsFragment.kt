package com.sanyapilot.yandexstation_controller.device.settings

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.SettingsErrors
import com.sanyapilot.yandexstation_controller.ui.theme.AppTheme
import kotlin.math.roundToInt

class SettingsFragment : Fragment() {
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                requireArguments().getString("deviceId")!!,
                MediaControllerCompat.getMediaController(requireActivity())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLayout(viewModel: SettingsViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val deviceName by viewModel.deviceName.collectAsState()

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

    // Rename dialog
    val showRenameDialog = remember { mutableStateOf(false) }
    if (showRenameDialog.value) {
        Dialog(onDismissRequest = { showRenameDialog.value = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ){
                    Text(
                        text = stringResource(id = R.string.renameDialogTitle),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val tempName = remember { mutableStateOf(deviceName) }
                    OutlinedTextField(
                        value = tempName.value,
                        onValueChange = { tempName.value = it },
                        label = { Text(text = stringResource(id = R.string.enterName)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ){
                        TextButton(onClick = { showRenameDialog.value = false }) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                        TextButton(onClick = {
                            viewModel.updateDeviceName(tempName.value)
                            showRenameDialog.value = false
                        }) {
                            Text(text = stringResource(id = android.R.string.ok))
                        }
                    }
                }
            }
        }
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

        val renameError = viewModel.renameError.collectAsState()
        if (renameError.value) {
            LaunchedEffect(snackbarHostState) {
                snackbarHostState.showSnackbar(
                    message = context.resources.getString(R.string.invalidName),
                    duration = SnackbarDuration.Long
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            val jingleEnabled by viewModel.jingleEnabled.collectAsState()
            ListItem(
                leadingContent = { Icon(painter = painterResource(id = R.drawable.ic_round_volume_up_24), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.jingleLabel)) },
                trailingContent = { Switch(checked = jingleEnabled, onCheckedChange = { viewModel.toggleJingle() }) },
                modifier = Modifier.clickable { viewModel.toggleJingle() }
            )

            val ssImages by viewModel.ssImages.collectAsState()
            ListItem(
                leadingContent = { Icon(painter = painterResource(id = R.drawable.round_wallpaper_24), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.ssLabel)) },
                supportingContent = {
                    Text(stringResource(R.string.ssSupportText) + " " +
                            if (ssImages) stringResource(R.string.images) else stringResource(R.string.video)
                    )
                },
                trailingContent = { Switch(checked = ssImages, onCheckedChange = { viewModel.toggleSSType() }) },
                modifier = Modifier.clickable { viewModel.toggleSSType() }
            )

            val dndEnabled = viewModel.dndEnabled.collectAsState()
            val dndStartTime = viewModel.dndStartValue.collectAsState()
            val dndStopTime = viewModel.dndStopValue.collectAsState()
            val dndStartTimeOpened = remember { mutableStateOf(false) }
            val dndStopTimeOpened = remember { mutableStateOf(false) }

            val startTimePickerState = rememberTimePickerState(dndStartTime.value.hour, dndStartTime.value.minute, true)
            val stopTimePickerState = rememberTimePickerState(dndStopTime.value.hour, dndStopTime.value.minute, true)
            if (dndStartTimeOpened.value) {
                TimePickerDialog(
                    onDismissRequest = { dndStartTimeOpened.value = false },
                    state = startTimePickerState,
                    label = stringResource(id = R.string.startTime),
                    onApply = { viewModel.setDNDValues(startTimePickerState, stopTimePickerState) }
                )
            }
            if (dndStopTimeOpened.value) {
                TimePickerDialog(
                    onDismissRequest = { dndStopTimeOpened.value = false },
                    state = stopTimePickerState,
                    label = stringResource(id = R.string.stopTime),
                    onApply = { viewModel.setDNDValues(startTimePickerState, stopTimePickerState) }
                )
            }

            ListItem(
                leadingContent = { Icon(painter = painterResource(id = R.drawable.round_do_not_disturb_on_24), contentDescription = null) },
                headlineContent = { Text(text = stringResource(id = R.string.dndLabel)) },
                trailingContent = { Switch(checked = dndEnabled.value, onCheckedChange = { viewModel.toggleDND() }) },
                modifier = Modifier.clickable { viewModel.toggleDND() }
            )
            AnimatedVisibility(
                visible = dndEnabled.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(text = stringResource(id = R.string.start)) },
                        supportingContent = {
                            Text(
                                text = "${stringResource(id = R.string.currentTime)} " +
                                        "${dndStartTime.value.hour.toString().padStart(2, '0')}:" +
                                        dndStartTime.value.minute.toString().padStart(2, '0')
                            )
                        },
                        modifier = Modifier.clickable { dndStartTimeOpened.value = true }
                    )
                    ListItem(
                        headlineContent = { Text(text = stringResource(id = R.string.stop)) },
                        supportingContent = {
                            Text(
                                text = "${stringResource(id = R.string.currentTime)} " +
                                        "${dndStopTime.value.hour.toString().padStart(2, '0')}:" +
                                        dndStopTime.value.minute.toString().padStart(2, '0')
                            )
                        },
                        modifier = Modifier.clickable { dndStopTimeOpened.value = true }
                    )
                }
            }

            val eqOpened = rememberSaveable { mutableStateOf(false) }
            ExpandingListItem(
                expanded = eqOpened,
                headlineContent = { Text(text = stringResource(id = R.string.eqLabel)) },
                leadingContent = { Icon(painter = painterResource(id = R.drawable.round_equalizer_24), contentDescription = null) }
            )

            AnimatedVisibility(
                visible = eqOpened.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                val eqValues by viewModel.eqValues.collectAsState()
                val curPresetName by viewModel.presetName.collectAsState()
                val presetMenuOpened = remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .height(340.dp)
                        .align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(24.dp)

                ) {
                    Column(
                        modifier = Modifier.padding(4.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            modifier = Modifier.padding(8.dp),
                            expanded = presetMenuOpened.value,
                            onExpandedChange = {
                                presetMenuOpened.value = !presetMenuOpened.value
                            }
                        ) {
                            OutlinedTextField(
                                value = curPresetName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(text = stringResource(id = R.string.eqPreset)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = presetMenuOpened.value
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = presetMenuOpened.value,
                                onDismissRequest = { presetMenuOpened.value = false }
                            ) {
                                EQ_PRESETS.forEach {
                                    DropdownMenuItem(
                                        text = { Text(text = it.name) },
                                        onClick = {
                                            viewModel.updateAllEQ(it.data)
                                            presetMenuOpened.value = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center
                        ) {
                            eqValues.forEach { item ->
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = ((item.state.floatValue * 10).roundToInt() / 10f).toString())
                                    VerticalSlider(
                                        modifier = Modifier
                                            .padding(8.dp, 0.dp)
                                            .weight(1f),
                                        value = item.state.floatValue,
                                        onValueChange = { item.state.floatValue = it },
                                        onValueChangeFinished = {
                                            item.state.floatValue =
                                                (item.state.floatValue * 10).roundToInt() / 10f
                                            viewModel.updateEQBand(
                                                item.id,
                                                item.state.floatValue
                                            )
                                        },
                                        valueRange = -12f..12f
                                    )
                                    Text(text = item.name)
                                }
                            }
                        }
                    }
                }
            }

            ListItem(
                leadingContent = { Icon(painter = painterResource(id = R.drawable.round_drive_file_rename_outline_24), contentDescription = null) },
                headlineContent = { Text(text = stringResource(id = R.string.renameLabel)) },
                supportingContent = { Text(text = "${stringResource(id = R.string.currentName)} $deviceName") },
                modifier = Modifier.clickable { showRenameDialog.value = true }
            )

            ListItem(
                leadingContent = { Icon(painter = painterResource(id = R.drawable.round_link_off_24), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.unlinkLabel)) },
                modifier = Modifier.clickable { showUnlinkDialog.value = true }
            )
        }
    }
}

@Composable
fun VerticalSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>
) {
    val mod = Modifier
        .graphicsLayer {
            rotationZ = 270f
            transformOrigin = TransformOrigin(0f, 0f)
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                Constraints(
                    minWidth = constraints.minHeight,
                    maxWidth = constraints.maxHeight,
                    minHeight = constraints.minWidth,
                    maxHeight = constraints.maxHeight,
                )
            )
            layout(placeable.height, placeable.width) {
                placeable.place(-placeable.width, 0)
            }
        }
        .then(modifier)

    Slider(
        modifier = mod,
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange
    )
}

@Composable
fun ExpandingListItem(
    expanded: MutableState<Boolean>,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null
) {
    ListItem(
        leadingContent = leadingContent,
        headlineContent = headlineContent,
        trailingContent = { Icon(
            painter = painterResource(id = if (expanded.value) R.drawable.round_keyboard_arrow_down_24
            else R.drawable.round_keyboard_arrow_right_24),
            contentDescription = null
        ) },
        supportingContent = supportingContent,
        modifier = Modifier
            .clickable { expanded.value = !expanded.value }
            .then(modifier)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    state: TimePickerState,
    label: String,
    onApply: (TimePickerState) -> Unit
) {
    val showPicker = remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    modifier = Modifier.padding(bottom = 20.dp),
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showPicker.value) {
                    TimePicker(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        state = state
                    )
                } else {
                    TimeInput(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        state = state
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    IconButton(onClick = { showPicker.value = !showPicker.value }) {
                        Icon(
                            painterResource(
                                id = if (showPicker.value)
                                        R.drawable.outline_keyboard_24 else
                                        R.drawable.outline_schedule_24
                            ),
                            contentDescription = if (showPicker.value) {
                                "Switch to Text Input"
                            } else {
                                "Switch to Touch Input"
                            }
                        )

                    }
                    Row {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                        TextButton(onClick = {
                            onApply(state)
                            onDismissRequest()
                        }) {
                            Text(text = stringResource(id = android.R.string.ok))
                        }
                    }
                }
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
