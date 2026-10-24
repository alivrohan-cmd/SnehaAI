package com.sneha.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.ChatMsg
import com.sneha.ai.ai.describe
import com.sneha.ai.apps.AppInfo
import com.sneha.ai.automation.SnehaAccessibilityService
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
    val keySet by vm.keySet.collectAsStateWithLifecycle()
    val connOk by vm.connOk.collectAsStateWithLifecycle()
    val retryStatus by vm.retryStatus.collectAsStateWithLifecycle()
    val handsFree by vm.handsFree.collectAsStateWithLifecycle()
    val a11yOn by SnehaAccessibilityService.connected.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) vm.startListening()
        else vm.banner.value = "মাইক্রোফোন অনুমতি ছাড়া ভয়েস ইনপুট চলবে না। Settings > পারমিশন থেকে অনুমতি দিন।"
    }

    fun onMic() {
        when {
            ph == Phase.Listening -> vm.stopListening()
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.startListening()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !keySet -> MaterialTheme.colorScheme.error
                                connOk == true -> Color(0xFF10B981)
                                connOk == false -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "স্নেহা",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = s.model.removePrefix("gemini-"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = when (ph) {
                            Phase.Idle -> if (handsFree) "হ্যান্ডস-ফ্রি ভয়েস মোড চালু" else if (keySet) "প্রস্তুত • ভয়েস অ্যাসিস্ট্যান্ট" else "API Key সেট করা নেই"
                            Phase.Listening -> "আপনার কথা শুনছি…"
                            Phase.Thinking -> retryStatus ?: "চিন্তা করছি…"
                            Phase.Speaking -> "কথা বলছি…"
                            Phase.Executing -> "কাজ করছি: ${exec.text}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (retryStatus != null || handsFree) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (!handsFree && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            vm.toggleHandsFree(!handsFree)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = "হ্যান্ডস-ফ্রি মোড",
                        tint = if (handsFree) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nav("coding") }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "কোডিং মোড",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nav("settings") }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "সেটিংস",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!keySet) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { nav("settings:ai") }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Gemini API Key সেট করুন",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "কথা বলা শুরু করতে এখানে ট্যাপ করে আপনার API Key দিন ও Test Connection চালান।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val orbHeight = if (msgs.isEmpty() || ph == Phase.Listening || ph == Phase.Speaking) 185.dp else 135.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Orb(
                    phase = ph,
                    level = level,
                    reduceMotion = s.reduceMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(orbHeight)
                        .clickable { onMic() }
                )
                if (partial.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "“$partial”",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (ph == Phase.Idle && msgs.isEmpty()) {
                    Text(
                        text = "অর্বে ট্যাপ করে কথা বলুন (যেমন: “মাকে ফোন করো” বা “একটা গান চালাও”)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        banner?.let { bText ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { vm.banner.value = null }) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = ph == Phase.Executing || ph == Phase.Thinking || ph == Phase.Speaking) {
            Button(
                onClick = { vm.stopAll() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (ph == Phase.Executing) "জরুরি STOP — অটোমেশন থামান" else "থামান",
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }

        if (msgs.isEmpty()) {
            WelcomeSuggestions(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onSend = { vm.send(it) },
                onOpenPerms = { nav("settings:privacy") }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(msgs) { m ->
                    Bubble(
                        m = m,
                        onSpeak = { vm.speak(it) },
                        onRetry = { prompt -> vm.retry(prompt) },
                        onOpenSettings = { nav("settings:ai") }
                    )
                }
            }
        }

        AnimatedVisibility(visible = showTools) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "অ্যাসিস্ট্যান্ট কন্ট্রোল ও টুলস",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (msgs.isNotEmpty()) {
                                TextButton(onClick = { vm.clearChat(); showTools = false }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("চ্যাট মুছুন", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { showTools = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "বন্ধ", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolPill(Icons.Default.Call, "কল পারমিশন") { showTools = false; nav("settings:privacy") }
                        ToolPill(Icons.Default.Message, "মেসেজ/SMS") { showTools = false; nav("settings:privacy") }
                        ToolPill(Icons.Default.MusicNote, "বাংলা গান চালাও") { showTools = false; vm.send("একটা জনপ্রিয় বাংলা গান চালাও") }
                        ToolPill(Icons.Default.Apps, "অ্যাপ খোলো") { showTools = false; showApps = true }
                        ToolPill(Icons.Default.Lock, "ফোন লক") { showTools = false; vm.send("ফোন লক করো") }
                        ToolPill(Icons.Default.PowerSettingsNew, "পাওয়ার মেনু") { showTools = false; vm.send("ফোনের পাওয়ার মেনু খোলো") }
                        ToolPill(
                            icon = Icons.Default.TouchApp,
                            label = if (a11yOn) "অটোমেশন (চালু)" else "অটোমেশন (বন্ধ)"
                        ) { showTools = false; nav("settings:auto") }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showTools = !showTools }
                ) {
                    Icon(
                        imageVector = if (showTools) Icons.Default.Close else Icons.Default.Build,
                        contentDescription = "টুলস প্যানেল",
                        tint = if (showTools) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "স্নেহাকে বলুন (কল, মেসেজ, গান, অ্যাপ)…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                if (input.isNotBlank()) {
                    FilledIconButton(
                        onClick = {
                            val toSend = input
                            input = ""
                            vm.send(toSend)
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "পাঠান",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = { onMic() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (ph == Phase.Listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (ph == Phase.Listening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "ভয়েস ইনপুট",
                            tint = if (ph == Phase.Listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.deny() },
            title = { Text("অ্যাকশন নিশ্চিত করুন") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (p.reply.isNotBlank()) {
                        Text(p.reply, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("স্নেহা নিচের কাজটি করতে চায়:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    p.actions.forEachIndexed { i, a ->
                        Text("${i + 1}. ${a.describe()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirm() }) { Text("অনুমোদন ও চালাও") }
            },
            dismissButton = {
                TextButton(onClick = { vm.deny() }) { Text("বাতিল") }
            }
        )
    }

    if (showApps) {
        val app = ctx.applicationContext as SnehaApp
        var searchApp by remember { mutableStateOf("") }
        val apps by produceState(emptyList<AppInfo>()) {
            value = withContext(Dispatchers.Default) { app.launcher.launchable() }
        }
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("কোন অ্যাপ খুলবেন?") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchApp,
                        onValueChange = { searchApp = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("অ্যাপের নাম লিখুন…") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(apps.filter { it.label.contains(searchApp, ignoreCase = true) }, key = { it.pkg }) { ap ->
                            TextButton(
                                onClick = {
                                    showApps = false
                                    vm.openApp(ap.label)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(ap.label, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApps = false }) { Text("বন্ধ") }
            }
        )
    }
}

@Composable
private fun WelcomeSuggestions(
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit,
    onOpenPerms: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "নমস্কার, আমি স্নেহা",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "আপনার ভয়েস-কন্ট্রোলড পার্সোনাল অ্যাসিস্ট্যান্ট:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        val suggestions = listOf(
            "📞 কল, মেসেজ ও মাইক্রোফোন পারমিশনগুলো এক ক্লিকে চালু করুন" to onOpenPerms,
            "🎵 অরিজিৎ সিং-এর একটা জনপ্রিয় গান YouTube-এ চালাও" to { onSend("অরিজিৎ সিং এর একটা জনপ্রিয় গান চালাও") },
            "💬 রাহুলকে মেসেজ দাও আমি ১০ মিনিট পরে আসছি" to { onSend("রাহুলকে মেসেজ দাও আমি ১০ মিনিট পরে আসছি") },
            "🔒 আমার ফোনের স্ক্রিন লক করো" to { onSend("ফোন লক করো") }
        )
        suggestions.forEach { (label, action) ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { action() }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ToolPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun Bubble(
    m: ChatMsg,
    onSpeak: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val isUser = m.role == "user"
    val isSys = m.role == "sys"

    if (m.isError) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "সংযোগ বা মডেল সমস্যা",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.retryPrompt?.let { rp ->
                        Button(
                            onClick = { onRetry(rp) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("আবার চেষ্টা করুন", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Brain সেটিংস", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        return
    }

    if (isSys) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = m.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 325.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "স্নেহা" + (m.modelUsed?.let { " • ${it.removePrefix("gemini-")}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { onSpeak(m.text) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "পড়ে শোনাও",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = m.text,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
