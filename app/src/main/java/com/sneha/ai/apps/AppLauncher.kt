package com.sneha.ai.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.sneha.ai.comms.Translit

data class AppInfo(val label: String, val pkg: String)
data class LaunchResult(val ok: Boolean, val msg: String, val pkg: String? = null)

class AppLauncher(private val ctx: Context) {
    private val pm = ctx.packageManager

    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube", "ইউটিউব" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music", "ইউটিউব মিউজিক" to "com.google.android.apps.youtube.music",
        "whatsapp" to "com.whatsapp", "হোয়াটসঅ্যাপ" to "com.whatsapp", "হোয়াটস্যাপ" to "com.whatsapp",
        "chrome" to "com.android.chrome", "ক্রোম" to "com.android.chrome",
        "facebook" to "com.facebook.katana", "ফেসবুক" to "com.facebook.katana", "fb" to "com.facebook.katana",
        "messenger" to "com.facebook.orca", "মেসেঞ্জার" to "com.facebook.orca",
        "instagram" to "com.instagram.android", "ইনস্টাগ্রাম" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically", "টিকটক" to "com.zhiliaoapp.musically",
        "telegram" to "org.telegram.messenger", "টেলিগ্রাম" to "org.telegram.messenger",
        "imo" to "com.imo.android.imoim", "ইমো" to "com.imo.android.imoim",
        "bkash" to "com.bKash.customerapp", "বিকাশ" to "com.bKash.customerapp",
        "nagad" to "com.konasl.nagad", "নগদ" to "com.konasl.nagad",
        "play store" to "com.android.vending", "প্লে স্টোর" to "com.android.vending", "playstore" to "com.android.vending",
        "gmail" to "com.google.android.gm", "জিমেইল" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps", "ম্যাপ" to "com.google.android.apps.maps",
        "camera" to "com.transsion.camera", "ক্যামেরা" to "com.transsion.camera",
        "gallery" to "com.transsion.ai.gallery", "গ্যালারি" to "com.transsion.ai.gallery",
        "phone master" to "com.transsion.phonemaster", "ফোন মাস্টার" to "com.transsion.phonemaster",
        "termux" to "com.termux", "টারমাক্স" to "com.termux"
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
                LaunchResult(true, "Settings খোলা হয়েছে", "com.android.settings")
            } catch (e: Exception) { LaunchResult(false, "Settings খোলা যায়নি") }
        }
        val apps = launchable().sortedByDescending { it.pkg in favorites }
        val aliasPkg = aliases[n]
        val app = apps.firstOrNull { aliasPkg != null && it.pkg.equals(aliasPkg, true) }
            ?: apps.firstOrNull { it.label.equals(name.trim(), true) }
            ?: apps.firstOrNull { it.label.contains(name.trim(), true) }
            ?: apps.firstOrNull { Translit.matches(name, it.label) }
            ?: apps.firstOrNull { it.pkg.substringAfterLast('.').contains(Translit.bnToLatin(name), true) }
            ?: return LaunchResult(false, "'$name' অ্যাপটি ফোনে খুঁজে পাওয়া যায়নি")

        val intent = pm.getLaunchIntentForPackage(app.pkg) ?: return LaunchResult(false, "${app.label} চালু করার ইন্টেন্ট নেই")
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult(true, "${app.label} খোলা হয়েছে", app.pkg)
        } catch (e: ActivityNotFoundException) {
            LaunchResult(false, "${app.label} খোলা যায়নি")
        } catch (e: SecurityException) {
            LaunchResult(false, "${app.label} খোলার অনুমতি নেই")
        }
    }

    fun openPlayStoreSearch(query: String): Pair<Boolean, String> {
        val q = Uri.encode(query.trim())
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps")).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (marketIntent.resolveActivity(pm) != null) {
                ctx.startActivity(marketIntent)
                true to "Play Store-এ '$query' সার্চ খোলা হয়েছে"
            } else {
                ctx.startActivity(webIntent)
                true to "Play Store-এ '$query' খোলা হয়েছে"
            }
        } catch (e: Exception) {
            false to "Play Store খোলা যায়নি: ${e.message}"
        }
    }
}
