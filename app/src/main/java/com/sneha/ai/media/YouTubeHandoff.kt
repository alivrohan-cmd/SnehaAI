package com.sneha.ai.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** অফিশিয়াল YouTube সার্চ লিংক খোলে (অ্যাপ থাকলে অ্যাপে, নইলে ব্রাউজারে)। কোনো বিজ্ঞাপন/সাবস্ক্রিপশন বাইপাস করা হয় না। */
object YouTubeHandoff {
    fun search(ctx: Context, query: String): Pair<Boolean, String> {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
        val yt = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (yt.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(yt); true to "YouTube অ্যাপে সার্চ খোলার অনুরোধ পাঠানো হয়েছে"
            } else {
                ctx.startActivity(web); true to "ব্রাউজারে YouTube সার্চ খোলার অনুরোধ পাঠানো হয়েছে (YouTube অ্যাপ নেই)"
            }
        } catch (e: Exception) {
            false to "YouTube সার্চ খোলা যায়নি: ${e.message}"
        }
    }
}
