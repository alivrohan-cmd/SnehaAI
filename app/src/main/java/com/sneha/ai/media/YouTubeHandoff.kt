package com.sneha.ai.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.net.URLEncoder

object YouTubeHandoff {
    fun search(ctx: Context, query: String): Pair<Boolean, String> {
        val pm = ctx.packageManager
        val playFromSearch = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ytmPlay = Intent(playFromSearch).setPackage("com.google.android.apps.youtube.music")
        if (ytmPlay.resolveActivity(pm) != null) {
            try {
                ctx.startActivity(ytmPlay)
                return true to "YouTube Music-এ '$query' চালানোর অনুরোধ পাঠানো হয়েছে"
            } catch (_: Exception) {}
        }

        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
        val yt = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.google.android.youtube")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (yt.resolveActivity(pm) != null) {
                ctx.startActivity(yt)
                true to "YouTube অ্যাপে '$query' খোলা হয়েছে"
            } else {
                ctx.startActivity(web)
                true to "ব্রাউজারে YouTube সার্চ খোলা হয়েছে"
            }
        } catch (e: Exception) {
            false to "YouTube খোলা যায়নি: ${e.message}"
        }
    }
}
