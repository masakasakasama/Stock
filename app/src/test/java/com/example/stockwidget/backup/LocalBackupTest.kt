package com.example.stockwidget.backup

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalBackupTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("stock_widget_prefs", Context.MODE_PRIVATE)
    @Before fun clean() {
        prefs.edit().clear().commit()
        java.io.File(context.noBackupFilesDir, "restore-pending.json").delete()
        java.io.File(context.noBackupFilesDir, "restore-rollback.json").delete()
    }
    @Test fun encryptedRoundTripRestoresEveryTypeAndContentAndRollback() {
        prefs.edit().putString("watchlist", "AAPL,7203.T").putString("holdings_json", "[{\"symbol\":\"AAPL\",\"shares\":2}]")
            .putLong("date", Long.MAX_VALUE).putFloat("ratio", 1.25f).putInt("count", 42)
            .putBoolean("done", true).putStringSet("seen", setOf("日本語", "b"))
            .putString("anthropic_api_key", "DO-NOT-EXPORT").commit()
        val raw = LocalBackup.snapshot(context)
        assertFalse(raw.contains("DO-NOT-EXPORT"))
        val encrypted = BackupCrypto.encrypt(raw.toByteArray(), "long-test-passphrase".toCharArray())
        assertFalse(String(encrypted).contains("AAPL"))
        val decoded = String(BackupCrypto.decrypt(encrypted, "long-test-passphrase".toCharArray()))
        assertEquals(raw, decoded)
        prefs.edit().putString("watchlist", "MSFT").putString("new", "existing").commit()
        val before = LocalBackup.snapshot(context)
        LocalBackup.restore(context, decoded, before)
        assertEquals(raw, LocalBackup.snapshot(context))
        assertEquals("DO-NOT-EXPORT", prefs.getString("anthropic_api_key", ""))
        LocalBackup.rollback(context)
        assertEquals(before, LocalBackup.snapshot(context))
    }
    @Test fun api24PortableDerivationMatchesStandardProvider() {
        val pass = "日本語-portable-passphrase".toCharArray(); val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec(pass, salt, 210000, 256)
        val expected = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        assertArrayEquals(expected, BackupCrypto.portableKey(pass, salt))
    }
    @Test fun wrongPasswordAndTamperingRejectBeforeAnyWrites() {
        val encrypted = BackupCrypto.encrypt("confidential".toByteArray(), "long-test-passphrase".toCharArray())
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(encrypted, "wrong-test-passphrase".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(encrypted, "long-test-passphrase".toCharArray()) }
    }
    @Test fun wrongAppOrInvalidTypeOrStaleConfirmationNeverOverwritesExistingData() {
        prefs.edit().putInt("value", 1).commit()
        val raw = LocalBackup.snapshot(context)
        val invalid = JSONObject(raw).put("app", "other.app").toString()
        assertThrows(IllegalArgumentException::class.java) { LocalBackup.restore(context, invalid, raw) }
        val badType = JSONObject(raw)
        badType.getJSONObject("stores").getJSONObject("stock_widget_prefs").put("bad", JSONObject().put("t", "unsupported"))
        assertThrows(Exception::class.java) { LocalBackup.restore(context, badType.toString(), raw) }
        assertEquals(raw, LocalBackup.snapshot(context))
        prefs.edit().putInt("value", 2).commit()
        assertThrows(IllegalStateException::class.java) { LocalBackup.restore(context, raw, raw) }
        assertEquals(2, prefs.getInt("value", 0))
    }
    @Test fun interruptedRestoreRollsBackOnNextStartup() {
        prefs.edit().putString("watchlist", "original").commit()
        val before = LocalBackup.snapshot(context)
        java.io.File(context.noBackupFilesDir, "restore-pending.json").writeText(before)
        prefs.edit().putString("watchlist", "partial write").commit()
        LocalBackup.recover(context)
        assertEquals(before, LocalBackup.snapshot(context))
        assertFalse(java.io.File(context.noBackupFilesDir, "restore-pending.json").exists())
    }
}
