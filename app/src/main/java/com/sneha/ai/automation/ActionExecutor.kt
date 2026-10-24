package com.sneha.ai.automation

import android.Manifest
import android.app.ActivityManager
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
        "permissioncontroller"
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
                log += "■ থামানো হয়েছে"; ok = false
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
                return when (a) {
                    is Action.YouTubeSearch -> {
                        val r = YouTubeHandoff.search(app, a.query)
                        R(r.first, r.second)
                    }
                    is Action.PlayStoreInstall -> {
                        val r = app.launcher.openPlayStoreSearch(a.query)
                        R(r.first, r.second + " (স্বয়ংক্রিয় Install চাপার জন্য Accessibility চালু করুন)")
                    }
                    is Action.WhatsAppMsg -> {
                        val r = app.comms.openWhatsApp(a.target, a.message)
                        R(r.first, r.second)
                    }
                    else -> R(false, "Accessibility সার্ভিস চালু নেই (Settings > স্ক্রিন অটোমেশন থেকে চালু করুন)")
                }
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
            is Action.YouTubeSearch -> {
                val r = YouTubeHandoff.search(app, a.query)
                R(r.first, r.second)
            }
            is Action.Media -> media(a.cmd)
            is Action.Volume -> adjustVolume(a.dir)
            is Action.SystemSetting -> openSystemSetting(a.panel)
            Action.CleanPhone -> cleanPhone()
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
            R(ok, if (ok) "ফোনের স্ক্রিন লক করা হয়েছে" else "স্ক্রিন লক করা যায়নি")
        }
        Action.PowerMenu -> {
            val ok = s.openPowerDialog()
            R(ok, if (ok) "পাওয়ার মেনু খোলা হয়েছে" else "পাওয়ার মেনু খোলা যায়নি")
        }
        is Action.TypeText -> { val e = s.typeText(a.text); if (e == null) R(true, "লেখা বসেছে") else R(false, e) }
        Action.Enter -> { val ok = s.pressEnter(); R(ok, if (ok) "Enter পাঠানো হয়েছে" else "Enter সম্ভব হয়নি") }
        Action.ReadScreen -> { val l = s.readScreen(); R(true, if (l.isEmpty()) "কোনো লেবেল পাওয়া যায়নি" else "\n" + l.joinToString("\n")) }
        is Action.YouTubeSearch -> playFirst(a, s)
        is Action.PlayStoreInstall -> installFromPlayStore(a.query, s)
        is Action.WhatsAppMsg -> autoSendWhatsApp(a.target, a.message, s)
        else -> R(false, "অসমর্থিত অ্যাকশন")
    }

    private suspend fun installFromPlayStore(query: String, s: SnehaAccessibilityService): R {
        val h = app.launcher.openPlayStoreSearch(query)
        if (!h.first) return R(false, h.second)
        delay(3200)
        var installed = false
        repeat(3) {
            if (s.tapByText("Install") || s.tapByText("ইনস্টল করুন") || s.tapByText("ইনস্টল")) {
                installed = true
                return@repeat
            }
            s.tapPlayStoreInstall(query)
            delay(2200)
            if (s.tapByText("Install") || s.tapByText("ইনস্টল করুন") || s.tapByText("ইনস্টল")) {
                installed = true
                return@repeat
            }
        }
        return if (installed) {
            R(true, "Play Store-এ '$query' খুঁজে Install বাটনে ট্যাপ করা হয়েছে!")
        } else {
            R(true, "Play Store-এ '$query' খোলা হয়েছে")
        }
    }

    private suspend fun autoSendWhatsApp(target: String, message: String, s: SnehaAccessibilityService): R {
        val res = app.comms.openWhatsApp(target, message)
        if (!res.first) return R(false, res.second)

        if (res.second.startsWith("DIRECT_CHAT:")) {
            delay(2500)
            repeat(4) {
                if (s.tapWhatsAppSend()) {
                    return R(true, "WhatsApp-এ $target-কে মেসেজ পাঠানো হয়েছে!")
                }
                delay(1000)
            }
            return R(true, "WhatsApp-এ $target-এর চ্যাট খোলা হয়েছে")
        } else {
            delay(2600)
            var chatOpened = s.tapByText(target)
            if (!chatOpened) {
                s.tapByText("Search") || s.tapByText("খুঁজুন")
                delay(900)
                s.typeText(target)
                delay(1400)
                chatOpened = s.tapByText(target)
            }
            if (chatOpened) {
                delay(1400)
                s.typeText(message)
                delay(800)
                if (s.tapWhatsAppSend()) {
                    return R(true, "WhatsApp-এ '$target' খুঁজে মেসেজ পাঠানো হয়েছে!")
                }
            }
            return R(true, "WhatsApp খোলা হয়েছে")
        }
    }

    private suspend fun cleanPhone(): R {
        return withContext(Dispatchers.Default) {
            try {
                app.cacheDir.deleteRecursively()
                Runtime.getRuntime().gc()

                val am = app.getSystemService(ActivityManager::class.java)
                val miBefore = ActivityManager.MemoryInfo()
                am.getMemoryInfo(miBefore)

                val pmOpen = app.launcher.open("Phone Master")
                if (pmOpen.ok) {
                    delay(2400)
                    SnehaAccessibilityService.instance?.let { svc ->
                        svc.tapByText("Clean") || svc.tapByText("Boost") || svc.tapByText("Junk")
                    }
                    return@withContext R(true, "ক্যাশ পরিষ্কার করা হয়েছে এবং Phone Master ক্লিনার চালু করা হয়েছে!")
                }

                val freeMb = miBefore.availMem / (1024 * 1024)
                R(true, "ফোন অপ্টিমাইজ ও ক্যাশ ক্লিন সম্পন্ন হয়েছে (ফ্রি RAM: ${freeMb}MB)")
            } catch (e: Exception) {
                R(false, "ক্লিন করা যায়নি: ${e.message}")
            }
        }
    }

    private suspend fun changed(s: SnehaAccessibilityService, act: suspend () -> Boolean): R {
        val before = s.readScreen().hashCode()
        if (!act()) return R(false, "এলিমেন্ট পাওয়া যায়নি")
        delay(800)
        val after = s.readScreen().hashCode()
        return if (before != after) R(true, "সম্পন্ন")
        else R(true, "ট্যাপ পাঠানো হয়েছে")
    }

    private suspend fun playFirst(a: Action.YouTubeSearch, s: SnehaAccessibilityService): R {
        val wasPlaying = audio.isMusicActive
        val h = YouTubeHandoff.search(app, a.query)
        if (!h.first) return R(false, h.second)
        delay(3800)
        if (audio.isMusicActive && !wasPlaying) {
            return R(true, "প্লেব্যাক শুরু হয়েছে")
        }
        var clicked = false
        repeat(3) {
            if (!clicked) {
                clicked = s.tapFirstYouTubeResult(a.query)
                if (!clicked) delay(1400)
            }
        }
        if (!clicked) return R(false, "YouTube খোলা হয়েছে কিন্তু ভিডিওতে ক্লিক করা যায়নি")
        repeat(14) {
            delay(500)
            if (audio.isMusicActive) {
                return R(true, "গান চালু হয়েছে")
            }
        }
        return R(true, "ভিডিও চালু করা হয়েছে")
    }

    private suspend fun openApp(name: String): R {
        val r = app.launcher.open(name, app.prefs.current().favorites)
        return R(r.ok, r.msg)
    }

    private fun adjustVolume(dir: String): R {
        return try {
            val am = audio
            when (dir) {
                "up" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "ভলিউম বাড়ানো হয়েছে")
                }
                "down" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "ভলিউম কমানো হয়েছে")
                }
                "mute" -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_MUTE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "অডিও মিউট করা হয়েছে")
                }
                else -> {
                    am.adjustSuggestedStreamVolume(AudioManager.ADJUST_UNMUTE, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI)
                    R(true, "অডিও আনমিউট করা হয়েছে")
                }
            }
        } catch (e: Exception) {
            R(false, "ভলিউম পরিবর্তন করা যায়নি")
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
            R(false, "সেটিংস খোলা যায়নি")
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
        delay(800)
        return R(true, "মিডিয়া কমান্ড পাঠানো হয়েছে")
    }
}

object Notifier {
    private const val CH = "doreman_automation"
    private const val ID = 4201

    fun show(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Doreman Automation", NotificationManager.IMPORTANCE_LOW))
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
            .setContentTitle("Doreman কাজ করছে")
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
