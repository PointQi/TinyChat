package io.tinychat.data.prompt

import io.tinychat.data.db.PromptPresetDao
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.PromptMode
import io.tinychat.model.prompt.PromptComposer
import io.tinychat.model.prompt.PromptSeeds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 新会话落库时写进 `sessions` 的那一对值（决策 10：快照之后会话只读）。
 * `content` 为 null 表示不发 system。
 */
data class PromptSnapshot(val content: String?, val mode: PromptMode)

/** 当前生效的一组设置：开关 + 选中的预设（null = 不使用自定义内容）。 */
data class PromptState(
    val includeOfficial: Boolean,
    val selected: PromptPresetEntity?,
)

/**
 * 系统提示词（决策 45）。
 *
 * 只在两个地方说「发什么」：这里给出组合结果，`ChatRepository` 落快照。
 * - 预设表里只有用户自己建的条目；内置五条已随 v2 迁移退场；
 * - 「无提示词」不再是条目，而是「开关关 + 不选预设」这个状态；
 * - 新会话快照里存的是**拼好的最终文本**，`mode` 恒为 `REPLACE`，
 *   所以协议层不会再自己拼一次官方前缀（旧会话的快照带着自己的 mode，行为不变）。
 */
class PromptPresetStore(
    private val dao: PromptPresetDao,
    private val selected: Flow<String?>,
    private val selectPreset: suspend (String?) -> Unit,
    private val includeOfficialSetting: Flow<Boolean>,
    private val setIncludeOfficialSetting: suspend (Boolean) -> Unit,
    private val migrated: Flow<Boolean>,
    private val markMigrated: suspend () -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * v1 → v2 的一次性迁移，启动时调用。
     *
     * 旧模型是「5 条内置 + 组合方式三选一」，新模型是「开关 + 用户预设」。映射必须无损：
     * - 旧的 `默认` → 开关开、不选预设；
     * - 旧的 `无提示词` → 开关关、不选预设；
     * - 旧的骨架（角色扮演/翻译润色/学习辅导）如果正被选中 → **复制成同名用户预设**再选中，
     *   否则这些人升级后会静默掉回「只发官方」，行为变了还不知道；
     * - 旧的自定义预设保留，组合方式归一成 `REPLACE`（组合的含义已经上移到开关）；
     * - 内容为空的预设没有意义了（空内容不再允许保存），直接删掉。
     */
    suspend fun migrateIfNeeded() {
        if (migrated.first()) return

        val rows = dao.all()
        val oldSelectedId = selected.first()
        val oldSelected = oldSelectedId?.let { id -> rows.firstOrNull { it.id == id } }

        var include = true
        var newSelected: String? = null

        when {
            oldSelected == null -> {
                // 从没动过提示词，或者选中的行早就不在了
                include = true
                newSelected = null
            }

            oldSelected.id == PromptSeeds.LEGACY_DEFAULT_ID -> {
                include = true
                newSelected = null
            }

            oldSelected.id == PromptSeeds.LEGACY_NONE_ID -> {
                include = false
                newSelected = null
            }

            oldSelected.builtin -> {
                val copy = oldSelected.copy(
                    id = UUID.randomUUID().toString(),
                    builtin = false,
                    mode = PromptMode.REPLACE,
                    sort = dao.nextSort(),
                    updatedAt = clock(),
                )
                dao.upsert(copy)
                newSelected = copy.id
                include = oldSelected.mode != PromptMode.REPLACE
            }

            else -> {
                newSelected = oldSelected.id
                include = oldSelected.mode == PromptMode.APPEND
                if (oldSelected.mode == PromptMode.CLEAR) {
                    newSelected = null
                    include = false
                }
            }
        }

        // 非内置行：空内容删掉，组合方式归一
        rows.filter { !it.builtin }.forEach { row ->
            when {
                row.content.isBlank() -> dao.delete(row.id)
                row.mode != PromptMode.REPLACE -> dao.upsert(row.copy(mode = PromptMode.REPLACE))
            }
        }
        // 内置行全部退场
        rows.filter { it.builtin }.forEach { dao.delete(it.id) }

        if (newSelected != null && dao.byId(newSelected) == null) newSelected = null

        selectPreset(newSelected)
        setIncludeOfficialSetting(include)
        markMigrated()
    }

    suspend fun all(): List<PromptPresetEntity> = dao.all()

    suspend fun byId(id: String): PromptPresetEntity? = dao.byId(id)

    suspend fun state(): PromptState {
        val id = selected.first()
        return PromptState(
            includeOfficial = includeOfficialSetting.first(),
            selected = id?.let { dao.byId(it) },
        )
    }

    suspend fun select(id: String?) = selectPreset(id)

    suspend fun setIncludeOfficial(enabled: Boolean) = setIncludeOfficialSetting(enabled)

    /**
     * 保存。`id == null` 为新建。名称与内容都不许为空——
     * 「不发自定义内容」用「不选预设」表达，不靠一条空预设。
     */
    suspend fun save(id: String?, name: String, content: String): String {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "名称不能为空" }
        require(content.isNotBlank()) { "内容不能为空" }

        val targetId = id ?: UUID.randomUUID().toString()
        val sort = dao.byId(targetId)?.sort ?: dao.nextSort()
        dao.upsert(
            PromptPresetEntity(
                id = targetId,
                name = trimmedName,
                content = content,
                mode = PromptMode.REPLACE,
                builtin = false,
                sort = sort,
                updatedAt = clock(),
            ),
        )
        return targetId
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        // 删掉的正是当前选中项 → 回到「不使用自定义内容」，而不是留一个指向空气的 id
        if (selected.first() == id) selectPreset(null)
    }

    /** 新会话的快照：变量在这里**不求值**，原文入库，发送时再求值（02 §5.6）。 */
    suspend fun snapshotForNewSession(): PromptSnapshot {
        val state = state()
        return PromptSnapshot(
            content = PromptComposer.compose(
                includeOfficial = state.includeOfficial,
                custom = state.selected?.content.orEmpty(),
            ),
            mode = PromptMode.REPLACE,
        )
    }

    companion object {
        /**
         * 给 `:app` 用的入口：那里不直接引 Room 的类型（DAO 的父类型在 app 模块不可见），
         * 所以从库里取 DAO 这一步留在 core:data 内完成。
         */
        fun create(
            database: TinyChatDatabase,
            selected: Flow<String?>,
            selectPreset: suspend (String?) -> Unit,
            includeOfficial: Flow<Boolean>,
            setIncludeOfficial: suspend (Boolean) -> Unit,
            migrated: Flow<Boolean>,
            markMigrated: suspend () -> Unit,
        ): PromptPresetStore = PromptPresetStore(
            dao = database.promptPresetDao(),
            selected = selected,
            selectPreset = selectPreset,
            includeOfficialSetting = includeOfficial,
            setIncludeOfficialSetting = setIncludeOfficial,
            migrated = migrated,
            markMigrated = markMigrated,
        )
    }
}
