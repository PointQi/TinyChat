package io.tinychat.feature.chat.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import java.io.File

/**
 * 分享抽屉（M8-2 / 02 §12.9）：生成图片 / 复制文本 / 分享文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onGenerateImage: () -> Unit,
    onCopyText: () -> Unit,
    onShareText: () -> Unit,
) {
    val palette = TcTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("分享", style = TcTypography.titleMedium, color = palette.labelPrimary)
            Spacer(Modifier.height(10.dp))
            ShareRow("生成图片", "长图，公式与代码一起带上", onGenerateImage)
            ShareRow("复制文本", null, onCopyText)
            ShareRow("分享文本", null, onShareText)
        }
    }
}

@Composable
private fun ShareRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = TcTypography.bodyLarge, color = palette.labelPrimary)
        if (subtitle != null) {
            Text(subtitle, style = TcTypography.labelMedium, color = palette.labelTertiary)
        }
    }
}

/** 生成中：一个不能被误触关掉的转圈对话框。 */
@Composable
fun ShareProgressDialog() {
    val palette = TcTheme.palette
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = palette.brand)
                Spacer(Modifier.height(12.dp))
                Text("正在生成图片", style = TcTypography.bodyMedium, color = palette.labelPrimary)
            }
        },
    )
}

/** 生成完的预览：能先看一眼再决定发不发。 */
@Composable
fun SharePreviewDialog(
    output: ShareCapture.Output,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TcTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (output.pages > 1) "分享图片（共 ${output.pages} 张）" else "分享图片",
                style = TcTypography.titleMedium,
                color = palette.labelPrimary,
            )
        },
        text = {
            Image(
                bitmap = output.preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        },
        confirmButton = {
            TextButton(onClick = onShare) { Text("分享") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "分享回答"))
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("回答", text))
}

fun shareImages(context: Context, files: List<File>) {
    val uris = files.map {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "分享图片"))
}
