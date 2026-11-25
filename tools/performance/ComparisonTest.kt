package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.system.measureNanoTime

class ComparisonTest {
    @Test fun identicalPoliciesAcrossSeededGames() {
        var decisions = 0
        repeat(60) { game ->
            val r = Random(game)
            val count = listOf(2, 3, 6, 8)[game % 4]
            val e = HandEngine(HandSetup(50, 100, 0, game % count,
                (0 until count).map { SeatSetup(it, "P$it", if (game % 3 == 0) 800 else 5000) }), RandomCardSource(r))
            val t = if (game % 2 == 0) TournamentContext(e.players.associate { it.seat to it.startStack }, Icm.payoutsFor(count)) else null
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val seed = game * 1000 + decisions
                val style = BotStyle.entries[(game + decisions) % BotStyle.entries.size]
                val old = BaselineBot.decide(e, e.toAct, style, Random(seed), t)
                val current = Bot.decide(e, e.toAct, style, Random(seed), t)
                assertEquals(old.options, current.options, "game=$game decision=$decisions")
                assertEquals(old.equity, current.equity)
                assertEquals(old.icmRequired, current.icmRequired)
                e.apply(current.sample(r))
                decisions++
            }
        }
        println("REGRESSION: $decisions seeded decisions identical to original, including all styles, ICM and 2/3/6/8 seats")
    }

    @Test fun benchmarkTurnRangeSampling() {
        val e = HandEngine(HandSetup(50, 100, 0, 0, (0..2).map { SeatSetup(it, "P$it", 10000) }),
            PresetCardSource(mapOf(0 to Cards.parseMany("AsKs"), 1 to Cards.parseMany("QdQs"), 2 to Cards.parseMany("JhTh")), Cards.parseMany("Qh9h4h8c3d")))
        e.apply(Move.raiseTo(400)); e.apply(Move.CALL); e.apply(Move.CALL)
        e.dealNextStreet()
        e.apply(Move.raiseTo(600)); e.apply(Move.CALL); e.apply(Move.CALL)
        e.dealNextStreet()
        e.apply(Move.CHECK); e.apply(Move.raiseTo(1200))
        fun run(old: Boolean): Long = measureNanoTime {
            repeat(60) { seed ->
                if (old) BaselineBot.decide(e, e.toAct, BotStyle.GTO, Random(seed))
                else Bot.decide(e, e.toAct, BotStyle.GTO, Random(seed))
            }
        }
        repeat(3) { run(true); run(false) }
        val old = mutableListOf<Long>(); val new = mutableListOf<Long>()
        repeat(5) { i -> if (i % 2 == 0) { old += run(true); new += run(false) } else { new += run(false); old += run(true) } }
        val a = old.sorted()[2] / 1e6; val b = new.sorted()[2] / 1e6
        println("BENCHMARK JVM (60 turn decisions, median of 5): baseline=${a}ms optimized=${b}ms ratio=${b/a}")
    }
}
