package com.riverrunner.engine

import java.io.File
import kotlin.random.Random
import kotlin.test.*

class StarReraiseTest {
    private fun probe(star: StarProfile, size: Int, scenario: String, count: Int): DoubleArray {
        val sum=DoubleArray(4)
        repeat(count) { i ->
            val r=Random(81819+i)
            val target=if(scenario=="opener")3 else listOf(5,6,7,0,1,2)[i%6]
            val e=HandEngine(HandSetup(50,100,0,0,(0..7).map { SeatSetup(it,"P$it",if(it==target)(star.minBB+star.maxBB)/2*100 else 30000) }),RandomCardSource(r))
            val weight=if(scenario=="opener")Bot.decide(e,3,star.baseStyle,Random(i),personality=star).probability(MoveType.RAISE) else 1.0
            if(scenario=="open")e.apply(Move.raiseTo(size*100))
            else {e.apply(Move.raiseTo(300));e.apply(Move.raiseTo(size*100))}
            while(e.toAct!=target)e.apply(Move.FOLD)
            val p=Bot.decide(e,target,star.baseStyle,Random(i+7),personality=star)
            for((j,type) in listOf(MoveType.FOLD,MoveType.CALL,MoveType.RAISE).withIndex())sum[j]+=weight*p.probability(type)
            sum[3]+=weight
        }
        return DoubleArray(3){sum[it]/sum[3]}
    }
    @Test fun exportSizeAndRaiseLevelProbes() {
        val path=System.getenv("RR_CALIBRATION_REPORT") ?: return
        val out=StringBuilder("id,scenario,total_bb,samples,fold,call,raise\n")
        for(star in StarProfiles.all)for(scenario in listOf("open","opener","cold"))for(size in listOf(5,8,9,15,30)){
            if(scenario=="opener" && star.traits.shortStackPushBB>0)continue // this persona shoves instead of opening to 3 BB
            val p=probe(star,size,scenario,1000)
            out.append("${star.id},$scenario,$size,1000,${p.joinToString(",")}\n")
        }
        File("$path.reraises").writeText(out.toString())
    }
    @Test fun exportWholeTableFoldThrough() {
        val path=System.getenv("RR_CALIBRATION_REPORT") ?: return
        val out=StringBuilder("total_bb,hands,all_fold\n")
        for(size in listOf(8,9,30)) {
            var hands=0;var folded=0;var attempts=0
            while(hands<1000 && attempts++<20000){
                val r=Random((System.getenv("RR_CALIBRATION_SEED")?.toInt() ?: 919000)+attempts)
                val profiles=StarProfiles.all.shuffled(r).take(7)
                val seats=(0..7).filter{it!=4}.zip(profiles).toMap()
                val e=HandEngine(HandSetup(50,100,0,0,(0..7).map{seat->val s=seats[seat];SeatSetup(seat,s?.name ?: "Hero",if(s==null)10000 else (s.minBB+s.maxBB)/2*100)}),RandomCardSource(r))
                val opener=seats.getValue(3)
                if(opener.traits.shortStackPushBB>0)continue // short-stack shove is not a 3 BB open
                // Condition on the opener actually choosing to raise with this hand.
                val p=Bot.decide(e,3,opener.baseStyle,r,personality=opener)
                if(r.nextDouble()>=p.probability(MoveType.RAISE))continue
                e.apply(Move.raiseTo(300));e.apply(Move.raiseTo(size*100));hands++
                var continued=false
                while(e.phase==Phase.BETTING){
                    val s=seats.getValue(e.toAct)
                    val move=Bot.decide(e,e.toAct,s.baseStyle,r,personality=s).sample(r)
                    if(move.type!=MoveType.FOLD){continued=true;break}
                    e.apply(move)
                }
                if(!continued)folded++
            }
            assertEquals(1000,hands)
            out.append("$size,$hands,$folded\n")
            if(size<=9)assertTrue(folded in 20..250,"normal 3-bet fold-through $folded/1000")
            else assertTrue(folded>300,"large 3-bet must retain fold equity")
        }
        File("$path.table").writeText(out.toString())
    }
    @Test fun eachPersonaDefendsNormalReraisesAndRespectsPrice() {
        for(star in StarProfiles.all) {
            for(size in listOf(8,9)) {
                val cold=probe(star,size,"cold",300)
                assertTrue(cold[0]<.92,"${star.id} cold fold ${cold[0]}")
                if(star.traits.shortStackPushBB==0.0) {
                    val opener=probe(star,size,"opener",300)
                    assertTrue(opener[0]<.6,"${star.id} opener fold ${opener[0]}")
                    assertTrue(opener[0]<cold[0],star.id)
                    assertTrue(probe(star,30,"opener",300)[0]>opener[0]+.15,star.id)
                }
                assertTrue(probe(star,30,"cold",300)[0]>cold[0]+.035,star.id)
            }
        }
    }
    @Test fun priorCallersCanContinueWithPlayableSuitedHands() {
        for(star in StarProfiles.all.filter { it.traits.shortStackPushBB==0.0 }) {
            fun callProbability():Double {
                val e=HandEngine(HandSetup(50,100,0,0,(0..7).map { SeatSetup(it,"P$it",20000) }),
                    PresetCardSource(mapOf(0 to Cards.parseMany("2c3c"),1 to Cards.parseMany("4c5c"),2 to Cards.parseMany("6c7c"),
                        3 to Cards.parseMany("AhAd"),4 to Cards.parseMany("Ks9s"),5 to Cards.parseMany("8h9h"),6 to Cards.parseMany("2d3d"),7 to Cards.parseMany("4d5d")),Cards.parseMany("6h7h8sJcTs")))
                e.apply(Move.raiseTo(300));e.apply(Move.CALL);e.apply(Move.raiseTo(900))
                while(e.toAct!=4)e.apply(Move.FOLD)
                return Bot.decide(e,4,star.baseStyle,Random(1),personality=star).probability(MoveType.CALL)
            }
            assertTrue(callProbability()>.5,star.id)
        }
    }

}
