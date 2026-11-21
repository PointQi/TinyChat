package io.tinychat.crash

import android.content.Context
import java.io.File

/**
 * 「清理本地数据」的实际执行（M9-2）。
 *
 * 崩溃页跑在 `:crash` 进程里，那里没有依赖注入，也不该在崩溃现场去开 Room 和 DataStore
 * （两个进程同时碰同一份 DataStore 是明确不支持的）。所以按钮只做一件事：留一个标记，
 * 下次正常启动、任何容器建起来之前，再由主进程把该删的删干净。
 */
object LocalWipe {

    private const val FLAG = "pending-wipe"

    /** 由崩溃页调用：只写标记，不碰数据。 */
    fun request(context: Context) {
        runCatching { File(context.filesDir, FLAG).writeText("1", Charsets.UTF_8) }
    }

    /** 由 [io.tinychat.TinyChatApplication] 在启动最前面调用。 */
    fun performIfRequested(context: Context) {
        val flag = File(context.filesDir, FLAG)
        if (!flag.isFile) return
        runCatching {
            // filesDir 里装着 Room 之外的几乎所有东西：DataStore、附件、缩略图、崩溃计数。
            // 只留 crashes/，免得刚清完就把「为什么清」的证据一起删了。
            deleteChildren(context.filesDir) { it.name == "crashes" }
            deleteChildren(context.cacheDir)
            // Room 的库文件在 dataDir/databases，不在 filesDir 下面
            deleteChildren(File(context.applicationInfo.dataDir, "databases"))
            deleteChildren(File(context.applicationInfo.dataDir, "shared_prefs"))
        }
        runCatching { flag.delete() }
    }

    private fun deleteChildren(dir: File, keep: (File) -> Boolean = { false }) {
        dir.listFiles()?.forEach { child ->
            if (!keep(child)) runCatching { child.deleteRecursively() }
        }
    }
}
