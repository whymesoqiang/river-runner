package com.riverrunner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.StarProfile
import com.riverrunner.engine.StarProfiles
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.ui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private fun portrait(id: String): DrawableResource = when (id) {
    "peter" -> Res.drawable.star_peter
    "dwan" -> Res.drawable.star_dwan
    "britney" -> Res.drawable.star_britney
    "texas_mike" -> Res.drawable.star_texas_mike
    "wang_ye" -> Res.drawable.star_wang_ye
    "tan_xuan" -> Res.drawable.star_tan_xuan
    "wesley" -> Res.drawable.star_wesley
    "st_wang" -> Res.drawable.star_st_wang
    else -> Res.drawable.star_keating
}

@Composable
internal fun StarPortrait(id: String, size: Dp = 36.dp, interactive: Boolean = true) {
    val star = StarProfiles.byId(id) ?: return
    val zh = LocalStrings.current.isZh
    var open by remember(id) { mutableStateOf(false) }
    Image(painterResource(portrait(id)), star.display(zh), contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier.size(size).clip(CircleShape).background(Palette.PanelHi)
            .then(if (interactive) Modifier.pressable { open = true } else Modifier))
    if (open) StarDetail(star) { open = false }
}

@Composable
private fun StarDetail(star: StarProfile, onClose: () -> Unit) {
    val zh = LocalStrings.current.isZh
    val links = LocalUriHandler.current
    AlertDialog(onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StarPortrait(star.id, 56.dp, interactive = false)
            Column { Text(star.display(zh)); Text(star.tag(zh), color = Palette.Gold, fontSize = 14.sp) }
        } },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (zh) "本游戏买入：${star.minBB}–${star.maxBB} BB" else "In-game buy-in: ${star.minBB}–${star.maxBB} BB")
            Text(star.description(zh))
            Text(if (zh) "牌风模拟：基于公开比赛印象设计，买入与频率是训练参数，不是选手真实统计或本人参与。"
                else "Style simulation inspired by public appearances. Buy-ins and frequencies are training parameters, not measured personal statistics or player participation.", fontSize = 12.sp)
            Text(if (star.id == "wesley") "Photo: Hustler Casino Live" else if (star.id == "peter" || star.id == "wang_ye") "Photo: Highroll Poker" else if (star.id == "texas_mike") "Photo: PokerNews" else "Photo: BadBeat.TV", fontSize = 11.sp)
            TextButton({ links.openUri(star.sourceUrl) }) { Text(if (zh) "查看公开资料" else "Public source") }
        } },
        confirmButton = { TextButton(onClose) { Text(if (zh) "关闭" else "Close") } })
}

@Composable
internal fun StarRosterButton(modifier: Modifier = Modifier) {
    val zh = LocalStrings.current.isZh
    var open by remember { mutableStateOf(false) }
    SmallButton(if (zh) "查看 9 位明星及牌风" else "Meet the 9 players", { open = true }, modifier)
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f).clip(RoundedCornerShape(14.dp))
            .background(Palette.Panel).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (zh) "明星候选人 · 随机入座" else "Star roster · random seats", color = Palette.Text, fontWeight = FontWeight.Bold)
                    Text(if (zh) "点击头像查看牌风 · 每桌不重复 · 本游戏买入范围" else "Tap a portrait · no duplicates · simulated buy-ins", color = Palette.TextDim, fontSize = 11.sp)
                }
                SmallButton(if (zh) "关闭" else "Close", { open = false })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StarProfiles.all.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (star in pair) Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StarPortrait(star.id, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(star.display(zh), color = Palette.Text, fontSize = 13.sp, maxLines = 1)
                                Text(star.tag(zh), color = Palette.Gold, fontSize = 11.sp, maxLines = 1)
                                Text("${star.minBB}–${star.maxBB} BB", color = Palette.TextDim, fontSize = 11.sp)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
