package com.riverrunner.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.*

/** Production-policy audit. No reduced equity samples, forced moves or opponent-card access.
 * RR_LARGE_DIR=/absolute/path RR_LARGE_HANDS=1000000 RR_LARGE_SEED=... RR_LARGE_WORKERS=4
 * ./gradlew :engine:jvmTest --tests '*LargeStarAuditTest.millionHandAudit*'
 * Fresh-stack mode: independently mixed hand seeds; partitioning never changes decisions.
 * RR_LARGE_SESSION_HANDS=200 instead carries stacks/button forward until session end/200 hands.
 * Continuous mode is deterministic for a fixed worker count, not across partitions.
 * Audits use 80% eight-seat and 20% six-seat fresh starts; continuous session survival can alter that mix.
 */
class LargeStarAuditTest {
    private val ids=StarProfiles.all.map { it.id }+"hero"
    private val totalsHeader="hands,vpip,pfr,preflop_shoves,flop_seen,turn_seen,river_seen,showdowns,positive_net,net_chips"
    private val cellsHeader="decisions,fold,check,call,raise,p_fold_sum,p_check_sum,p_call_sum,p_raise_sum,shoves,small_bets,medium_bets,large_bets,pot_bets,overbets,equity_sum,equity_count"
    private class Cell {
        val actual=LongArray(4);val expected=DoubleArray(4);val sizes=LongArray(5)
        var n=0L;var shoves=0L;var equity=0.0;var equityN=0L
        fun add(o:Cell){n+=o.n;shoves+=o.shoves;equity+=o.equity;equityN+=o.equityN;for(i in 0..3){actual[i]+=o.actual[i];expected[i]+=o.expected[i]};for(i in 0..4)sizes[i]+=o.sizes[i]}
        fun csv()=listOf(n,*actual.toTypedArray(),*expected.toTypedArray(),shoves,*sizes.toTypedArray(),equity,equityN).joinToString(",")
    }
    private class Audit {
        val totals=Array(20){LongArray(10)} // player * 2 + table (0=8,1=6)
        val cells=HashMap<String,Cell>()
        var hands=0L;var decisions=0L;var maxSteps=0
        fun add(o:Audit){hands+=o.hands;decisions+=o.decisions;maxSteps=maxOf(maxSteps,o.maxSteps);for(i in totals.indices)for(j in totals[i].indices)totals[i][j]+=o.totals[i][j];o.cells.forEach{(k,v)->cells.getOrPut(k){Cell()}.add(v)}}
    }
    private fun seed(root:Long,index:Int):Long {
        var z=root+index.toLong()*-7046029254386353131L
        z=(z xor (z ushr 30))*-4658895280553007687L
        z=(z xor (z ushr 27))*-7723592293110705685L
        return z xor (z ushr 31)
    }
    private fun hand(index:Int,root:Long,out:Audit,existing:GameSession?=null){
        val size=existing?.config?.tableSize ?: if(index%5==0)6 else 8
        val table=if(size==8)0 else 1
        val s=existing ?: GameSession(SessionConfig(GameMode.CASH,size,100,TableType.STARS,seed=seed(root,index)))
        val profiles=s.players.map{StarProfiles.byId(it.starId)}
        val playerIds=s.players.map{ids.indexOf(it.starId ?: "hero")}
        val e=s.startHand();val chips=e.players.sumOf{it.stack+it.committed}
        val vpip=BooleanArray(size);val pfr=BooleanArray(size)
        var steps=0
        for(p in e.players)out.totals[playerIds[p.seat]*2+table][0]++
        while(e.phase!=Phase.HAND_DONE){
            check(++steps<250){"loop seed=$root index=$index"}
            if(e.phase==Phase.STREET_DONE){
                e.dealNextStreet()
                for(p in e.players)if(!p.folded)out.totals[playerIds[p.seat]*2+table][3+e.street.ordinal]++
                continue
            }
            val seat=e.toAct;val me=e.player(seat);val la=e.legalActions()
            val opponents=e.players.filter{it.seat!=seat && !it.folded}
            val field=when(opponents.size){1->"HU";2->"three_way";else->"four_plus"}
            val ip=opponents.filter{!it.allIn}.all{e.order.indexOf(it.seat)<e.order.indexOf(seat)}
            val street=e.street.ordinal
            val initiative=street>0 && e.actions.lastOrNull{it.street.ordinal==street-1 && it.type.isAggressive}?.seat==seat
            val cls=if(street==0){val pct=HandClasses.percentile(me.hole[0],me.hole[1]);when{pct<.1->0;pct<.25->1;pct<.5->2;pct<.75->3;else->4}} else Bot.handClass(me.hole,e.board)
            val raises=if(street==0)e.actions.count{it.street==Street.PREFLOP && it.type.isAggressive}.coerceAtMost(3) else 0
            val prev=e.actions.lastOrNull{it.seat==seat && it.street==e.street && !it.type.isPost}?.type
            val line=if(street==0)"r${raises}_"+when{e.actions.any{it.seat==seat && it.type.isAggressive}->"raiser";prev==ActionType.CALL->"caller";else->"new"} else if(prev==ActionType.CHECK && la.toCall>0)"check_raise" else "first"
            val odds=la.toCall.toDouble()/(la.pot+la.toCall).coerceAtLeast(1)
            val price=when{la.toCall==0->"free";odds<.20->"low";odds<.33->"mid";else->"high"}
            val depth=if(street==0)when{me.stack+me.streetBet<=3500->"short";me.stack+me.streetBet<15000->"normal";else->"deep"} else when{me.stack.toDouble()/la.pot.coerceAtLeast(1)<1.5->"low_spr";me.stack.toDouble()/la.pot.coerceAtLeast(1)<4->"mid_spr";else->"high_spr"}
            val key=listOf(ids[playerIds[seat]],size,e.street,cls,field,if(ip)"IP" else "OOP",if(initiative)"initiative" else "no_initiative",price,depth,line,if(la.canRaise)"can_raise" else "no_raise").joinToString(",")
            val cell=out.cells.getOrPut(key){Cell()}
            val policy=Bot.decide(e,seat,s.players[seat].style,s.random,personality=profiles[seat])
            var mass=0.0
            for((move,p) in policy.options){
                check(p.isFinite() && p>=0){"invalid probability $root/$index"};mass+=p
                check(when(move.type){MoveType.FOLD->!la.canCheck;MoveType.CHECK->la.canCheck;MoveType.CALL->la.canCall;MoveType.RAISE->la.canRaise && move.raiseTo<=la.maxRaiseTo && (move.raiseTo>=la.minRaiseTo || move.raiseTo==la.maxRaiseTo)}){"illegal option $root/$index $move"}
                cell.expected[move.type.ordinal]+=p
            }
            check(kotlin.math.abs(mass-1)<1e-8){"unnormalized policy $root/$index"}
            cell.n++;out.decisions++
            policy.equity?.let{check(it in 0.0..1.0);cell.equity+=it;cell.equityN++}
            val move=policy.sample(s.random);cell.actual[move.type.ordinal]++
            if(move.type==MoveType.RAISE){
                if(move.raiseTo==la.maxRaiseTo){cell.shoves++;if(street==0)out.totals[playerIds[seat]*2+table][3]++}
                if(street>0 && la.toCall==0)cell.sizes[SizeClass.of(move.raiseTo-la.myStreetBet,la.pot).ordinal]++
            }
            if(street==0){if(move.type==MoveType.CALL || move.type==MoveType.RAISE)vpip[seat]=true;if(move.type==MoveType.RAISE)pfr[seat]=true}
            e.apply(move)
        }
        check(chips==e.players.sumOf{it.stack}){"chip conservation $root/$index"}
        val result=checkNotNull(e.result)
        check(result.net.values.sum()==0){"net conservation $root/$index"}
        for(player in e.players){val i=player.seat;val t=out.totals[playerIds[i]*2+table];if(vpip[i])t[1]++;if(pfr[i])t[2]++;if(i in result.showdown)t[7]++;val net=result.net[i] ?: 0;if(net>0)t[8]++;t[9]+=net}
        if(existing!=null)s.completeHand(e)
        out.hands++;out.maxSteps=maxOf(out.maxSteps,steps)
    }
    private fun atomic(file:File,text:String){val tmp=File(file.path+".tmp");tmp.writeText(text);Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)}
    private fun save(dir:File,name:String,a:Audit,root:Long,elapsed:Double){
        atomic(File(dir,"$name-summary.csv"),"id,table_size,$totalsHeader\n"+a.totals.mapIndexed{i,t->"${ids[i/2]},${if(i%2==0)8 else 6},${t.joinToString(",")}"}.joinToString("\n")+"\n")
        atomic(File(dir,"$name-streets.csv"),"id,table_size,street,hand_class,field,position,initiative,price,depth,line,raise_permission,$cellsHeader\n"+a.cells.toSortedMap().entries.joinToString("\n"){(k,v)->"$k,${v.csv()}"}+"\n")
        atomic(File(dir,"$name-progress.json"),"{\"hands\":${a.hands},\"decisions\":${a.decisions},\"seed\":$root,\"elapsedSeconds\":$elapsed,\"maxSteps\":${a.maxSteps}}\n")
    }
    @Test fun millionHandAudit(){
        val path=System.getenv("RR_LARGE_DIR") ?: return
        val count=System.getenv("RR_LARGE_HANDS")?.toInt() ?: 1000000
        val root=System.getenv("RR_LARGE_SEED")?.toLong() ?: 26112601L
        val workers=(System.getenv("RR_LARGE_WORKERS")?.toInt() ?: 4).coerceIn(1,8)
        require(count>0)
        val dir=File(path);dir.mkdirs();val start=System.nanoTime();val pool=Executors.newFixedThreadPool(workers)
        try{
            val futures=(0 until workers).map{worker->pool.submit<Audit>{
                val a=Audit();var index=worker
                val sessionLimit=System.getenv("RR_LARGE_SESSION_HANDS")?.toInt() ?: 1
                var session:GameSession?=null
                while(index<count){
                    if(sessionLimit>1 && (session==null || session.isOver || session.handNumber>=sessionLimit))
                        session=GameSession(SessionConfig(GameMode.CASH,if(index%5==0)6 else 8,100,TableType.STARS,seed=seed(root,index)))
                    hand(index,root,a,session);index+=workers
                    if(a.hands%25000L==0L)save(dir,"shard-$worker",a,root,(System.nanoTime()-start)/1e9)
                }
                save(dir,"shard-$worker",a,root,(System.nanoTime()-start)/1e9);a
            }}
            val total=Audit();futures.forEach{total.add(it.get())};assertEquals(count.toLong(),total.hands)
            save(dir,"total",total,root,(System.nanoTime()-start)/1e9)
        }finally{pool.shutdownNow()}
    }
    @Test fun seedPartitionAndAccountingAreDeterministic(){
        val a=Audit();val b=Audit();repeat(100){hand(it,17,a)}
        for(partition in 0..3)for(i in partition until 100 step 4)hand(i,17,b)
        assertEquals(a.hands,b.hands);assertEquals(a.decisions,b.decisions)
        assertEquals(a.cells.keys,b.cells.keys)
        for(k in a.cells.keys){assertContentEquals(a.cells.getValue(k).actual,b.cells.getValue(k).actual)}
        for(i in a.totals.indices)assertContentEquals(a.totals[i],b.totals[i])
        assertEquals(0L,a.totals.sumOf{it[9]})
        assertEquals(760L,a.totals.sumOf{it[0]})
    }
}
