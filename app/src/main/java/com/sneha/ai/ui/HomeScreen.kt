package com.sneha.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.ChatMsg
import com.sneha.ai.ai.describe
import com.sneha.ai.apps.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: MainViewModel, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val ph by vm.uiPhase.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val exec by vm.exec.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) vm.startListening()
        else vm.banner.value = "মাইক্রোফোন অনুমতি ছাড়া ভয়েস ইনপুট চলবে না। টেক্সট ব্যবহার করুন, অথবা Settings > প্রাইভেসি থেকে অনুমতি দিন।"
    }
    fun onMic() {
        when {
            ph == Phase.Listening -> vm.stopListening()
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.startListening()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("স্নেহা", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    when (ph) {
                        Phase.Idle -> "প্রস্তুত"
                        Phase.Listening -> "শুনছি…"
                        Phase.Thinking -> "ভাবছি…"
                        Phase.Speaking -> "বলছি…"
                        Phase.Executing -> "কাজ করছি: ${exec.text}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary
                )
            }
            TextButton(onClick = { nav("settings") }) { Text("⚙ সেটিংস") }
        }

        Orb(
            ph, level, s.reduceMotion,
            Modifier.fillMaxWidth().height(150.dp).clickable { onMic() }
        )
        if (partial.isNotBlank()) Text(partial, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)

        banner?.let {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { vm.banner.value = null }) { Text("✕") }
                }
            }
        }

        if (ph == Phase.Executing || ph == Phase.Thinking || ph == Phase.Speaking) {
            Button(
                onClick = { vm.stopAll() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (ph == Phase.Executing) "■ STOP — অটোমেশন থামাও" else "■ থামাও") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(msgs) { Bubble(it) }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { showApps = true }, label = { Text("📱 অ্যাপ খোলো") })
            AssistChip(onClick = { vm.send("একটা বাংলা গান চালাও") }, label = { Text("🎵 গান চালাও") })
            AssistChip(onClick = { nav("settings:auto") }, label = { Text("👆 টাচ অটোমেশন") })
            AssistChip(onClick = { nav("coding") }, label = { Text("💻 কোডিং") })
            AssistChip(onClick = { nav("settings") }, label = { Text("⚙ সেটিংস") })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("স্নেহাকে লিখুন…") }, maxLines = 3
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank()) { Text("➤") }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { onMic() },
                colors = if (ph == Phase.Listening) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) { Text("🎤") }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.deny() },
            title = { Text("নিশ্চিত করুন") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (p.reply.isNotBlank()) Text(p.reply)
                    Spacer(Modifier.height(8.dp))
                    Text("এই ধাপগুলো চলবে:", style = MaterialTheme.typography.titleSmall)
                    p.actions.forEachIndexed { i, a -> Text("${i + 1}. ${a.describe()}") }
                    Spacer(Modifier.height(8.dp))
                    Text("চলার সময় নোটিফিকেশন/স্ক্রিনে STOP পাবেন।", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { vm.confirm() }) { Text("চালাও") } },
            dismissButton = { TextButton(onClick = { vm.deny() }) { Text("বাতিল") } }
        )
    }

    if (showApps) {
        val app = ctx.applicationContext as SnehaApp
        val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.Default) { app.launcher.launchable() } }
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("কোন অ্যাপ খুলবেন?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps, key = { it.pkg }) { ap ->
                        TextButton(onClick = { showApps = false; vm.openApp(ap.label) }, modifier = Modifier.fillMaxWidth()) {
                            Text(ap.label, Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("বন্ধ") } }
        )
    }
}

@Composable
fun Bubble(m: ChatMsg) {
    val user = m.role == "user"
    val sys = m.role == "sys"
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentAlignment = if (sys) Alignment.Center else if (user) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                user -> MaterialTheme.colorScheme.primary
                sys -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                m.text, Modifier.padding(10.dp),
                color = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = if (sys) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
            )
        }
    }
}
