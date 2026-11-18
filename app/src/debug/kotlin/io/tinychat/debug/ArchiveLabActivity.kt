package io.tinychat.debug

import android.app.Activity
import android.os.Bundle
import io.tinychat.AppContainer
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 导入/导出验收台（**仅 debug 构建**）。
 *
 * 数据管理页的 SAF 流程要靠手点，跑 3340 会话的往返验收时太慢且不好复现；
 * 这里直接按路径跑同一条 `ChatArchive` 代码，把结果写进 `cacheDir/archive.txt`。
 *
 * ```bash
 * # 导入 87MB 真样本
 * adb shell am start -n io.tinychat/io.tinychat.debug.ArchiveLabActivity \
 *   --es import /sdcard/Download/deepseek_data.zip
 * adb shell run-as io.tinychat cat cache/archive.txt
 *
 * # 导出到可读的位置
 * adb shell am start -n io.tinychat/io.tinychat.debug.ArchiveLabActivity \
 *   --es export /sdcard/Download/tinychat_export.zip
 * ```
 */
class ArchiveLabActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(applicationContext)
        val archive = container.archive
        val importPath = intent.getStringExtra("import")
        val exportPath = intent.getStringExtra("export")
        // SAF 的 Uri 版本：`am start -d content://… --grant-read-uri-permission`
        val importUri = intent.data

        val lines = mutableListOf<String>()
        try {
            if (importUri != null) {
                lines += "import uri=$importUri"
                val report = runBlocking { archive.import(importUri) }
                lines += "import ok sessions=${report.sessions} messages=${report.messages} " +
                    "fragments=${report.fragments} files=${report.files} " +
                    "dropped=${report.droppedFragments} ms=${report.durationMs}"
            }
            if (importPath != null) {
                val source = File(importPath)
                lines += "import source=${source.absolutePath} size=${source.length()}"
                val report = runBlocking { archive.importFile(source) }
                lines += "import ok sessions=${report.sessions} messages=${report.messages} " +
                    "fragments=${report.fragments} files=${report.files} " +
                    "dropped=${report.droppedFragments} ms=${report.durationMs}"
            }
            if (exportPath != null) {
                val target = File(exportPath)
                val report = runBlocking { archive.exportFile(target) }
                lines += "export ok sessions=${report.sessions} messages=${report.messages} " +
                    "fragments=${report.fragments} files=${report.files} " +
                    "bytes=${report.bytes} ms=${report.durationMs} path=${target.absolutePath}"
            }
            if (importPath == null && exportPath == null) {
                lines += "usage: --es import <zip path> | --es export <zip path>"
            }
        } catch (e: Throwable) {
            lines += "FAILED ${e::class.java.simpleName}: ${e.message}"
            android.util.Log.e("ArchiveLab", "failed", e)
        } finally {
            lines.forEach { android.util.Log.i("ArchiveLab", it) }
            val out = File(cacheDir, "archive.txt")
            out.writeText(lines.joinToString("\n"))
        }
        finish()
    }
}
