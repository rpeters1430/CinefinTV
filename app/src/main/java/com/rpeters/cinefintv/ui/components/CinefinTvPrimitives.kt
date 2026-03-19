package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.widthIn(min = 420.dp, max = 760.dp),
            shape = RoundedCornerShape(spacing.cornerContainer),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.elevatedSurface.copy(alpha = 0.98f),
            ),
            tonalElevation = 10.dp,
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            expressiveColors.chromeSurface.copy(alpha = 0.95f),
                            expressiveColors.elevatedSurface.copy(alpha = 0.98f),
                        ),
                    ),
                )
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinSettingListItem(
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        overlineContent = overline?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinSwitchListItem(
    headline: String,
    supporting: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> CinefinOptionDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onDismissRequest: () -> Unit,
    onOptionSelected: (T) -> Unit,
    supportingText: String? = null,
    ) {
        val spacing = LocalCinefinSpacing.current
        val listState = rememberLazyListState()
        val initialFocusRequester = remember { FocusRequester() }
        val selectedIndex = options.indexOf(selected).takeIf { it >= 0 } ?: 0

        LaunchedEffect(selectedIndex, options.size) {
            listState.scrollToItem(selectedIndex)
            initialFocusRequester.requestFocus()
        }

        CinefinDialogSurface(onDismissRequest = onDismissRequest) {
            Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    ListItem(
                        selected = option == selected,
                        onClick = {
                            onOptionSelected(option)
                            onDismissRequest()
                        },
                        modifier = if (index == selectedIndex) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                        headlineContent = {
                            Text(
                                text = labelFor(option),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                        trailingContent = {
                            if (option == selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinDialogActions(
    dismissLabel: String?,
    confirmLabel: String,
    onDismiss: (() -> Unit)?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
    ) {
        if (dismissLabel != null && onDismiss != null) {
            OutlinedButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
        ) {
            Text(confirmLabel)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    Surface(
            modifier = modifier,
            shape = RoundedCornerShape(spacing.cornerCard),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.76f),
            ),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = expressiveColors.titleAccent,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = SurfaceDefaults.colors(
            containerColor = if (isFocused) {
                expressiveColors.elevatedSurface.copy(alpha = 0.96f)
            } else {
                expressiveColors.accentSurface.copy(alpha = 0.72f)
            },
        ),
        border = Border(
            border = androidx.compose.foundation.BorderStroke(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) expressiveColors.focusRing else expressiveColors.borderSubtle,
            ),
        ),
        tonalElevation = if (isFocused) 6.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) expressiveColors.titleAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = imeAction,
                    keyboardType = keyboardType,
                ),
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                    .then(modifier),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}
