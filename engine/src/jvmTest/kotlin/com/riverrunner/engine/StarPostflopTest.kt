package com.riverrunner.engine

import java.io.File
import kotlinx.serialization.json.*
import kotlin.random.Random
import kotlin.test.*

class StarPostflopTest {
    private fun fixture(street:Street,hole:String="Kc4d",ip:Boolean=true,multi:Boolean=false,bet:Boolean=false,opp:String="AsAd"):HandEngine {
        val target=if(ip)0 else 1
        val holes=(0 until if(multi)3 else 2).associateWith { Cards.parseMany(if(it==target)hole else if(it==2)"5c6c" else opp) }
        val e=HandEngine(HandSetup(50,100,0,0,holes.keys.map{SeatSetup(it,"P$it",30000)}),
            PresetCardSource(holes,Cards.parseMany("9h7h2cQs3d")))
        while(e.street!=street){
            if(e.phase==Phase.STREET_DONE)e.dealNextStreet()
            else e.apply(if(e.legalActions().canCheck)Move.CHECK else Move.CALL)
        }
        if(bet && ip)e.apply(Move.raiseTo(300))
        while(e.toAct!=target)e.apply(if(e.legalActions().canCheck)Move.CHECK else Move.CALL)
        if(bet && !ip){
            e.apply(Move.CHECK)
            e.apply(Move.raiseTo(300))
            while(e.toAct!=target)e.apply(Move.CALL)
        }
        return e
    }
    private fun policy(star:StarProfile,e:HandEngine,seed:Int=31)=Bot.decide(e,e.toAct,star.baseStyle,Random(seed),personality=star)
    @Test fun latePolarizationContainsBluffsAndValueAndRespectsMultiway() {
        for(id in listOf("dwan","tan_xuan","wesley")) {
            val star=StarProfiles.byId(id)!!
            for(street in listOf(Street.TURN,Street.RIVER))for(hole in listOf("Kc4d","9c9d")) {
                val e=fixture(street,hole);val p=policy(star,e)
                val over=p.options.filter{it.first.type==MoveType.RAISE && it.first.raiseTo>e.pot}.sumOf{it.second}
                assertTrue(over>0,"$id $street $hole needs a polarized size")
                val m=fixture(street,hole,multi=true)
                assertTrue(policy(star,m).options.none{it.first.type==MoveType.RAISE && it.first.raiseTo>m.pot},"no multiway overbet")
            }
        }
    }
    @Test fun personaDecisionsDoNotReadOpponentsHoleCards() {
        for(star in StarProfiles.all)for(street in listOf(Street.FLOP,Street.TURN,Street.RIVER)) {
            val a=policy(star,fixture(street,opp="AsAd"));val b=policy(star,fixture(street,opp="5s6s"))
            assertEquals(a.options,b.options,star.id);assertEquals(a.equity,b.equity,star.id)
        }
    }
    @Test fun allPersonasHaveLegalStageAndPositionSpecificPolicies() {
        val export=System.getenv("RR_STAR_AUDIT_PROBES")
        val probes=mutableListOf<String>()
        for(star in StarProfiles.all)for(street in listOf(Street.FLOP,Street.TURN,Street.RIVER))
            for(hole in listOf("Kc4d","JhTh","9c9d"))for(ip in listOf(false,true))for(multi in listOf(false,true))for(bet in listOf(false,true)) {
                val e=fixture(street,hole,ip,multi,bet);val la=e.legalActions();val p=policy(star,e)
                if(export!=null)probes+=buildJsonObject {
                    put("request",Json.parseToJsonElement(ComputeProtocol.policyRequest(e,e.toAct,star.baseStyle,31L,starId=star.id)))
                    put("expected",Json.parseToJsonElement(ComputeProtocol.encodePolicy(p)))
                }.toString()
                assertEquals(1.0,p.options.sumOf{it.second},1e-9)
                assertTrue(p.options.all{(m,w)->w>0 && w.isFinite() && when(m.type){MoveType.CHECK->la.canCheck;MoveType.CALL->la.canCall;MoveType.FOLD->!la.canCheck;MoveType.RAISE->la.canRaise && m.raiseTo in la.minRaiseTo..la.maxRaiseTo}})
            }
        if(export!=null)File(export).writeText(probes.joinToString("\n")+"\n")
    }
    @Test fun lowFrequencyStarBetsSurviveSizeSplitting() {
        // OOP, no initiative, air: previously the small bet mass disappeared in per-size pruning.
        val e=fixture(Street.FLOP,ip=false)
        for(star in StarProfiles.all)assertTrue(policy(star,e).probability(MoveType.RAISE)>0,star.id)
    }
}
