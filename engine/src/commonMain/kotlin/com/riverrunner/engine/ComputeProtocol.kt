package com.riverrunner.engine

import kotlinx.serialization.json.*
import kotlin.random.Random

/** Version-local worker transport. Seeds are strings to preserve all 64 bits in JavaScript. */
object ComputeProtocol {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject.also {
        require("error" !in it) { "Background calculation failed" }
    }
    private fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int
    private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
    private fun ints(v: List<Int>) = JsonArray(v.map(::JsonPrimitive))
    private fun JsonElement.ints() = jsonArray.map { it.jsonPrimitive.int }
    private fun reason(r: PolicyReason) = buildJsonObject { put("zh", r.zh); put("en", r.en) }
    private fun readReason(r: JsonElement) = r.jsonObject.let { PolicyReason(it.s("zh"), it.s("en")) }

    fun policyRequest(e: HandEngine, seat: Int, style: BotStyle, seed: Long,
        tournament: TournamentContext? = null, starId: String? = null): String {
        val rec = HandRecord.from(e, if (tournament == null) GameMode.CASH else GameMode.TOURNAMENT,
            0, seat, e.players.size, emptyMap())
        return policyRequest(rec, rec.actions.count { !it.type.isPost }, seat, style, seed, tournament, starId)
    }
    fun policyRequest(rec: HandRecord, actionIndex: Int, seat: Int, style: BotStyle, seed: Long,
        tournament: TournamentContext? = null, starId: String? = null): String = buildJsonObject {
        put("op", "policy"); put("record", rec.encode()); put("index", actionIndex)
        put("seat", seat); put("style", style.code); put("seed", seed.toString())
        starId?.let { put("star", it) }
        tournament?.let { t ->
            put("stacks", buildJsonObject { t.stacks.forEach { (s, n) -> put(s.toString(), n) } })
            put("payouts", JsonArray(t.payouts.map(::JsonPrimitive)))
        }
    }.toString()
    fun equityRequest(holes: List<IntArray>, board: List<Int>, seed: Long, iterations: Int) = buildJsonObject {
        put("op", "equity"); put("holes", JsonArray(holes.map { ints(it.toList()) }))
        put("board", ints(board)); put("seed", seed.toString()); put("iterations", iterations)
    }.toString()
    fun factsRequest(rec: HandRecord) = buildJsonObject { put("op", "facts"); put("record", rec.encode()) }.toString()
    fun readEquity(s: String) = obj(s).getValue("equity").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
    fun readFacts(s: String) = obj(s).getValue("facts").jsonObject.mapValues { it.value.jsonPrimitive.long }

    fun run(request: String): String {
        val q = obj(request)
        return when(q.s("op")) {
            "policy" -> {
                val rec = requireNotNull(HandRecord.decode(q.s("record")))
                val t = q["stacks"]?.let { TournamentContext(it.jsonObject.map { (k,v) -> k.toInt() to v.jsonPrimitive.int }.toMap(),
                    q.getValue("payouts").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()) }
                encodePolicy(Bot.decide(Replayer(rec).engineBeforeAction(q.i("index")), q.i("seat"),
                    BotStyle.fromCode(q.s("style")), Random(q.s("seed").toLong()), t,
                    StarProfiles.byId(q["star"]?.jsonPrimitive?.content)))
            }
            "equity" -> buildJsonObject { put("equity", JsonArray(Equity.showdown(
                q.getValue("holes").jsonArray.map { it.ints().toIntArray() }, q.getValue("board").ints(),
                Random(q.s("seed").toLong()), q.i("iterations")).map(::JsonPrimitive))) }.toString()
            "facts" -> buildJsonObject { put("facts", buildJsonObject {
                Facts.forHand(requireNotNull(HandRecord.decode(q.s("record")))).forEach { (k,v) -> put(k,v) }
            }) }.toString()
            else -> error("Unknown calculation")
        }
    }
    fun encodePolicy(p: Policy) = buildJsonObject {
        put("options", JsonArray(p.options.map { (m,f) -> buildJsonObject {
            put("type",m.type.name); put("raiseTo",m.raiseTo); put("frequency",f)
        } }))
        p.equity?.let { put("equity",it) }; put("potOdds",p.potOdds)
        p.icmRequired?.let { put("icm",it) }; put("pot",p.pot)
        put("reasons",JsonArray(p.reasons.map(::reason))); p.summary?.let { put("summary",reason(it)) }
        p.context?.let { c -> put("context",buildJsonObject {
            put("hole",ints(c.hole)); put("board",ints(c.board)); put("bigBlind",c.bigBlind)
            put("toCall",c.toCall); put("stack",c.stack); put("streetBet",c.streetBet)
        }) }
    }.toString()
    fun readPolicy(s: String): Policy {
        val q = obj(s)
        return Policy(q.getValue("options").jsonArray.map { it.jsonObject.let { o ->
            Move(MoveType.valueOf(o.s("type")),o.i("raiseTo")) to o.getValue("frequency").jsonPrimitive.double
        } }, q["equity"]?.jsonPrimitive?.double, q.getValue("potOdds").jsonPrimitive.double,
            q["icm"]?.jsonPrimitive?.double, q.i("pot"), q.getValue("reasons").jsonArray.map(::readReason),
            q["summary"]?.let(::readReason), q["context"]?.jsonObject?.let { c ->
                PolicyContext(c.getValue("hole").ints(),c.getValue("board").ints(),c.i("bigBlind"),c.i("toCall"),c.i("stack"),c.i("streetBet"))
            })
    }
}
