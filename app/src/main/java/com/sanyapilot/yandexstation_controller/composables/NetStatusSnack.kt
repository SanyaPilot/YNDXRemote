package com.sanyapilot.yandexstation_controller.composables

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.SettingsErrors
import com.sanyapilot.yandexstation_controller.device.settings.NetStatus

@Composable
fun NetStatusSnack(
    context: Context,
    netStatus: NetStatus,
    snackbarHostState: SnackbarHostState
) {
    if (!netStatus.ok) {
        LaunchedEffect(snackbarHostState) {
            snackbarHostState.showSnackbar(
                message = when(netStatus.error) {
                    SettingsErrors.NOT_LINKED -> context.resources.getString(R.string.deviceNotLinked)
                    SettingsErrors.UNAUTHORIZED -> context.resources.getString(R.string.unauthorizedError)
                    SettingsErrors.NO_INTERNET -> context.resources.getString(R.string.errorNoInternet)
                    SettingsErrors.PARSING_ERROR -> context.resources.getString(R.string.parsingError)
                    SettingsErrors.TIMEOUT -> context.resources.getString(R.string.timeoutError)
                    SettingsErrors.UNKNOWN -> context.resources.getString(R.string.unknownError)
                    else -> context.resources.getString(R.string.wtf)
                },
                duration = SnackbarDuration.Long
            )
        }
    }
}