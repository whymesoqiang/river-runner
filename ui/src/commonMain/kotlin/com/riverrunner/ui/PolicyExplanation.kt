package com.riverrunner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Policy
import com.riverrunner.i18n.LocalStrings

/** Explanations use the same decision snapshot; opening this never runs another simulation. */
@Composable
internal fun PolicyExplanation(policy: Policy) {
    val st = LocalStrings.current
    val zh = st.isZh
    var open by remember(policy) { mutableStateOf(false) }
    var details by remember(policy) { mutableStateOf(false) }
    SmallButton(if (zh) "解答" else "Why?", { open = true })
    if (open) AlertDialog(
        modifier = Modifier.fillMaxWidth(0.82f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { open = false },
        title = { Text(if (zh) "策略依据（近似 GTO）" else "Strategy rationale (approximate GTO)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(policyMix(policy, st), color = Palette.Accent)
                policy.summary?.let { Text(if (zh) it.zh else it.en) }
                BeginnerExplanation(policy)
                val sizes = com.riverrunner.engine.Deviation.sizeMix(policy)
                if (sizes.isNotEmpty()) Text(st.gtoSizing + ": " + sizes.entries.joinToString(" · ") {
                    "${sizeName(st, it.key)} ${Format.pct(it.value)}"
                })
                policy.equity?.let { Text("${st.estEquity}: ${Format.pct(it)}") }
                if (policy.potOdds > 0) Text("${st.needEquity}: ${Format.pct(policy.potOdds)}")
                TextButton(onClick = { details = !details }) {
                    Text(if (zh) (if (details) "收起计算依据" else "展开计算依据与参数") else "Calculation details")
                }
                if (details) policy.reasons.forEach { Text(if (zh) it.zh else it.en, fontSize = 16.sp) }
                if (policy.reasons.isEmpty()) Text(if (zh) "此条建议没有保存详细依据。" else "No detailed rationale was saved for this suggestion.")
                Text(if (zh)
                    "以上解释来自本次决策的实际规则与数值。模拟胜率针对推测的对手范围，不使用对手底牌，也不同于复盘中亮牌后的胜率。混合频率表示多种行动的使用比例，不代表获胜概率；边缘决策会受抽样误差影响。这是启发式近似策略，不是严格 GTO 求解或盈利保证。"
                    else "These are the rules and values used for this decision. Simulated equity is against inferred ranges, without opponents' hole cards; it differs from face-up replay equity. Mixed frequencies describe how often to use actions, not chances of winning. Sampling noise affects borderline decisions. This is a heuristic approximation, not an exact GTO solution or a profit guarantee.", fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(if (zh) "关闭" else "Close") } },
    )
}


internal fun policyMix(policy: Policy, st: com.riverrunner.i18n.Strings): String {
    val mix = com.riverrunner.engine.Deviation.actionMix(policy)
    return mix.entries.sortedByDescending { it.value }.joinToString(" · ") { (type, probability) ->
        val name = when (type) {
            com.riverrunner.engine.MoveType.FOLD -> st.fold
            com.riverrunner.engine.MoveType.CHECK -> st.check
            com.riverrunner.engine.MoveType.CALL -> st.call
            com.riverrunner.engine.MoveType.RAISE -> st.raise
        }
        "$name ${Format.pct(probability)}"
    }
}

/** A bounded preview leaves the history usable even on short landscape screens. */
@Composable
internal fun AdviceCard(policy: Policy?, heading: String? = null) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().heightIn(max = 116.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(Palette.PanelHi).padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (heading != null) Text(heading, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (policy == null) st.computing else "GTO · " + policyMix(policy, st),
                color = Palette.Accent, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.weight(1f), maxLines = 2)
            if (policy != null) PolicyExplanation(policy)
        }
        policy?.summary?.let {
            Text(if (st.isZh) it.zh else it.en, color = Palette.Text, fontSize = 12.sp,
                lineHeight = 16.sp)
        }
    }
}

/** Explain saved inputs without running another simulation or claiming an exact EV. */
@Composable
private fun BeginnerExplanation(p: Policy) {
    val zh = LocalStrings.current.isZh
    val c = p.context
    fun chips(n: Int): String = if (c != null) com.riverrunner.Fmt.fixed(n.toDouble() / c.bigBlind, 1) + " BB" else n.toString()
    val mix = com.riverrunner.engine.Deviation.actionMix(p)
    val best = mix.maxBy { it.value }
    Text(if (zh) "① 先看你面对什么" else "① Read the situation", color = Palette.Accent)
    if (c != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            c.hole.forEach { PlayingCard(it, 25.dp) }
            Text(if (zh) "你的底牌" else "Your cards", fontSize = 12.sp)
        }
        if (c.board.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            c.board.forEach { PlayingCard(it, 25.dp) }
        }
        Text(if (zh) "底池 ${chips(p.pot)}；你还剩 ${chips(c.stack)}；" +
            (if (c.toCall > 0) "继续跟注需补 ${chips(c.toCall)}。" else "现在可以免费过牌。") +
            (if (c.board.isEmpty()) "目前是翻牌前，判断重点是起手牌、位置和前面的加注。" else "判断要同时考虑底牌和公共牌，不能只看自己有没有一对。")
            else "Pot ${chips(p.pot)}; stack ${chips(c.stack)}; call costs ${chips(c.toCall)}. Evaluate your holding together with the board and previous action.")
    }
    Text(if (zh) "② 建议怎么做" else "② What to do", color = Palette.Accent)
    val explanation = when (best.key) {
        com.riverrunner.engine.MoveType.FOLD -> if (zh) "优先弃牌：放弃争夺当前底池，也不用继续投入。已经投进去的筹码无法收回，不应该成为硬跟的理由。" else "Prefer folding: stop investing. Chips already committed are not a reason to call again."
        com.riverrunner.engine.MoveType.CHECK -> if (zh) "优先过牌：不额外投入，保留底牌继续观察。过牌不等于弃牌；后面有人下注时，你还会有下一次选择。" else "Prefer checking: keep your hand without adding chips. You can decide again if someone bets."
        com.riverrunner.engine.MoveType.CALL -> if (zh) "优先跟注：补齐当前需要的金额，继续争夺底池。跟注不会主动迫使对手弃牌；后续街仍可能面临更大的下注。" else "Prefer calling: match the price to continue. Calling does not force opponents to fold and future bets may follow."
        com.riverrunner.engine.MoveType.RAISE -> if (zh) "优先下注或加注：主动增加对手继续游戏的成本。强牌希望较弱的牌跟注；诈唬则希望对手弃牌。不要把所有加注都理解成自己一定领先。" else "Prefer betting or raising: value bets seek calls from weaker hands; bluffs seek folds. Raising does not mean you are always ahead."
    }
    Text(explanation)
    val raises = p.options.filter { it.first.type == com.riverrunner.engine.MoveType.RAISE }.sortedByDescending { it.second }
    if (best.key == com.riverrunner.engine.MoveType.RAISE && raises.isNotEmpty()) {
        val amount = raises.first().first.raiseTo
        Text(if (zh) "较常用尺寸：本轮总投入加到 ${chips(amount)}" +
            (c?.let { "，还需投入 ${chips(amount - it.streetBet)}" } ?: "") + "。这是加到的总额，不是再加同样多的筹码。"
            else "Most frequent raise size: ${chips(amount)} total on this street, including chips already bet.")
    }
    Text(if (zh) "这里的 ${Format.pct(best.value)} 是这类行动的使用频率，不是获胜概率。若两种行动都有比例，表示相似局面下可以混合使用，并不要求每次都选最大比例。"
        else "${Format.pct(best.value)} is an action frequency, not a win probability. A mixed strategy can use more than one action in similar situations.")
    Text(if (zh) "③ 跟注值不值：先算价格" else "③ Understand the call price", color = Palette.Accent)
    if (c != null && c.toCall > 0) {
        Text(if (zh) "跟注后底池约为 ${chips(p.pot)} + ${chips(c.toCall)} = ${chips(p.pot + c.toCall)}。\n基础门槛 = 跟注金额 ÷ 跟注后底池 = ${Format.pct(p.potOdds)}。例如底池已有 100，你补 25，门槛就是 25 ÷ 125 = 20%，而不是 25%。"
            else "Call price = call / (pot + call): ${chips(c.toCall)} / ${chips(p.pot + c.toCall)} = ${Format.pct(p.potOdds)}. A call of 25 into 100 needs 25/125 = 20% before further adjustments.")
        p.equity?.let { eq -> Text(if (zh) "本次对推测范围的模拟胜率约 ${Format.pct(eq)}，基础价格门槛为 ${Format.pct(p.potOdds)}。胜率指打到摊牌时预计分到的底池份额，平局也计入；它不是对某一手已知底牌的胜率。"
            else "Estimated equity against inferred ranges is ${Format.pct(eq)}, versus a basic price of ${Format.pct(p.potOdds)}. Equity includes ties; opponents' actual cards are not used.") }
            ?: Text(if (zh) "本次没有单独计算范围胜率。翻牌前主要用起手牌范围和位置规则；‘前多少百分比的起手牌’是牌力排名，不是赢牌概率。" else "No range equity was calculated here. Preflop decisions use hand ranges and position; a starting-hand percentile is not a win probability.")
        Text(if (zh) "这个门槛只是起点：还要考虑后续下注、多人底池、边池资格和位置。胜率高于门槛并不自动等于跟注最好；低于门槛也不能单靠‘也许能中牌’就跟注。"
            else "This is a starting point. Future bets, multiway pots, side-pot eligibility and position also matter; crossing this threshold alone does not prove a call is best.")
    } else Text(if (zh) "当前无需付费跟注，过牌可以免费保留权益。是否下注，需要看较弱的牌能否跟注、较强的牌能否弃牌，以及是否值得扩大底池。"
        else "There is no call price. Check for free, or evaluate whether a bet gets calls from worse hands or folds from better hands.")
    p.icmRequired?.let { Text(if (zh) "锦标赛还有奖金风险：本次 ICM 调整后的门槛约 ${Format.pct(it)}。筹码翻倍不代表奖金价值也翻倍，因此淘汰风险会影响决策。"
        else "Tournament payout risk gives an ICM-adjusted threshold of ${Format.pct(it)}. Doubling chips does not necessarily double prize value.") }
    Text(if (zh) "④ 看懂解答里的术语" else "④ Decode the terms", color = Palette.Accent)
    Text(if (zh) "范围：根据对手行动推测的一组可能底牌，不是知道他的底牌。\n有位置：同一轮通常能在对手之后行动，多看到信息再决定。\n听牌：还需要后续公共牌才能组成顺子或同花等强牌；没中也可能输。\nSPR：有效剩余筹码与底池的比值，越低，后续投入通常越容易接近全下。\n价值下注与诈唬：前者争取弱牌跟注，后者争取对手弃牌。详细参数是本次近似规则的计算依据，不是职业求解器的精确结果。"
        else "Range: plausible opposing hands inferred from actions. Position: acting later with more information. Draw: needing future cards to improve. SPR: effective remaining stack divided by pot. Value bets seek calls from worse hands; bluffs seek folds. These are approximate rules, not exact solver outputs.")
}
