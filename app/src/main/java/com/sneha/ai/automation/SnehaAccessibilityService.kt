package com.sneha.ai.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ব্যবহারকারী Android Accessibility Settings-এ নিজে চালু করলে তবেই কাজ করে।
 * কোনো ইভেন্ট/স্ক্রিন কনটেন্ট নিজে থেকে সংগ্রহ বা পাঠায় না — শুধু অনুমোদিত কাজের সময় স্ক্রিন পড়ে।
 */
class SnehaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: SnehaAccessibilityService? = null
        val connected = MutableStateFlow(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* ইচ্ছাকৃতভাবে খালি: কিছু সংগ্রহ করা হয় না */ }
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null; connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null; connected.value = false
        super.onDestroy()
    }

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    private fun walk(n: AccessibilityNodeInfo?, depth: Int = 0, visit: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (n == null || depth > 40) return false
        if (visit(n)) return true
        for (i in 0 until n.childCount) {
            if (walk(n.getChild(i), depth + 1, visit)) return true
        }
        return false
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c: AccessibilityNodeInfo? = n
        var d = 0
        while (c != null && d < 8) {
            if (c.isClickable) return c
            c = c.parent; d++
        }
        return null
    }

    /** দৃশ্যমান টেক্সট/ডেসক্রিপশন দিয়ে এলিমেন্ট খুঁজে ACTION_CLICK পাঠায়। true = ক্লিক ডিসপ্যাচ হয়েছে (ফলাফল নয়)। */
    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            if (n.isPassword) continue
            val c = clickableAncestor(n) ?: continue
            if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        var done = false
        walk(root) { n ->
            val d = n.contentDescription?.toString()
            if (d != null && d.contains(text, true) && !n.isPassword) {
                val c = clickableAncestor(n)
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { done = true; true } else false
            } else false
        }
        return done
    }

    suspend fun tapXY(x: Int, y: Int): Boolean {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return gesture(p, 60)
    }

    private suspend fun gesture(p: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { c ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, durationMs)).build()
        val dispatched = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, null)
        if (!dispatched && c.isActive) c.resume(false)
    }

    suspend fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var target: AccessibilityNodeInfo? = null
        walk(root) { if (it.isScrollable) { target = it; true } else false }
        val t = target
        if (t != null) {
            return t.performAction(
                if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
        }
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val hi = dm.heightPixels * 0.7f
        val lo = dm.heightPixels * 0.3f
        val p = Path().apply { if (down) { moveTo(x, hi); lineTo(x, lo) } else { moveTo(x, lo); lineTo(x, hi) } }
        return gesture(p, 300)
    }

    /** null = সফল (যাচাইসহ), নইলে ত্রুটির বার্তা */
    fun typeText(text: String): String? {
        val root = rootInActiveWindow ?: return "স্ক্রিন তথ্য পাওয়া যায়নি"
        var node: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        if (node == null) {
            var cand: AccessibilityNodeInfo? = null
            walk(root) { if (it.isEditable && !it.isPassword) { cand = it; true } else false }
            node = cand
            node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val target = node ?: return "লেখার মতো টেক্সট ফিল্ড পাওয়া যায়নি"
        if (target.isPassword) return "পাসওয়ার্ড ফিল্ডে লেখা নিষিদ্ধ"
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "টেক্সট বসানো যায়নি"
        target.refresh()
        return if (target.text?.toString() == text) null else "লেখা বসানোর পর যাচাই মেলেনি"
    }

    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** দৃশ্যমান লেবেলের তালিকা (সর্বোচ্চ ৬০টি)। পাসওয়ার্ড ফিল্ড বাদ। ফোনেই দেখানো হয়, Gemini-তে পাঠানো হয় না। */
    fun readScreen(): List<String> {
        val out = mutableListOf<String>()
        val root = rootInActiveWindow ?: return out
        walk(root) { n ->
            if (!n.isPassword) {
                val l = (n.text ?: n.contentDescription)?.toString()
                if (!l.isNullOrBlank()) out.add((if (n.isClickable) "[tap] " else "") + l.take(80))
            }
            out.size >= 60
        }
        return out
    }
}
