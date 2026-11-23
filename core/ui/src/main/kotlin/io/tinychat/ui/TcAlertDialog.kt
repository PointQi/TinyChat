package io.tinychat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcTheme

@Composable
fun TcAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = TcTheme.palette.bgLayer2,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = containerColor,
        titleContentColor = TcTheme.palette.labelPrimary,
        textContentColor = TcTheme.palette.labelSecondary,
        tonalElevation = 0.dp,
    )
}
