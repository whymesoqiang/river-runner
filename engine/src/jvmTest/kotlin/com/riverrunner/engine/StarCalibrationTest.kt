package com.riverrunner.engine

import java.io.File
import kotlin.random.Random
import kotlin.test.*

/** Opt-in complete-hand calibration: RR_CALIBRATION_REPORT=/absolute/report.csv, optional RR_CALIBRATION_SEED. */
class StarCalibrationTest {
    private class Stats {
        var hands=0; var vpip=0; var pfr=0; var opens=0; var folds=0; var reraises=0
        var airChecks=0; var airBets=0; var lateChecks=0; var lateBets=0
    }
    @Test fun thousandCompleteHands() {
        val path=System.getenv("RR_CALIBRATION_REPORT") ?: return
        val seed=System.getenv("RR_CALIBRATION_SEED")?.toLong() ?: 602611L
        val stats=StarProfiles.all.associate { it.id to Stats() } + ("hero" to Stats())
        val times=mutableListOf<Long>(); var decisions=0
        val requests=mutableListOf<String>()
        repeat(1000) { hand ->
            val s=GameSession(SessionConfig(GameMode.CASH,8,100,TableType.STARS,seed=seed+hand))
            val e=s.startHand(); val total=e.players.sumOf { it.stack+it.committed }
            val vpip=mutableSetOf<Int>(); val pfr=mutableSetOf<Int>(); val faced=mutableSetOf<Int>()
            s.players.forEach { stats.getValue(it.starId ?: "hero").hands++ }
            var steps=0
            while(e.phase != Phase.HAND_DONE && steps++ < 250) {
                if(e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val seat=e.toAct; val player=s.players[seat]; val st=stats.getValue(player.starId ?: "hero")
                val la=e.legalActions()
                val firstOpen=e.street==Street.PREFLOP && e.actions.count { it.street==Street.PREFLOP && it.type.isAggressive }==1 && la.toCall>0 && faced.add(seat)
                val air=e.street!=Street.PREFLOP && la.toCall==0 && Bot.handClass(e.player(seat).hole,e.board)==0
                if(e.street!=Street.PREFLOP && requests.size<500 && System.getenv("RR_CALIBRATION_REQUESTS")!=null)
                    requests += ComputeProtocol.policyRequest(e,seat,BotStyle.GTO,111L)
                val start=System.nanoTime()
                val p=Bot.decide(e,seat,player.style,s.random,personality=StarProfiles.byId(player.starId))
                times += System.nanoTime()-start; decisions++
                assertEquals(1.0,p.options.sumOf { it.second },1e-8)
                assertTrue(p.options.all { it.second.isFinite() && it.second>=0 })
                val move=p.sample(s.random)
                if(firstOpen) { st.opens++; if(move.type==MoveType.FOLD) st.folds++; if(move.type==MoveType.RAISE) st.reraises++ }
                if(air) { st.airChecks++; if(move.type==MoveType.RAISE) st.airBets++
                    if(e.street==Street.TURN || e.street==Street.RIVER) { st.lateChecks++; if(move.type==MoveType.RAISE) st.lateBets++ } }
                if(e.street==Street.PREFLOP) {
                    if(move.type==MoveType.RAISE || move.type==MoveType.CALL) vpip+=seat
                    if(move.type==MoveType.RAISE) pfr+=seat
                }
                e.apply(move)
            }
            assertEquals(Phase.HAND_DONE,e.phase,"hand $hand")
            assertEquals(total,e.players.sumOf { it.stack },"chip conservation hand $hand")
            vpip.forEach { stats.getValue(s.players[it].starId ?: "hero").vpip++ }
            pfr.forEach { stats.getValue(s.players[it].starId ?: "hero").pfr++ }
        }
        val report=StringBuilder("id,hands,vpip,pfr,first_open_opportunities,fold_to_open,three_bet,air_bet_opportunities,air_bets,late_air_opportunities,late_air_bets\n")
        stats.forEach { (id,s) -> report.append("$id,${s.hands},${s.vpip},${s.pfr},${s.opens},${s.folds},${s.reraises},${s.airChecks},${s.airBets},${s.lateChecks},${s.lateBets}\n") }
        File(path).writeText(report.toString())
        // Broad training acceptance bands; not claims about these players' true frequencies.
        val bands=mapOf("peter" to .45.. .70,"dwan" to .35.. .60,"britney" to .10.. .30,
            "texas_mike" to .50.. .75,"wang_ye" to .28.. .45,"tan_xuan" to .30.. .50,
            "wesley" to .28.. .50,"st_wang" to .18.. .35,"keating" to .48.. .75)
        for((id,band) in bands) {
            val st=stats.getValue(id)
            assertTrue(st.hands>700,id)
            assertTrue(st.vpip.toDouble()/st.hands in band,"$id VPIP ${st.vpip}/${st.hands} outside $band")
        }
        System.getenv("RR_CALIBRATION_REQUESTS")?.let { File(it).writeText(requests.joinToString("\n")) }
        times.sort()
        File("$path.timing").writeText("seed=$seed hands=1000 decisions=$decisions total_ms=${times.sum()/1e6} p50_ms=${times[times.size/2]/1e6} p95_ms=${times[times.size*95/100]/1e6} p99_ms=${times[times.size*99/100]/1e6}\n")
    }
    @Test fun controlledOpenDefense() {
        val path=System.getenv("RR_CALIBRATION_REPORT") ?: return
        val report=StringBuilder("id,open_bb,samples,fold_probability,call_probability,raise_probability\n")
        for(star in StarProfiles.all) for(size in listOf(5,15,40)) {
            val sums=DoubleArray(3)
            repeat(1000) { i ->
                val r=Random(419+i)
                val target=listOf(4,5,6,7,0,1,2)[i%7]
                val stack=(star.minBB+star.maxBB)/2*100
                val e=HandEngine(HandSetup(50,100,0,0,(0..7).map { SeatSetup(it,"P$it",if(it==target) stack else 30000) }),RandomCardSource(r))
                e.apply(Move.raiseTo(size*100))
                while(e.toAct!=target) e.apply(Move.FOLD)
                val p=Bot.decide(e,target,star.baseStyle,r,personality=star)
                sums[0]+=p.probability(MoveType.FOLD);sums[1]+=p.probability(MoveType.CALL);sums[2]+=p.probability(MoveType.RAISE)
            }
            report.append("${star.id},$size,1000,${sums[0]/1000},${sums[1]/1000},${sums[2]/1000}\n")
        }
        File("$path.probes").writeText(report.toString())
    }

    @Test fun exportFullBoardLayoutFixture() {
        val path=System.getenv("RR_LAYOUT_FIXTURE") ?: return
        val s=GameSession(SessionConfig(GameMode.CASH,8,100,TableType.STARS,seed=42))
        val e=s.startHand()
        while(e.phase!=Phase.HAND_DONE) {
            if(e.phase==Phase.STREET_DONE) e.dealNextStreet()
            else e.apply(if(e.legalActions().canCheck)Move.CHECK else Move.CALL)
        }
        assertEquals(5,e.board.size)
        File(path).writeText(s.completeHand(e).encode()+"\n")
    }

}
