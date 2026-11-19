package io.tinychat.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭据存取（M7-6）。
 *
 * 抽成接口是为了两件事：单测不碰 Keystore（Robolectric 没有实现），
 * 以及将来换存储方式时只动实现。
 */
interface SecretStore {

    /** 已保存的 Key；`null` = 未配置（解密失败也按未配置处理）。 */
    suspend fun read(): String?

    /** 写入（空白值等价于 [clear]）。 */
    suspend fun write(value: String)

    suspend fun clear()
}

/** 凭据单独一个 DataStore 文件：设置项频繁读写，不该和密文挤在同一份 prefs 里。 */
private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")

/**
 * Android Keystore 加密后的 Key 存储。
 *
 * - 密钥由 **AndroidKeyStore** 生成并保管（AES-256/GCM，不可导出）；
 * - 本机只落 `Base64(IV ‖ 密文)`，明文 Key 不写磁盘、不进日志；
 * - 不用 `androidx.security:security-crypto`：该库已废弃，且它管的也只是这种用法；
 * - 卸载重装、或系统清掉 Keystore（换机、恢复出厂）后密文解不开 → [read] 返回 `null`，
 *   表现是"回到未配置"，而不是崩在启动路径上。
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val store = context.applicationContext.credentialsDataStore

    override suspend fun read(): String? {
        val raw = store.data.first()[API_KEY_CIPHER] ?: return null
        return runCatching { decrypt(raw) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override suspend fun write(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val cipher = encrypt(trimmed)
        store.edit { it[API_KEY_CIPHER] = cipher }
    }

    override suspend fun clear() {
        store.edit { it.remove(API_KEY_CIPHER) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(packed: String): String {
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "密文长度不合法" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val body = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    /** 第一次用时生成并永久留在 Keystore 里；存在就直接取。 */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tinychat.api_key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        val API_KEY_CIPHER = stringPreferencesKey("api_key_cipher")
    }
}
