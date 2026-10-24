package com.sneha.ai.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.Action
import com.sneha.ai.ai.describe
import com.sneha.ai.ai.isUi
import com.sneha.ai.media.YouTubeHandoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExecStatus(val running: Boolean = false, val text: String = "")

object Guard {
    private val deny = listOf(
        "bank", "paytm", "phonepe", "paisa", "bkash", "nagad", "razorpay", "wallet",
        "authenticator", "keepass", "bitwarden", "lastpass", "1password",
        "permissioncontroller", "packageinstaller", "com.android.settings", "securitycenter"
    )
    fun isBlocked(pkg: String?): Boolean = pkg != null && deny.any { pkg.contains(it, true) }
}

private data class R(val ok: Boolean, val msg: String)

class ActionExecutor(private val app: SnehaApp) {
    val status = MutableStateFlow(ExecStatus())
    private var job: Job? = null
    val busy: Boolean get() = job?.isActive == true
    private val audio: AudioManager get() = app.getSystemService(AudioManager::class.java)

    fun run(actions: List<Action>, onDone: (List<String>, Boolean) -> Unit) {
        if (busy) return
        job = app.appScope.launch {
            val log = mutableListOf<String>()
            var ok = true
            val notify = app.prefs.current().notifyAutomation
            try {
                for ((i, a) in actions.withIndex()) {
                    val label = "${i + 1}/${actions.size}: ${a.describe()}"
                    status.value = ExecStatus(true, label)
                    if (notify) Notifier.show(app, label)
                    val r = step(a)
                    log += (if (r.ok) "✓ " else "✗ ") + a.describe() + " — " + r.msg
                    if (!r.ok) { ok = false; break }
                }
            } catch (e: CancellationException) {
                log += "■ ব্যবহারকারী থামিয়েছেন"; ok = false
            } finally {
                status.value = ExecStatus()
                Notifier.cancel(app)
            }
            onDone(log, ok)
        }
    }

    fun cancel() { job?.cancel() }

    private suspend fun step(a: Action): R {
        if (a.isUi()) {
            val svc = SnehaAccessibilityService.instance
            if (svc == null) {
                if (a is Action.YouTubeSearch) {
                    val r = YouTubeHandoff.search(app, a.query)
                    return R(r.first, r.second + " (স্বয়ংক্রিয় প্লেব্যাকের জন্য Settings > স্ক্রিন অটোমেশন থেকে Accessibility চালু করুন)")
                }
                return R(false, "Accessibility সার্ভিস চালু নেই (Settings > স্ক্রিন অটোমেশন থেকে চালু করুন)")
            }
            val pkg = svc.foregroundPackage()
            if (a !is Action.LockScreen && a !is Action.PowerMenu && a !is Action.Back && a !is Action.Home && Guard.isBlocked(pkg)) {
                return R(false, "নিরাপত্তার কারণে এই অ্যাপে ($pkg) অটোমেশন বন্ধ")
            }
            return withContext(Dispatchers.Default) { uiStep(a, svc) }
        }
        return when (a) {
            is Action.OpenApp -> openApp(a.app)
            is Action.CallContact -> {
                val r = app.comms.placeCall(a.target)
                R(r.first, r.second)
            }
            is Action.SendSms -> {
                val r = app.comms.sendSms(a.target, a.message, a.direct)
                R(r.first, r.second)
            }
            is Action.WhatsAppMsg -> {
                val r = app.comms.openWhatsApp(a.target, a.message)
                R(r.first, r.second)
            }
            is Action.YouTubeSearch -> {
                val r = YouTubeHandoff.search(app, a.query)
                R(r.first, r.second)
            }
            is Action.Media -> media(a.cmd)
            is Action.Volume -> adjustVolume(a.dir)
            is Action.SystemSetting -> openSystemSetting(a.panel)
            is Action.Wait -> { delay(a.ms); R(true, "ঠিক আছে") }
            is Action.Remember -> {
                if (!app.prefs.current().memoryEnabled) R(false, "মেমরি বন্ধ আছে")
                else {
                    val e = app.memory.add(a.text)
                    if (e == null) R(true, "মেমরিতে সংরক্ষিত") else R(false, e)
                }
            }
            else -> R(false, "অসমর্থিত অ্যাকশন")
        }
    }

    private suspend fun uiStep(a: Action, s: SnehaAccessibilityService): R = when (a) {
        is Action.TapText -> changed(s) { s.tapByText(a.text) }
        is Action.TapXY -> changed(s) { s.tapXY(a.x, a.y) }
        is Action.Scroll -> changed(s) { s.scroll(a.down) }
        Action.Back -> { val ok = s.back(); R(ok, if (ok) "Back পাঠানো হয়েছে" else "Back ব্যর্থ") }
        Action.Home -> { val ok = s.home(); R(ok, if (ok) "Home পাঠানো হয়েছে" else "Home ব্যর্থ") }
        Action.LockScreen -> {
            val ok = s.lockScreen()
            R(ok, if (ok) "ফোনের স্ক্রিন লক করা হয়েছে" else "স্ক্রিন লক করা যায়নি (Android 9+ লাগে)")
        }
        Action.PowerMenu -> {
            val ok = s.openPowerDialog()
            R(ok, if (ok) "পাওয়ার/শাটডাউন মেনু খোলা হয়েছে — এবার Power Off ট্যাপ করুন" else "পাওয়ার মেনু খোলা যায়নি")
        }
        is Action.TypeText -> { val e = s.typeText(a.text); if (e == null) R(true, "লেখা বসেছে ও যাচাই হয়েছে") else R(false, e) }
        Action.Enter -> { val ok = s.pressEnter(); R(ok, if (ok) "Enter পাঠানো হয়েছে" else "Enter সম্ভব হয়নি") }
        Action.ReadScreen -> { val l = s.readScreen(); R(true, if (l.isEmpty()) "কোনো লেবেল পাওয়া যায়নি" else "\n" + l.joinToString("\n")) }
        is Action.YouTubeSearch -> playFirst(a, s)
        else -> R(false, "অসমর্থিত অ্যাকশন")
    }

    private suspend fun changed(s: SnehaAccessibilityService, act: suspend () -> Boolean): R {
        val before = s.readScreen().hashCode()
        if (!act()) return R(false, "অ্যাকশন ডিসপ্যাচ করা যায়নি (এলিমেন্ট পাওয়া যায়নি)")
        delay(800)
        val after = s.readScreen().hashCode()
        return if (before != after) R(true, "সম্পন্ন — স্ক্রিন বদলেছে")
        else R(false, "অ্যাকশন পাঠানো হয়েছে কিন্তু স্ক্রিনে কোনো পরিবর্তন দেখা যায়নি")
    }

    private suspend fun playFirst(a: Action.YouTubeSearch, s: SnehaAccessibilityService): R {
        val wasPlaying = audio.isMusicActive
        val h = YouTubeHandoff.search(app, a.query)
        if (!h.first) return R(false, h.second)
        delay(3800)
        if (audio.isMusicActive && !wasPlaying) {
            return R(true, "প্লেব্যাক শুরু হয়েছে (অডিও সক্রিয় পাওয়া গেছে)")
        }
        var clicked = false
        repeat(3) {
            if (!clicked) {
                clicked = s.tapFirstYouTubeResult(a.query)
                if (!clicked) delay(1400)
            }
        }
        if (!clicked) return R(false, "YouTube খোলা হয়েছে কিন্তু প্রথম ভিডিওতে ক্লিক করা যায়নি")
        repeat(16) {
            delay(500)
            if (audio.isMusicActive) {
                return R(true, "গান/ভিডিও চালু হয়েছে এবং অডিও প্লেব্যাক যাচাই হয়েছে")
            }
        }
        return R(true, "প্রথম ভিডিওতে ট্যাপ করা হয়েছে (বিজ্ঞাপন বা লোডিং চলছে)")
    }

    private suspend fun openApp(name: String): R {
        val r = app.launcher.open(name, app.prefs.current().favorites)
        if (!r.ok) return R(false, r.msg)
        val pkg = r.pkg ?: return R(true, r.msg)
        val s = SnehaAccessibilityService.instance ?: return R(true, r.msg)
        repeat(10) {
            delay(300)
            if (s.foregroundPackage() == pkg) return R(true, "${r.msg} — অ্যাপ সামনে এসেছে")
        }
        return R(true, r.msg)
    }

    private fun adjustVolume(dir: String): R {
        return try {
            val am = audio
            when (dir) {
                "up" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "মিডিয়া ভলিউম বাড়ানো হয়েছে")
                }
                "down" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "মিডিয়া ভলিউম কমানো হয়েছে")
                }
                "mute" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_MUTE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "মিডিয়া অডিও মিউট করা হয়েছে")
                }
                else -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_UNMUTE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "মিডিয়া অডিও আনমিউট করা হয়েছে")
                }
            }
        } catch (e: Exception) {
            R(false, "ভলিউম পরিবর্তন করা যায়নি: ${e.message}")
        }
    }

    private fun openSystemSetting(panel: String): R {
        val action = when (panel) {
            "wifi" -> AndroidSettings.ACTION_WIFI_SETTINGS
            "bluetooth" -> AndroidSettings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> AndroidSettings.ACTION_SOUND_SETTINGS
            "display" -> AndroidSettings.ACTION_DISPLAY_SETTINGS
            "location" -> AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> AndroidSettings.ACTION_AIRPLANE_MODE_SETTINGS
            else -> AndroidSettings.ACTION_SETTINGS
        }
        return try {
            app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            R(true, "$panel সেটিংস খোলা হয়েছে")
        } catch (e: Exception) {
            R(false, "সেটিংস খোলা যায়নি: ${e.message}")
        }
    }

    private suspend fun media(cmd: String): R {
        val code = when (cmd) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val am = audio
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        delay(1200)
        return when (cmd) {
            "pause" -> if (!am.isMusicActive) R(true, "অডিও থেমেছে") else R(false, "এখনও অডিও চলছে")
            "play" -> if (am.isMusicActive) R(true, "অডিও চলছে") else R(false, "সক্রিয় মিডিয়া সেশন পাওয়া যায়নি")
            else -> R(true, "মিডিয়া কমান্ড পাঠানো হয়েছে")
        }
    }
}

object Notifier {
    private const val CH = "sneha_automation"
    private const val ID = 4201

    fun show(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Automation status", NotificationManager.IMPORTANCE_LOW))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val stop = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, StopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Sneha কাজ করছে")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .build()
        try { nm.notify(ID, n) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        (c.applicationContext as SnehaApp).executor.cancel()
    }
}
