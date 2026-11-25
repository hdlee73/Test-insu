package io.github.hdlee73.insu.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import io.github.hdlee73.insu.core.CrashLogger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SQLCipher로 DB 파일 전체를 암호화한다.
 * DB 암호(32바이트 난수)는 Android Keystore의 AES-GCM 키로 감싸 SharedPreferences에 둔다.
 * Keystore 키는 기기 밖으로 꺼낼 수 없으므로 DB 파일만 복사해 가도 열 수 없다.
 *
 * 반드시 백그라운드 스레드에서 [database]를 처음 호출한다(Keystore·DB 열기는 수십 ms 걸린다).
 */
class DatabaseProvider(private val context: Context) {

    @Volatile
    private var instance: AppDatabase? = null

    fun database(): AppDatabase = instance ?: synchronized(this) {
        instance ?: build().also { instance = it }
    }

    private fun build(): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = try {
            loadOrCreatePassphrase()
        } catch (e: Exception) {
            // Keystore 키가 사라진 경우(기기 초기화·잠금 해제 정보 변경 등): 기존 DB는 복구 불가하므로 새로 만든다.
            CrashLogger.logError("DatabaseKey", e)
            context.deleteDatabase(DB_NAME)
            prefs().edit().remove(PREF_KEY).commit()
            loadOrCreatePassphrase()
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    private fun prefs() = context.getSharedPreferences("db_key", Context.MODE_PRIVATE)

    private fun loadOrCreatePassphrase(): ByteArray {
        val stored = prefs().getString(PREF_KEY, null)
        if (stored != null) return decrypt(stored)
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs().edit().putString(PREF_KEY, encrypt(raw)).commit()
        return raw
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ct = cipher.doFinal(data)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): ByteArray {
        val (ivB64, ctB64) = stored.split(":").also { require(it.size == 2) { "손상된 키 데이터" } }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
    }

    private companion object {
        const val DB_NAME = "insu.db"
        const val PREF_KEY = "wrapped"
        const val KEY_ALIAS = "insu_db_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
