package com.sneha.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sneha.ai.SnehaApp
import com.sneha.ai.apps.AppInfo
import com.sneha.ai.automation.SnehaAccessibilityService
import com.sneha.ai.coding.TermuxRunner
import com.sneha.ai.data.K
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SettingSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val sections = listOf(
    SettingSection("ai", "AI Brain (Google Gemini)", "API Key, মডেল বাছাই, অটো-ফলব্যাক ও Test Connection", Icons.Default.Psychology),
    SettingSection("voice", "ভয়েস ও উচ্চারণ", "বাংলা/ইংরেজি কণ্ঠস্বর, গতি ও ভলিউম", Icons.Default.RecordVoiceOver),
    SettingSection("auto", "স্ক্রিন অটোমেশন", "Accessibility সার্ভিস ও জরুরি STOP নিয়ন্ত্রণ", Icons.Default.AccessibilityNew),
    SettingSection("apps", "অ্যাপ্লিকেশন তালিকা", "প্রিয় অ্যাপ নির্বাচন ও দ্রুত চালু", Icons.Default.Apps),
    SettingSection("coding", "কোডিং রানটাইম", "WebView স্যান্ডবক্স ও Termux ইন্টিগ্রেশন", Icons.Default.Code),
    SettingSection("memory", "ব্যক্তিগত মেমরি", "সংরক্ষিত তথ্য দেখা, এডিট ও এক্সপোর্ট", Icons.Default.Memory),
    SettingSection("privacy", "প্রাইভেসি ও নিরাপত্তা", "পারমিশন ড্যাশবোর্ড ও ডেটা নীতিমালা", Icons.Default.Security),
    SettingSection("look", "থিম ও ফন্ট", "ডার্ক/লাইট থিম, লেখার আকার ও অ্যানিমেশন", Icons.Default.Palette),
    SettingSection("notif", "নোটিফিকেশন", "অটোমেশন স্ট্যাটাস ও STOP নোটিফিকেশন", Icons.Default.Notifications),
    SettingSection("about", "অ্যাপ পরিচিতি (About)", "সংস্করণ, ফিচারের প্রকৃত অবস্থা ও তথ্য", Icons.Default.Info)
)

@Composable
fun SettingsScreen(vm: MainViewModel, initial: String?, onBack: () -> Unit) {
    var page by rememberSaveable(initial) { mutableStateOf<String?>(initial) }
    BackHandler(page != null) { page = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (page != null) page = null else onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "পেছনে")
            }
            Text(
                text = sections.firstOrNull { it.key == page }?.title ?: "সেটিংস ও নিয়ন্ত্রণ",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(8.dp))

        when (page) {
            null -> Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { sec ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { page = sec.key }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = sec.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sec.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(sec.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            "ai" -> AiPage(vm)
            "voice" -> VoicePage(vm)
            "auto" -> AutoPage(vm)
            "apps" -> AppsPage(vm)
            "coding" -> CodingSettingsPage(vm)
            "memory" -> MemoryPage(vm)
            "privacy" -> PrivacyPage(vm)
            "look" -> LookPage(vm)
            "notif" -> NotifPage(vm)
            else -> AboutPage()
        }
    }
}

@Composable
fun SecCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun Sec(t: String) = Text(
    t,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
)

@Composable
fun Note(t: String) = Text(
    t,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(vertical = 2.dp)
)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PrefSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Text("$label: ${fmt(v)}", style = MaterialTheme.typography.bodyMedium)
    Slider(v, { v = it }, valueRange = range, onValueChangeFinished = { onDone(v) })
}

@Composable
fun resumeTick(): Int {
    val lo = LocalLifecycleOwner.current
    var t by remember { mutableIntStateOf(0) }
    DisposableEffect(lo) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) t++ }
        lo.lifecycle.addObserver(ob)
        onDispose { lo.lifecycle.removeObserver(ob) }
    }
    return t
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
private fun openIntent(ctx: Context, i: Intent, vm: MainViewModel) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        vm.banner.value = "সেটিংস স্ক্রিন খোলা যায়নি"
    }
}

@Composable
fun AiPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val keySet by vm.keySet.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val testing by vm.testingConn.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("১. Gemini API Key ও সংযোগ") {
            Note("আপনার API key ফোনের Android Keystore (AES-256-GCM) দিয়ে এনক্রিপ্ট করে রাখা হয়। সোর্স কোড বা APK-তে কোনো কী নেই।")
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (keySet) "নতুন API key দিলে আগেরটি বদলে যাবে" else "Google AI Studio থেকে পাওয়া API key দিন") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveKey(key); key = "" },
                    enabled = key.isNotBlank()
                ) { Text("Key সেভ করুন") }
                Button(
                    onClick = { vm.testConnection() },
                    enabled = keySet && !testing
                ) { Text(if (testing) "পরীক্ষা চলছে…" else "Test Connection") }
                if (keySet) {
                    TextButton(onClick = { vm.deleteKey() }) { Text("মুছুন") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = conn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        SecCard("২. প্রাথমিক মডেল নির্বাচন") {
            Text("বর্তমান প্রাথমিক মডেল: ${s.model}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (models.isEmpty()) {
                Note("উপরে 'Test Connection' চাপলে আপনার API key-এর জন্য অনুমোদিত ও সক্রিয় সব Gemini মডেলের যাচাইকৃত তালিকা এখানে দেখাবে।")
            } else {
                Spacer(Modifier.height(4.dp))
                models.forEach { m ->
                    RadioRow(m, m == s.model) { vm.set(K.model, m) }
                }
            }
        }

        SecCard("৩. High Demand (503) রি-ট্রাই ও যাচাইকৃত বিকল্প মডেল") {
            PrefSlider(
                label = "স্বয়ংক্রিয় পুনরায় চেষ্টা (Exponential Backoff Retries)",
                value = s.maxRetries.toFloat(),
                range = 0f..5f,
                fmt = { "${it.toInt()} বার" }
            ) { vm.set(K.maxRetries, it.toInt()) }

            SwitchRow("প্রাথমিক মডেলে অতিরিক্ত চাপ (503/429) থাকলে বিকল্প মডেলে চেষ্টা করো", s.autoFallback) {
                vm.set(K.autoFallback, it)
            }
            Note("বিকল্প মডেলটি শুধুমাত্র আপনার API key-এর যাচাইকৃত তালিকা (listModels) থেকে বেছে নেওয়া হয়। কোনো মডেল অনুমান করা হয় না।")
            if (s.autoFallback && models.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("পছন্দের বিকল্প (Fallback) মডেল: ${s.fallbackModel}", style = MaterialTheme.typography.bodySmall)
                models.filter { it != s.model }.take(8).forEach { m ->
                    RadioRow(m, m == s.fallbackModel) { vm.set(K.fallbackModel, m) }
                }
            }
        }

        SecCard("৪. জেনারেশন প্যারামিটার ও ভাষা") {
            SwitchRow("কাস্টম Temperature পাঠাও", s.useTemperature) { vm.set(K.useTemp, it) }
            Note("নতুন Gemini 3.6+ মডেল কাস্টম temperature গ্রহণ না করলে অ্যাপ নিজে থেকেই তা বাদ দিয়ে পুনরায় চেষ্টা করে।")
            if (s.useTemperature) {
                PrefSlider("Temperature", s.temperature, 0f..1f, { "%.1f".format(it) }) { vm.set(K.temperature, it) }
            }
            PrefSlider("সর্বোচ্চ আউটপুট টোকেন", s.maxTokens.toFloat(), 512f..8192f, { it.toInt().toString() }) {
                vm.set(K.maxTokens, it.toInt())
            }
            Spacer(Modifier.height(4.dp))
            Text("কথোপকথনের প্রধান ভাষা:", style = MaterialTheme.typography.bodyMedium)
            RadioRow("বাংলা (Bengali)", s.lang == "bn") { vm.set(K.lang, "bn") }
            RadioRow("English", s.lang == "en") { vm.set(K.lang, "en") }
        }

        SecCard("৫. ব্যবহার পরিসংখ্যান (এই ফোনে)") {
            Text(
                "মোট সফল অনুরোধ: ${s.reqCount}\nইনপুট টোকেন: ${s.tokensIn} • আউটপুট টোকেন: ${s.tokensOut}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun VoicePage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val a = ctx.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    val voices = remember(s.lang, tick) { a.tts.voices(s.lang) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("কণ্ঠস্বর ও উচ্চারণ") {
            SwitchRow("স্নেহার উত্তর জোরে পড়ে শোনাও (Auto-Speak)", s.autoSpeak) { vm.set(K.autoSpeak, it) }
            PrefSlider("কথার গতি", s.speechRate, 0.5f..2f, { "%.1fx".format(it) }) { vm.set(K.rate, it) }
            PrefSlider("ভলিউম", s.volume, 0f..1f, { "${(it * 100).toInt()}%" }) { vm.set(K.volume, it) }
        }

        SecCard("কণ্ঠস্বর নির্বাচন") {
            Note("Android TTS ইঞ্জিন ভয়েসের লিঙ্গ আলাদাভাবে জানায় না। তালিকা থেকে একটি বেছে 'ভয়েস টেস্ট' চেপে শুনে আপনার পছন্দের নারী কণ্ঠস্বরটি বেছে নিন।")
            RadioRow("সিস্টেম ডিফল্ট", s.voiceName.isBlank()) { vm.set(K.voice, "") }
            voices.forEach { RadioRow(it, s.voiceName == it) { vm.set(K.voice, it) } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.speak(if (s.lang == "bn") "নমস্কার, আমি স্নেহা। আমার কথা পরিষ্কার শুনতে পাচ্ছেন?" else "Hello, I am Sneha. Can you hear me clearly?")
                }) { Text("ভয়েস টেস্ট") }
                OutlinedButton(onClick = { tick++ }) { Text("তালিকা রিফ্রেশ") }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { openIntent(ctx, Intent("com.android.settings.TTS_SETTINGS"), vm) }) {
                Text("ফোনের TTS ইঞ্জিন সেটিংস খুলুন")
            }
        }

        SecCard("ভয়েস ইনপুট (মাইক্রোফোন)") {
            Text(
                "স্পিচ রিকগনিশন সার্ভিস: " + if (SpeechRecognizer.isRecognitionAvailable(ctx)) "✓ উপলব্ধ" else "✗ এই ফোনে উপলব্ধ নয়",
                style = MaterialTheme.typography.bodyMedium
            )
            Note("শুধুমাত্র আপনি মাইক বাটনে বা অর্বে ট্যাপ করলে মাইক্রোফোন চালু হয়। ব্যাকগ্রাউন্ডে গোপনে কোনো অডিও রেকর্ড করা হয় না।")
        }
    }
}

@Composable
fun AutoPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val on by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Accessibility সার্ভিস অবস্থা") {
            Text(
                if (on) "✓ Accessibility সার্ভিস চালু আছে" else "✗ Accessibility সার্ভিস বন্ধ আছে",
                style = MaterialTheme.typography.titleMedium,
                color = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) {
                Text("Android Accessibility সেটিংস খুলুন")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.stopAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("■ চলমান অটোমেশন থামাও") }
                OutlinedButton(
                    onClick = { SnehaAccessibilityService.instance?.disableSelf() },
                    enabled = on
                ) { Text("সার্ভিস বন্ধ করুন") }
            }
        }

        SecCard("চালু করার নিয়ম (Infinix / Android 13+)") {
            Note(
                "১. উপরের বাটনে চাপুন → 'Installed apps' বা 'Downloaded apps' → Sneha AI বেছে নিন।\n" +
                    "২. Android 13+ এ সরাসরি APK ইনস্টল করলে সুইচ ধূসর (Restricted) থাকতে পারে। সেক্ষেত্রে ফোনের Settings > Apps > Sneha AI > উপরের ডানে (⋮) মেনু > 'Allow restricted settings' চালু করুন।\n" +
                    "৩. এরপর আবার Accessibility সেটিংসে গিয়ে Sneha AI চালু করুন।"
            )
        }

        SecCard("নিরাপত্তা ও সীমাবদ্ধতা") {
            Note(
                "• ব্যাংকিং/পেমেন্ট অ্যাপ (bKash, Nagad ইত্যাদি), পাসওয়ার্ড ম্যানেজার, লক স্ক্রিন ও সিস্টেম সেটিংসে অটোমেশন সম্পূর্ণ ব্লক থাকে।\n" +
                    "• প্রতিটি স্ক্রিন অ্যাকশনের আগে আপনার অনুমোদন নেওয়া হয়।\n" +
                    "• অন্য অ্যাপকে Force-stop করা বা লক স্ক্রিন আনলক করা সাধারণ Android অ্যাপের পক্ষে সম্ভব নয়।"
            )
        }
    }
}

@Composable
fun AppsPage(vm: MainViewModel) {
    val a = LocalContext.current.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val apps by produceState(emptyList<AppInfo>()) {
        value = withContext(Dispatchers.Default) { a.launcher.launchable() }
    }
    Column {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ইনস্টল করা অ্যাপ খুঁজুন") })
        Note("চেকবক্সে টিক দিলে অ্যাপটি প্রিয় তালিকায় থাকবে।")
        LazyColumn(Modifier.weight(1f)) {
            items(apps.filter { it.label.contains(q, true) }, key = { it.pkg }) { ap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ap.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.openApp(ap.label) }) { Text("খোলো") }
                    Checkbox(
                        checked = ap.pkg in s.favorites,
                        onCheckedChange = { c -> vm.set(K.favorites, if (c) s.favorites + ap.pkg else s.favorites - ap.pkg) }
                    )
                }
            }
        }
    }
}

@Composable
fun CodingSettingsPage(vm: MainViewModel) {
    val a = LocalContext.current.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    val tick = resumeTick()
    val installed = remember(tick) { a.termux.installed() }
    val perm = remember(tick) { a.termux.hasPermission() }
    var projTick by remember { mutableIntStateOf(0) }
    val projects = remember(projTick) { a.projects.list() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Termux কোড রানটাইম (ঐচ্ছিক)") {
            SwitchRow("Termux দিয়ে কোড চালানো চালু", s.termuxEnabled) { vm.set(K.termux, it) }
            Text("Termux অ্যাপ: " + if (installed) "✓ ইনস্টল আছে" else "✗ ইনস্টল নেই")
            Text("RUN_COMMAND অনুমতি: " + if (perm) "✓ দেওয়া আছে" else "✗ দেওয়া হয়নি")
            Spacer(Modifier.height(6.dp))
            Button(onClick = { permLauncher.launch(TermuxRunner.PERM) }, enabled = installed && !perm) {
                Text("RUN_COMMAND অনুমতি দিন")
            }
            Note(
                "HTML/CSS/JS অ্যাপের ভেতরেই স্যান্ডবক্স WebView-এ চলে। Python, C, C++, Java, Bash চালাতে চাইলে ফোনে Termux থাকতে হবে এবং প্রতিবার কমান্ড দেখে আপনি অনুমোদন দিলে তবেই চলবে।"
            )
        }

        SecCard("সংরক্ষিত প্রজেক্ট (${projects.size})") {
            Text(a.projects.root.absolutePath, style = MaterialTheme.typography.bodySmall)
            if (projects.isEmpty()) Note("এখনো কোনো প্রজেক্ট সেভ করা হয়নি।")
            projects.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$p (${a.projects.files(p).size} ফাইল)", Modifier.weight(1f))
                    TextButton(onClick = { a.projects.delete(p); projTick++ }) { Text("মুছুন") }
                }
            }
        }
    }
}

@Composable
fun MemoryPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val a = ctx.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    val items by vm.memory.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(a.memory.exportJson().toByteArray()) } }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("মেমরি নিয়ন্ত্রণ") {
            SwitchRow("স্নেহার মেমরি চালু রাখুন", s.memoryEnabled) { vm.set(K.memory, it) }
            Note("শুধুমাত্র আপনার অনুমোদিত পছন্দগুলো ফোনের স্টোরেজে থাকে। পাসওয়ার্ড/OTP/API key কখনো মেমরিতে রাখা হয় না।")
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("নতুন তথ্য যোগ করুন (যেমন: আমার প্রিয় রঙ নীল)") }
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    err = a.memory.add(newText)
                    if (err == null) newText = ""
                },
                enabled = newText.isNotBlank()
            ) { Text("যোগ করুন") }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        SecCard("সংরক্ষিত তথ্য (${items.size})") {
            if (items.isEmpty()) Note("কোনো তথ্য সংরক্ষিত নেই।")
            items.forEach { e ->
                if (editId == e.id) {
                    OutlinedTextField(editText, { editText = it }, Modifier.fillMaxWidth())
                    Row {
                        Button(onClick = { err = a.memory.update(e.id, editText); if (err == null) editId = null }) { Text("সেভ") }
                        TextButton(onClick = { editId = null }) { Text("বাতিল") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { editId = e.id; editText = e.text }) { Text("এডিট") }
                        TextButton(onClick = { a.memory.delete(e.id) }) { Text("মুছুন") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { export.launch("sneha_memory.json") }) { Text("JSON এক্সপোর্ট") }
                OutlinedButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) { Text("সব মুছুন") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("সব মেমরি মুছে ফেলবেন?") },
            text = { Text("এটি মুছে ফেললে আর ফেরানো যাবে না।") },
            confirmButton = { Button(onClick = { a.memory.clear(); confirmClear = false }) { Text("মুছুন") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("বাতিল") } }
        )
    }
}

@Composable
fun PrivacyPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val a = ctx.applicationContext as SnehaApp
    val tick = resumeTick()
    val a11y by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()
    val exec by vm.exec.collectAsStateWithLifecycle()
    val mic = remember(tick) { granted(ctx, Manifest.permission.RECORD_AUDIO) }
    val notif = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val termuxPerm = remember(tick) { a.termux.hasPermission() }
    fun yn(b: Boolean) = if (b) "✓ চালু/অনুমোদিত" else "✗ বন্ধ"

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("পারমিশন ড্যাশবোর্ড") {
            Text("মাইক্রোফোন: ${yn(mic)}")
            Text("Accessibility (স্ক্রিন অটোমেশন): ${yn(a11y)}")
            Text("নোটিফিকেশন: ${yn(notif)}")
            Text("Termux RUN_COMMAND: ${yn(termuxPerm)}")
            Text("চলমান অটোমেশন: ${if (exec.running) "হ্যাঁ (${exec.text})" else "না"}")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    openIntent(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")), vm)
                }) { Text("অ্যাপ পারমিশন") }
                OutlinedButton(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) { Text("Accessibility") }
            }
        }

        SecCard("চ্যাট ইতিহাস ও ডেটা") {
            Note("আপনার চ্যাট ইতিহাস এবং মেমরি শুধুমাত্র ফোনের নিজস্ব স্টোরেজে সংরক্ষিত থাকে।")
            OutlinedButton(onClick = { vm.clearChat() }) { Text("সম্পূর্ণ চ্যাট ইতিহাস মুছে ফেলুন") }
        }
    }
}

@Composable
fun LookPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("থিম নির্বাচন") {
            RadioRow("ডার্ক (Deep Cosmic Dark)", s.theme == "dark") { vm.set(K.theme, "dark") }
            RadioRow("লাইট (Clean Daylight)", s.theme == "light") { vm.set(K.theme, "light") }
            RadioRow("সিস্টেম ডিফল্ট", s.theme == "system") { vm.set(K.theme, "system") }
        }
        SecCard("ফন্ট ও অ্যানিমেশন") {
            PrefSlider("লেখার আকার (Font Scale)", s.fontScale, 0.85f..1.40f, { "%.2fx".format(it) }) { vm.set(K.fontScale, it) }
            SwitchRow("অ্যানিমেশন কমান (ব্যাটারি সাশ্রয় মোড)", s.reduceMotion) { vm.set(K.reduceMotion, it) }
            Note("Idle অবস্থায় অর্ব অ্যানিমেশন স্বয়ংক্রিয়ভাবেই বন্ধ থাকে যাতে ফোনের ব্যাটারি খরচ না হয়।")
        }
    }
}

@Composable
fun NotifPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val tick = resumeTick()
    val ok = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("অটোমেশন নোটিফিকেশন") {
            SwitchRow("অটোমেশন চলাকালীন STOP বাটনসহ নোটিফিকেশন দেখাও", s.notifyAutomation) { vm.set(K.notify, it) }
            Text("নোটিফিকেশন অনুমতি: " + if (ok) "✓ দেওয়া আছে" else "✗ দেওয়া নেই")
            if (!ok) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("নোটিফিকেশন অনুমতি দিন")
                }
            }
        }
    }
}

@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val ver = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "0.2.0" }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Sneha AI (স্নেহা) — v$ver") {
            Text("প্যাকেজ: com.sneha.ai • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Note("মস্তিষ্ক: Google Gemini API • UI: Jetpack Compose + Material 3")
        }
        SecCard("ফিচারের প্রকৃত অবস্থা") {
            Note(
                "• Gemini কথোপকথন, Exponential Backoff রি-ট্রাই, যাচাইকৃত মডেল ফলব্যাক ও Retry বাটন: সংযোজিত\n" +
                    "• ফিউচারিস্টিক গ্লাসমরফিক UI ও অ্যানিমেটেড অর্ব: সংযোজিত\n" +
                    "• ভয়েস ইনপুট (STT) ও আউটপুট (TTS): সংযোজিত\n" +
                    "• অ্যাপ খোলা, YouTube হ্যান্ডঅফ ও Accessibility অটোমেশন: সংযোজিত\n" +
                    "• কোডিং মোড (WebView স্যান্ডবক্স + ঐচ্ছিক Termux): সংযোজিত"
            )
        }
    }
}
