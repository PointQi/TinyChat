package io.tinychat.feature.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/** 「从相册选择」最终落到哪个选择器 */
enum class ImagePickSource { PHOTO_PICKER, GALLERY, DOCUMENTS }

/**
 * 「从相册选择」的三个可能落点，按优先级排：
 *
 * 1. **系统照片选择器**（Android 13+ / 有后向移植的机型）——隐私最好，自带相册分组；
 * 2. **ACTION_PICK + 媒体库 Uri** —— 这是"直接进手机相册"的那个 intent：
 *    Pixel 上它由照片选择器接，华为上是 `com.huawei.photos` 的相册选择页；
 * 3. **DocumentsUI 多选**（最后的兜底）。
 *
 * ⚠️ 不能用 `ACTION_GET_CONTENT` 当"进图库"：真机实测（FIN-AL60 / Android 12）它只被
 * `com.android.documentsui` 和华为文件管理接住，`com.huawei.photos` 完全不接 —— 于是
 * 用户看到的是文件浏览器，而不是相册。
 */
object ImagePick {

    private fun pickImagesIntent(): Intent =
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
    /** 系统照片选择器（`PickVisualMedia`）可用吗 */
    fun hasPhotoPicker(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val intent = Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        return runCatching {
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }.getOrDefault(false)
    }

    /** 有能接 `ACTION_PICK` 的相册吗（华为图库、Pixel 照片选择器都算） */
    fun hasGallery(context: Context): Boolean = runCatching {
        context.packageManager.queryIntentActivities(pickImagesIntent(), 0).isNotEmpty()
    }.getOrDefault(false)
}

/**
 * 「从相册选择」契约：`ACTION_PICK` + 媒体库 Uri，尽量要**多选**
 * （`EXTRA_ALLOW_MULTIPLE` 不是官方保证的，OEM 相册认就多选，不认就单选，两种都接）。
 */
class PickImagesFromGallery : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        // 多选走 clipData，单选走 data；有的相册两个都给，去重即可
        val clip = intent.clipData
        val fromClip = if (clip != null) {
            (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri }
        } else {
            emptyList()
        }
        if (fromClip.isNotEmpty()) return fromClip.distinct()
        return listOfNotNull(intent.data)
    }
}
