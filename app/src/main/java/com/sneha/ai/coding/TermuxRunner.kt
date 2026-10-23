package com.sneha.ai.coding

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

data class TermuxResult(val id: String, val stdout: String, val stderr: String, val exit: Int?, val err: String?)

object TermuxBus {
    val results = MutableSharedFlow<TermuxResult>(extraBufferCapacity = 8)
}

/**
 * Termux আলাদা অ্যাপ। এখানে শুধু Termux-এর RUN_COMMAND ইন্টেন্ট পাঠানো হয়; কোড আমাদের অ্যাপের প্রসেসে চলে না।
 * Termux-এ ~/.termux/termux.properties-এ allow-external-apps=true এবং আমাদের অ্যাপকে RUN_COMMAND অনুমতি দিতে হয়।
 */
class TermuxRunner(private val ctx: Context) {
    companion object {
        const val PKG = "com.termux"
        const val PERM = "com.termux.permission.RUN_COMMAND"
        private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val HOME = "/data/data/com.termux/files/home"
    }

    fun installed(): Boolean = try { ctx.packageManager.getPackageInfo(PKG, 0); true } catch (e: Exception) { false }
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED

    /** ব্যবহারকারীকে দেখানোর জন্য হুবহু যে স্ক্রিপ্ট চলবে সেটি তৈরি করে। অসমর্থিত ভাষায় null। */
    fun buildScript(lang: String, code: String): String? {
        val (file, cmd) = when (lang) {
            "python" -> "main.py" to "python main.py"
            "c" -> "main.c" to "clang main.c -o main && ./main"
            "cpp" -> "main.cpp" to "clang++ main.cpp -o main && ./main"
            "javascript" -> "main.js" to "node main.js"
            "bash" -> "main.sh" to "bash main.sh"
            "java" -> "Main.java" to "javac Main.java && java Main"
            else -> return null
        }
        var delim: String
        do { delim = "SNEHA_EOF_" + UUID.randomUUID().toString().take(8) } while (code.contains(delim))
        val body = if (code.endsWith("\n")) code else code + "\n"
        return "mkdir -p \"\$HOME/sneha_run\" && cd \"\$HOME/sneha_run\" || exit 1\n" +
            "cat > $file <<'$delim'\n$body$delim\n" +
            "timeout 60 sh -c '$cmd'\n"
    }

    /** null = ইন্টেন্ট পাঠানো গেছে; নইলে ত্রুটির বার্তা */
    fun run(script: String, id: String): String? {
        val i = Intent("com.termux.RUN_COMMAND").setClassName(PKG, "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(
            ctx, id.hashCode(), Intent(ctx, TermuxResultReceiver::class.java).putExtra("exec_id", id), flags
        )
        i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        return try {
            ctx.startService(i); null
        } catch (e: SecurityException) {
            "অনুমতি সমস্যা: Termux-এ allow-external-apps=true দিন ও এখানে RUN_COMMAND অনুমতি দিন (${e.message})"
        } catch (e: Exception) {
            e.message ?: "Termux সার্ভিস চালু করা যায়নি"
        }
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("exec_id") ?: ""
        val b = i.getBundleExtra("result")
        TermuxBus.results.tryEmit(
            TermuxResult(
                id,
                b?.getString("stdout").orEmpty(),
                b?.getString("stderr").orEmpty(),
                if (b != null && b.containsKey("exitCode")) b.getInt("exitCode") else null,
                b?.getString("errmsg")
            )
        )
    }
}
