package com.sneha.ai.comms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.net.URLEncoder

data class ContactMatch(val name: String, val number: String)

object Translit {
    private val kinshipAliases = mapOf(
        "বাবা" to listOf("baba", "abbu", "abba", "dad", "father", "papa", "বাবা", "আব্বু", "আব্বা"),
        "baba" to listOf("baba", "abbu", "abba", "dad", "father", "papa", "বাবা", "আব্বু", "আব্বা"),
        "আব্বু" to listOf("abbu", "baba", "abba", "dad", "father", "বাবা", "আব্বু", "আব্বা"),
        "মা" to listOf("ma", "maa", "ammu", "amma", "mom", "mother", "মা", "আম্মু", "আম্মা"),
        "ma" to listOf("ma", "maa", "ammu", "amma", "mom", "mother", "মা", "আম্মু", "আম্মা"),
        "আম্মু" to listOf("ammu", "ma", "maa", "amma", "mom", "mother", "মা", "আম্মু", "আম্মা"),
        "ভাই" to listOf("bhai", "bhaiya", "bro", "brother", "ভাই", "ভাইয়া"),
        "ভাইয়া" to listOf("bhaiya", "bhai", "bro", "brother", "ভাই", "ভাইয়া"),
        "বোন" to listOf("bon", "apu", "apa", "sis", "sister", "বোন", "আপু", "আপা"),
        "আপু" to listOf("apu", "apa", "bon", "sis", "sister", "আপু", "আপা", "বোন"),
        "দাদা" to listOf("dada", "dadu", "grandpa", "দাদা", "দাদু"),
        "নানা" to listOf("nana", "nanu", "নানা", "নানু"),
        "কাকা" to listOf("kaka", "chacha", "uncle", "কাকা", "চাচা"),
        "মামা" to listOf("mama", "uncle", "মামা")
    )

    fun bnToLatin(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            val s = when (ch) {
                'অ' -> "o"; 'আ', 'া' -> "a"; 'ই', 'ঈ', 'ি', 'ী' -> "i"
                'উ', 'ঊ', 'ু', 'ূ' -> "u"; 'এ', 'ে' -> "e"; 'ঐ', 'ৈ' -> "oi"
                'ও', 'ো' -> "o"; 'ঔ', 'ৌ' -> "ou"
                'ক' -> "k"; 'খ' -> "kh"; 'গ' -> "g"; 'ঘ' -> "gh"; 'ঙ', 'ং' -> "ng"
                'চ' -> "ch"; 'ছ' -> "chh"; 'জ', 'য' -> "j"; 'ঝ' -> "jh"; 'ঞ' -> "n"
                'ট', 'ত', 'ৎ' -> "t"; 'ঠ', 'থ' -> "th"; 'ড', 'দ' -> "d"; 'ঢ', 'ধ' -> "dh"
                'ণ', 'ন' -> "n"; 'প' -> "p"; 'ফ' -> "f"; 'ব' -> "b"; 'ভ' -> "bh"; 'ম' -> "m"
                'র', 'ড়', 'ঢ়' -> "r"; 'ল' -> "l"; 'শ', 'ষ', 'স' -> "s"; 'হ' -> "h"
                'য়' -> "y"; '্', 'ঁ', 'ঃ' -> ""
                else -> ch.lowercase()
            }
            sb.append(s)
        }
        return sb.toString().replace(Regex("[^a-z0-9]"), "")
    }

    fun normalizeSkeleton(s: String): String {
        val latin = bnToLatin(s.lowercase())
        return latin.replace("bh", "v")
            .replace("ph", "f")
            .replace("sh", "s")
            .replace("chh", "c")
            .replace("ch", "c")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("dh", "d")
            .replace("th", "t")
            .replace(Regex("[aeiou]+"), "a")
    }

    fun candidateKeys(query: String): List<String> {
        val q = query.trim().lowercase()
        val base = mutableSetOf(q, bnToLatin(q))
        kinshipAliases[q]?.let { base.addAll(it) }
        kinshipAliases[bnToLatin(q)]?.let { base.addAll(it) }
        return base.filter { it.isNotBlank() }
    }

    fun matches(targetQuery: String, candidateLabel: String): Boolean {
        val qKeys = candidateKeys(targetQuery)
        val candLower = candidateLabel.trim().lowercase()
        val candLatin = bnToLatin(candLower)
        val candSkel = normalizeSkeleton(candLower)

        for (k in qKeys) {
            val kLatin = bnToLatin(k)
            val kSkel = normalizeSkeleton(k)
            if (candLower == k || candLower.contains(k) || k.contains(candLower)) return true
            if (kLatin.isNotBlank() && (candLatin == kLatin || candLatin.contains(kLatin) || kLatin.contains(candLatin))) return true
            if (kSkel.length >= 2 && (candSkel == kSkel || candSkel.contains(kSkel))) return true
        }
        return false
    }
}

class CommsHelper(private val ctx: Context) {

    fun hasContactsPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasCallPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun hasSmsPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun searchContacts(query: String): List<ContactMatch> {
        if (!hasContactsPerm() || query.isBlank()) return emptyList()
        val all = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                    val num = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        if (Translit.matches(query, name)) {
                            all.add(ContactMatch(name, num.replace(" ", "").replace("-", "")))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val qLatin = Translit.bnToLatin(query)
        return all.distinctBy { it.name.lowercase() + "|" + it.number.takeLast(10) }
            .sortedByDescending { m ->
                val nLatin = Translit.bnToLatin(m.name)
                when {
                    m.name.equals(query.trim(), true) || nLatin == qLatin -> 3
                    nLatin.startsWith(qLatin) -> 2
                    else -> 1
                }
            }
            .take(5)
    }

    fun placeCall(target: String): Pair<Boolean, String> {
        val raw = target.trim()
        val isDigits = raw.any { it.isDigit() } && raw.count { it.isDigit() } >= 3
        val resolvedName: String
        val resolvedNumber: String

        if (isDigits) {
            resolvedName = raw
            resolvedNumber = raw
        } else {
            if (!hasContactsPerm()) {
                return false to "কন্টাক্টস অনুমতি নেই। Settings > পারমিশন সেটআপ থেকে অনুমতি দিন।"
            }
            val matches = searchContacts(raw)
            if (matches.isEmpty()) {
                return false to "'$raw' (${Translit.bnToLatin(raw)}) নামে কোনো কন্টাক্ট নম্বর খুঁজে পাওয়া যায়নি।"
            }
            resolvedName = matches.first().name
            resolvedNumber = matches.first().number
        }

        val uri = Uri.parse("tel:" + Uri.encode(resolvedNumber))
        return try {
            if (hasCallPerm()) {
                val i = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true to "$resolvedName ($resolvedNumber)-এ সরাসরি কল শুরু করা হয়েছে।"
            } else {
                val i = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true to "ডায়ালারে $resolvedName ($resolvedNumber) খোলা হয়েছে।"
            }
        } catch (e: Exception) {
            false to "কল করা যায়নি: ${e.message}"
        }
    }

    fun sendSms(target: String, message: String, directSend: Boolean): Pair<Boolean, String> {
        val raw = target.trim()
        val isDigits = raw.any { it.isDigit() } && raw.count { it.isDigit() } >= 3
        val resolvedName: String
        val resolvedNumber: String

        if (isDigits) {
            resolvedName = raw
            resolvedNumber = raw
        } else {
            if (!hasContactsPerm()) {
                return false to "কন্টাক্টস অনুমতি নেই। Settings > পারমিশন সেটআপ থেকে অনুমতি দিন।"
            }
            val matches = searchContacts(raw)
            if (matches.isEmpty()) {
                return false to "'$raw' নামে কোনো কন্টাক্ট নম্বর পাওয়া যায়নি।"
            }
            resolvedName = matches.first().name
            resolvedNumber = matches.first().number
        }

        return try {
            if (hasSmsPerm()) {
                val smsManager = if (Build.VERSION.SDK_INT >= 31) {
                    ctx.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(resolvedNumber, null, message, null, null)
                true to "$resolvedName ($resolvedNumber)-কে সরাসরি SMS পাঠানো হয়েছে।"
            } else {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(resolvedNumber)))
                    .putExtra("sms_body", message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true to "Messages অ্যাপে $resolvedName-এর মেসেজ খোলা হয়েছে।"
            }
        } catch (e: Exception) {
            false to "SMS পাঠানো যায়নি: ${e.message}"
        }
    }

    fun openWhatsApp(target: String, message: String): Pair<Boolean, String> {
        val raw = target.trim()
        var number = if (raw.count { it.isDigit() } >= 8) raw else ""
        var displayName = raw

        if (number.isBlank() && hasContactsPerm()) {
            val m = searchContacts(raw).firstOrNull()
            if (m != null) {
                displayName = m.name
                number = m.number
            }
        }

        var cleanDigits = number.filter { it.isDigit() }
        if (cleanDigits.length == 11 && cleanDigits.startsWith("01")) {
            cleanDigits = "88$cleanDigits"
        } else if (cleanDigits.length == 10 && cleanDigits.first() in '6'..'9') {
            cleanDigits = "91$cleanDigits"
        }

        return try {
            if (cleanDigits.length >= 10) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=" + URLEncoder.encode(message, "UTF-8"))
                val i = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
                true to "DIRECT_CHAT:$displayName"
            } else {
                val launch = ctx.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launch != null) {
                    ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true to "SEARCH_IN_WA:$displayName"
                } else {
                    false to "WhatsApp ইনস্টল করা নেই"
                }
            }
        } catch (e: Exception) {
            false to "WhatsApp খোলা যায়নি: ${e.message}"
        }
    }
}
