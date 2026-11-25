package com.riverrunner.data

import com.riverrunner.Settings
import com.riverrunner.TextFiles
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.Facts
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Progress
import com.riverrunner.engine.StyleMix
import com.riverrunner.engine.TableType
import com.riverrunner.engine.nowMillis
import com.riverrunner.locked
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/** App-wide settings (on Android the SharedPreferences file "river_runner"). Career data lives in save slots, see [ProfileStore]. */
class Prefs(private val sp: Settings, private val systemChinese: Boolean) {

    var chinese: Boolean
        get() = sp.getBoolean("zh", systemChinese)
        set(v) = sp.putBoolean("zh", v)

    var practiceDefault: Boolean
        get() = sp.getBoolean("practice", true)
        set(v) = sp.putBoolean("practice", v)

    /** 0 slow, 1 normal, 2 fast */
    var speed: Int
        get() = sp.getInt("speed", 1)
        set(v) = sp.putInt("speed", v)

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.putBoolean("sound", v)

    var deckStyle: String
        get() = sp.getString("deck", "4")
        set(v) = sp.putString("deck", v)

    /** Newest changelog version the player has already seen in "What's new" ("" = never shown). */
    var seenChangelog: String
        get() = sp.getString("seen_changelog", "")
        set(v) = sp.putString("seen_changelog", v)

    /** After every hand, open its replay before the next deal. */
    var reviewEachHand: Boolean
        get() = sp.getBoolean("review", false)
        set(v) = sp.putBoolean("review", v)

    var showInBB: Boolean
        get() = sp.getBoolean("bb", true)
        set(v) = sp.putBoolean("bb", v)

    // last used setup
    var lastMode: GameMode
        get() = GameMode.fromCode(sp.getString("mode", "C"))
        set(v) = sp.putString("mode", v.code)
    var lastSize: Int
        get() = sp.getInt("size", 6)
        set(v) = sp.putInt("size", v)
    var lastStackBB: Int
        get() = sp.getInt("stackBB", 100)
        set(v) = sp.putInt("stackBB", v)
    var lastTableType: TableType
        get() = when (sp.getString("table", "G")) { "S" -> TableType.STARS; "M" -> TableType.MIXED; else -> TableType.GTO }
        set(v) = sp.putString("table", when (v) { TableType.STARS -> "S"; TableType.MIXED -> "M"; TableType.GTO -> "G" })
    var lastHandsPerLevel: Int
        get() = sp.getInt("hpl", 10)
        set(v) = sp.putInt("hpl", v)
    var lastAnte: Boolean
        get() = sp.getBoolean("ante", false)
        set(v) = sp.putBoolean("ante", v)
    /** Mixed-table line-up: "" = random seats, otherwise "M:" + [StyleMix.encode]. */
    var lastStyleMix: StyleMix?
        get() = sp.getString("mix", "").takeIf { it.startsWith("M:") }?.let { StyleMix.decode(it.removePrefix("M:")) }
        set(v) = sp.putString("mix", if (v == null) "" else "M:" + v.encode())

    /** Table HUD: each bot's VPIP / PFR / AF for this session. */
    var showHud: Boolean
        get() = sp.getBoolean("hud", false)
        set(v) = sp.putBoolean("hud", v)

    /** After each of your decisions, show the GTO mix and whether you deviated. */
    var showDeviation: Boolean
        get() = sp.getBoolean("devfb", false)
        set(v) = sp.putBoolean("devfb", v)

    /** Career totals written by version 0.2 and earlier (read once when migrating to save slots). */
    fun legacyCareer(): Map<String, Long> {
        if (!sp.contains("c_sessions") && !sp.contains("t_count")) return emptyMap()
        return mapOf(
            Progress.CASH_SESSIONS to sp.getInt("c_sessions", 0).toLong(),
            Progress.CASH_PROFIT_CENTI_BB to (sp.getFloat("c_profit", 0f) * 100.0).roundToLong(),
            Progress.TOURNEYS to sp.getInt("t_count", 0).toLong(),
            Progress.TOURNEY_WINS to sp.getInt("t_wins", 0).toLong(),
            Progress.TOURNEY_ITM to sp.getInt("t_itm", 0).toLong(),
            Progress.TOURNEY_POINTS to sp.getFloat("t_profit", 0f).toDouble().roundToLong(),
        )
    }
}

/** One save slot: career counters and unlocked achievements. Hand history is stored next to it. */
data class Profile(
    val id: String,
    val name: String,
    val created: Long,
    /** 0..3, the suit shown on the player card */
    val emblem: Int,
    val counters: Map<String, Long>,
    /** achievement id -> unlock time */
    val unlocked: Map<String, Long>,
) {
    fun counter(key: String): Long = counters[key] ?: 0L

    fun toJson(): String = buildJsonObject {
        put("id", id); put("name", name); put("created", created); put("emblem", emblem)
        put("counters", buildJsonObject { counters.forEach { (k, v) -> put(k, v) } })
        put("unlocked", buildJsonObject { unlocked.forEach { (k, v) -> put(k, v) } })
    }.toString()

    companion object {
        fun fromJson(text: String): Profile {
            val o = Json.parseToJsonElement(text).jsonObject
            fun map(obj: Any?): Map<String, Long> =
                (obj as? JsonObject)?.mapValues { (_, v) -> v.jsonPrimitive.long } ?: emptyMap()
            return Profile(
                o.getValue("id").jsonPrimitive.content, o.getValue("name").jsonPrimitive.content,
                o["created"]?.jsonPrimitive?.longOrNull ?: 0L, o["emblem"]?.jsonPrimitive?.intOrNull ?: 0,
                map(o["counters"]), map(o["unlocked"]),
            )
        }
    }
}

/**
 * Save slots stored as files: profiles.json (index), profile_<id>.json, hands_<id>.txt.
 * The first launch after upgrading moves the old single history into the first slot.
 */
class ProfileStore(private val files: TextFiles, private val prefs: Prefs?, private val defaultName: (Int) -> String) {
    companion object { const val MAX_SLOTS = 6 }

    private val indexFile = "profiles.json"
    private val profiles = LinkedHashMap<String, Profile>()
    var activeId: String = ""; private set

    init {
        files.read(indexFile)?.let { text ->
            runCatching {
                val idx = Json.parseToJsonElement(text).jsonObject
                for (e in idx.getValue("ids").jsonArray) {
                    val id = e.jsonPrimitive.content
                    files.read(profileFile(id))?.let { profiles[id] = Profile.fromJson(it) }
                }
                activeId = (idx["active"] as? JsonPrimitive)?.content ?: ""
            }
        }
        if (profiles.isEmpty()) migrateOrCreate()
        if (activeId !in profiles) activeId = profiles.keys.first()
        writeIndex()
    }

    private fun profileFile(id: String) = "profile_$id.json"
    fun historyFile(id: String) = "hands_$id.txt"

    fun all(): List<Profile> = profiles.values.toList()
    fun active(): Profile = profiles.getValue(activeId)
    fun get(id: String): Profile? = profiles[id]

    private fun newId(): String = "p" + nowMillis().toString(36) + (100..999).random()

    private fun migrateOrCreate() {
        val p = create(defaultName(1), activate = true)
        val legacyHands = "hands.txt"
        val counters = HashMap<String, Long>()
        if (prefs != null) Progress.apply(counters, prefs.legacyCareer())
        files.read(legacyHands)?.let { text ->
            text.lineSequence().forEach { line ->
                HandRecord.decode(line)?.let { r -> runCatching { Progress.apply(counters, Facts.forHand(r)) } }
            }
            files.rename(legacyHands, historyFile(p.id))
        }
        if (counters.isNotEmpty()) {
            val now = nowMillis()
            val unlocked = Achievements.newlyUnlocked(counters, emptySet()).associate { it.id to now }
            save(p.copy(counters = counters, unlocked = unlocked))
        }
    }

    fun create(name: String, activate: Boolean = false): Profile {
        val used = profiles.values.map { it.emblem }.toSet()
        val emblem = (0..3).firstOrNull { it !in used } ?: (profiles.size % 4)
        val p = Profile(newId(), name, nowMillis(), emblem, emptyMap(), emptyMap())
        profiles[p.id] = p
        save(p)
        if (activate) activeId = p.id
        writeIndex()
        return p
    }

    fun save(p: Profile) {
        profiles[p.id] = p
        files.write(profileFile(p.id), p.toJson())
    }

    fun rename(id: String, name: String) {
        profiles[id]?.let { save(it.copy(name = name)) }
    }

    fun activate(id: String) {
        if (id in profiles) { activeId = id; writeIndex() }
    }

    /** Deletes a slot and its files. Deleting the last slot leaves a fresh, empty one. */
    fun delete(id: String) {
        profiles.remove(id) ?: return
        files.delete(profileFile(id))
        files.delete(historyFile(id))
        if (profiles.isEmpty()) create(defaultName(1), activate = true)
        if (activeId == id) activeId = profiles.keys.first()
        writeIndex()
    }

    fun nextDefaultName(): String {
        var i = profiles.size + 1
        while (profiles.values.any { it.name == defaultName(i) }) i++
        return defaultName(i)
    }

    private fun writeIndex() {
        val o = buildJsonObject {
            put("active", activeId)
            put("ids", buildJsonArray { profiles.keys.forEach { add(JsonPrimitive(it)) } })
        }
        files.write(indexFile, o.toString())
    }
}

/**
 * Hand histories of one save slot, one encoded line per hand (~400 bytes).
 * Keeps the newest [LIMIT] hands plus every starred hand.
 */
class HistoryStore(private val files: TextFiles, private val file: String) {
    private val records = ArrayList<HandRecord>()
    private val lock = Any()
    // Records are immutable. Encode each hand once, rather than encoding the entire
    // archive again on every hand after the retention limit is reached.
    private val encoded = ArrayList<String>()
    private var nonFavorites = 0

    companion object { const val LIMIT = 1000 }

    init {
        files.read(file)?.lineSequence()?.forEach { line -> HandRecord.decode(line)?.let { records += it; encoded += line + "\n"; if (!it.favorite) nonFavorites++ } }
    }

    fun all(): List<HandRecord> = locked(lock) { records.toList() }

    fun add(r: HandRecord): Unit = locked(lock) {
        records += r
        encoded += r.encode() + "\n"
        if (!r.favorite) nonFavorites++
        val nonFav = nonFavorites
        if (nonFav > LIMIT) {
            var drop = nonFav - LIMIT
            val iter = records.iterator()
            var index = 0
            while (iter.hasNext() && drop > 0) {
                val old = iter.next()
                if (!old.favorite) { encoded.removeAt(index); iter.remove(); drop--; nonFavorites-- } else index++
            }
            rewrite()
        } else {
            files.append(file, encoded.last())
        }
    }

    fun toggleFavorite(id: Long): HandRecord? = locked(lock) {
        val i = records.indexOfFirst { it.id == id }
        if (i < 0) return@locked null
        val updated = records[i].copy(favorite = !records[i].favorite)
        records[i] = updated
        nonFavorites += if (updated.favorite) -1 else 1
        encoded[i] = updated.encode() + "\n"
        rewrite()
        updated
    }

    fun setFavorite(id: Long, value: Boolean): HandRecord? = locked(lock) {
        val i = records.indexOfFirst { it.id == id }
        if (i < 0) return@locked null
        if (records[i].favorite == value) return@locked records[i]
        records[i] = records[i].copy(favorite = value)
        nonFavorites += if (value) -1 else 1
        encoded[i] = records[i].encode() + "\n"
        rewrite()
        records[i]
    }

    fun clearNonFavorites(): Unit = locked(lock) {
        for (i in records.indices.reversed()) if (!records[i].favorite) { records.removeAt(i); encoded.removeAt(i) }
        nonFavorites = 0
        rewrite()
    }

    fun count(): Int = locked(lock) { records.size }

    private fun rewrite() {
        files.write(file, encoded.joinToString(""))
    }
}
