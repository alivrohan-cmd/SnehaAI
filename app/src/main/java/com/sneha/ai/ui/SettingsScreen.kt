package com.sneha.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
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
    SettingSection("ai", "AI Brain & Custom Gateway", "থার্ড-পার্টি Gateway URL, sk- Token, Claude/GPT ও Gemini", Icons.Default.Psychology),
    SettingSection("privacy", "১০০% পারমিশন সেটআপ হাব", "কল, কন্টাক্টস, SMS, মাইক ও Accessibility পারমিশন", Icons.Default.Security),
    SettingSection("voice", "ভয়েস ও নিঃশব্দ ওয়েক-মোড", "বাংলা/ইংরেজি কণ্ঠস্বর, সাইলেন্ট লিসেনিং ও গতি", Icons.Default.RecordVoiceOver),
    SettingSection("auto", "স্ক্রিন অটোমেশন ও সিস্টেম কন্ট্রোল", "গেম ইনস্টল, WhatsApp সেন্ড, স্ক্রিন লক ও পাওয়ার মেনু", Icons.Default.AccessibilityNew),
    SettingSection("apps", "সব ইনস্টল করা অ্যাপ্লিকেশন", "প্রিয় অ্যাপ নির্বাচন ও দ্রুত চালু", Icons.Default.Apps),
    SettingSection("coding", "কোডিং রানটাইম", "WebView স্যান্ডবক্স ও Termux ইন্টিগ্রেশন", Icons.Default.Code),
    SettingSection("memory", "ব্যক্তিগত মেমরি", "সংরক্ষিত তথ্য দেখা, এডিট ও এক্সপোর্ট", Icons.Default.Memory),
    SettingSection("look", "থিম ও ফন্ট", "ডার্ক/লাইট থিম, লেখার আকার ও অ্যানিমেশন", Icons.Default.Palette),
    SettingSection("notif", "নোটিফিকেশন", "অটোমেশন স্ট্যাটাস ও STOP নোটিফিকেশন", Icons.Default.Notifications),
    SettingSection("about", "অ্যাপ পরিচিতি (About Doreman)", "সংস্করণ, ফিচারের প্রকৃত অবস্থা ও তথ্য", Icons.Default.Info)
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
                text = sections.firstOrNull { it.key == page }?.title ?: "Doreman সেটিংস",
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
    val gemKeySet by vm.geminiKeySet.collectAsStateWithLifecycle()
    val gwKeySet by vm.gatewayKeySet.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val testing by vm.testingConn.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()

    var baseUrlInput by remember(s.gatewayBaseUrl) { mutableStateOf(s.gatewayBaseUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var customModelInput by remember(s.model) { mutableStateOf(s.model) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("১. কানেকশন মোড (Connection Mode)") {
            RadioRow(
                label = "Custom Gateway (থার্ড-পার্টি URL + sk-... Token / Claude / GPT)",
                selected = s.providerMode == "gateway"
            ) { vm.set(K.providerMode, "gateway") }
            RadioRow(
                label = "Google Gemini Official API",
                selected = s.providerMode == "gemini"
            ) { vm.set(K.providerMode, "gemini") }
        }

        if (s.providerMode == "gateway") {
            SecCard("২. Gateway Credentials (ভিডিওর মতো সেটআপ)") {
                Note("যেকোনো থার্ড-পার্টি ওয়েবসাইটের Base URL (যেমন https://tabitoken.com) এবং আপনার মিলিয়ন/বিলিয়ন টোকেনের API Key (sk-...) দিন:")
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = {
                        baseUrlInput = it
                        vm.set(K.gatewayBaseUrl, it.trim())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Gateway Base URL (যেমন: https://tabitoken.com)") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (gwKeySet) "নতুন Gateway API Key (sk-...) দিলে আগেরটি বদলে যাবে" else "Gateway API Key (sk-...)") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Text("Gateway Auth Scheme:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = s.gatewayAuthScheme == "bearer",
                        onClick = { vm.set(K.gatewayAuthScheme, "bearer") },
                        label = { Text("Authorization: Bearer") }
                    )
                    FilterChip(
                        selected = s.gatewayAuthScheme == "x-api-key",
                        onClick = { vm.set(K.gatewayAuthScheme, "x-api-key") },
                        label = { Text("x-api-key (Claude)") }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Endpoint Format:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.gatewayFormat == "auto",
                        onClick = { vm.set(K.gatewayFormat, "auto") },
                        label = { Text("Auto (OpenAI + Claude)") }
                    )
                    FilterChip(
                        selected = s.gatewayFormat == "openai",
                        onClick = { vm.set(K.gatewayFormat, "openai") },
                        label = { Text("/v1/chat/completions") }
                    )
                    FilterChip(
                        selected = s.gatewayFormat == "anthropic",
                        onClick = { vm.set(K.gatewayFormat, "anthropic") },
                        label = { Text("/v1/messages") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.set(K.gatewayBaseUrl, baseUrlInput.trim())
                            if (apiKeyInput.isNotBlank()) {
                                vm.saveGatewayKey(apiKeyInput)
                                apiKeyInput = ""
                            }
                        }
                    ) { Text("Save Gateway") }
                    Button(
                        onClick = { vm.testConnection() },
                        enabled = !testing
                    ) { Text(if (testing) "পরীক্ষা চলছে…" else "Test Connection") }
                }
                Spacer(Modifier.height(6.dp))
                Text(conn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            SecCard("২. Google Gemini API Key") {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (gemKeySet) "নতুন Gemini API key দিলে আগেরটি বদলে যাবে" else "Gemini API key দিন") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.saveKey(apiKeyInput); apiKeyInput = "" },
                        enabled = apiKeyInput.isNotBlank()
                    ) { Text("Key সেভ করুন") }
                    Button(
                        onClick = { vm.testConnection() },
                        enabled = gemKeySet && !testing
                    ) { Text(if (testing) "পরীক্ষা চলছে…" else "Test Connection") }
                }
                Spacer(Modifier.height(6.dp))
                Text(conn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        SecCard("৩. প্রিমিয়াম মডেল নির্বাচন বা যেকোনো মডেলের নাম লিখুন") {
            Text("বর্তমান সক্রিয় মডেল: ${s.model}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customModelInput,
                onValueChange = {
                    customModelInput = it
                    if (it.isNotBlank()) vm.set(K.model, it.trim())
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("যেকোনো মডেল আইডি লিখুন (যেমন: claude-opus-4-6, gpt-4o)") }
            )
            if (models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("তালিকা থেকে বেছে নিন:", style = MaterialTheme.typography.bodySmall)
                models.forEach { m ->
                    RadioRow(m, m == s.model) {
                        customModelInput = m
                        vm.set(K.model, m)
                    }
                }
            } else {
                Note("'Test Connection' চাপলে আপনার টোকেনে থাকা সব প্রিমিয়াম মডেলের তালিকা স্বয়ংক্রিয়ভাবে নিচে চলে আসবে।")
            }
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
        SecCard("নিঃশব্দ ওয়েক-মোড (No Beep Sound)") {
            SwitchRow("Doreman-এর উত্তর জোরে পড়ে শোনাও", s.autoSpeak) { vm.set(K.autoSpeak, it) }
            SwitchRow("নিঃশব্দে শুধু 'Doreman' ডাকের অপেক্ষায় থাকবে", handsFree) { vm.toggleHandsFree(it) }
            Note("মাইক্রোফোন চালু বা বন্ধ হওয়ার সময় কোনো 'টং/বিপ' সাউন্ড করবে না এবং অ্যাপ থেকে বের হয়ে গেলে সম্পূর্ণ নীরব থাকবে।")
            PrefSlider("কথার গতি", s.speechRate, 0.5f..2f, { "%.1fx".format(it) }) { vm.set(K.rate, it) }
            PrefSlider("ভলিউম", s.volume, 0f..1f, { "${(it * 100).toInt()}%" }) { vm.set(K.volume, it) }
        }

        SecCard("কণ্ঠস্বর নির্বাচন") {
            RadioRow("সিস্টেম ডিফল্ট", s.voiceName.isBlank()) { vm.set(K.voice, "") }
            voices.forEach { RadioRow(it, s.voiceName == it) { vm.set(K.voice, it) } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.speak(if (s.lang == "bn") "হ্যালো, আমি ডোরেমন। বলুন আপনার জন্য কী করতে পারি?" else "Hello, I am Doreman. How can I help you?")
                }) { Text("ভয়েস টেস্ট") }
                OutlinedButton(onClick = { tick++ }) { Text("তালিকা রিফ্রেশ") }
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
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Termux কোড রানটাইম") {
            SwitchRow("Termux দিয়ে কোড চালানো চালু", s.termuxEnabled) { vm.set(K.termux, it) }
            Text("Termux অ্যাপ: " + if (installed) "✓ ইনস্টল আছে" else "✗ ইনস্টল নেই")
            Text("RUN_COMMAND অনুমতি: " + if (perm) "✓ দেওয়া আছে" else "✗ দেওয়া হয়নি")
            Spacer(Modifier.height(6.dp))
            Button(onClick = { permLauncher.launch(TermuxRunner.PERM) }, enabled = installed && !perm) {
                Text("RUN_COMMAND অনুমতি দিন")
            }
        }
    }
}

@Composable
fun MemoryPage(vm: MainViewModel) {
    val a = LocalContext.current.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    val items by vm.memory.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("মেমরি নিয়ন্ত্রণ") {
            SwitchRow("Doreman-এর মেমরি চালু রাখুন", s.memoryEnabled) { vm.set(K.memory, it) }
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("নতুন তথ্য যোগ করুন") }
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (a.memory.add(newText) == null) newText = ""
                },
                enabled = newText.isNotBlank()
            ) { Text("যোগ করুন") }
        }

        SecCard("সংরক্ষিত তথ্য (${items.size})") {
            items.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { a.memory.delete(e.id) }) { Text("মুছুন") }
                }
            }
        }
    }
}

@Composable
fun PrivacyPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    var permTick by remember { mutableIntStateOf(0) }
    val tick = resumeTick() + permTick
    val a11y by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()
    val mic = remember(tick) { granted(ctx, Manifest.permission.RECORD_AUDIO) }
    val contacts = remember(tick) { granted(ctx, Manifest.permission.READ_CONTACTS) }
    val phone = remember(tick) { granted(ctx, Manifest.permission.CALL_PHONE) }
    val sms = remember(tick) { granted(ctx, Manifest.permission.SEND_SMS) }
    val notif = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }

    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }

    fun yn(b: Boolean) = if (b) "✓ চালু আছে" else "✗ দেওয়া হয়নি"

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("১-ক্লিকে সব পারমিশন চালু করুন") {
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
                Text("১০০% পারমিশন দিন (মাইক, কল, কন্টাক্টস, SMS)")
            }
        }

        SecCard("পারমিশন স্ট্যাটাস") {
            Text("🎙️ মাইক্রোফোন: ${yn(mic)}")
            Text("👥 কন্টাক্টস (Baba/বাবা খোঁজা): ${yn(contacts)}")
            Text("📞 সরাসরি ফোন কল: ${yn(phone)}")
            Text("💬 সরাসরি SMS: ${yn(sms)}")
            Text("🔔 নোটিফিকেশন: ${yn(notif)}")
            Text("👆 Accessibility (গেম ইনস্টল ও WhatsApp সেন্ড): ${yn(a11y)}")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    openIntent(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")), vm)
                }) { Text("অ্যাপ পারমিশন") }
                OutlinedButton(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) { Text("Accessibility চালু করুন") }
            }
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
    }
}

@Composable
fun NotifPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("অটোমেশন নোটিফিকেশন") {
            SwitchRow("অটোমেশন চলাকালীন STOP নোটিফিকেশন দেখাও", s.notifyAutomation) { vm.set(K.notify, it) }
        }
    }
}

@Composable
fun AboutPage() {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SecCard("Doreman AI — v1.1.0") {
            Text("কাস্টম গেটওয়ে (Claude/OpenAI/Gemini) ও সাইলেন্ট ওয়েক ভয়েস অ্যাসিস্ট্যান্ট")
        }
    }
}
