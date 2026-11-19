package io.tinychat.data.provider

import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.Capabilities
import io.tinychat.model.KnownProviders
import io.tinychat.model.ModelInfo
import io.tinychat.model.Provider
import io.tinychat.model.ProviderCheck
import io.tinychat.model.ProviderKind
import io.tinychat.model.ThinkingMode
import io.tinychat.model.ProviderProtocol
import io.tinychat.model.TINYCHAT_USER_AGENT
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M8-6：服务商配置的播种、增删改、校验状态与元信息往返。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderStoreTest {

    private lateinit var db: TinyChatDatabase
    private lateinit var store: ProviderStore

    @Before
    fun setUp() {
        db = DatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        store = ProviderStore.create(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `播种 DeepSeek 且重复调用不重复插入`() = runBlocking {
        store.ensureSeeded()
        store.ensureSeeded()
        val all = store.all()
        assertEquals(1, all.size)
        val deepseek = all.single()
        assertEquals("deepseek", deepseek.id)
        assertTrue(deepseek.builtin)
        assertEquals(ProviderProtocol.DEEPSEEK_MESSAGES, deepseek.protocol)
        assertEquals(2, deepseek.models.size)
        assertEquals(KnownProviders.flash.id, deepseek.defaultModel?.id)
        assertTrue(deepseek.supportsBalance)
    }

    @Test
    fun `自定义服务商全字段往返`() = runBlocking {
        val provider = Provider(
            id = "acme",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            displayName = "Acme 网关",
            baseUrl = "https://gateway.example/v1",
            protocol = ProviderProtocol.OPENAI_CHAT,
            models = listOf(
                ModelInfo(
                    id = "acme-1",
                    displayName = "Acme One",
                    contextWindow = 64_000,
                    maxOutputTokens = 4_096,
                    capabilities = Capabilities(thinking = true, reasoning = true, effort = true),
                    thinking = ThinkingMode.EFFORT,
                ),
            ),
            meta = mapOf("user_agent" to TINYCHAT_USER_AGENT),
        )
        store.save(provider)

        val loaded = store.byId("acme")!!
        assertEquals(provider.copy(check = ProviderCheck.UNKNOWN), loaded)
        assertEquals("Acme One", loaded.models.single().displayName)
        assertTrue(loaded.models.single().capabilities.reasoning)
        assertEquals(ThinkingMode.EFFORT, loaded.models.single().thinking)
        assertEquals(TINYCHAT_USER_AGENT, loaded.meta["user_agent"])
        assertNull(loaded.checkMessage)
    }

    @Test
    fun `校验状态写回并保留排序`() = runBlocking {
        store.ensureSeeded()
        store.save(
            KnownProviders.openCodeGo.copy(id = "opencode-go", displayName = "OpenCode Go"),
        )
        store.setCheck("opencode-go", ProviderCheck.OK, null, 1_700_000_000_000L)

        val all = store.all()
        assertEquals(listOf("deepseek", "opencode-go"), all.map { it.id })
        assertEquals(ProviderCheck.OK, all[1].check)
    }

    @Test
    fun `内置服务商不允许删除`() = runBlocking {
        store.ensureSeeded()
        store.delete("deepseek")
        assertNotNull(store.byId("deepseek"))
    }

    @Test
    fun `删除自定义服务商`() = runBlocking {
        store.save(
            Provider(
                id = "tmp",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                displayName = "临时",
                baseUrl = "https://x/v1",
                protocol = ProviderProtocol.OPENAI_CHAT,
                models = listOf(
                    ModelInfo("m", "m", 8_000, 1_000, Capabilities(thinking = false)),
                ),
            ),
        )
        store.delete("tmp")
        assertNull(store.byId("tmp"))
    }
}
