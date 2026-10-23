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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

private val pages = listOf(
    "ai" to "১. AI Brain (Gemini)", "voice" to "২. ভয়েস", "auto" to "৩. অটোমেশন",
    "apps" to "৪. অ্যাপ্লিকেশন", "coding" to "৫. কোডিং", "memory" to "৬. মেমরি",
    "privacy" to "৭. প্রাইভেসি", "look" to "৮. অ্যাপিয়ারেন্স", "notif" to "৯. নোটিফিকেশন", "about" to "১০. About"
)

@Composable
fun SettingsScreen(vm: MainViewModel, initial: String?, onBack: () -> Unit) {
    var page by rememberSaveable(initial) { mutableStateOf<String?>(initial) }
    BackHandler(page != null) { page = null }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (page != null) page = null else onBack() }) { Text("← পেছনে") }
            Text(pages.firstOrNull { it.first == page }?.second ?: "সেটিংস", style = MaterialTheme.typography.titleLarge)
        }
        when (page) {
            null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                pages.forEach { (k, l) ->
                    TextButton(onClick = { page = k }, modifier = Modifier.fillMaxWidth()) { Text(l, Modifier.fillMaxWidth()) }
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

// ---------- ছোট UI সহায়ক ----------
@Composable fun Sec(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
@Composable fun Note(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}

@Composable
fun PrefSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Text("$label: ${fmt(v)}")
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
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { vm.banner.value = "সেটিংস স্ক্রিন খোলা যায়নি" }
}

// ---------- ১. AI Brain ----------
@Composable
fun AiPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val keySet by vm.keySet.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Sec("প্রোভাইডার")
        RadioRow("Google Gemini (ডিফল্ট)", true) {}
        Note("Claude / OpenAI / লোকাল মডেল: Not implemented — AiProvider ইন্টারফেস তৈরি আছে, কিন্তু কোনো অতিরিক্ত প্রোভাইডার যুক্ত করা হয়নি। একাধিক সাবস্ক্রিপশন লাগে না।")
        Sec("Gemini API key")
        Note("কী ফোনে Android Keystore দিয়ে এনক্রিপ্ট করে রাখা হয়; APK-তে কোনো কী নেই। ব্যক্তিগত প্রোটোটাইপের জন্য ঠিক আছে, তবে ফোন রুট/ক্ষতিকর অ্যাপে ঝুঁকি থাকে। প্রোডাকশনে নিজস্ব সিকিউর ব্যাকএন্ড দরকার। কী কখনো GitHub-এ তুলবেন না।")
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (keySet) "নতুন কী দিলে বদলে যাবে (একটি সংরক্ষিত আছে)" else "Gemini API key") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("সেভ") }
            if (keySet) TextButton(onClick = { vm.deleteKey() }) { Text("কী মুছুন") }
        }
        Button(onClick = { vm.testConnection() }, enabled = keySet) { Text("Test Connection") }
        Text(conn, style = MaterialTheme.typography.bodySmall)
        Sec("মডেল")
        Text("নির্বাচিত: ${s.model}")
        if (models.isEmpty()) Note("Test Connection চালালে আপনার কী-র জন্য উপলব্ধ মডেলের তালিকা আসবে; তখন বেছে নিন।")
        else models.forEach { RadioRow(it, it == s.model) { vm.set(K.model, it) } }
        Sec("জেনারেশন")
        SwitchRow("Temperature পাঠাও", s.useTemperature) { vm.set(K.useTemp, it) }
        Note("নতুন কিছু Gemini মডেল temperature/top_p/top_k গ্রহণ করে না, তাই ডিফল্টে বন্ধ। অ্যাপ ত্রুটি পেলে নিজে থেকে বাদ দিয়ে আবার চেষ্টা করে।")
        if (s.useTemperature) PrefSlider("Temperature", s.temperature, 0f..2f, { "%.1f".format(it) }) { vm.set(K.temperature, it) }
        PrefSlider("সর্বোচ্চ আউটপুট টোকেন", s.maxTokens.toFloat(), 256f..8192f, { it.toInt().toString() }) { vm.set(K.maxTokens, it.toInt()) }
        Sec("কথোপকথনের ভাষা")
        RadioRow("বাংলা", s.lang == "bn") { vm.set(K.lang, "bn") }
        RadioRow("English", s.lang == "en") { vm.set(K.lang, "en") }
        Sec("ব্যবহার পরিসংখ্যান (এই ফোনে)")
        Text("অনুরোধ: ${s.reqCount} • ইনপুট টোকেন: ${s.tokensIn} • আউটপুট টোকেন: ${s.tokensOut}")
    }
}

// ---------- ২. ভয়েস ----------
@Composable
fun VoicePage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val a = ctx.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    val voices = remember(s.lang, tick) { a.tts.voices(s.lang) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Sec("ভাষা")
        RadioRow("বাংলা", s.lang == "bn") { vm.set(K.lang, "bn") }
        RadioRow("English", s.lang == "en") { vm.set(K.lang, "en") }
        SwitchRow("উত্তর জোরে পড়ে শোনাও", s.autoSpeak) { vm.set(K.autoSpeak, it) }
        PrefSlider("কথার গতি", s.speechRate, 0.5f..2f, { "%.1fx".format(it) }) { vm.set(K.rate, it) }
        PrefSlider("ভলিউম", s.volume, 0f..1f, { "${(it * 100).toInt()}%" }) { vm.set(K.volume, it) }
        Sec("কণ্ঠস্বর")
        Note("Android ভয়েসের লিঙ্গ (নারী/পুরুষ) জানায় না, তাই স্বয়ংক্রিয়ভাবে নারী কণ্ঠ বাছা যায় না। তালিকা থেকে একটি বেছে 'ভয়েস টেস্ট' দিয়ে শুনে ঠিক করুন। বাংলা ভয়েস না থাকলে TTS ইঞ্জিন সেটিংস থেকে ডাউনলোড করুন।")
        RadioRow("ডিফল্ট", s.voiceName.isBlank()) { vm.set(K.voice, "") }
        voices.forEach { RadioRow(it, s.voiceName == it) { vm.set(K.voice, it) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.speak(if (s.lang == "bn") "নমস্কার, আমি স্নেহা। আমার কথা শুনতে পাচ্ছেন?" else "Hello, I am Sneha. Can you hear me?")
            }) { Text("ভয়েস টেস্ট") }
            OutlinedButton(onClick = { tick++ }) { Text("তালিকা রিফ্রেশ") }
        }
        OutlinedButton(onClick = { openIntent(ctx, Intent("com.android.settings.TTS_SETTINGS"), vm) }) { Text("TTS ইঞ্জিন সেটিংস") }
        Sec("ভয়েস ইনপুট")
        Text("স্পিচ রিকগনিশন: " + if (SpeechRecognizer.isRecognitionAvailable(ctx)) "উপলব্ধ" else "এই ফোনে উপলব্ধ নয়")
        Note("মাইক শুধু আপনি মাইক বাটন চাপলে চালু হয়। ডিফল্ট রিকগনাইজার অনেক সময় অনলাইনে কাজ করে।")
        Sec("Wake phrase")
        Note("Not implemented — অ্যাপ বন্ধ থাকলে সবসময় শোনার জন্য স্থায়ী নোটিফিকেশনসহ ফোরগ্রাউন্ড সার্ভিস ও অফলাইন কীওয়ার্ড ইঞ্জিন লাগে; এই সংস্করণে তৈরি করা হয়নি। মাইক বাটন ব্যবহার করুন।")
    }
}

// ---------- ৩. অটোমেশন ----------
@Composable
fun AutoPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val on by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Sec("অবস্থা")
        Text(if (on) "✓ Accessibility সার্ভিস চালু" else "✗ Accessibility সার্ভিস বন্ধ")
        Button(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) { Text("Accessibility সেটিংস খুলুন") }
        Sec("চালু করার ধাপ")
        Note(
            "১. উপরের বাটনে চাপুন → 'Installed apps' বা 'Downloaded apps' → Sneha AI।\n" +
                "২. Android 13+ এ APK নিজে ইনস্টল করলে সুইচ ধূসর থাকতে পারে: ফোনের Settings > Apps > Sneha AI > ⋮ (উপরের ডানে) > 'Allow restricted settings' দিন, তারপর আবার ১ নম্বর ধাপে যান।\n" +
                "৩. Sneha AI চালু করুন → সতর্কবার্তা পড়ে 'Allow' দিন।\n" +
                "৪. অ্যাপে ফিরে এসে এই পেজে ✓ দেখুন।\n" +
                "বন্ধ করতে একই জায়গা থেকে সুইচ অফ করুন, বা নিচের বাটন চাপুন। অ্যাপ নিজে থেকে কখনো এটি চালু করে না।"
        )
        Sec("নিয়ন্ত্রণ")
        Button(onClick = { vm.stopAll() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("■ চলমান অটোমেশন থামাও") }
        OutlinedButton(onClick = { SnehaAccessibilityService.instance?.disableSelf() }, enabled = on) { Text("অটোমেশন সার্ভিস বন্ধ করুন") }
        Sec("কী কী করতে পারে")
        Note("ট্যাপ (টেক্সট/স্থানাঙ্ক), স্ক্রল, Back/Home, ফোকাসড ফিল্ডে টেক্সট লেখা (পাসওয়ার্ড ফিল্ড বাদে), স্ক্রিনের লেবেল পড়া। প্রতিটি ধাপের পর ফলাফল যাচাই করে সফল/ব্যর্থ জানানো হয়।")
        Sec("সীমা ও নিরাপত্তা")
        Note("• লক স্ক্রিন, প্রমাণীকরণ, CAPTCHA, ব্যাংকিং/পেমেন্ট অ্যাপ, পাসওয়ার্ড ম্যানেজার, সিস্টেম Settings/পারমিশন ডায়ালগে অটোমেশন বন্ধ।\n• স্ক্রিন রেকর্ড/ক্যাপচার করা হয় না; পড়া লেবেল Gemini-তে পাঠানো হয় না।\n• প্রতিটি স্ক্রিন-অটোমেশনের আগে আপনার নিশ্চিতকরণ চাওয়া হয়।\n• 'অ্যাপ বন্ধ' মানে Back/Home দিয়ে সরে যাওয়া; অন্য অ্যাপ Force-stop করা সাধারণ অ্যাপের পক্ষে সম্ভব নয়।")
    }
}

// ---------- ৪. অ্যাপ্লিকেশন ----------
@Composable
fun AppsPage(vm: MainViewModel) {
    val a = LocalContext.current.applicationContext as SnehaApp
    val s by vm.settings.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.Default) { a.launcher.launchable() } }
    Column {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("অ্যাপ খুঁজুন") })
        Note("টিক দিলে অ্যাপটি প্রিয় হয়; একই নামের একাধিক অ্যাপ থাকলে প্রিয়টি আগে খোলা হয়।")
        LazyColumn(Modifier.weight(1f)) {
            items(apps.filter { it.label.contains(q, true) }, key = { it.pkg }) { ap ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ap.label, Modifier.weight(1f))
                    TextButton(onClick = { vm.openApp(ap.label) }) { Text("খোলো") }
                    Checkbox(ap.pkg in s.favorites, { c -> vm.set(K.favorites, if (c) s.favorites + ap.pkg else s.favorites - ap.pkg) })
                }
            }
        }
    }
}

// ---------- ৫. কোডিং ----------
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
        Sec("Termux ইন্টিগ্রেশন")
        SwitchRow("Termux দিয়ে কোড চালানো চালু", s.termuxEnabled) { vm.set(K.termux, it) }
        Text("Termux: " + if (installed) "ইনস্টল আছে" else "ইনস্টল নেই")
        Text("RUN_COMMAND অনুমতি: " + if (perm) "দেওয়া আছে" else "দেওয়া হয়নি")
        Button(onClick = { permLauncher.launch(TermuxRunner.PERM) }, enabled = installed && !perm) { Text("অনুমতি চান") }
        Note(
            "Termux আলাদা অ্যাপ। সেটআপ (Termux-এ চালান):\n" +
                "১. pkg install python clang nodejs openjdk-17   (যেগুলো লাগবে)\n" +
                "২. mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties\n" +
                "৩. Termux পুরোপুরি বন্ধ করে (নোটিফিকেশন > Exit) আবার খুলুন।\n" +
                "৪. এখানে 'অনুমতি চান' চাপুন।\n" +
                "প্রতিটি কমান্ড চালানোর আগে হুবহু স্ক্রিপ্ট দেখানো হয় এবং আপনার অনুমোদন লাগে। Termux-এ ৬০ সেকেন্ডের timeout থাকে।"
        )
        Sec("প্রজেক্ট ডিরেক্টরি")
        Text(a.projects.root.absolutePath, style = MaterialTheme.typography.bodySmall)
        if (projects.isEmpty()) Note("এখনো কোনো প্রজেক্ট সেভ করা হয়নি।")
        projects.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$p (${a.projects.files(p).size} ফাইল)", Modifier.weight(1f))
                TextButton(onClick = { a.projects.delete(p); projTick++ }) { Text("মুছুন") }
            }
        }
        Sec("রানটাইম অবস্থা")
        Note("HTML/CSS/JS: অ্যাপের ভেতরে স্যান্ডবক্স WebView।\nPython, C, C++, Java, Node, Bash: Termux লাগবে।\nKotlin ও অন্য ভাষা: শুধু কোড তৈরি; চালানো Not implemented।")
    }
}

// ---------- ৬. মেমরি ----------
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
        SwitchRow("মেমরি চালু", s.memoryEnabled) { vm.set(K.memory, it) }
        Note("শুধু আপনার অনুমোদিত তথ্য এই ফোনে থাকে। পাসওয়ার্ড/কী/OTP/কার্ড নম্বর সংরক্ষণ করা হয় না। মেমরি চালু থাকলে এই তালিকা Gemini-কে পাঠানো প্রম্পটে যোগ হয়; বন্ধ করলে যোগ হয় না।")
        OutlinedTextField(newText, { newText = it }, Modifier.fillMaxWidth(), label = { Text("নতুন তথ্য (যেমন: আমাকে 'দোস্ত' বলে ডাকবে)") })
        Button(onClick = { err = a.memory.add(newText); if (err == null) newText = "" }, enabled = newText.isNotBlank()) { Text("যোগ করুন") }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Sec("সংরক্ষিত (${items.size})")
        items.forEach { e ->
            if (editId == e.id) {
                OutlinedTextField(editText, { editText = it }, Modifier.fillMaxWidth())
                Row {
                    Button(onClick = { err = a.memory.update(e.id, editText); if (err == null) editId = null }) { Text("সেভ") }
                    TextButton(onClick = { editId = null }) { Text("বাতিল") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.text, Modifier.weight(1f))
                    TextButton(onClick = { editId = e.id; editText = e.text }) { Text("এডিট") }
                    TextButton(onClick = { a.memory.delete(e.id) }) { Text("মুছুন") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("sneha_memory.json") }) { Text("এক্সপোর্ট") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) { Text("সব মুছুন") }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("সব মেমরি মুছবেন?") },
        text = { Text("এটি ফেরানো যাবে না।") },
        confirmButton = { Button(onClick = { a.memory.clear(); confirmClear = false }) { Text("মুছুন") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("বাতিল") } }
    )
}

// ---------- ৭. প্রাইভেসি ----------
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
    fun yn(b: Boolean) = if (b) "✓ চালু/দেওয়া আছে" else "✗ বন্ধ/দেওয়া নেই"
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Sec("পারমিশন ড্যাশবোর্ড")
        Text("মাইক্রোফোন: ${yn(mic)}")
        Text("Accessibility (স্ক্রিন অটোমেশন): ${yn(a11y)}")
        Text("নোটিফিকেশন: ${yn(notif)}")
        Text("Termux RUN_COMMAND: ${yn(termuxPerm)}")
        Text("এখন অটোমেশন চলছে: ${if (exec.running) "হ্যাঁ — ${exec.text}" else "না"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                openIntent(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")), vm)
            }) { Text("অ্যাপ পারমিশন") }
            OutlinedButton(onClick = { openIntent(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS), vm) }) { Text("Accessibility") }
        }
        Button(onClick = { vm.stopAll() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("■ সব অটোমেশন থামাও") }
        Sec("ডেটা কোথায় যায়")
        Note(
            "• Gemini-তে পাঠানো হয়: আপনার লেখা/বলা বার্তা, সাম্প্রতিক ১৪টি চ্যাট বার্তা, (মেমরি চালু থাকলে) সংরক্ষিত পছন্দ, তারিখ-সময়, এবং Accessibility চালু আছে কিনা। কোডিং মোডে আপনার কোড/প্রম্পট।\n" +
                "• পাঠানো হয় না: স্ক্রিনের লেবেল/কনটেন্ট, কন্টাক্ট, মেসেজ, ফাইল, API key। অটোমেশন লগ শুধু ফোনে দেখানো হয়।\n" +
                "• ভয়েস: মাইক বাটন চাপলে ফোনের স্পিচ রিকগনিশন সার্ভিস (সাধারণত Google) অডিও প্রক্রিয়া করে — তাদের নীতি প্রযোজ্য।\n" +
                "• Gemini API-র ডেটা ব্যবহারের নীতি আপনার অ্যাকাউন্ট/টিয়ারের উপর নির্ভর করে; Google-এর বর্তমান শর্ত দেখে নিন।\n" +
                "• চ্যাট ইতিহাস, মেমরি, প্রজেক্ট শুধু অ্যাপের অভ্যন্তরীণ স্টোরেজে থাকে; ক্লাউড ব্যাকআপ বন্ধ।"
        )
        Sec("ডেটা মুছুন")
        OutlinedButton(onClick = { vm.clearChat() }) { Text("চ্যাট ইতিহাস মুছুন") }
    }
}

// ---------- ৮. অ্যাপিয়ারেন্স ----------
@Composable
fun LookPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Sec("থিম")
        RadioRow("ডার্ক", s.theme == "dark") { vm.set(K.theme, "dark") }
        RadioRow("লাইট", s.theme == "light") { vm.set(K.theme, "light") }
        RadioRow("সিস্টেম অনুযায়ী", s.theme == "system") { vm.set(K.theme, "system") }
        Sec("ফন্ট সাইজ")
        PrefSlider("স্কেল", s.fontScale, 0.8f..1.5f, { "%.2fx".format(it) }) { vm.set(K.fontScale, it) }
        Sec("অ্যানিমেশন")
        SwitchRow("অ্যানিমেশন কমাও (ব্যাটারি সাশ্রয়)", s.reduceMotion) { vm.set(K.reduceMotion, it) }
        Note("Idle অবস্থায় অর্ব অ্যানিমেশন এমনিতেই বন্ধ থাকে।")
    }
}

// ---------- ৯. নোটিফিকেশন ----------
@Composable
fun NotifPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val tick = resumeTick()
    val ok = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SwitchRow("অটোমেশনের সময় স্ট্যাটাস নোটিফিকেশন (STOP বাটনসহ)", s.notifyAutomation) { vm.set(K.notify, it) }
        Text("নোটিফিকেশন অনুমতি: " + if (ok) "✓ আছে" else "✗ নেই")
        if (!ok) Button(onClick = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("অনুমতি দিন") }
        Note("অন্য অ্যাপের ওপর অটোমেশন চলার সময় Sneha-র স্ক্রিন দেখা যায় না, তাই নোটিফিকেশনের STOP-ই প্রধান থামানোর উপায়।")
        Sec("রিমাইন্ডার")
        Note("Not implemented — এই সংস্করণে রিমাইন্ডার/টাস্ক নোটিফিকেশন নেই।")
    }
}

// ---------- ১০. About ----------
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" } }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Sneha AI (স্নেহা) $ver", style = MaterialTheme.typography.titleMedium)
        Text("com.sneha.ai • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
        Sec("নির্ভরতা")
        Note("Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Material 3, DataStore, OkHttp 4.12, Coroutines, AGP 8.2.2")
        Sec("বৈশিষ্ট্যের অবস্থা (এই সংস্করণ)")
        Note(
            "Implemented but not tested — সব নিচেরগুলো ডিভাইসে এখনও পরীক্ষা করা হয়নি:\n" +
                "• Gemini চ্যাট ও Test Connection, JSON অ্যাকশন প্ল্যান\n• ভয়েস ইনপুট/আউটপুট, ভাষা, গতি, ভলিউম, ভয়েস বাছাই\n• অ্যাপ খোলা, প্রিয় অ্যাপ\n• Accessibility: ট্যাপ/স্ক্রল/Back/টাইপ/রিড, STOP\n• YouTube সার্চ হ্যান্ডঅফ ও প্রথম ফলাফল চালানোর চেষ্টা\n• মিডিয়া কী (pause/next)\n• কোডিং মোড, WebView স্যান্ডবক্স, প্রজেক্ট সেভ/এক্সপোর্ট\n• মেমরি, প্রাইভেসি ড্যাশবোর্ড, থিম\n\n" +
                "Partially implemented:\n• Termux এক্সিকিউশন (প্রোটোকল ডক থেকে লেখা; আসল Termux-এ যাচাই বাকি)\n\n" +
                "Not implemented:\n• Wake phrase / সবসময় শোনা\n• ফ্লোটিং অ্যাসিস্ট্যান্ট বাবল\n• Claude/OpenAI/লোকাল মডেল প্রোভাইডার\n• রিমাইন্ডার\n• Room ডেটাবেস (সরল JSON ফাইল ব্যবহৃত)\n• Kotlin/অন্য ভাষা চালানো"
        )
    }
}
