package io.tinychat.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 全局崩溃兜底（M9-2）。
 *
 * 只在 [Application.onCreate] 里装一次。装上之后，任何线程抛出的未捕获异常都会走
 * [handle]：写一份诊断信息 → 判断是不是连续崩溃 → 起 `:crash` 进程的崩溃页 → 结束主进程。
 *
 * **为什么不复用进程**：主线程已经死了，同一个进程里再也画不出界面；崩溃页必须是独立进程，
 * 才可能在崩溃现场给用户一句人话。
 *
 * **为什么写文件用 `runCatching` 全包**：崩溃路径上再抛一次异常，用户看到的就是系统弹窗
 * 而不是我们的页面，兜底等于没做。
 */
object CrashGuard {

    /** 崩溃时正开着流式生成（决定正文要不要带「正在生成的内容可能未保存」那半句）。 */
    @Volatile
    var streaming: Boolean = false

    /** 当前所在页面，只用于诊断信息里的「现场」一行。 */
    @Volatile
    var stage: String = "启动"

    private const val TAG = "CrashGuard"
    private const val LOOP_FILE = "crash-loop.txt"
    private const val CRASH_DIR = "crashes"

    /** 主进程活过这段时间就认为这次启动是正常的，清掉循环计数。 */
    private const val HEALTHY_AFTER_MILLIS = 30_000L

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val handled = runCatching { handle(app, thread, error) }.getOrDefault(false)
            // 兜底自己出错时才退回系统默认处理（会露出系统弹窗，但总比静默死掉好）
            if (!handled) previous?.uncaughtException(thread, error)
        }
        // 能活过 30 秒说明这次不是「开了就崩」，把计数清掉，别让偶发崩溃越攒越像循环
        runCatching {
            Handler(Looper.getMainLooper()).postDelayed(
                { CrashLoop.clear(loopFile(app)) },
                HEALTHY_AFTER_MILLIS,
            )
        }
    }

    fun crashDir(context: Context): File = File(context.filesDir, CRASH_DIR)

    private fun loopFile(context: Context): File = File(context.filesDir, LOOP_FILE)

    /** @return true 表示已经接手（无论有没有起页面），false 表示交给系统默认处理。 */
    private fun handle(app: Application, thread: Thread, error: Throwable): Boolean {
        val now = System.currentTimeMillis()
        val signature = signatureOf(error)
        val report = buildReport(app, thread, error, now)

        // 1. 先落盘：这是唯一「崩完之后还能带走」的东西，排在起界面之前
        CrashReporter.write(crashDir(app), now, signature, report)

        // 2. 崩溃页自己崩了就别再来一轮
        val inCrashProcess = processName().endsWith(":crash")
        if (inCrashProcess) return true.also { kill() }

        // 3. 连续崩溃：第 4 次起不再弹页面，直接退出
        val prior = CrashLoop.recent(loopFile(app), signature, now)
        CrashLoop.record(loopFile(app), signature, now)
        if (prior >= CrashLoop.LIMIT) return true.also { kill() }

        // 4. 拉起崩溃页（第二次起换成「连续遇到问题」那一屏）
        launchScreen(app, loop = prior >= 1)
        Log.w(TAG, "已接管崩溃：$signature")
        return true.also { kill() }
    }

    private fun launchScreen(app: Application, loop: Boolean) {
        runCatching {
            val intent = Intent(app, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashActivity.EXTRA_LOOP, loop)
                putExtra(CrashActivity.EXTRA_STREAMING, streaming)
            }
            app.startActivity(intent)
        }
    }

    private fun kill(): Nothing {
        runCatching { Process.killProcess(Process.myPid()) }
        exitProcess(10)
    }

    /**
     * 异常签名：根因的类名 + 第一条自己代码里的栈帧。
     *
     * 用**根因**而不是最外层异常：系统常把 Receiver/线程里的异常包一层
     * `RuntimeException: Unable to start receiver ...`，拿外层类型当签名会让完全不同的
     * 毛病挤成一个签名，循环判定就会误伤。
     */
    private fun signatureOf(error: Throwable): String {
        var root = error
        val seen = HashSet<Throwable>()
        while (true) {
            if (!seen.add(root)) break
            root = root.cause ?: break
        }
        val frame = run {
            var current: Throwable? = error
            var guard = 0
            while (current != null && guard++ < 8) {
                val hit = current.stackTrace.firstOrNull { it.className.startsWith("io.tinychat") }
                if (hit != null) return@run hit
                current = current.cause
            }
            null
        }
        val where = frame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: ""
        return "${root.javaClass.simpleName}@$where".take(64)
    }

    private fun buildReport(app: Application, thread: Thread, error: Throwable, now: Long): String {
        val builder = StringBuilder()
        runCatching {
            builder.append("时间: ").append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(now)),
            ).append('\n')
            builder.append("版本: ").append(versionName(app)).append(" (").append(versionCode(app)).append(")\n")
            builder.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            builder.append("系统: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            builder.append("进程: ").append(processName()).append('\n')
            val main = Looper.getMainLooper()?.thread
            builder.append("线程: ").append(thread.name)
                .append(if (thread === main) "（主线程）" else "").append('\n')
            builder.append("现场: ").append(stage)
                .append(if (streaming) " · 正在生成" else "").append('\n')
            builder.append("异常: ").append(error.javaClass.name)
                .append(if (error.message.isNullOrBlank()) "" else ": " + error.message).append('\n')
            builder.append('\n').append("堆栈:\n").append(Log.getStackTraceString(error))
        }
        val text = builder.toString()
        return if (text.isBlank()) "诊断信息生成失败\n" else CrashReporter.redact(text)
    }

    private fun versionName(app: Application): String = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun versionCode(app: Application): Long = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    /** API 28 起有现成的；26/27 读 `/proc/self/cmdline`。 */
    private fun processName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            File("/proc/self/cmdline").readText().trimEnd('\u0000')
        }
    }.getOrDefault("")
}
