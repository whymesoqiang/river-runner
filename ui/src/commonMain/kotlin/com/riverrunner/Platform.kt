package com.riverrunner

import androidx.compose.runtime.Composable
import com.riverrunner.audio.Sound
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.abs
import kotlin.math.floor

/**
 * What the shared app needs from the device. The Android app implements it with
 * SharedPreferences, files and a SoundPool; the web app with localStorage and audio elements.
 */
interface Platform {
    suspend fun compute(request: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        com.riverrunner.engine.ComputeProtocol.run(request)
    }

    fun updateStatus(): String? = null
    fun checkUpdates() {}
    fun applyUpdate() {}

    /** Small settings (the Android SharedPreferences file "river_runner"). */
    val settings: Settings
    /** Save slots and hand histories, stored as named text files. */
    val files: TextFiles
    val sound: SoundOut
    /** Version shown in Settings, such as "0.6.41". */
    val appVersion: String
    /** True when the device language is Chinese (the first-start language). */
    val systemChinese: Boolean
    /** CHANGELOG.md of this build, or "" when it is not available. */
    fun changelogText(): String
}

interface Settings {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    /** Only read: version 0.2 stored some career totals as floats. */
    fun getFloat(key: String, default: Float): Float
}

/** Named text files in the app's private storage. */
interface TextFiles {
    fun exists(name: String): Boolean
    fun read(name: String): String?
    /** Replaces the file; on Android through a temporary file, so a crash never leaves half a file. */
    fun write(name: String, text: String)
    fun append(name: String, text: String)
    fun delete(name: String)
    fun rename(from: String, to: String)
}

interface SoundOut {
    var enabled: Boolean
    /** [volume] 0..1; [varyPitch] adds a small random pitch change. */
    fun play(sound: Sound, volume: Float = 1f, varyPitch: Boolean = true)
    fun release() {}
}

/** Dispatcher for file work: Dispatchers.IO on Android; the browser has only one thread. */
expect val IoDispatcher: CoroutineDispatcher

/** Runs [block] holding [lock] on Android (history is read from a background thread); plain call in the browser. */
expect inline fun <T> locked(lock: Any, block: () -> T): T

/** Minutes to add to UTC for the device's local time at [epochMs]. */
expect fun localOffsetMinutes(epochMs: Long): Int

/** Back button / gesture. Android: the system back; web: nothing for now. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Number formatting without java.lang.String.format, which the browser builds do not have. */
object Fmt {
    /** 12345 -> "12,345" */
    fun grouped(n: Long): String {
        val digits = abs(n).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return (if (n < 0) "-" else "") + sb
    }

    fun grouped(n: Int): String = grouped(n.toLong())

    /** [x] with [decimals] digits after the point, rounded half away from zero ("%.1f"). */
    fun fixed(x: Double, decimals: Int): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = floor(abs(x) * scale + 0.5).toLong()
        val whole = scaled / scale
        val frac = scaled % scale
        val neg = x < 0 && scaled != 0L
        val fracText = if (decimals == 0) "" else "." + frac.toString().padStart(decimals, '0')
        return (if (neg) "-" else "") + whole + fracText
    }

    /** Local date of [epochMs]: "yyyy-MM-dd", or "MM-dd HH:mm" with [withTime]. */
    fun date(epochMs: Long, withTime: Boolean = false): String {
        val local = epochMs + localOffsetMinutes(epochMs) * 60_000L
        val days = floorDiv(local, 86_400_000L)
        val msOfDay = local - days * 86_400_000L
        // civil date from days since 1970-01-01 (Howard Hinnant's algorithm)
        val z = days + 719_468
        val era = floorDiv(z, 146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        fun two(v: Long) = v.toString().padStart(2, '0')
        return if (withTime) {
            val minutes = msOfDay / 60_000
            "${two(m)}-${two(d)} ${two(minutes / 60)}:${two(minutes % 60)}"
        } else "$y-${two(m)}-${two(d)}"
    }

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }
}
