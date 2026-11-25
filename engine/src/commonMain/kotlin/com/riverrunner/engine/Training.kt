package com.riverrunner.engine

import kotlin.random.Random

/**
 * Who sits at a mixed table: how many bots play each non-GTO style.
 * [randomSpecial] bots get a non-GTO style drawn at random; every other bot seat plays GTO.
 */
data class StyleMix(val counts: Map<BotStyle, Int> = emptyMap(), val randomSpecial: Int = 0) {
    fun count(s: BotStyle): Int = counts[s] ?: 0

    /** Bots that deviate from GTO. */
    val total: Int get() = SPECIAL.sumOf { count(it) } + randomSpecial

    /** Trims the mix to [botSeats] seats, keeping styles in [SPECIAL] order, random ones last. */
    fun fit(botSeats: Int): StyleMix {
        var room = botSeats
        val out = LinkedHashMap<BotStyle, Int>()
        for (s in SPECIAL) {
            val k = minOf(count(s), room)
            if (k > 0) { out[s] = k; room -= k }
        }
        return StyleMix(out, minOf(randomSpecial, room))
    }

    /** Styles for [botSeats] bot seats, in random seat order. */
    fun assign(botSeats: Int, random: Random): List<BotStyle> {
        val m = fit(botSeats)
        val list = ArrayList<BotStyle>(botSeats)
        for (s in SPECIAL) repeat(m.count(s)) { list += s }
        repeat(m.randomSpecial) { list += SPECIAL[random.nextInt(SPECIAL.size)] }
        while (list.size < botSeats) list += BotStyle.GTO
        list.shuffle(random)
        return list
    }

    /** Compact text for settings, e.g. "S3,R1". */
    fun encode(): String {
        val parts = SPECIAL.filter { count(it) > 0 }.map { "${it.code}${count(it)}" }.toMutableList()
        if (randomSpecial > 0) parts += "R$randomSpecial"
        return parts.joinToString(",")
    }

    companion object {
        /** The non-GTO styles, in display order. */
        val SPECIAL = listOf(BotStyle.TAG, BotStyle.LAG, BotStyle.STATION, BotStyle.NIT, BotStyle.MANIAC)

        fun decode(text: String): StyleMix {
            val counts = LinkedHashMap<BotStyle, Int>()
            var random = 0
            for (tok in text.split(',')) {
                val t = tok.trim()
                if (t.length < 2) continue
                val n = t.substring(1).toIntOrNull()?.coerceAtLeast(0) ?: continue
                if (t[0] == 'R') random = n
                else BotStyle.entries.firstOrNull { it.code == t.substring(0, 1) && it != BotStyle.GTO }?.let { counts[it] = n }
            }
            return StyleMix(counts, random)
        }
    }
}

/** Ready-made table line-ups. RANDOM is the original mixed table: every bot seat draws any style, GTO included. */
enum class TablePreset(val mix: StyleMix?) {
    RANDOM(null),
    FISH(StyleMix(mapOf(BotStyle.STATION to 3))),
    AGGRO(StyleMix(mapOf(BotStyle.LAG to 2, BotStyle.MANIAC to 1))),
    TIGHT(StyleMix(mapOf(BotStyle.NIT to 2, BotStyle.TAG to 1))),
}

/** What a HUD shows for one player. */
data class SeatStats(
    val hands: Int,
    /** voluntarily put money in the pot preflop */
    val vpip: Double,
    /** raised preflop */
    val pfr: Double,
    /** postflop aggression factor: (bets + raises) / calls; null before any postflop action */
    val af: Double?,
)

/** One session's HUD; keeps counters rather than retaining and rescanning all hands. */
class SeatStatsAccumulator {
    private class Counts {
        var hands = 0; var vpip = 0; var pfr = 0; var aggression = 0; var calls = 0
        fun snapshot() = SeatStats(hands, if (hands == 0) 0.0 else vpip.toDouble() / hands,
            if (hands == 0) 0.0 else pfr.toDouble() / hands,
            if (aggression == 0 && calls == 0) null else if (calls == 0) aggression.toDouble() else aggression.toDouble() / calls)
    }
    private val seats = mutableMapOf<Int, Counts>()
    fun clear() = seats.clear()
    fun add(record: HandRecord) {
        for (p in record.players) {
            val c = seats.getOrPut(p.seat) { Counts() }
            c.hands++
            var voluntary = false; var raised = false
            for (a in record.actions) {
                if (a.seat != p.seat || a.type.isPost) continue
                if (a.street == Street.PREFLOP) {
                    if (a.type == ActionType.CALL || a.type.isAggressive) voluntary = true
                    if (a.type.isAggressive) raised = true
                } else if (a.type.isAggressive) c.aggression++ else if (a.type == ActionType.CALL) c.calls++
            }
            if (voluntary) c.vpip++
            if (raised) c.pfr++
        }
    }
    fun forSeat(seat: Int): SeatStats = seats[seat]?.snapshot() ?: SeatStats(0, 0.0, 0.0, null)
}

object SeatStatsCalc {
    /** HUD numbers for [seat] over the hands it was dealt into. */
    fun forSeat(records: List<HandRecord>, seat: Int): SeatStats {
        var hands = 0; var vpip = 0; var pfr = 0; var aggr = 0; var calls = 0
        for (r in records) {
            if (r.players.none { it.seat == seat }) continue
            hands++
            val mine = r.actions.filter { it.seat == seat && !it.type.isPost }
            val pre = mine.filter { it.street == Street.PREFLOP }
            if (pre.any { it.type == ActionType.CALL || it.type.isAggressive }) vpip++
            if (pre.any { it.type.isAggressive }) pfr++
            for (a in mine) if (a.street != Street.PREFLOP) {
                if (a.type.isAggressive) aggr++ else if (a.type == ActionType.CALL) calls++
            }
        }
        fun pct(a: Int) = if (hands == 0) 0.0 else a.toDouble() / hands
        val af = when {
            aggr == 0 && calls == 0 -> null
            calls == 0 -> aggr.toDouble()
            else -> aggr.toDouble() / calls
        }
        return SeatStats(hands, pct(vpip), pct(pfr), af)
    }
}

/**
 * Whether a decision follows the GTO policy. An action is a deviation when the policy takes it
 * less than [ACTION_MIN] of the time; a bet or raise of a size class the policy uses less than
 * [SIZE_MIN] of the time is a sizing deviation. Replay and live feedback share these rules.
 */
object Deviation {
    const val ACTION_MIN = 0.15
    const val SIZE_MIN = 0.08

    enum class Verdict { OK, ACTION, SIZE }

    fun moveType(t: ActionType): MoveType = when (t) {
        ActionType.FOLD -> MoveType.FOLD
        ActionType.CHECK -> MoveType.CHECK
        ActionType.CALL -> MoveType.CALL
        else -> MoveType.RAISE
    }

    fun actionMix(p: Policy): Map<MoveType, Double> =
        p.options.groupBy { it.first.type }.mapValues { e -> e.value.sumOf { it.second } }

    fun sizeMix(p: Policy): Map<SizeClass, Double> =
        p.options.filter { it.first.type == MoveType.RAISE }
            .groupBy { SizeClass.of(it.first.raiseTo, p.pot) }
            .mapValues { e -> e.value.sumOf { it.second } }

    fun judge(p: Policy, a: Action): Verdict {
        val t = moveType(a.type)
        if ((actionMix(p)[t] ?: 0.0) < ACTION_MIN) return Verdict.ACTION
        if (t == MoveType.RAISE && (sizeMix(p)[SizeClass.of(a.streetTotal, p.pot)] ?: 0.0) < SIZE_MIN) return Verdict.SIZE
        return Verdict.OK
    }
}

/** Running count of the hero's decisions and deviations in one session. Pairs are (decisions, deviations). */
data class DeviationTally(
    val decisions: Int = 0,
    val deviations: Int = 0,
    val byStreet: Map<Street, Pair<Int, Int>> = emptyMap(),
    val byPosition: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    val rate: Double get() = if (decisions == 0) 0.0 else deviations.toDouble() / decisions

    fun add(street: Street, position: String, verdict: Deviation.Verdict): DeviationTally {
        val off = if (verdict == Deviation.Verdict.OK) 0 else 1
        fun <K> bump(m: Map<K, Pair<Int, Int>>, k: K): Map<K, Pair<Int, Int>> {
            val (d, o) = m[k] ?: (0 to 0)
            return m + (k to (d + 1 to o + off))
        }
        return DeviationTally(decisions + 1, deviations + off, bump(byStreet, street), bump(byPosition, position))
    }
}
