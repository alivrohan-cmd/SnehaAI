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

class CommsHelper(private val ctx: Context) {

    fun hasContactsPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasCallPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun hasSmsPerm(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun searchContacts(query: String): List<ContactMatch> {
        if (!hasContactsPerm() || query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val out = mutableListOf<ContactMatch>()
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
                        if (name.lowercase().contains(q) || q.contains(name.lowercase())) {
                            out.add(ContactMatch(name, num.replace(" ", "")))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return out.distinctBy { it.name.lowercase() + "|" + it.number.takeLast(10) }.take(5)
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
                return false to "কন্টাক্টস (READ_CONTACTS) অনুমতি নেই। Settings > পারমিশন সেটআপ থেকে অনুমতি দিন।"
            }
            val matches = searchContacts(raw)
            if (matches.isEmpty()) {
                return false to "'$raw' নামে কোনো কন্টাক্ট নম্বর খুঁজে পাওয়া যায়নি।"
            }
            if (matches.size > 1 && matches.map { it.name.lowercase() }.distinct().size > 1) {
                val names = matches.joinToString(", ") { "${it.name} (${it.number})" }
                return false to "একই নামের একাধিক নম্বর পাওয়া গেছে: $names — নির্দিষ্ট করে বলুন।"
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
                true to "CALL_PHONE অনুমতি না থাকায় ডায়ালারে $resolvedName ($resolvedNumber) খোলা হয়েছে।"
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
            if (directSend && hasSmsPerm()) {
                val smsManager = if (Build.VERSION.SDK_INT >= 31) {
                    ctx.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(resolvedNumber, null, message, null, null)
                true to "$resolvedName ($resolvedNumber)-কে SMS পাঠানো হয়েছে।"
            } else {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(resolvedNumber)))
                    .putExtra("sms_body", message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true to "Messages অ্যাপে $resolvedName-এর জন্য মেসেজ ড্রাফট খোলা হয়েছে।"
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
            val uri = if (cleanDigits.length >= 10) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=" + URLEncoder.encode(message, "UTF-8"))
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(message, "UTF-8"))
            }
            val i = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (i.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(i)
                true to "WhatsApp-এ $displayName-এর চ্যাট মেসেজসহ খোলা হয়েছে।"
            } else {
                val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(fallback)
                true to "WhatsApp মেসেজ ড্রাফট খোলা হয়েছে।"
            }
        } catch (e: Exception) {
            false to "WhatsApp খোলা যায়নি: ${e.message}"
        }
    }
}
