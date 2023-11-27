package com.sanyapilot.yandexstation_controller.composables

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sanyapilot.yandexstation_controller.R

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
