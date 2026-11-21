package io.tinychat.crash

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.widget.TextView
import io.tinychat.MainActivity
import io.tinychat.R

/**
 * 崩溃页（M9-2）。跑在 `:crash` 进程，用传统 View 画。
 *
 * 两种状态由 [EXTRA_LOOP] 决定：
 * - 普通崩溃：给「重新打开 / 暂不重启」，用户回到主界面就能接着用；
 * - 连续崩溃（同一种异常第二次起）：给「退出 / 清理本地数据」，把能自救的路留出来。
 *
 * 主进程在崩的那一刻已经被 [CrashGuard] 杀掉了，所以这一屏没有「返回上一页」可去，
 * 按返回键等同于点第二个按钮（不重启，直接结束这个进程）。
 */
class CrashActivity : Activity() {

    companion object {
        const val EXTRA_LOOP = "loop"
        const val EXTRA_STREAMING = "streaming"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
        val streaming = intent.getBooleanExtra(EXTRA_STREAMING, false)

        val title = findViewById<TextView>(R.id.crash_title)
        val body = findViewById<TextView>(R.id.crash_body)
        val primary = findViewById<TextView>(R.id.crash_primary)
        val secondary = findViewById<TextView>(R.id.crash_secondary)

        if (loop) {
            title.text = "连续遇到问题"
            body.text = "诊断信息已保存。建议稍后再试；若仍无法正常使用，可清理本地数据。"
            primary.text = "退出"
            secondary.text = "清理本地数据"
            secondary.setTextColor(getColor(R.color.tc_crash_danger))
            primary.setOnClickListener { quit() }
            secondary.setOnClickListener { confirmWipe() }
        } else {
            title.text = "刚刚出了点状况"
            body.text = if (streaming) {
                "诊断信息已保存。重新打开即可继续，当前正在生成的内容可能未保存。"
            } else {
                "诊断信息已保存。重新打开即可继续。"
            }
            primary.text = "重新打开"
            secondary.text = "暂不重启"
            primary.setOnClickListener { restart() }
            secondary.setOnClickListener { quit() }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        quit()
    }

    private fun restart() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        finish()
        killSelf()
    }

    private fun confirmWipe() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) {
            android.R.style.Theme_Material_Dialog_Alert
        } else {
            android.R.style.Theme_Material_Light_Dialog_Alert
        }
        AlertDialog.Builder(this, theme)
            .setTitle("确定要清理本地数据吗？")
            .setMessage("此操作将永久删除本机上的对话记录、偏好设置及服务商配置。数据仅保存在本地，删除将无法恢复。")
            .setPositiveButton("全部删除") { _, _ ->
                LocalWipe.request(this)
                quit()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun quit() {
        finish()
        killSelf()
    }

    private fun killSelf() {
        runCatching { Process.killProcess(Process.myPid()) }
    }
}
