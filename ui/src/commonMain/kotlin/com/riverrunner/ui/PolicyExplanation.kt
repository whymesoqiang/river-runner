package com.riverrunner.ui

import androidx.compose.foundation.layout.Arrangement
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
    val zh = LocalStrings.current.isZh
    var open by remember(policy) { mutableStateOf(false) }
    SmallButton(if (zh) "解释" else "Why?", { open = true })
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(if (zh) "策略依据（近似 GTO）" else "Strategy rationale (approximate GTO)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
