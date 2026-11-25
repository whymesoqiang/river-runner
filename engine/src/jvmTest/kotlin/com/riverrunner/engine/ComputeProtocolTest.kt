package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.*

class ComputeProtocolTest {
    @Test fun workerMatchesDirectDecisionsThroughoutAHand() {
        val rng = Random(934)
        repeat(5) { hand ->
            val e = HandEngine(HandSetup(50,100,0,0,(0..3).map { SeatSetup(it,"P$it",1200 + it * 700) }), RandomCardSource(rng))
            val tournament = if (hand % 2 == 0) TournamentContext(e.players.associate { it.seat to it.startStack }, Icm.payoutsFor(6)) else null
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val seed = Long.MAX_VALUE - e.actions.size
                val star = StarProfiles.all.firstOrNull()
                val expected = Bot.decide(e,e.toAct,BotStyle.LAG,Random(seed),tournament,star)
                val actual = ComputeProtocol.readPolicy(ComputeProtocol.run(ComputeProtocol.policyRequest(e,e.toAct,BotStyle.LAG,seed,tournament,star?.id)))
                assertEquals(expected.options, actual.options)
                assertEquals(expected.equity, actual.equity)
                assertEquals(expected.icmRequired, actual.icmRequired)
                assertEquals(expected.reasons, actual.reasons)
                assertEquals(expected.summary, actual.summary)
                assertEquals(expected.context, actual.context)
                e.apply(if (e.legalActions().canCheck) Move.CHECK else Move.CALL)
            }
            val record = HandRecord.from(e,GameMode.CASH,hand,0,4,emptyMap())
            assertEquals(Facts.forHand(record),ComputeProtocol.readFacts(ComputeProtocol.run(ComputeProtocol.factsRequest(record))))
            val rebuilt = Replayer(record).engineBeforeAction(record.actions.count { !it.type.isPost })
            assertEquals(e.result, rebuilt.result)
            assertEquals(e.board, rebuilt.board)
        }
    }
    @Test fun equityTransportPreservesSeedCardsAndSamples() {
        val holes = listOf(Cards.parseMany("AsKs"),Cards.parseMany("QhQd"),Cards.parseMany("9c9d"))
        val board = Cards.parseMany("JsTs2c").toList()
        val expected = Equity.showdown(holes,board,Random(Long.MIN_VALUE),3000)
        assertContentEquals(expected,ComputeProtocol.readEquity(ComputeProtocol.run(ComputeProtocol.equityRequest(holes,board,Long.MIN_VALUE,3000))))
    }
}
