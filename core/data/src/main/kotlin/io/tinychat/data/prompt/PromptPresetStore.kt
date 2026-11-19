package io.tinychat.data.prompt

import io.tinychat.data.db.PromptPresetDao
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.PromptMode
import io.tinychat.model.prompt.PromptSeeds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 新会话落库时写进 `sessions` 的那一对值（决策 10：快照之后会话只读）。
 * `content` 为 null 表示不发 system（`CLEAR` 或内容为空）。
 */
data class PromptSnapshot(val content: String?, val mode: PromptMode)

/**
 * 系统提示词预设（M7.5）。
 *
 * 三件事只在这里做，别处不许再拼：
 * 1. **播种**：内置 5 条每次启动对齐一遍（内置不可改，所以覆盖写是安全的）；
 * 2. **选择**：全局只有一个「当前预设」，id 存在 DataStore；
 * 3. **快照**：新会话创建时取一次，之后会话怎么改都不回写。
 */
class PromptPresetStore(
    private val dao: PromptPresetDao,
    private val selected: Flow<String>,
    private val selectPreset: suspend (String) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** 启动时调用。表空则插入，已有则把内置条目对齐到官方正文。 */
    suspend fun ensureSeeded() {
        val now = clock()
        val existing = dao.all().associateBy { it.id }
        PromptSeeds.builtIns.forEach { seed ->
            val row = existing[seed.id]
            if (row == null || !row.builtin || row.content != seed.content ||
                row.name != seed.name || row.mode != seed.mode
            ) {
                dao.upsert(
                    PromptPresetEntity(
                        id = seed.id,
                        name = seed.name,
                        content = seed.content,
                        mode = seed.mode,
                        builtin = true,
                        sort = seed.sort,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    suspend fun all(): List<PromptPresetEntity> = dao.all()

    suspend fun byId(id: String): PromptPresetEntity? = dao.byId(id)

    suspend fun select(id: String) = selectPreset(id)

    /** 当前选中项；行被删掉/不存在时回退到内置默认（不留在「无预设」这种状态里）。 */
    suspend fun current(): PromptPresetEntity {
        val id = selected.first()
        return dao.byId(id) ?: dao.byId(PromptSeeds.DEFAULT_ID) ?: fallbackDefault()
    }

    /**
     * 保存。`id == null` 为新建；内置预设**不允许**就地改（编辑器会先复制），
     * 所以这里遇到内置 id 直接拒绝，避免 UI 的 bug 悄悄改掉官方原文。
     */
    suspend fun save(id: String?, name: String, content: String, mode: PromptMode): String {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "名称不能为空" }
        val targetId = if (id == null) {
            UUID.randomUUID().toString()
        } else {
            val row = dao.byId(id) ?: error("预设不存在：$id")
            require(!row.builtin) { "内置预设不可修改" }
            id
        }
        val sort = dao.byId(targetId)?.sort ?: dao.nextSort()
        dao.upsert(
            PromptPresetEntity(
                id = targetId,
                name = trimmedName,
                content = content,
                mode = mode,
                builtin = false,
                sort = sort,
                updatedAt = clock(),
            ),
        )
        return targetId
    }

    /** 内置预设不可改，先复制一份再编辑（02 §12.7）。返回新预设 id。 */
    suspend fun copyBuiltin(id: String): String? {
        val row = dao.byId(id) ?: return null
        val newId = UUID.randomUUID().toString()
        dao.upsert(
            PromptPresetEntity(
                id = newId,
                name = "${row.name} 副本",
                content = row.content,
                mode = row.mode,
                builtin = false,
                sort = dao.nextSort(),
                updatedAt = clock(),
            ),
        )
        return newId
    }

    suspend fun deleteCustom(id: String) {
        dao.deleteCustom(id)
        // 删掉的正是当前选中项 → 回退到默认，避免下一个新会话拿到空快照
        if (selected.first() == id) selectPreset(PromptSeeds.DEFAULT_ID)
    }

    /**
     * 恢复默认：把「默认」这一条写回官方原文并选中它。
     * 自定义预设一概不动（02 §12.7 的确认文案承诺了这一点）。
     */
    suspend fun restoreDefault() {
        val seed = PromptSeeds.builtIns.first { it.id == PromptSeeds.DEFAULT_ID }
        dao.upsert(
            PromptPresetEntity(
                id = seed.id,
                name = seed.name,
                content = seed.content,
                mode = seed.mode,
                builtin = true,
                sort = seed.sort,
                updatedAt = clock(),
            ),
        )
        selectPreset(seed.id)
    }

    /** 新会话的快照：变量在这里**不求值**，原文入库，发送时再求值（02 §5.6）。 */
    suspend fun snapshotForNewSession(): PromptSnapshot {
        val row = current()
        val content = row.content.takeIf { row.mode != PromptMode.CLEAR && it.isNotBlank() }
        return PromptSnapshot(content = content, mode = row.mode)
    }

    private fun fallbackDefault(): PromptPresetEntity {
        val seed = PromptSeeds.builtIns.first { it.id == PromptSeeds.DEFAULT_ID }
        return PromptPresetEntity(
            id = seed.id,
            name = seed.name,
            content = seed.content,
            mode = seed.mode,
            builtin = true,
            sort = seed.sort,
            updatedAt = 0,
        )
    }

    companion object {
        /**
         * 给 `:app` 用的入口：那里不直接引 Room 的类型（DAO 的父类型在 app 模块不可见），
         * 所以从库里取 DAO 这一步留在 core:data 内完成。
         */
        fun create(
            database: TinyChatDatabase,
            selected: Flow<String>,
            selectPreset: suspend (String) -> Unit,
        ): PromptPresetStore = PromptPresetStore(
            dao = database.promptPresetDao(),
            selected = selected,
            selectPreset = selectPreset,
        )
    }
}
