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
    SettingSection("privacy", "পারমিশন সেটআপ ও প্রাইভেসি", "কল, কন্টাক্টস, SMS, মাইক ও Accessibility পারমিশন", Icons.Default.Security),
    SettingSection("voice", "ভয়েস ও উচ্চারণ", "বাংলা/ইংরেজি কণ্ঠস্বর, হ্যান্ডস-ফ্রি ও গতি", Icons.Default.RecordVoiceOver),
    SettingSection("auto", "স্ক্রিন অটোমেশন ও সিস্টেম কন্ট্রোল", "Accessibility, স্ক্রিন লক, পাওয়ার মেনু ও STOP", Icons.Default.AccessibilityNew),
    SettingSection("apps", "অ্যাপ্লিকেশন তালিকা", "প্রিয় অ্যাপ নির্বাচন ও দ্রুত চালু", Icons.Default.Apps),
    SettingSection("coding", "কোডিং রানটাইম", "WebView স্যান্ডবক্স ও Termux ইন্টিগ্রেশন", Icons.Default.Code),
    SettingSection("memory", "ব্যক্তিগত মেমরি", "সংরক্ষিত তথ্য দেখা, এডিট ও এক্সপোর্ট", Icons.Default.Memory),
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
            Note("বিকল্প মডেলটি শুধুমাত্র আপনার API key-এর যাচাইকৃত তালিকা (listModels) থেকে বেছে নেওয়া হয়।")
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
    }
}

@Composable
fun VoicePage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val a = ctx.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    val handsFree by vm.handsFree.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    val voices = remember(s.lang, tick) { a.tts.voices(s.lang) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("কণ্ঠস্বর ও হ্যান্ডস-ফ্রি মোড") {
            SwitchRow("স্নেহার উত্তর জোরে পড়ে শোনাও (Auto-Speak)", s.autoSpeak) { vm.set(K.autoSpeak, it) }
            SwitchRow("হ্যান্ডস-ফ্রি ভয়েস লুপ (উত্তর বলা শেষ হলে আবার শুনবে)", handsFree) { vm.toggleHandsFree(it) }
            PrefSlider("কথার গতি", s.speechRate, 0.5f..2f, { "%.1fx".format(it) }) { vm.set(K.rate, it) }
            PrefSlider("ভলিউম", s.volume, 0f..1f, { "${(it * 100).toInt()}%" }) { vm.set(K.volume, it) }
        }

        SecCard("কণ্ঠস্বর নির্বাচন") {
            Note("তালিকা থেকে একটি বেছে 'ভয়েস টেস্ট' চেপে শুনে আপনার পছন্দের নারী কণ্ঠস্বরটি বেছে নিন।")
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
        }

        SecCard("সংরক্ষিত প্রজেক্ট (${projects.size})") {
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
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("নতুন তথ্য যোগ করুন (যেমন: আমাকে ভাইয়া বলে ডাকবে)") }
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
    var permTick by remember { mutableIntStateOf(0) }
    val tick = resumeTick() + permTick
    val a11y by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()
    val mic = remember(tick) { granted(ctx, Manifest.permission.RECORD_AUDIO) }
    val contacts = remember(tick) { granted(ctx, Manifest.permission.READ_CONTACTS) }
    val phone = remember(tick) { granted(ctx, Manifest.permission.CALL_PHONE) }
    val sms = remember(tick) { granted(ctx, Manifest.permission.SEND_SMS) }
    val notif = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val termuxPerm = remember(tick) { a.termux.hasPermission() }

    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }

    fun yn(b: Boolean) = if (b) "✓ চালু আছে" else "✗ দেওয়া হয়নি"

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("১-ক্লিকে সব অ্যাসিস্ট্যান্ট পারমিশন চালু করুন") {
            Note("ভয়েস কমান্ড দিয়ে কল করা, কন্টাক্ট খোঁজা, মেসেজ পাঠানো এবং নোটিফিকেশনের জন্য নিচের বাটনে চাপুন:")
            Button(
                onClick = {
                    val list = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.SEND_SMS
                    )
                    if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                    multiLauncher.launch(list.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("সব প্রয়োজনীয় পারমিশন দিন (মাইক, কল, কন্টাক্টস, SMS)")
            }
        }

        SecCard("পারমিশন স্ট্যাটাস ড্যাশবোর্ড") {
            Text("🎙️ মাইক্রোফোন (ভয়েস কমান্ড): ${yn(mic)}")
            Text("👥 কন্টাক্টস (নাম দিয়ে নম্বর খোঁজা): ${yn(contacts)}")
            Text("📞 ফোন কল (সরাসরি কল করা): ${yn(phone)}")
            Text("💬 SMS (মেসেজ পাঠানো): ${yn(sms)}")
            Text("🔔 নোটিফিকেশন: ${yn(notif)}")
            Text("👆 Accessibility (স্ক্রিন অটোমেশন, লক ও YouTube অটো-প্লে): ${yn(a11y)}")
            Text("💻 Termux RUN_COMMAND: ${yn(termuxPerm)}")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    openIntent(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")), vm)
                }) { Text("অ্যাপ পারমিশন সেটিংস") }
                OutlinedButton(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) { Text("Accessibility চালু করুন") }
            }
        }

        SecCard("চ্যাট ইতিহাস ও ডেটা") {
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
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "0.3.0" }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Sneha AI (স্নেহা) — v$ver") {
            Text("প্যাকেজ: com.sneha.ai • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Note("মস্তিষ্ক: Google Gemini API • UI: Jetpack Compose + Material 3")
        }
        SecCard("ফিচারের প্রকৃত অবস্থা") {
            Note(
                "• ভয়েস কমান্ড ও হ্যান্ডস-ফ্রি ভয়েস লুপ (bn-BD/bn-IN/en): সংযোজিত\n" +
                    "• কন্টাক্টস থেকে নাম খুঁজে সরাসরি ফোন কল ও ডায়ালার: সংযোজিত\n" +
                    "• SMS পাঠানো, ড্রাফট খোলা ও WhatsApp মেসেজ হ্যান্ডঅফ: সংযোজিত\n" +
                    "• YouTube ও YouTube Music-এ সরাসরি গান চালানো ও অডিও যাচাই: সংযোজিত\n" +
                    "• স্ক্রিন লক, পাওয়ার/শাটডাউন মেনু, ভলিউম ও সিস্টেম সেটিংস: সংযোজিত\n" +
                    "• ১-ক্লিক পারমিশন সেটআপ হাব: সংযোজিত"
            )
        }
    }
}
