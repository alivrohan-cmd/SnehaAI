package com.sneha.ai.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sneha.ai.coding.TermuxRunner
import java.io.ByteArrayInputStream

@Composable
fun CodingScreen(vm: CodingViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val lang by vm.lang.collectAsStateWithLifecycle()
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val code by vm.code.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val pendingScript by vm.pendingScript.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val needPerm by vm.needPerm.collectAsStateWithLifecycle()
    var project by rememberSaveable { mutableStateOf("my-project") }
    var task by rememberSaveable { mutableStateOf("write") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) vm.requestRun() else vm.log.value = "Termux RUN_COMMAND অনুমতি দেওয়া হয়নি — কোড চালানো হয়নি।"
    }
    LaunchedEffect(needPerm) {
        if (needPerm) { vm.needPerm.value = false; permLauncher.launch(TermuxRunner.PERM) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(code.toByteArray()) } }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← পেছনে") }
            Text("কোডিং মোড", style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.langs.forEach { l -> FilterChip(selected = l == lang, onClick = { vm.lang.value = l }, label = { Text(l) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("write" to "লেখো", "explain" to "ব্যাখ্যা", "fix" to "ঠিক করো").forEach { (k, l) ->
                FilterChip(selected = task == k, onClick = { task = k }, label = { Text(l) })
            }
        }
        OutlinedTextField(
            value = prompt, onValueChange = { vm.prompt.value = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (task == "fix") "সমস্যা/এরর (ফাঁকা থাকলে শেষ লগ ব্যবহার হবে)" else "কী বানাতে চান?") }
        )
        Button(onClick = { vm.ask(task) }, enabled = !busy) { Text(if (busy) "অপেক্ষা করুন…" else "Gemini-কে বলুন") }
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = code, onValueChange = { vm.code.value = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            label = { Text("কোড ($lang)") }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.requestRun() }) { Text("▶ চালাও") }
            OutlinedButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text("কপি") }
            OutlinedButton(onClick = { vm.save(project) }) { Text("সেভ") }
            OutlinedButton(onClick = { export.launch(vm.fileName()) }) { Text("এক্সপোর্ট") }
        }
        OutlinedTextField(project, { project = it }, Modifier.fillMaxWidth(), label = { Text("প্রজেক্টের নাম (সেভের জন্য)") }, singleLine = true)
        Text(
            "HTML/CSS/JS: আলাদা স্যান্ডবক্স WebView-এ প্রিভিউ (নেটওয়ার্ক বন্ধ)। Python/C/C++/Java/Bash/Node: Termux লাগবে, প্রতিবার আপনার অনুমোদনে। Kotlin ইত্যাদি: শুধু তৈরি হয়, চলে না।",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (log.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Text(log, Modifier.fillMaxWidth().padding(8.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (running) OutlinedButton(onClick = { vm.cancelRun() }) { Text("■ অপেক্ষা বাতিল") }
    }

    pendingScript?.let { sc ->
        AlertDialog(
            onDismissRequest = { vm.pendingScript.value = null },
            title = { Text("Termux-এ এই কমান্ড চলবে") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(sc, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                    Spacer(Modifier.height(6.dp))
                    Text("সর্বোচ্চ ৬০ সেকেন্ড চলবে। ফাইল থাকবে ~/sneha_run-এ। বুঝে অনুমোদন দিন।", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { vm.approveRun() }) { Text("অনুমোদন ও চালাও") } },
            dismissButton = { TextButton(onClick = { vm.pendingScript.value = null }) { Text("বাতিল") } }
        )
    }

    preview?.let { html ->
        Dialog(onDismissRequest = { vm.preview.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("স্যান্ডবক্স প্রিভিউ", Modifier.padding(12.dp).weight(1f))
                        TextButton(onClick = { vm.preview.value = null }) { Text("বন্ধ ✕") }
                    }
                    SandboxWebView(html, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** JS চলে কিন্তু: কোনো Java bridge নেই, ফাইল/কন্টেন্ট অ্যাক্সেস বন্ধ, নেটওয়ার্ক ও নেভিগেশন ব্লক। */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.blockNetworkLoads = true
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val sch = request?.url?.scheme
                        return if (sch == "http" || sch == "https")
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}
