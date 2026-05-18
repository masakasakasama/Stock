package com.example.stockwidget

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Appends uncaught-exception stack traces to a file so the app is
 *  self-diagnosable without a PC. */
object CrashLog {

    private const val FILE = "crash.log"
    private const val MAX_BYTES = 200_000L

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun append(context: Context, header: String, t: Throwable) {
        runCatching {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sb = StringBuilder()
            sb.append("==== ").append(ts).append(" ").append(header).append(" ====\n")
            sb.append(t.javaClass.name).append(": ").append(t.message).append('\n')
            for (e in t.stackTrace.take(40)) sb.append("  at ").append(e).append('\n')
            var c = t.cause
            var depth = 0
            while (c != null && depth < 3) {
                sb.append("Caused by: ").append(c.javaClass.name)
                    .append(": ").append(c.message).append('\n')
                for (e in c.stackTrace.take(20)) sb.append("  at ").append(e).append('\n')
                c = c.cause
                depth++
            }
            sb.append('\n')
            f.appendText(sb.toString())
        }
    }

    fun read(context: Context): String {
        val f = file(context)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
