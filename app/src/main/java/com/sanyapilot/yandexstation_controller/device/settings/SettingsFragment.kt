package com.sanyapilot.yandexstation_controller.device.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.sanyapilot.yandexstation_controller.ui.theme.AppTheme

class SettingsFragment : Fragment() {
    /*
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                requireArguments().getString("deviceId")!!,
                requireArguments().getString("devicePlatform")!!,
                MediaControllerCompat.getMediaController(requireActivity())
            )
        )[SettingsViewModel::class.java]
    }
    */

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
                        // SettingsLayout(viewModel, requireArguments().getString("devicePlatform")!!)
                        SettingsLayout()
                    }
                }
            }
        }
    }
    companion object {
        fun instance(deviceId: String, devicePlatform: String): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            args.apply {
                putString("deviceId", deviceId)
                putString("devicePlatform", devicePlatform)
            }
            fragment.arguments = args
            return fragment
        }
    }
}

/* TODO: Implement device settings
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLayout(
    viewModel: SettingsViewModel = viewModel(),
    platform: String = "yandexstation_2"
) {
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
        NetStatusSnack(
            context = context,
            netStatus = netStatus.value,
            snackbarHostState = snackbarHostState
        )

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
                modifier = Modifier.align(Alignment.CenterHorizontally)
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
                        modifier = Modifier.padding(8.dp)
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
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            eqValues.forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(4.dp)
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

            // Yandex.Station Max specific
            if (platform == "yandexstation_2") {
                // Visualizer
                val visOpened = rememberSaveable { mutableStateOf(false) }
                ExpandingListItem(
                    expanded = visOpened,
                    headlineContent = { Text(text = stringResource(id = R.string.visualizationLabel)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.round_graphic_eq_24),
                            contentDescription = null
                        )
                    }
                )
                AnimatedVisibility(
                    visible = visOpened.value
                ) {
                    val visPresetName by viewModel.visPresetName.collectAsState()
                    val visRandomEnabled by viewModel.visRandomEnabled.collectAsState()
                    Column {
                        ListItem(
                            leadingContent = { Icon(painter = painterResource(id = R.drawable.round_shuffle_24), contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.visualizationRandom)) },
                            trailingContent = { Switch(checked = visRandomEnabled, onCheckedChange = { viewModel.toggleVisRandom() }) },
                            modifier = Modifier.clickable { viewModel.toggleVisRandom() }
                        )
                        var i = 0
                        while (i < VIS_PRESETS.size) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val endIdx = min(i + 2, VIS_PRESETS.size - 1)
                                VIS_PRESETS.slice(i..endIdx).forEach {
                                    BigSelectableButton(
                                        selected = it.id == visPresetName,
                                        enabled = !visRandomEnabled,
                                        onClick = { viewModel.setVisPreset(it.id) },
                                        painter = painterResource(id = it.drawableId)
                                    )
                                }
                                i += 3
                            }
                        }
                    }
                }

                // Clock type
                val clockOpened = rememberSaveable { mutableStateOf(false) }
                ExpandingListItem(
                    expanded = clockOpened,
                    headlineContent = { Text(text = stringResource(id = R.string.clockTypeLabel)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.round_access_time_24),
                            contentDescription = null
                        )
                    }
                )
                AnimatedVisibility(visible = clockOpened.value) {
                    val selectedClockType by viewModel.clockType.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CLOCK_TYPES.forEach {
                            BigSelectableButton(
                                selected = it.id == selectedClockType,
                                onClick = { viewModel.setClockType(it.id) },
                                painter = painterResource(id = it.drawableId)
                            )
                        }
                    }
                }

                // Screen brightness
                val brightnessOpened = rememberSaveable { mutableStateOf(false) }
                ExpandingListItem(
                    expanded = brightnessOpened,
                    headlineContent = { Text(text = stringResource(id = R.string.screenBrightnessLabel)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_settings_brightness_24),
                            contentDescription = null
                        )
                    }
                )
                AnimatedVisibility(visible = brightnessOpened.value) {
                    val autoBrightness by viewModel.screenAutoBrightness.collectAsState()
                    val brightnessLevel by viewModel.screenBrightness.collectAsState()
                    val curSliderLevel = remember { mutableFloatStateOf(brightnessLevel) }
                    Column {
                        ListItem(
                            leadingContent = { Icon(painter = painterResource(id = R.drawable.round_brightness_auto_24), contentDescription = null) },
                            headlineContent = { Text(text = stringResource(id = R.string.screenAutoBrightnessLabel)) },
                            trailingContent = { Switch(checked = autoBrightness, onCheckedChange = { viewModel.toggleAutoBrightness() }) },
                            modifier = Modifier.clickable { viewModel.toggleAutoBrightness() }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Slider(
                                value = curSliderLevel.floatValue,
                                enabled = !autoBrightness,
                                onValueChange = { curSliderLevel.floatValue = it },
                                onValueChangeFinished = { viewModel.updateScreenBrightness(curSliderLevel.floatValue) },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(curSliderLevel.floatValue * 100).roundToInt()}%",
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .alpha(if (autoBrightness) 0.38f else 1f)
                            )
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

@Composable
fun BigSelectableButton(
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    painter: Painter? = null,
    content: @Composable (() -> Unit)? = null
) {
    val colors =
        if (selected) ButtonDefaults.filledTonalButtonColors()
        else ButtonDefaults.outlinedButtonColors()
    val elevation =
        if (selected) ButtonDefaults.filledTonalButtonElevation()
        else null
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = colors,
        enabled = enabled,
        elevation = elevation
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            }
            if (content != null) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    AppTheme {
        Surface {
            SettingsLayout(viewModel(
                factory = SettingsViewModelFactory("deaddeaddeaddeaddead", "yandexstation_2", null)
            ))
        }
    }
}
*/

@Composable
fun SettingsLayout() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        Text(text = "¯\\_(ツ)_/¯", fontSize = 48.sp)
        Text(text = "Здесь точно что-то будет", modifier = Modifier.padding(top = 16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    AppTheme {
        Surface {
            SettingsLayout()
        }
    }
}
