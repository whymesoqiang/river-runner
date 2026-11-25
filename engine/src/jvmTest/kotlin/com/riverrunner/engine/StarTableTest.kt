package com.riverrunner.engine

import kotlin.random.Random
import kotlin.test.*

class StarTableTest {
    private fun session(seed: Long, size: Int = 8) = GameSession(SessionConfig(GameMode.CASH, size, 100, TableType.STARS, seed = seed))
    private fun hand(hole: String = "9s6s", stack: Int = 20000, opponent: String = "QdQs"): HandEngine = HandEngine(
        HandSetup(50, 100, 0, 0, listOf(SeatSetup(0,"Hero",stack), SeatSetup(1,"B",30000), SeatSetup(2,"C",30000))),
        PresetCardSource(mapOf(0 to Cards.parseMany(hole), 1 to Cards.parseMany(opponent), 2 to Cards.parseMany("2c7d")), Cards.parseMany("Kh8h4h5c3d")),
    )
    @Test fun seededLineupsAreUniqueVariedAndUsePersonalBuyIns() {
        val seen = mutableSetOf<String>()
        val lineups = mutableSetOf<List<String?>>()
        repeat(30) { seed ->
            val a = session(seed.toLong()); val b = session(seed.toLong())
            assertEquals(a.players.map { it.starId to it.stack }, b.players.map { it.starId to it.stack })
            val bots = a.players.filterNot { it.isHuman }
            assertEquals(bots.size, bots.map { it.starId }.toSet().size)
            assertNull(a.hero.starId); assertEquals(10000, a.hero.stack)
            bots.forEach { p ->
                val profile = assertNotNull(StarProfiles.byId(p.starId))
                assertTrue(p.stack / 100 in profile.minBB..profile.maxBB)
                seen += profile.id
            }
            lineups += bots.map { it.starId }
        }
        assertEquals(9, seen.size); assertTrue(lineups.size > 20)
    }
    @Test fun britneyHasRealShortStackShovesAndStopsWhenDeep() {
        val star = StarProfiles.byId("britney")!!
        val short = hand("AsKs",2500)
        val p = Bot.decide(short,0,BotStyle.GTO,Random(1),personality=star)
        assertTrue(p.options.any { it.first.type == MoveType.RAISE && it.first.raiseTo == 2500 && it.second > .9 })
        assertTrue(p.reasons.any { it.en.contains("shove midpoint") })
        val deep = hand("AsKs",10000)
        val pd = Bot.decide(deep,0,BotStyle.GTO,Random(1),personality=star)
        assertTrue(pd.reasons.none { it.en.contains("shove midpoint") })
    }
    @Test fun personalitiesActuallyChangeActionDistributions() {
        val e=hand()
        fun raising(id:String) = Bot.decide(e,0,BotStyle.GTO,Random(12),personality=StarProfiles.byId(id))
            .options.filter { it.first.type == MoveType.RAISE }.sumOf { it.second }
        assertTrue(raising("texas_mike") > raising("wesley") + .1)
        assertEquals(9, StarProfiles.all.map { it.traits }.toSet().size)
    }
    @Test fun starsNeverReadOpponentHoleCards() {
        for (star in StarProfiles.all) {
            val a=hand("AsKs",20000,"QdQs"); val b=hand("AsKs",20000,"JdJs")
            for(e in listOf(a,b)) { e.apply(Move.raiseTo(300));e.apply(Move.CALL);e.apply(Move.CALL);e.dealNextStreet();e.apply(Move.CHECK);e.apply(Move.CHECK) }
            val pa=Bot.decide(a,0,BotStyle.GTO,Random(40),personality=star)
            val pb=Bot.decide(b,0,BotStyle.GTO,Random(40),personality=star)
            assertEquals(pa.options,pb.options,star.id); assertEquals(pa.equity,pb.equity,star.id)
        }
    }
    @Test fun starHandsRemainLegalAndReplayKeepsIdentityAndStacks() {
        repeat(3) { seed ->
            val s=session(seed.toLong(),9);val e=s.startHand(); val original=s.players.associate { it.seat to it.stack }
            val total=e.players.sumOf { it.stack+it.committed };var steps=0
            while(e.phase != Phase.HAND_DONE && steps++ < 200) {
                if(e.phase == Phase.STREET_DONE) { e.dealNextStreet();continue }
                val player=s.players[e.toAct]
                val policy=Bot.decide(e,e.toAct,player.style,s.random,personality=StarProfiles.byId(player.starId))
                assertEquals(1.0,policy.options.sumOf { it.second },1e-9)
                assertTrue(policy.options.all { it.second >= 0 && it.second.isFinite() })
                e.apply(policy.sample(s.random))
            }
            assertTrue(steps<200);assertEquals(total,e.players.sumOf { it.stack })
            val saved=s.completeHand(e);val loaded=assertNotNull(HandRecord.decode(saved.encode()))
            assertEquals(s.players.associate { it.seat to it.starId },loaded.players.associate { it.seat to it.starId })
            assertEquals(original, loaded.players.associate { it.seat to it.startStack })
            assertEquals(original, Replayer(loaded).frames.first().players.associate { it.seat to it.startStack })
            val legacy=saved.copy(players=saved.players.map { it.copy(starId=null) })
            assertTrue(HandRecord.decode(legacy.encode())!!.players.all { it.starId == null })
        }
    }
    @Test fun tanXuanBarrelsMoreOftenWithAirAndInitiative() {
        val e = hand("JdTs")
        e.apply(Move.raiseTo(300)); e.apply(Move.FOLD); e.apply(Move.CALL)
        e.dealNextStreet(); e.apply(Move.CHECK); e.apply(Move.raiseTo(400)); e.apply(Move.CALL)
        e.dealNextStreet(); e.apply(Move.CHECK)
        fun betChance(star: StarProfile?) = Bot.decide(e, 0, BotStyle.GTO, Random(51), personality = star)
            .options.filter { it.first.type == MoveType.RAISE }.sumOf { it.second }
        assertTrue(betChance(StarProfiles.byId("tan_xuan")) > betChance(null) + .1)
    }
    @Test fun regularTablesKeepEqualStacksAndNoStars() {
        for(type in listOf(TableType.GTO,TableType.MIXED)) {
            val s=GameSession(SessionConfig(GameMode.CASH,6,100,type,seed=42))
            assertTrue(s.players.all { it.starId == null && it.stack == 10000 })
        }
        assertFailsWith<IllegalArgumentException> { GameSession(SessionConfig(GameMode.TOURNAMENT,6,100,TableType.STARS)) }
    }
}
