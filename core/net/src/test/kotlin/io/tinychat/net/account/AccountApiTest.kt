package io.tinychat.net.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class AccountApiTest {

    @Test
    fun `解析官方余额报文`() {
        val json = """
            {"is_available":true,"balance_infos":[
              {"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"},
              {"currency":"USD","total_balance":"1.00","granted_balance":"0.00","topped_up_balance":"1.00"}
            ]}
        """.trimIndent()
        val snapshot = AccountClient.parseBalance(json)
        assertTrue(snapshot.isAvailable)
        assertEquals(2, snapshot.infos.size)
        // 人民币优先
        assertEquals("CNY", snapshot.primary?.currency)
        assertEquals("110.00", snapshot.primary?.totalBalance)
    }

    @Test
    fun `缺字段按零 不崩`() {
        val snapshot = AccountClient.parseBalance("""{"balance_infos":[{"currency":"CNY"}]}""")
        assertEquals("0", snapshot.primary?.totalBalance)
        assertEquals("0", snapshot.primary?.grantedBalance)
    }

    @Test
    fun `空列表时没有主余额`() {
        assertNull(AccountClient.parseBalance("""{"is_available":false,"balance_infos":[]}""").primary)
    }

    @Test
    fun `报文不是 JSON 时抛 AccountException`() {
        assertThrows(AccountException::class.java) { AccountClient.parseBalance("<html>502</html>") }
    }

    @Test
    fun `请求带 Bearer 头且路径正确`() {
        val request = AccountRequests.get("https://api.deepseek.com/", AccountRequests.BALANCE_PATH, "sk-test")
        assertEquals("https://api.deepseek.com/user/balance", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
    }
}
