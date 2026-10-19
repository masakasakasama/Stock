package com.example.stockwidget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object StockSecrets {
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("stock-api-key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("stock-api-key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun prefs(c: Context) = c.getSharedPreferences("stock_secrets", Context.MODE_PRIVATE)
    fun save(c: Context, value: String) {
        val editor=prefs(c).edit()
        if(value.isBlank()) { check(editor.clear().commit()); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(editor.putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("ciphertext",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
    }
    fun load(c: Context): String {
        val p=prefs(c);val ciphertext=p.getString("ciphertext",null)?:return ""
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(p.getString("iv", ""),Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(ciphertext,Base64.NO_WRAP)),Charsets.UTF_8)
    }
}
