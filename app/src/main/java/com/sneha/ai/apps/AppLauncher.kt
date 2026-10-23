package com.sneha.ai.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class AppInfo(val label: String, val pkg: String)
data class LaunchResult(val ok: Boolean, val msg: String, val pkg: String? = null)

class AppLauncher(private val ctx: Context) {
    private val pm = ctx.packageManager

    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube", "ইউটিউব" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music", "ইউটিউব মিউজিক" to "com.google.android.apps.youtube.music",
        "whatsapp" to "com.whatsapp", "হোয়াটসঅ্যাপ" to "com.whatsapp", "হোয়াটস্যাপ" to "com.whatsapp",
        "chrome" to "com.android.chrome", "ক্রোম" to "com.android.chrome",
        "facebook" to "com.facebook.katana", "ফেসবুক" to "com.facebook.katana",
        "gmail" to "com.google.android.gm", "জিমেইল" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps", "ম্যাপ" to "com.google.android.apps.maps",
        "termux" to "com.termux", "টারমাক্স" to "com.termux",
    )

    @Suppress("DEPRECATION")
    fun launchable(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(i, PackageManager.ResolveInfoFlags.of(0))
        else pm.queryIntentActivities(i, 0)
        return list.map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun open(name: String, favorites: Set<String> = emptySet()): LaunchResult {
        val n = name.trim().lowercase()
        if (n in setOf("settings", "setting", "সেটিংস", "সেটিং")) {
            return try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                LaunchResult(true, "Settings খোলার অনুরোধ পাঠানো হয়েছে", "com.android.settings")
            } catch (e: Exception) { LaunchResult(false, "Settings খোলা যায়নি") }
        }
        val apps = launchable().sortedByDescending { it.pkg in favorites }
        val aliasPkg = aliases[n]
        val app = apps.firstOrNull { aliasPkg != null && it.pkg == aliasPkg }
            ?: apps.firstOrNull { it.label.equals(name.trim(), true) }
            ?: apps.firstOrNull { it.label.contains(name.trim(), true) }
            ?: return LaunchResult(false, "'$name' নামে কোনো অ্যাপ ইনস্টল করা নেই (বা খুঁজে পাইনি)")
        val intent = pm.getLaunchIntentForPackage(app.pkg) ?: return LaunchResult(false, "${app.label} চালু করার ইন্টেন্ট নেই")
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult(true, "${app.label} খোলার অনুরোধ পাঠানো হয়েছে", app.pkg)
        } catch (e: ActivityNotFoundException) {
            LaunchResult(false, "${app.label} খোলা যায়নি")
        } catch (e: SecurityException) {
            LaunchResult(false, "${app.label} খোলার অনুমতি নেই")
        }
    }
}
