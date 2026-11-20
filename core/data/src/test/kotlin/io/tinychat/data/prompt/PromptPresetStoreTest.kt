package io.tinychat.data.prompt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.PromptMode
import io.tinychat.model.prompt.PromptSeeds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 系统提示词 v2（决策 45）：开关 + 用户预设，以及 v1 数据的一次性迁移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptPresetStoreTest {

    private lateinit var db: TinyChatDatabase
    private lateinit var selected: MutableStateFlow<String?>
    private lateinit var includeOfficial: MutableStateFlow<Boolean>
    private lateinit var migrated: MutableStateFlow<Boolean>
    private lateinit var store: PromptPresetStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = DatabaseFactory.createInMemory(context)
        selected = MutableStateFlow<String?>(null)
        includeOfficial = MutableStateFlow(true)
        migrated = MutableStateFlow(false)
        store = PromptPresetStore(
            dao = db.promptPresetDao(),
            selected = selected,
            selectPreset = { id -> selected.value = id },
            includeOfficialSetting = includeOfficial,
            setIncludeOfficialSetting = { value -> includeOfficial.value = value },
            migrated = migrated,
            markMigrated = { migrated.value = true },
            clock = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun row(
        id: String,
        name: String,
        content: String,
        mode: PromptMode,
        builtin: Boolean,
    ) {
        db.promptPresetDao().upsert(
            PromptPresetEntity(
                id = id,
                name = name,
                content = content,
                mode = mode,
                builtin = builtin,
                sort = 0,
                updatedAt = 0L,
            ),
        )
    }

    /** 旧版本的 5 条内置，迁移的输入。 */
    private suspend fun seedLegacyBuiltIns() {
        row(PromptSeeds.LEGACY_DEFAULT_ID, "默认", PromptSeeds.OFFICIAL_SYSTEM, PromptMode.REPLACE, builtin = true)
        row(PromptSeeds.LEGACY_NONE_ID, "无提示词", "", PromptMode.CLEAR, builtin = true)
        row("roleplay", "角色扮演", PromptSeeds.roleplay, PromptMode.REPLACE, builtin = true)
        row("translate", "翻译润色", PromptSeeds.translate, PromptMode.REPLACE, builtin = true)
        row("tutor", "学习辅导", PromptSeeds.tutor, PromptMode.REPLACE, builtin = true)
    }

    @Test
    fun `旧版本的默认迁成开关开且不选预设`() = runBlocking {
        seedLegacyBuiltIns()
        selected.value = PromptSeeds.LEGACY_DEFAULT_ID

        store.migrateIfNeeded()

        assertTrue(includeOfficial.value)
        assertNull(selected.value)
        assertTrue(store.all().isEmpty())
        assertTrue(migrated.value)
    }

    @Test
    fun `旧版本的无提示词迁成开关关且不选预设`() = runBlocking {
        seedLegacyBuiltIns()
        selected.value = PromptSeeds.LEGACY_NONE_ID

        store.migrateIfNeeded()

        assertFalse(includeOfficial.value)
        assertNull(selected.value)
    }

    @Test
    fun `旧版本选中骨架时复制成用户预设并继续选中`() = runBlocking {
        seedLegacyBuiltIns()
        selected.value = "roleplay"

        store.migrateIfNeeded()

        val copiedId = selected.value
        assertTrue(copiedId != null && copiedId != "roleplay")
        val copied = store.byId(copiedId!!)!!
        assertEquals("角色扮演", copied.name)
        assertEquals(PromptSeeds.roleplay, copied.content)
        assertFalse(copied.builtin)
        // 旧骨架是「替换」→ 新模型里等于不发送官方那句
        assertFalse(includeOfficial.value)
    }

    @Test
    fun `旧版本的自定义预设保留并把组合方式归一到替换`() = runBlocking {
        row("mine", "我的", "你是猫", PromptMode.APPEND, builtin = false)
        selected.value = "mine"

        store.migrateIfNeeded()

        assertEquals("mine", selected.value)
        assertTrue(includeOfficial.value)
        assertEquals(PromptMode.REPLACE, store.byId("mine")!!.mode)
    }

    @Test
    fun `迁移只跑一次`() = runBlocking {
        seedLegacyBuiltIns()
        selected.value = "roleplay"

        store.migrateIfNeeded()
        val afterFirst = selected.value
        store.select(null)
        store.migrateIfNeeded()

        // 第二次调用不再改状态
        assertNull(selected.value)
        assertTrue(afterFirst != null)
    }

    @Test
    fun `内容为空的预设不允许保存`(): Unit = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.save(id = null, name = "空的", content = "   ") }
        }
    }

    @Test
    fun `删除选中的预设后回到不使用自定义内容`() = runBlocking {
        val id = store.save(id = null, name = "我的", content = "你是猫")
        store.select(id)
        assertEquals(id, store.state().selected?.id)

        store.delete(id)

        assertNull(store.state().selected)
        assertNull(selected.value)
    }

    @Test
    fun `快照把开关与预设拼成真正要发的文本`() = runBlocking {
        val id = store.save(id = null, name = "我的", content = "你是猫")

        store.select(id)
        store.setIncludeOfficial(true)
        assertEquals(
            PromptSeeds.OFFICIAL_SYSTEM + "\n\n你是猫",
            store.snapshotForNewSession().content,
        )

        store.setIncludeOfficial(false)
        assertEquals("你是猫", store.snapshotForNewSession().content)
        assertEquals(PromptMode.REPLACE, store.snapshotForNewSession().mode)

        store.select(null)
        assertNull(store.snapshotForNewSession().content)
    }

    @Test
    fun `新建的预设按创建顺序排在后面`() = runBlocking {
        val first = store.save(id = null, name = "一", content = "a")
        val second = store.save(id = null, name = "二", content = "b")
        assertEquals(listOf(first, second), store.all().map { it.id })
    }
}
