package com.riverrunner.engine

import kotlin.test.*
import kotlin.random.Random

class LongSessionTest {
    @Test fun incrementalHudMatchesFullHistoryAcrossLongSession() {
        val records = ArrayList<HandRecord>()
        val stats = SeatStatsAccumulator()
        val random = Random(900)
        repeat(2500) { n ->
            val seats = (0 until 2 + n % 7).map { SeatSetup(it, "P$it", 1000 + it * 200) }
            val e = HandEngine(HandSetup(50,100,0,0,seats),RandomCardSource(random))
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val a = e.legalActions()
                val m = when {
                    random.nextInt(5) == 0 && a.canRaise -> Move.raiseTo(a.minRaiseTo)
                    random.nextInt(4) == 0 && !a.canCheck -> Move.FOLD
                    a.canCheck -> Move.CHECK
                    else -> Move.CALL
                }
                e.apply(m)
            }
            val rec = HandRecord.from(e,GameMode.CASH,n,0,seats.size,emptyMap())
            stats.add(rec);records += rec
            if (n < 10 || n % 250 == 249) for (seat in 0..8)
                assertEquals(SeatStatsCalc.forSeat(records,seat),stats.forSeat(seat),"hand $n seat $seat")
        }
        stats.clear()
        for (seat in 0..8) assertEquals(SeatStats(0,0.0,0.0,null),stats.forSeat(seat))
    }
}
