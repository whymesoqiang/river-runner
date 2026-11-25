package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyExplanationTest {
    private fun hand(opponent: String = "QdQs"): HandEngine = HandEngine(
        HandSetup(50, 100, 0, 0, listOf(SeatSetup(0, "Hero", 5000), SeatSetup(1, "Bot", 5000))),
        PresetCardSource(mapOf(0 to Cards.parseMany("AsKs"), 1 to Cards.parseMany(opponent)), Cards.parseMany("Qh9h4h8c3d")),
    )

    @Test fun explanationsDoNotUseOpponentHoleCards() {
        val a = hand()
        val b = hand("2c7d")
        for (e in listOf(a, b)) {
            e.apply(Move.raiseTo(400)); e.apply(Move.CALL); e.dealNextStreet()
            e.apply(Move.CHECK) // opponent's public action informs the range sampler
        }
        val pa = Bot.decide(a, a.toAct, BotStyle.GTO, Random(42))
        val pb = Bot.decide(b, b.toAct, BotStyle.GTO, Random(42))
        assertEquals(pa.options, pb.options)
        assertEquals(pa.equity, pb.equity)
        assertEquals(pa.reasons, pb.reasons)
        assertEquals(pa.summary, pb.summary)
        assertTrue(pa.reasons.size >= 3)
    }

    @Test fun everyDecisionHasBilingualRationaleAndLegalNormalizedMix() {
        val random = Random(981)
        repeat(15) {
            val e = HandEngine(
                HandSetup(50, 100, 0, 0, (0..2).map { SeatSetup(it, "P$it", 2000) }),
                RandomCardSource(random),
            )
            var steps = 0
            while (e.phase != Phase.HAND_DONE && steps++ < 150) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val p = Bot.decide(e, e.toAct, BotStyle.GTO, random)
                assertTrue(p.reasons.size >= 2)
                assertTrue(p.summary?.zh?.isNotBlank() == true)
                assertTrue(p.summary?.en?.isNotBlank() == true)
                assertTrue(p.reasons.all { it.zh.isNotBlank() && it.en.isNotBlank() })
                assertEquals(1.0, p.options.sumOf { it.second }, 1e-9)
                e.apply(p.sample(random))
            }
            assertTrue(steps < 150)
        }
    }

    @Test fun bigCallExplainsTheThresholdActuallyUsed() {
        val e = hand()
        e.apply(Move.raiseTo(5000))
        val p = Bot.decide(e, e.toAct, BotStyle.GTO, Random(8))
        assertTrue(p.equity != null)
        assertTrue(p.reasons.any { it.en.contains("calling threshold") })
        assertTrue(p.reasons.none { it.en.contains("ICM prize equity") })
    }
    @Test fun tournamentReplayPreservesIcmThreshold() {
        val e = hand()
        e.apply(Move.raiseTo(5000))
        val context = TournamentContext(mapOf(0 to 5000, 1 to 5000), Icm.payoutsFor(6))
        val live = Bot.decide(e, e.toAct, BotStyle.GTO, Random(19), context)
        e.apply(Move.CALL)
        while (e.phase == Phase.STREET_DONE) e.dealNextStreet()
        val record = HandRecord.from(e, GameMode.TOURNAMENT, 1, 1, 6, emptyMap())
        val saved = HandRecord.decode(record.encode())!!
        val before = Replayer(saved).engineBeforeAction(1)
        val replay = Bot.decide(before, before.toAct, BotStyle.GTO, Random(19), saved.tournamentContext())
        assertTrue(live.icmRequired != null)
        assertEquals(live.icmRequired, replay.icmRequired)
        assertEquals(live.options, replay.options)
        assertEquals(live.reasons, replay.reasons)
        assertEquals(null, saved.copy(mode = GameMode.CASH).tournamentContext())
    }

}
