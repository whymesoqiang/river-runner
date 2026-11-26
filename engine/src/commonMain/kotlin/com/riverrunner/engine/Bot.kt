package com.riverrunner.engine

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Player styles. GTO approximates solver-like play; the others deviate in
 * recognisable, exploitable ways so a "mixed" table resembles a real game.
 */
enum class BotStyle(
    val code: String,
    /** multiplies preflop range widths */
    val looseness: Double,
    /** multiplies value raising / betting frequencies */
    val aggression: Double,
    /** multiplies bluffing frequencies */
    val bluff: Double,
    /** added to effective equity when deciding to call (positive = calls more) */
    val callBias: Double,
    /** extra width multiplier for flat calls preflop */
    val preflopCall: Double,
) {
    GTO("G", 1.0, 1.0, 1.0, 0.0, 1.0),
    TAG("T", 0.85, 1.1, 0.75, -0.02, 0.85),
    LAG("L", 1.35, 1.25, 1.5, 0.01, 1.1),
    STATION("S", 1.5, 0.45, 0.25, 0.12, 2.2),
    NIT("N", 0.6, 0.8, 0.35, -0.06, 0.7),
    MANIAC("M", 1.9, 1.8, 2.3, 0.04, 1.4);

    companion object {
        fun fromCode(c: String): BotStyle = entries.firstOrNull { it.code == c } ?: GTO
    }
}

/** Tournament info a bot may use for ICM decisions. */
class TournamentContext(
    /** stacks (start of this hand) of every player still in the tournament, by seat */
    val stacks: Map<Int, Int>,
    val payouts: DoubleArray,
)

data class PolicyReason(val zh: String, val en: String)

data class PolicyContext(val hole: List<Int>, val board: List<Int>, val bigBlind: Int,
    val toCall: Int, val stack: Int, val streetBet: Int)

/** A mixed strategy over legal moves. */
class Policy(
    val options: List<Pair<Move, Double>>,
    /** estimated equity vs. opponents' ranges, when computed */
    val equity: Double?,
    /** pot odds faced (0 when nothing to call) */
    val potOdds: Double,
    /** required equity under ICM when it applied */
    val icmRequired: Double?,
    /** pot when the decision was made, used to classify bet sizes */
    val pot: Int = 0,
    val reasons: List<PolicyReason> = emptyList(),
    val summary: PolicyReason? = null,
    val context: PolicyContext? = null,
) {
    fun sample(random: Random): Move {
        var r = random.nextDouble()
        for ((m, p) in options) {
            r -= p
            if (r <= 0) return m
        }
        return options.last().first
    }

    fun probability(type: MoveType): Double = options.filter { it.first.type == type }.sumOf { it.second }

    /** The single most likely move. */
    val best: Move get() = options.maxBy { it.second }.first
}

/** A percentile interval [lo, hi) of the starting-hand order describing a range. */
data class RangeEstimate(val lo: Double, val hi: Double)

object Bot {

    // ------------------------------------------------------------------ tables

    /**
     * Raise-first-in width by number of players left to act behind.
     * Based on common solver opening charts (100bb, ~2.5x opens):
     * BTN ~44%, CO ~28%, HJ ~21%, LJ ~17.5%, UTG(7-8 max) ~12-14%. SB raises ~42%.
     */
    fun rfiWidth(behind: Int, headsUp: Boolean): Double {
        if (headsUp) return 0.82
        return when (behind) {
            0 -> 0.0
            1 -> 0.42  // SB vs BB
            2 -> 0.44  // BTN
            3 -> 0.28  // CO
            4 -> 0.21  // HJ
            5 -> 0.175
            6 -> 0.145
            else -> 0.12
        }
    }

    /**
     * Approximate Nash push width (fraction of hands) by effective stack in big
     * blinds and players left to act. Reference points: SB 10bb ~58%,
     * BTN 10bb ~40%, CO 10bb ~29%, UTG(6-max) 10bb ~17%; heads-up SB 10bb ~62%.
     */
    fun pushWidth(effBB: Double, behind: Int): Double {
        val base10 = when (behind) {
            0 -> 0.0
            1 -> 0.58
            2 -> 0.40
            3 -> 0.29
            4 -> 0.22
            5 -> 0.17
            6 -> 0.14
            else -> 0.12
        }
        // widen as stacks get shorter, tighten as they get deeper
        val factor = (10.0 / effBB.coerceAtLeast(1.0)).let { it.pow(0.85) }
        return (base10 * factor).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------------------ helpers

    private fun smooth(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp((x - threshold) / band))   // 1 when x << threshold

    private fun above(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp(-(x - threshold) / band))  // 1 when x >> threshold

    private fun isSuited(h: IntArray) = Cards.suit(h[0]) == Cards.suit(h[1])
    private fun isSuitedWheelAce(h: IntArray): Boolean {
        if (!isSuited(h)) return false
        val r = listOf(Cards.rank(h[0]), Cards.rank(h[1])).sorted()
        return r[1] == 12 && r[0] <= 3
    }

    // ------------------------------------------------------------------ ranges

    /** Estimates every player's preflop range from the public action sequence. */
    fun preflopRanges(engine: HandEngine): Map<Int, RangeEstimate> {
        val n = engine.order.size
        val hu = n == 2
        val ranges = HashMap<Int, RangeEstimate>()
        for (s in engine.order) ranges[s] = RangeEstimate(0.0, 1.0)
        var raises = 0
        var limpers = 0
        var lastRaiserWidth = 1.0
        val bb = engine.bigBlind.toDouble()
        for (a in engine.actions) {
            if (a.street != Street.PREFLOP || a.type.isPost) continue
            val behind = engine.playersBehindPreflop(a.seat)
            val isBB = a.seat == engine.bigBlindSeat
            when (a.type) {
                ActionType.FOLD -> {}
                ActionType.CHECK -> ranges[a.seat] = RangeEstimate(0.06, 1.0)
                ActionType.CALL -> {
                    ranges[a.seat] = when (raises) {
                        0 -> RangeEstimate(0.06, 0.6)
                        1 -> if (isBB) RangeEstimate(min(0.03, lastRaiserWidth * 0.25), min(0.8, lastRaiserWidth * 2.0 + 0.1))
                             else RangeEstimate(lastRaiserWidth * 0.2, min(0.7, lastRaiserWidth * 0.9))
                        2 -> RangeEstimate(0.012, max(0.05, lastRaiserWidth * 1.4))
                        else -> RangeEstimate(0.0, max(0.03, lastRaiserWidth * 1.5))
                    }
                }
                ActionType.BET, ActionType.RAISE -> {
                    val stackBB = (engine.player(a.seat).startStack) / bb
                    val w = when (raises) {
                        0 -> if (a.allIn && stackBB <= 15) pushWidth(stackBB, behind)
                             else if (limpers > 0) rfiWidth(behind, hu) * 0.65
                             else rfiWidth(behind, hu)
                        1 -> if (a.allIn && stackBB <= 20) max(0.06, lastRaiserWidth * 0.5)
                             else max(0.03, lastRaiserWidth * 0.33)
                        2 -> max(0.025, lastRaiserWidth * 0.4)
                        else -> max(0.02, lastRaiserWidth * 0.6)
                    }
                    ranges[a.seat] = RangeEstimate(0.0, w.coerceIn(0.015, 1.0))
                    lastRaiserWidth = w
                    raises++
                }
                else -> {}
            }
            if (a.type == ActionType.CALL && raises == 0) limpers++
        }
        return ranges
    }

    /** Coarse made-hand class: 0 air, 1 draw, 2 weak made, 3 strong one-pair, 4 two pair or better. */
    fun handClass(hole: IntArray, board: List<Int>): Int {
        if (board.size < 3) return 0
        val boardMask = Cards.mask(board.toIntArray())
        val all = boardMask or Cards.mask(hole)
        val heroCat = HandEvaluator.category(HandEvaluator.evaluate(all))
        val boardCat = HandEvaluator.category(HandEvaluator.evaluate(boardMask))
        val boardRanks = board.map { Cards.rank(it) }
        val topBoard = boardRanks.max()
        val h0 = Cards.rank(hole[0]); val h1 = Cards.rank(hole[1])
        if (heroCat > boardCat) {
            when {
                heroCat >= HandEvaluator.STRAIGHT -> return 4
                heroCat == HandEvaluator.TRIPS -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.HIGH_CARD -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.PAIR -> {
                    val pr = if (h0 == h1) h0 else if (h0 in boardRanks) h0 else h1
                    return if (pr >= topBoard) 3 else 2
                }
                heroCat == HandEvaluator.PAIR -> {
                    if (h0 == h1) return if (h0 > topBoard) 3 else 2
                    val pr = if (h0 in boardRanks) h0 else h1
                    val kicker = if (pr == h0) h1 else h0
                    return if (pr == topBoard && (kicker >= 8 || pr >= 11)) 3 else 2
                }
                else -> return 2
            }
        }
        val outs = if (board.size < 5) drawOuts(hole, board) else 0
        return if (outs >= 8 || (outs >= 4 && h0 >= 9 && h1 >= 9)) 1 else 0
    }

    /** Rough count of outs to a flush or straight (only draws using a hole card). */
    fun drawOuts(hole: IntArray, board: List<Int>): Int {
        var outs = 0
        // Rank/suit masks avoid allocating up to 26 temporary sets for every sampled hand.
        var boardRanks = 0
        var ranks = 0
        val holeSuits = (1 shl Cards.suit(hole[0])) or (1 shl Cards.suit(hole[1]))
        var suits = 0 // four 4-bit counters, enough for a seven-card hand
        for (c in board) {
            boardRanks = boardRanks or (1 shl Cards.rank(c))
            suits += 1 shl (Cards.suit(c) * 4)
        }
        ranks = boardRanks or (1 shl Cards.rank(hole[0])) or (1 shl Cards.rank(hole[1]))
        for (c in hole) suits += 1 shl (Cards.suit(c) * 4)
        for (suit in 0..3) if ((suits ushr (suit * 4)) and 15 == 4 && holeSuits and (1 shl suit) != 0) outs += 9
        if (!HandEvaluator.hasStraightRanks(ranks)) {
            var straightRanks = 0
            for (r in 0..12) {
                val bit = 1 shl r
                if (ranks and bit != 0) continue
                if (HandEvaluator.hasStraightRanks(ranks or bit) && !HandEvaluator.hasStraightRanks(boardRanks or bit)) straightRanks++
            }
            outs += min(straightRanks, 2) * 4
        }
        return outs
    }

    /** 0 = very dry, 1 = very wet. */
    fun wetness(board: List<Int>): Double {
        if (board.size < 3) return 0.0
        val suits = IntArray(4)
        board.forEach { suits[Cards.suit(it)]++ }
        val maxSuit = suits.max()
        val ranks = board.map { Cards.rank(it) }.distinct().sorted()
        var connected = 0
        for (i in 1 until ranks.size) if (ranks[i] - ranks[i - 1] <= 2) connected++
        val paired = ranks.size < board.size
        var w = 0.0
        if (maxSuit >= 3) w += 0.45 else if (maxSuit == 2) w += 0.2
        w += connected * 0.18
        if (ranks.size >= 3 && ranks.last() - ranks[ranks.size - 3] <= 4) w += 0.15
        if (paired) w -= 0.2
        if (ranks.last() >= 12 && connected == 0) w -= 0.1
        return w.coerceIn(0.0, 1.0)
    }

    private fun weightFor(cls: Int, action: ActionType?): Double = when (action) {
        ActionType.BET, ActionType.RAISE -> when (cls) { 0 -> 0.3; 1 -> 0.85; 2 -> 0.45; else -> 1.0 }
        ActionType.CALL -> when (cls) { 0 -> 0.12; 1 -> 1.0; 2 -> 0.9; 3 -> 0.9; else -> 0.6 }
        ActionType.CHECK -> when (cls) { 0 -> 1.0; 1 -> 0.9; 2 -> 1.0; 3 -> 0.7; else -> 0.45 }
        else -> 1.0
    }

    /** Builds a sampler for an opponent's hand using preflop range + postflop action weights. */
    private fun samplerFor(engine: HandEngine, seat: Int, range: RangeEstimate): Equity.HandSampler {
        val board = engine.board
        // strongest action per postflop street for this opponent
        val streetActions = HashMap<Street, ActionType>()
        for (a in engine.actions) {
            if (a.seat != seat || a.street == Street.PREFLOP) continue
            val prev = streetActions[a.street]
            if (prev == null || a.type.isAggressive || (prev == ActionType.CHECK && a.type == ActionType.CALL)) streetActions[a.street] = a.type
        }
        val prefixes = streetActions.map { (street, action) -> board.take(street.boardCount) to action }
        // Decision-local, bounded cache: no stale board/action state and no hidden-card access.
        val weights = if (prefixes.isEmpty()) DoubleArray(0) else DoubleArray(52 * 52) { -1.0 }
        return Equity.HandSampler { dead, random ->
            var lastMask = 0L
            for (attempt in 0 until 25) {
                val packed = HandClasses.sample(range.lo, range.hi, dead, random)
                    .let { if (it < 0) HandClasses.sample(0.0, 1.0, dead, random) else it }
                if (packed < 0) return@HandSampler 0L
                val c1 = packed / 64; val c2 = packed % 64
                val mask = (1L shl c1) or (1L shl c2)
                lastMask = mask
                if (streetActions.isEmpty()) return@HandSampler mask
                val key = min(c1, c2) * 52 + max(c1, c2)
                var w = weights[key]
                if (w < 0.0) {
                    w = 1.0
                    val hole = intArrayOf(c1, c2)
                    for ((prefix, act) in prefixes) {
                        if (prefix.size >= 3) w *= weightFor(handClass(hole, prefix), act)
                    }
                    weights[key] = w
                }
                if (random.nextDouble() < w) return@HandSampler mask
            }
            lastMask
        }
    }

    fun equityVsField(engine: HandEngine, seat: Int, random: Random, iters: Int = 300): Double {
        val ranges = preflopRanges(engine)
        val me = engine.player(seat)
        val opps = engine.players.filter { it.seat != seat && !it.folded }
        val samplers = opps.map { samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
        return Equity.vsRanges(me.hole, engine.board, samplers, iters, random)
    }

    // ------------------------------------------------------------------ decision

    /**
     * Decides for [seat] using only information that seat legitimately has:
     * its own hole cards, the board, stacks and the public action history.
     */
    fun decide(
        engine: HandEngine,
        seat: Int,
        style: BotStyle,
        random: Random,
        tournament: TournamentContext? = null,
        personality: StarProfile? = null,
    ): Policy {
        val la = engine.legalActions()
        require(la.seat == seat)
        val traits = personality?.traits ?: BotTraits.from(style)
        val p = if (engine.street == Street.PREFLOP) preflop(engine, seat, traits, la, random, tournament)
        else postflop(engine, seat, traits, la, random, tournament)
        return Policy(p.options, p.equity, p.potOdds, p.icmRequired, la.pot, p.reasons, p.summary,
            PolicyContext(engine.player(seat).hole.toList(), engine.board.toList(), engine.bigBlind,
                la.toCall, engine.player(seat).stack, la.myStreetBet))
    }

    /**
     * Size mix for bets, indexed by [SizeClass] (SMALL..OVER). Value hands lean
     * larger on wet boards; bluffs lean small. The overlap keeps bet size from
     * telling the player what the hand is.
     */
    private val VALUE_SIZES_WET = doubleArrayOf(0.1, 0.2, 0.3, 0.25, 0.15)
    private val VALUE_SIZES_MID = doubleArrayOf(0.15, 0.3, 0.3, 0.2, 0.05)
    private val VALUE_SIZES_DRY = doubleArrayOf(0.25, 0.35, 0.25, 0.12, 0.03)
    private val BLUFF_SIZES = doubleArrayOf(0.4, 0.35, 0.15, 0.1, 0.0)

    private fun betSizeWeights(value: Boolean, wet: Double, street: Street, nOpp: Int): DoubleArray {
        val w = when {
            !value -> BLUFF_SIZES.copyOf()
            wet >= 0.5 -> VALUE_SIZES_WET.copyOf()
            wet >= 0.25 -> VALUE_SIZES_MID.copyOf()
            else -> VALUE_SIZES_DRY.copyOf()
        }
        // no overbets on the flop/turn, and none into several players
        if (street != Street.RIVER || nOpp >= 2) {
            w[3] += w[4]; w[4] = 0.0
        }
        if (nOpp >= 2) {
            w[2] += w[3]; w[3] = 0.0
        }
        return w
    }

    /** Raise multipliers (of the current bet) and their mix, facing a bet. */
    private fun raiseSizes(value: Boolean, street: Street): Pair<DoubleArray, DoubleArray> {
        val mults = if (street == Street.RIVER) doubleArrayOf(2.2, 2.5, 3.0) else doubleArrayOf(2.5, 3.0, 3.5)
        val w = if (value) doubleArrayOf(0.3, 0.4, 0.3) else doubleArrayOf(0.5, 0.35, 0.15)
        return mults to w
    }

    private fun normalized(opts: List<Pair<Move, Double>>): List<Pair<Move, Double>> {
        val merged = LinkedHashMap<Move, Double>()
        for ((m, p) in opts) if (p > 0) merged[m] = (merged[m] ?: 0.0) + p
        val total = merged.values.sum()
        if (total <= 0) return listOf(Move.CHECK to 1.0)
        // drop tiny frequencies to keep play clean
        val cleaned = merged.filter { it.value / total >= 0.03 }
        val t2 = cleaned.values.sum()
        return cleaned.map { it.key to it.value / t2 }
    }

    private fun raiseMove(la: LegalActions, target: Int): Move {
        var t = target.coerceIn(la.minRaiseTo, la.maxRaiseTo)
        // if the raise would leave a tiny stack behind, just shove
        if (la.maxRaiseTo - t < (la.maxRaiseTo - la.myStreetBet) * 0.25) t = la.maxRaiseTo
        return Move.raiseTo(t)
    }

    private fun passiveMove(la: LegalActions): Move = if (la.canCheck) Move.CHECK else Move.FOLD

    /** Equity needed to call: pot odds, or ICM when it is a big tournament decision. */
    private fun requiredEquity(engine: HandEngine, seat: Int, la: LegalActions, tournament: TournamentContext?): Pair<Double, Double?> {
        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        if (tournament == null) return potOdds to null
        val me = engine.player(seat)
        val bigDecision = la.toCall >= me.stack * 0.5 || la.toCall == me.stack
        val live = engine.players.filter { !it.folded && it.seat != seat }
        if (!bigDecision || live.size != 1) return potOdds to null
        val v = live[0]
        val seats = tournament.stacks.keys.sorted()
        val idx = seats.indexOf(seat)
        if (idx < 0) return potOdds to null
        val call = min(la.toCall, me.stack)
        val myTotal = me.committed + call
        val vMatched = min(v.committed, myTotal)
        val dead = engine.players.filter { it.seat != seat && it.seat != v.seat }.sumOf { it.committed }
        fun stacksWith(f: (Int, Int) -> Int): IntArray = IntArray(seats.size) { i ->
            val s = seats[i]
            val ps = engine.players.firstOrNull { it.seat == s }
            val now = ps?.stack ?: tournament.stacks.getValue(s)
            f(s, now)
        }
        val ifFold = stacksWith { s, now -> if (s == v.seat) now + engine.pot else now }
        val ifWin = stacksWith { s, now ->
            when (s) {
                seat -> now - call + myTotal + vMatched + dead
                v.seat -> now + (v.committed - vMatched)
                else -> now
            }
        }
        val ifLose = stacksWith { s, now ->
            when (s) {
                seat -> now - call
                v.seat -> now + myTotal + v.committed + dead
                else -> now
            }
        }
        val req = Icm.requiredEquity(idx, ifFold, ifWin, ifLose, tournament.payouts)
        return max(req, potOdds) to req
    }

    private fun pctText(value: Double) = "${kotlin.math.round(value * 1000) / 10}%"
    private fun number(value: Double) = "${kotlin.math.round(value * 100) / 100}"

    private fun preflop(engine: HandEngine, seat: Int, style: BotTraits, la: LegalActions, random: Random, t: TournamentContext?): Policy {
        val me = engine.player(seat)
        val bb = engine.bigBlind
        val pct = HandClasses.percentile(me.hole[0], me.hole[1])
        val hu = engine.order.size == 2
        val behind = engine.playersBehindPreflop(seat)
        val acts = engine.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
        val raises = acts.count { it.type.isAggressive }
        val callers = acts.count { it.type == ActionType.CALL }
        val othersMax = engine.players.filter { it.seat != seat && !it.folded }.maxOfOrNull { it.stack + it.streetBet } ?: 0
        val effBB = min(me.stack + me.streetBet, othersMax).toDouble() / bb
        val opts = ArrayList<Pair<Move, Double>>()
        val ranges = preflopRanges(engine)
        val iWasRaiser = acts.any { it.seat == seat && it.type.isAggressive }

        val reasons = mutableListOf(PolicyReason(
            "起手牌 ${HandClasses.className(me.hole[0], me.hole[1])} 在引擎排序中位于前 ${pctText(pct)}（不是胜率）；后面还有 $behind 人，有效筹码 ${number(effBB)} BB。",
            "${HandClasses.className(me.hole[0], me.hole[1])} ranks in the top ${pctText(pct)} of this engine's hand ordering (not equity); $behind players remain behind, effective stack ${number(effBB)} BB."
        ))
        fun explain(zh: String, en: String) { reasons += PolicyReason(zh, en) }
        var summary: PolicyReason? = null
        // A short-stack persona changes action selection, not just the displayed label.
        // Only public information and this player's own cards enter this branch.
        if (style.shortStackPushBB > 0 && effBB <= style.shortStackPushBB && la.canRaise) {
            val price = if (raises == 0) 1.0 else (5.0 / (la.currentBet.toDouble() / bb).coerceAtLeast(5.0)).pow(0.5)
            var width = (pushWidth(effBB.coerceAtMost(20.0), behind) * style.looseness * price /
                (1.0 + raises * 0.65)).coerceIn(0.025, 0.65)
            if (raises > 0 && style.shortReshove > 0 && engine.players.none { it.allIn && !it.folded }) {
                val reshovePrice = (9.0 / (la.currentBet.toDouble() / bb).coerceAtLeast(9.0)).pow(.9)
                width = max(width, style.shortReshove * reshovePrice * (if (behind >= 4) .85 else 1.0))
            }
            val push = smooth(pct, width, 0.025)
            opts += Move.raiseTo(la.maxRaiseTo) to push
            val flatWidth = style.shortStackFlat * (1.0 - la.toCall.toDouble() / max(1, me.stack + me.streetBet) / 0.15).coerceIn(0.0, 1.0)
            val flat = if (!la.canCheck && flatWidth > 0 && raises <= 1) smooth(pct, flatWidth, .02) * (1 - push) * .7 else 0.0
            if (flat > 0) opts += Move.CALL to flat
            opts += (if (la.canCheck) Move.CHECK else Move.FOLD) to (1 - push - flat)
            summary = PolicyReason("短码推牌设定：用全下施压，低价时部分跟注，范围外保留筹码。", "Short-stack preset: shove for pressure, mix cheap calls, preserve chips outside the range.")
            explain("有效筹码 ${number(effBB)} BB；推牌范围中心为前 ${pctText(width)}，面对加注收紧。", "Effective stack ${number(effBB)} BB; shove midpoint top ${pctText(width)}, tighter when facing raises.")
            return Policy(normalized(opts), null, la.toCall.toDouble() / (la.pot + la.toCall), null,
                reasons = reasons, summary = summary)
        }
        // ---- big calls (facing a shove or a raise that commits a large part of the stack)
        if (la.toCall > 0 && (la.toCall >= me.stack * 0.4 || engine.players.any { it.allIn && !it.folded && it.seat != seat && it.streetBet >= la.currentBet })) {
            val opps = engine.players.filter { !it.folded && it.seat != seat }
            val samplers = opps.map { samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
            val eq = Equity.vsRanges(me.hole, emptyList(), samplers, 500, random)
            val (req, icm) = requiredEquity(engine, seat, la, t)
            val margin = eq + style.callBias * 0.5 - req
            val pCall = above(margin, 0.0, 0.02)
            summary = PolicyReason(
                if (margin >= 0) "估计胜率达到大额跟注门槛，倾向继续。" else "估计胜率不足以支付大额跟注，倾向弃牌。",
                if (margin >= 0) "Estimated equity meets the large-call threshold, favoring continuation." else "Estimated equity falls below the large-call threshold, favoring a fold.")
            val canJam = la.canRaise && raises <= 3 && pct < 0.03 * style.aggression
            if (canJam) opts += Move.raiseTo(la.maxRaiseTo) to pCall
            else opts += Move.CALL to pCall
            opts += Move.FOLD to (1 - pCall)
            explain("面对大额跟注：对公开行动推测范围的模拟胜率 ${pctText(eq)}，策略使用的跟注门槛 ${pctText(req)}。调整后胜率与门槛差 ${pctText(margin)}；越高越倾向继续，接近门槛时混合行动。",
                "Facing a large call: simulated range equity ${pctText(eq)}, calling threshold ${pctText(req)}. Adjusted equity minus threshold is ${pctText(margin)}; a larger margin favors continuing, with mixed actions near the threshold.")
            if (icm != null) explain("此处计入 ICM 奖金权益；门槛取 ICM 与底池赔率中的较高值。", "ICM prize equity applies here; the threshold is the larger of ICM and pot odds.")
            if (canJam) explain("手牌进入再推范围且允许加注，继续部分改为全下。", "This hand qualifies for the re-shove range and raising is legal, so the continuing portion shoves.")
            return Policy(normalized(opts), eq, la.toCall.toDouble() / (la.pot + la.toCall), icm, reasons = reasons, summary = summary)
        }

        if (raises == 0) {
            // ---- unopened (possibly limped) pot
            val limpers = callers
            if (la.canCheck) {
                // big blind option after limps / folds
                val thr = 0.12 * style.looseness * style.aggression
                explain("可以免费过牌；加注范围中心为前 ${pctText(thr)}，弱牌更多过牌，边缘牌混合。隔离尺度随跛入人数增加。", "Checking is free; the raise-range midpoint is top ${pctText(thr)}. Weaker hands check more, borderline hands mix; isolation sizing grows with limpers.")
                summary = PolicyReason(if (pct < thr) "手牌进入隔离加注范围，可向跛入者施压。" else "手牌偏弱且可以免费过牌，无需扩大底池。",
                    if (pct < thr) "This hand enters the isolation range against limpers." else "A weaker hand can check for free without building the pot.")
                val pR = smooth(pct, thr, 0.02)
                val size = bb * (3 + limpers)
                if (la.canRaise) opts += raiseMove(la, size) to pR
                opts += Move.CHECK to (1 - pR)
                return Policy(normalized(opts), null, 0.0, null, reasons = reasons, summary = summary)
            }
            if (effBB <= 13 && t != null || effBB <= 10) {
                val w = pushWidth(effBB, behind) * (if (hu) 1.07 else 1.0) * style.looseness
                explain("短筹码推/弃分支：推牌范围中心为前 ${pctText(w)}，由有效筹码、后位人数与风格调整；边缘牌混合全下和弃牌。", "Short-stack push/fold: shove-range midpoint top ${pctText(w)}, adjusted for stack depth, players behind and style; marginal hands mix shoves and folds.")
                summary = PolicyReason(if (pct < w) "筹码较浅，手牌进入当前推牌范围。" else "筹码较浅，但手牌未达到当前推牌门槛。",
                    if (pct < w) "At this shallow stack, the hand enters the shove range." else "At this shallow stack, the hand falls outside the shove range.")
                val pJam = smooth(pct, w, 0.012)
                opts += Move.raiseTo(la.maxRaiseTo) to pJam
                opts += Move.FOLD to (1 - pJam)
                return Policy(normalized(opts), null, 0.0, null, reasons = reasons, summary = summary)
            }
            var thr = max(rfiWidth(behind, hu) * style.looseness, style.openFloor)
            if (limpers > 0) thr *= 0.65
            explain("无人加注时，开池范围中心为前 ${pctText(thr)}；位置越靠后通常范围越宽，有跛入时收紧。起手牌越强于该门槛，加注频率越高。", "Unraised pot: opening-range midpoint top ${pctText(thr)}. Later positions usually open wider; limpers tighten the range. Hands stronger than the midpoint raise more often.")
            summary = PolicyReason(if (pct < thr) "手牌达到当前位置的开池门槛，主动加注争取底池。" else "手牌未达到当前位置的开池门槛，避免投入更多筹码。",
                if (pct < thr) "This hand meets the opening threshold for this position." else "This hand falls below the opening threshold for this position.")
            val pRaise = smooth(pct, thr, 0.02)
            val openBB = when {
                hu -> 2.5
                seat == engine.smallBlindSeat -> 3.0
                effBB < 30 -> 2.2
                else -> 2.5
            } + limpers
            // open sizes vary a little so the opening raise is not a fixed tell
            for ((m, w) in listOf(0.9 to 0.3, 1.0 to 0.4, 1.2 to 0.3)) {
                opts += raiseMove(la, (openBB * m * bb).toInt()) to pRaise * w
            }
            // passive styles sometimes limp
            if (style.limp) {
                val pLimp = smooth(pct, thr * 1.5, 0.03) * (1 - pRaise) * 0.6
                opts += Move.CALL to pLimp
                opts += Move.FOLD to (1 - pRaise - pLimp)
            } else {
                opts += Move.FOLD to (1 - pRaise)
            }
            return Policy(normalized(opts), null, 0.0, null, reasons = reasons, summary = summary)
        }

        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        val lastRaise = acts.last { it.type.isAggressive }
        val raiserWidth = ranges.getValue(lastRaise.seat).hi
        val isBB = seat == engine.bigBlindSeat
        val isSB = seat == engine.smallBlindSeat && !hu
        // in position = acts after the raiser postflop
        val ip = engine.order.indexOf(seat) > engine.order.indexOf(lastRaise.seat)
        val callersAfterRaise = acts.dropWhile { it !== lastRaise }.count { it.type == ActionType.CALL }

        explain("已出现 $raises 次加注；最近加注者范围估为前 ${pctText(raiserWidth)}，跟注底池赔率为 ${pctText(potOdds)}。根据位置、加注层级和后续跟注人数调整再加注与跟注范围。", "There have been $raises raises; the last raiser's range is estimated as top ${pctText(raiserWidth)}, with pot odds ${pctText(potOdds)}. Position, raise level and intervening callers adjust the re-raise and calling ranges.")
        when (raises) {
            1 -> {
                var threeBet = max(0.022, raiserWidth * 0.30) * style.aggression
                var callTop = when {
                    isBB -> min(0.75, raiserWidth * 1.9 + 0.08 - (potOdds - 0.25))
                    isSB -> raiserWidth * 0.45
                    hu -> min(0.75, raiserWidth * 0.8)
                    else -> raiserWidth * 0.85
                } * style.preflopCall
                if (isSB) threeBet *= 1.35
                if (callersAfterRaise > 0) { callTop *= 0.8; threeBet *= 1.1 }
                if (!ip && !isBB) callTop *= 0.85
                // Deep cash personas defend a normal live open beyond generic tight chart cutoffs.
                // Discount the extra width for expensive opens, shallow stacks and poor position.
                if (style.defendOpen > 0) {
                    val price = (style.openComfortBB / (lastRaise.streetTotal.toDouble() / bb).coerceAtLeast(style.openComfortBB)).pow(0.9)
                    val depth = (effBB / 80.0).coerceIn(0.0, 1.0)
                    val position = if (ip || isBB) 1.0 else 0.85
                    val width = style.defendOpen * price * depth * position * (if (callersAfterRaise > 0) 0.9 else 1.0)
                    callTop = max(callTop, width)
                    threeBet = max(threeBet, width * 0.13 * style.aggression)
                }
                callTop = callTop.coerceIn(threeBet, 0.85)
                explain("价值 3-bet 范围中心为前 ${pctText(threeBet)}，跟注范围上界中心为前 ${pctText(callTop)}；部分同花边缘牌和同花小 A 会加入诈唬加注。", "Value 3-bet midpoint top ${pctText(threeBet)}, call-range cutoff midpoint top ${pctText(callTop)}; some marginal suited hands and suited wheel aces add bluff raises.")
                val pValue = smooth(pct, threeBet, 0.008)
                var pBluff = 0.0
                if (pct > callTop && pct < callTop + 0.09 && (isSuited(me.hole))) pBluff = 0.35 * style.bluff
                if (isSuitedWheelAce(me.hole) && pct > threeBet) pBluff = max(pBluff, 0.4 * style.bluff)
                val p3 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (smooth(pct, callTop, 0.02) * (1 - p3)).coerceAtLeast(0.0)
                val mult = (if (ip) 3.0 else 3.8) + callersAfterRaise
                if (la.canRaise) opts += raiseMove(la, (lastRaise.streetTotal * mult).toInt()) to p3
                else opts += Move.CALL to p3
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p3 - pCall).coerceAtLeast(0.0)
            }
            2 -> {
                var fourBet: Double
                var callTop: Double
                if (iWasRaiser) {
                    fourBet = max(0.02, raiserWidth * 0.33) * style.aggression
                    callTop = max(0.06, raiserWidth * (if (ip) 1.05 else 0.8)) * style.preflopCall
                } else {
                    fourBet = 0.02 * style.aggression
                    callTop = 0.04 * style.preflopCall
                }
                if (style.defendThreeBet > 0) {
                    val invested = acts.any { it.seat == seat && it.type == ActionType.CALL }
                    val base = when { iWasRaiser -> style.defendThreeBet; invested -> style.defendThreeBet * .85; else -> style.coldThreeBet }
                    val price = (9.0 / (lastRaise.streetTotal.toDouble() / bb).coerceAtLeast(9.0)).pow(.9)
                    val depth = (effBB / 100.0).coerceIn(0.0, 1.0)
                    val position = if (ip) 1.0 else .85
                    val width = base * price * depth * position * (if (callersAfterRaise > 0) .9 else 1.0)
                    callTop = max(callTop, width)
                    // Most added defense is calling; aggression still distinguishes the personas.
                    fourBet = max(fourBet, width * (if (iWasRaiser) .10 else .06) * style.aggression)
                }
                callTop = callTop.coerceIn(fourBet, .85)
                explain("价值 4-bet 范围中心为前 ${pctText(fourBet)}，跟注范围中心为前 ${pctText(callTop)}；是否为原加注者影响防守范围。", "Value 4-bet midpoint top ${pctText(fourBet)}, calling midpoint top ${pctText(callTop)}; being the original raiser changes the defense range.")
                val pValue = smooth(pct, fourBet, 0.006)
                val pBluff = if (iWasRaiser && isSuitedWheelAce(me.hole)) 0.3 * style.bluff else 0.0
                val p4 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (smooth(pct, callTop, 0.012) * (1 - p4))
                val target = (lastRaise.streetTotal * (if (ip) 2.3 else 2.6)).toInt()
                if (la.canRaise) opts += raiseMove(la, target) to p4 else opts += Move.CALL to p4
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p4 - pCall).coerceAtLeast(0.0)
            }
            else -> {
                val jam = 0.022 * style.aggression
                explain("多次再加注后使用窄范围：全下范围中心为前 ${pctText(jam)}，跟注范围中心为前 ${pctText(0.045 * style.preflopCall)}。", "After repeated raises, use narrow ranges: shove midpoint top ${pctText(jam)}, call midpoint top ${pctText(0.045 * style.preflopCall)}.")
                val pJam = smooth(pct, jam, 0.005)
                val pCall = smooth(pct, 0.045 * style.preflopCall, 0.008) * (1 - pJam)
                if (la.canRaise) opts += Move.raiseTo(la.maxRaiseTo) to pJam else opts += Move.CALL to pJam
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - pJam - pCall).coerceAtLeast(0.0)
            }
        }
        val main = opts.groupBy { it.first.type }.maxByOrNull { e -> e.value.sumOf { it.second } }?.key
        summary = when (main) {
            MoveType.FOLD -> PolicyReason("面对加注，手牌主要落在当前位置的继续范围之外。", "Facing a raise, this hand mostly falls outside this position's continuing range.")
            MoveType.RAISE -> PolicyReason("手牌进入再加注范围，以价值牌或部分同花诈唬施压。", "This hand enters the re-raise range as value or a suited bluff.")
            else -> PolicyReason("手牌进入跟注范围，保留筹码而非进一步扩大底池。", "This hand enters the calling range without building a larger pot through a raise.")
        }
        return Policy(normalized(opts), null, potOdds, null, reasons = reasons, summary = summary)
    }

    private fun postflop(engine: HandEngine, seat: Int, style: BotTraits, la: LegalActions, random: Random, t: TournamentContext?): Policy {
        val me = engine.player(seat)
        val board = engine.board
        val street = engine.street
        val opps = engine.players.filter { !it.folded && it.seat != seat }
        val nOpp = opps.size
        val eq = equityVsField(engine, seat, random, if (nOpp >= 3) 220 else 320)
        // 1.0 = a fair share of the pot: 1/(players) equity, scaled to heads-up (0.5 -> 1.0)
        val rel = eq * (nOpp + 1)
        val cls = handClass(me.hole, board)
        val wet = wetness(board)
        val pot = la.pot
        val spr = me.stack.toDouble() / max(1, pot)
        val order = engine.order
        val myIdx = order.indexOf(seat)
        val ip = opps.filter { !it.allIn }.all { order.indexOf(it.seat) < myIdx }
        // initiative: last aggressor on the previous street
        val prevStreet = Street.entries[street.ordinal - 1]
        val prevAggressor = engine.actions.lastOrNull { it.street == prevStreet && it.type.isAggressive }?.seat
        val initiative = prevAggressor == seat
        val opts = ArrayList<Pair<Move, Double>>()
        val multi = if (nOpp >= 2) 0.35 else 1.0
        val reasons = mutableListOf(PolicyReason(
            "面对 $nOpp 名对手，按公开行动估计范围，模拟胜率 ${pctText(eq)}；相对均分底池份额为 ${number(rel)} 倍。SPR ${number(spr)}，${if (ip) "有位置" else "无位置"}，牌面湿度 ${number(wet)}（越高听牌越多）。",
            "Against $nOpp opponents' ranges inferred from public actions, simulated equity is ${pctText(eq)} (${number(rel)} times an equal pot share). SPR ${number(spr)}, ${if (ip) "in position" else "out of position"}, board wetness ${number(wet)} (higher means more draws)."
        ))
        fun explain(zh: String, en: String) { reasons += PolicyReason(zh, en) }
        var summary: PolicyReason? = null

        explain("引擎牌力分类：${listOf("空气牌", "听牌", "弱成牌", "强一对", "两对或更强")[cls]}；${if (initiative) "持有" else "没有"}前街主动权。", "Engine hand class: ${listOf("air", "draw", "weak made hand", "strong one-pair", "two-pair or better")[cls]}; ${if (initiative) "has" else "does not have"} prior-street initiative.")
        if (la.toCall == 0) {
            // ---------------- we can check or bet
            if (!la.canRaise) {
                explain("无需跟注且不能加注，合法行动为过牌。", "No call is required and raising is unavailable; check is the legal action.")
                summary = PolicyReason("无需跟注且不能加注，免费过牌即可。", "No call is needed and raising is unavailable: check for free.")
                return Policy(listOf(Move.CHECK to 1.0), eq, 0.0, null, reasons = reasons, summary = summary)
            }
            val valueThr = when (street) { Street.FLOP -> 1.22; Street.TURN -> 1.3; else -> 1.4 }
            explain("价值下注参考门槛为均分份额的 ${number(valueThr)} 倍；当前${if (rel >= valueThr) "高于" else "低于"}门槛。低于门槛时，听牌、前街主动权和位置决定诈唬频率；多人底池降低诈唬频率，中等成牌偏向控池。",
                "Value-bet reference is ${number(valueThr)} times an equal share; current equity is ${if (rel >= valueThr) "above" else "below"} it. Below it, draws, prior initiative and position set bluff frequency; multiway pots reduce bluffing and medium hands favor pot control.")
            explain("价值下注在湿润牌面偏大、诈唬偏小；低 SPR 且价值足够时直接全下。多个尺寸按概率混合，并受最小加注和剩余筹码限制。", "Value sizes lean larger on wet boards, bluff sizes smaller. Low SPR with enough value shoves; sizes mix probabilistically and are constrained by legal raises and remaining chips.")
            var pBet = above(rel, valueThr, 0.06) * min(1.0, 0.85 * style.aggression + 0.1)
            if (cls == 4 && street == Street.FLOP && wet < 0.3 && nOpp == 1) pBet *= 0.7 // some slow-playing
            val pValue = pBet
            if (rel < valueThr) {
                val bluff: Double = when {
                    cls == 1 && street != Street.RIVER -> 0.5
                    initiative && street == Street.FLOP && nOpp == 1 -> if (wet < 0.3) 0.65 else if (wet < 0.6) 0.45 else 0.3
                    initiative && street == Street.TURN && nOpp == 1 -> if (cls == 0) 0.3 else 0.15
                    initiative && street == Street.RIVER && nOpp == 1 -> if (cls == 0) 0.22 else 0.05
                    !initiative && ip && nOpp == 1 && prevAggressor == null -> 0.35  // stab when checked to
                    else -> 0.06
                }
                // A check to the caller can be a missed c-bet, not only a fully checked prior street.
                // Limit probes to heads-up air/draws; avoid turning showdown hands into random bluffs.
                val probe = if (nOpp == 1 && ip && !initiative && cls <= 1) style.probe * (1.0 - wet * 0.35) else 0.0
                val medium = if (cls == 2 || cls == 3) 0.6 else 1.0  // medium hands prefer pot control
                pBet = max(pBet, max(probe, bluff * style.bluff * multi * medium * (if (street == Street.TURN || street == Street.RIVER) style.lateBarrel else 1.0)))
            }
            val cap = if (style.probe > 0 && cls == 0 && street != Street.FLOP) 0.78 else 0.97
            pBet = pBet.coerceIn(0.0, cap)
            if (spr < 1.3 && rel > valueThr) {
                opts += raiseMove(la, la.maxRaiseTo) to pBet
            } else if (pBet > 0) {
                val vShare = (pValue / max(1e-9, pBet)).coerceIn(0.0, 1.0)
                val wV = betSizeWeights(true, wet, street, nOpp)
                val wB = betSizeWeights(false, wet, street, nOpp)
                for (size in SizeClass.entries) {
                    val i = size.ordinal
                    val w = vShare * wV[i] + (1 - vShare) * wB[i]
                    if (w <= 0) continue
                    val chips = (pot * size.center).toInt().coerceAtLeast(engine.bigBlind)
                    opts += raiseMove(la, chips) to pBet * w
                }
            }
            summary = when {
                pBet < 0.5 -> PolicyReason("当前价值下注优势有限，更多过牌控制底池。", "The current value-betting edge is limited, so checking is more frequent.")
                rel >= valueThr -> PolicyReason("对推测范围有足够胜率优势，下注争取价值。", "The equity edge against inferred ranges supports betting for value.")
                else -> PolicyReason("利用听牌或位置与主动权，以诈唬争取弃牌。", "Draws, position or initiative support a bluff to generate folds.")
            }
            opts += Move.CHECK to (1 - pBet)
            return Policy(normalized(opts), eq, 0.0, null, reasons = reasons, summary = summary)
        }

        // ---------------- facing a bet
        val (req, icm) = requiredEquity(engine, seat, la, t)
        val potOdds = la.toCall.toDouble() / (pot + la.toCall)
        val realization = if (ip) 1.0 else 0.9
        val implied = if (cls == 1 && street != Street.RIVER && spr > 1.5) 0.05 else 0.0
        val eqEff = eq * realization + implied + style.callBias
        explain("跟注需投入 ${la.toCall}，当前底池 $pot，底池赔率 ${pctText(potOdds)}。策略门槛 ${pctText(req)}；位置实现率 ${pctText(realization)}，听牌隐含收益修正 ${pctText(implied)}，调整后胜率 ${pctText(eqEff)}。高于门槛更倾向继续，接近门槛时混合。",
            "Calling costs ${la.toCall} into pot $pot: pot odds ${pctText(potOdds)}, policy threshold ${pctText(req)}. Position realization ${pctText(realization)}, draw implied-equity adjustment ${pctText(implied)}, adjusted equity ${pctText(eqEff)}. Above the threshold favors continuing; near it the strategy mixes.")
        if (icm != null) explain("此处应用 ICM 奖金权益门槛，且不低于底池赔率。", "An ICM prize-equity threshold applies here, floored at pot odds.")
        if (potOdds < 0.22 && cls >= 2) explain("面对小注且有成牌，最小防守保护将加注前的跟注倾向至少提高到 85%。", "A made hand facing a small bet triggers a defense guard: the call propensity before allocating raises is at least 85%.")
        explain("加注结合相对胜率与价值门槛，部分听牌加入半诈唬；多人底池减少诈唬，低 SPR 优先全下，否则混合加注尺寸。", "Raises combine relative equity and a value threshold, with some draws as semi-bluffs. Multiway pots reduce bluffs; low SPR favors shoving, otherwise raise sizes mix.")
        val raiseThr = when (street) { Street.FLOP -> 1.45; Street.TURN -> 1.52; else -> 1.6 }
        explain("价值加注参考门槛为均分份额的 ${number(raiseThr)} 倍；当前相对胜率 ${number(rel)} 倍。", "Value-raise reference is ${number(raiseThr)} times an equal share; current relative equity is ${number(rel)}.")
        var pRaise = 0.0
        var pValueRaise = 0.0
        if (la.canRaise) {
            pRaise = above(rel, raiseThr, 0.05) * min(1.0, 0.65 * style.aggression)
            if (cls == 4 && street == Street.FLOP && wet < 0.3) pRaise *= 0.6
            pValueRaise = pRaise
            val bluffRaise = when {
                cls == 1 && street == Street.FLOP -> 0.12
                cls == 1 && street == Street.TURN -> 0.06
                cls == 0 && street == Street.RIVER -> 0.03
                else -> 0.0
            } * style.bluff * multi
            if (rel < raiseThr) pRaise = max(pRaise, bluffRaise)
        }
        var pCall = above(eqEff - req, 0.0, 0.03)
        // minimum-defence guard vs small bets with real hands (GTO-ish)
        if (potOdds < 0.22 && cls >= 2) pCall = max(pCall, 0.85)
        pCall *= (1 - pRaise)
        if (pRaise > 0) {
            if (spr < 2.0) {
                opts += raiseMove(la, la.maxRaiseTo) to pRaise
            } else {
                val vShare = (pValueRaise / max(1e-9, pRaise)).coerceIn(0.0, 1.0)
                val (mults, wValue) = raiseSizes(true, street)
                val (_, wBluff) = raiseSizes(false, street)
                for (i in mults.indices) {
                    val w = vShare * wValue[i] + (1 - vShare) * wBluff[i]
                    val target = (la.currentBet * mults[i]).toInt().coerceAtLeast(la.minRaiseTo)
                    opts += raiseMove(la, target) to pRaise * w
                }
            }
        }
        opts += Move.CALL to pCall
        opts += Move.FOLD to (1 - pRaise - pCall).coerceAtLeast(0.0)
        summary = when {
            pRaise >= pCall && pRaise >= 1 - pRaise - pCall -> PolicyReason("价值优势或听牌半诈唬支持加注施压。", "Value strength or a drawing semi-bluff supports raising.")
            pCall >= 1 - pRaise - pCall -> PolicyReason("调整后胜率或小注防守规则支持跟注继续。", "Adjusted equity or the small-bet defense rule supports calling.")
            else -> PolicyReason("调整后胜率不足以覆盖跟注门槛，倾向弃牌。", "Adjusted equity falls short of the calling threshold, favoring a fold.")
        }
        return Policy(normalized(opts), eq, potOdds, icm, reasons = reasons, summary = summary)
    }
}
