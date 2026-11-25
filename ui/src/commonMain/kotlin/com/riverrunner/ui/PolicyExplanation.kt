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
                val sizes = com.riverrunner.engine.Deviation.sizeMix(policy)
                if (sizes.isNotEmpty()) Text(st.gtoSizing + ": " + sizes.entries.joinToString(" · ") {
                    "${sizeName(st, it.key)} ${Format.pct(it.value)}"
                })
                policy.equity?.let { Text("${st.estEquity}: ${Format.pct(it)}") }
                if (policy.potOdds > 0) Text("${st.needEquity}: ${Format.pct(policy.potOdds)}")
                policy.reasons.forEach { Text(if (zh) it.zh else it.en, fontSize = 16.sp) }
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
