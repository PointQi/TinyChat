package io.tinychat.data.prompt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.PromptMode
import io.tinychat.model.prompt.PromptSeeds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptPresetStoreTest {

    private lateinit var db: TinyChatDatabase
    private lateinit var selected: MutableStateFlow<String>
    private lateinit var store: PromptPresetStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = DatabaseFactory.createInMemory(context)
        selected = MutableStateFlow(PromptSeeds.DEFAULT_ID)
        store = PromptPresetStore(
            dao = db.promptPresetDao(),
            selected = selected,
            selectPreset = { id -> selected.update { id } },
            clock = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `播种 5 个内置且默认是官方原文`() = runBlocking {
        store.ensureSeeded()
        val all = store.all()
        assertEquals(5, all.size)
        assertTrue(all.all { it.builtin })
        val default = store.byId(PromptSeeds.DEFAULT_ID)!!
        assertEquals(PromptSeeds.OFFICIAL_SYSTEM, default.content)
        assertEquals(PromptMode.REPLACE, default.mode)
    }

    @Test
    fun `重复播种不会重复插入`() = runBlocking {
        store.ensureSeeded()
        store.ensureSeeded()
        assertEquals(5, store.all().size)
    }

    @Test
    fun `默认预设的新会话快照是官方原文`() = runBlocking {
        store.ensureSeeded()
        val snapshot = store.snapshotForNewSession()
        assertEquals(PromptSeeds.OFFICIAL_SYSTEM, snapshot.content)
        assertEquals(PromptMode.REPLACE, snapshot.mode)
    }

    @Test
    fun `无提示词预设给 CLEAR 且内容为空`() = runBlocking {
        store.ensureSeeded()
        store.select(PromptSeeds.NONE_ID)
        val snapshot = store.snapshotForNewSession()
        assertNull(snapshot.content)
        assertEquals(PromptMode.CLEAR, snapshot.mode)
    }

    @Test
    fun `选中项被删掉后回退到默认`() = runBlocking {
        store.ensureSeeded()
        val id = store.save(id = null, name = "我的", content = "你是猫", mode = PromptMode.REPLACE)
        store.select(id)
        assertEquals(id, store.current().id)

        store.deleteCustom(id)
        assertEquals(PromptSeeds.DEFAULT_ID, store.current().id)
        assertEquals(PromptSeeds.DEFAULT_ID, selected.value)
    }

    @Test
    fun `内置预设不能被就地改写`() = runBlocking {
        store.ensureSeeded()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                store.save(
                    id = PromptSeeds.DEFAULT_ID,
                    name = "默认",
                    content = "被改掉了",
                    mode = PromptMode.REPLACE,
                )
            }
        }
        assertEquals(PromptSeeds.OFFICIAL_SYSTEM, store.byId(PromptSeeds.DEFAULT_ID)!!.content)
    }

    @Test
    fun `复制内置得到可编辑的自定义预设`() = runBlocking {
        store.ensureSeeded()
        val copied = store.copyBuiltin(PromptSeeds.DEFAULT_ID)
        assertNotNull(copied)
        val row = store.byId(copied!!)!!
        assertTrue(!row.builtin)
        assertEquals("默认 副本", row.name)
        assertEquals(PromptSeeds.OFFICIAL_SYSTEM, row.content)

        // 副本可以改
        store.save(copied, "默认 副本", "改过的内容", PromptMode.APPEND)
        assertEquals("改过的内容", store.byId(copied)!!.content)
        assertEquals(PromptMode.APPEND, store.byId(copied)!!.mode)
    }

    @Test
    fun `新建的自定义预设进入列表末尾`() = runBlocking {
        store.ensureSeeded()
        val id = store.save(id = null, name = "口播稿", content = "{{language}}", mode = PromptMode.REPLACE)
        val all = store.all()
        assertEquals(6, all.size)
        assertEquals(id, all.last().id)
    }

    @Test
    fun `恢复默认写回官方原文并选中默认`() = runBlocking {
        store.ensureSeeded()
        store.select("roleplay")
        store.restoreDefault()
        assertEquals(PromptSeeds.DEFAULT_ID, store.current().id)
        assertEquals(PromptSeeds.OFFICIAL_SYSTEM, store.current().content)
    }

    @Test
    fun `名称不能为空`(): Unit = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.save(id = null, name = "  ", content = "x", mode = PromptMode.REPLACE) }
        }
    }
}
