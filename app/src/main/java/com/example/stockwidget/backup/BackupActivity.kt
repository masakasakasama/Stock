package com.example.stockwidget.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.AtomicFile
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable authenticated encryption; no device key or credential is exported. */
object BackupCrypto {
    const val LIMIT = 16 * 1024 * 1024
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "パスフレーズは12文字以上にしてください" }
        require(plain.size <= LIMIT)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return byteArrayOf(72, 66, 75, 1) + salt + iv + cipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in 48..LIMIT + 48 && bytes.take(4) == listOf<Byte>(72, 66, 75, 1)) { "未対応または破損したファイル" }
        return cipher(Cipher.DECRYPT_MODE, password, bytes.copyOfRange(4, 20), bytes.copyOfRange(20, 32))
            .doFinal(bytes.copyOfRange(32, bytes.size))
    }
    internal fun portableKey(password: CharArray, salt: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
        try { mac.init(SecretKeySpec(passwordBytes, "HmacSHA256")) } finally { passwordBytes.fill(0) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(209999) { val next = mac.doFinal(u); u.fill(0); u = next; for (n in result.indices) result[n] = (result[n].toInt() xor u[n].toInt()).toByte() }
        u.fill(0)
        return result
    }
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, 210000, 256)
        val key = try {
            try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            catch (_: java.security.NoSuchAlgorithmException) { portableKey(password, salt) }
        }
            finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(byteArrayOf(72, 66, 75, 1))
        } } finally { key.fill(0) }
    }
}

object LocalBackup {
    private val stores = listOf("stock_widget_prefs", "stock_updater")
    private val files = listOf<String>("crash.log")
    private fun excluded(store: String, key: String) = store == "personal_home_secrets" || key == "anthropic_api_key"
    private fun prefs(c: Context, name: String) = c.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun record(value: Any?): JSONObject = JSONObject().apply {
        when (value) {
            is String -> put("t", "s").put("v", value)
            is Boolean -> put("t", "b").put("v", value.toString())
            is Int -> put("t", "i").put("v", value.toString())
            is Long -> put("t", "l").put("v", value.toString())
            is Float -> put("t", "f").put("v", value.toString())
            is Set<*> -> put("t", "set").put("v", JSONArray(value.map { it as String }.sorted()))
            else -> error("未対応の保存形式")
        }
    }
    private fun value(o: JSONObject): Any = when (o.getString("t")) {
        "s" -> o.getString("v")
        "b" -> o.getString("v").let { require(it == "true" || it == "false"); it.toBoolean() }
        "i" -> o.getString("v").toInt()
        "l" -> o.getString("v").toLong()
        "f" -> o.getString("v").toFloat().also { require(it.isFinite()) }
        "set" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> error("未対応のデータ型")
    }
    fun snapshot(c: Context): String {
        val data = JSONObject()
        stores.sorted().forEach { name ->
            val entries = JSONObject()
            prefs(c, name).all.toSortedMap().forEach { (key, v) -> if (!excluded(name, key)) entries.put(key, record(v)) }
            data.put(name, entries)
        }
        val content = JSONObject()
        files.sorted().forEach { name ->
            val f = File(c.filesDir, name)
            content.put(name, if (f.exists()) f.readText() else JSONObject.NULL)
        }
        return JSONObject().put("version", 1).put("app", c.packageName).put("stores", data).put("files", content).toString()
    }
    fun validate(c: Context, raw: String): JSONObject {
        require(raw.toByteArray().size <= BackupCrypto.LIMIT)
        val root = JSONObject(raw)
        require(root.getInt("version") == 1 && root.getString("app") == c.packageName) { "別アプリまたは未対応バージョンです" }
        val data = root.getJSONObject("stores")
        require(data.keys().asSequence().toSet() == stores.toSet())
        stores.forEach { name -> data.getJSONObject(name).let { entries ->
            entries.keys().forEach { key -> require(!excluded(name, key)); value(entries.getJSONObject(key)) }
        } }
        val content = root.getJSONObject("files")
        require(content.keys().asSequence().toSet() == files.toSet())
        files.forEach { if (!content.isNull(it)) content.getString(it) }
        return root
    }
    fun summary(c: Context, raw: String): String {
        val data = validate(c, raw).getJSONObject("stores")
        return stores.joinToString("\n") { "$it: ${data.getJSONObject(it).length()}項目" } +
            "\nファイル: ${files.size}件\nSHA-256: " + MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun journal(c: Context, name: String) = AtomicFile(File(c.noBackupFilesDir, name))
    private fun save(c: Context, name: String, raw: String) {
        val file = journal(c, name); val out = file.startWrite()
        try { out.write(raw.toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun apply(c: Context, raw: String) {
        val root = validate(c, raw)
        stores.forEach { name ->
            val editor = prefs(c, name).edit()
            prefs(c, name).all.keys.filterNot { excluded(name, it) }.forEach { editor.remove(it) }
            val entries = root.getJSONObject("stores").getJSONObject(name)
            entries.keys().forEach { key -> when (val v = value(entries.getJSONObject(key))) {
                is String -> editor.putString(key, v)
                is Boolean -> editor.putBoolean(key, v)
                is Int -> editor.putInt(key, v)
                is Long -> editor.putLong(key, v)
                is Float -> editor.putFloat(key, v)
                is Set<*> -> editor.putStringSet(key, v.map { it as String }.toSet())
            } }
            check(editor.commit()) { "端末への保存に失敗しました" }
        }
        files.forEach { name ->
            val f = AtomicFile(File(c.filesDir, name)); val content = root.getJSONObject("files")
            if (content.isNull(name)) f.delete()
            else { val out = f.startWrite(); try { out.write(content.getString(name).toByteArray()); f.finishWrite(out) }
                catch(e: Exception) { f.failWrite(out); throw e } }
        }
        // Structural comparison includes all types, values and counts, irrespective of JSON key order.
        check(JSONObject(snapshot(c)).toMapValue() == root.toMapValue()) { "復元後の件数・内容が一致しません" }
    }
    private fun JSONObject.toMapValue(): Map<String, Any?> = keys().asSequence().associateWith { key ->
        when (val v = get(key)) { is JSONObject -> v.toMapValue(); is JSONArray -> (0 until v.length()).map { v.get(it) }; JSONObject.NULL -> null; else -> v }
    }
    @Synchronized fun recover(c: Context) {
        val pending = journal(c, "restore-pending.json")
        if (pending.baseFile.exists()) { apply(c, String(pending.readFully())); pending.delete() }
    }
    @Synchronized fun restore(c: Context, raw: String, expectedBefore: String) {
        validate(c, raw)
        check(snapshot(c) == expectedBefore) { "確認後に保存データが変わりました。もう一度確認してください" }
        save(c, "restore-rollback.json", expectedBefore)
        save(c, "restore-pending.json", expectedBefore)
        try { apply(c, raw); journal(c, "restore-pending.json").delete() }
        catch(e: Exception) { recover(c); throw e }
    }
    fun rollback(c: Context) {
        val raw = String(journal(c, "restore-rollback.json").readFully())
        restore(c, raw, snapshot(c))
    }
}

/** SAF picker does not require broad storage permissions. Never accepts external intents. */
class BackupActivity : Activity() {
    private lateinit var password: EditText
    private lateinit var status: TextView
    private var busy = false
    private var exportBytes: ByteArray? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24) }
        status = TextView(this).apply { text = "暗号化バックアップ・復元\n12文字以上のパスフレーズを安全に保管してください。認証情報は移行せず再認証します。復元は既存データを置き換えるため、先に別ファイルへのバックアップを保存してください。" }
        layout.addView(status)
        password = EditText(this).apply { hint = "バックアップのパスフレーズ"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; isSaveEnabled = false }
        layout.addView(password)
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { if (!busy) action() } }) }
        button("エクスポート（保存先を選択）") {
            work {
                val raw = LocalBackup.snapshot(this)
                val pass = password.text.toString().toCharArray()
                exportBytes = try { BackupCrypto.encrypt(raw.toByteArray(), pass) } finally { pass.fill('\u0000') }
                runOnUiThread { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, "${packageName}-${System.currentTimeMillis()}.hbk")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }, 1) }
                "保存先を選択してください"
            }
        }
        button("インポート（内容を検証）") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 2) }
        button("直前の復元を取り消す") { AlertDialog.Builder(this).setMessage("復元直前のデータに戻します。続行しますか？").setNegativeButton("キャンセル", null)
            .setPositiveButton("戻す") { _, _ -> work { LocalBackup.rollback(this); "ロールバック後の全内容の照合に成功しました。アプリを開き直してください。" } }.show() }
        setContentView(ScrollView(this).apply { addView(layout) })
        work { LocalBackup.recover(this); "バックアップ準備完了。復元後は再認証とウィジェット再配置・設定が必要です。" }
    }
    private fun work(action: () -> String) {
        if (busy) return
        busy = true
        Thread { val result = runCatching(action).getOrElse { "処理できませんでした。パスフレーズ・ファイル・空き容量を確認してください。既存データは保持／ロールバック対象です。" }
            runOnUiThread { status.text = result; busy = false } }.start()
    }
    @Deprecated("Legacy activity result supported on minimum API")
    override fun onActivityResult(request: Int, result: Int, intent: Intent?) {
        super.onActivityResult(request, result, intent)
        if (result != RESULT_OK) { exportBytes = null; return }
        val uri = intent?.data ?: return
        work {
            if (request == 1) {
                val bytes = exportBytes ?: error("保存をやり直してください")
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                val read = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                check(bytes.contentEquals(read))
                exportBytes = null
                "保存ファイルを読み直し、全バイトの一致を確認しました。別端末での復元検証が済むまで旧アプリを削除しないでください。"
            } else {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytesBounded() }
                val pass = password.text.toString().toCharArray()
                val raw = try { String(BackupCrypto.decrypt(bytes, pass), Charsets.UTF_8) } finally { pass.fill('\u0000') }
                val summary = LocalBackup.summary(this, raw)
                val before = LocalBackup.snapshot(this)
                runOnUiThread { AlertDialog.Builder(this).setTitle("復元前確認").setMessage("$summary\n既存データを置換します。端末内に復元直前の状態を保存し、失敗時は戻します。先に外部バックアップを保存しましたか？")
                    .setNegativeButton("キャンセル", null).setPositiveButton("復元する") { _, _ -> work { LocalBackup.restore(this, raw, before); "$summary\n全項目・全内容の照合に成功。アプリを開き直してください。認証情報は再設定してください。" } }.show() }
                "整合性検証に成功。復元確認待ちです"
            }
        }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= BackupCrypto.LIMIT + 48); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
}
