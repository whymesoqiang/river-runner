package com.riverrunner.ui

import com.riverrunner.Fmt
import com.riverrunner.PlatformBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.heightIn
import com.riverrunner.engine.Deviation
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.SizeClass
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.Cards
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import kotlin.math.roundToInt

/** Live game screen, wired to the view model. */
@Composable
fun TableScreen(vm: AppViewModel) {
    var confirmExit by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    PlatformBackHandler { if (showGuide) showGuide = false else confirmExit = true }
    val st = LocalStrings.current
    val ui = vm.tableScreen
    val bb = ui.table?.bigBlind ?: 100
    TableContent(
        ui = ui,
        fmt = { vm.fmt(it, bb) },
        onMove = vm::heroAct,
        onNext = vm::continueToNextHand,
        onFastForward = { vm.fastForward = true },
        onPractice = { vm.practice = it },
        onExit = { confirmExit = true },
        onHelp = { showGuide = true; vm.guideOpenedInGame() },
        onSound = { vm.setSound(!vm.soundOn) },
        onStar = vm::toggleStarHand,
        onReview = vm::setReviewEach,
        onHud = vm::setHud,
        onDeviation = vm::setDeviation,
        onReplayLast = vm::openLastReplay,
    )
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            confirmButton = { TextButton({ confirmExit = false; vm.requestQuit() }) { Text(st.leaveTable, color = Palette.Red) } },
            dismissButton = { TextButton({ confirmExit = false }) { Text(st.keepPlaying) } },
            title = { Text(st.exitConfirmTitle) },
            text = { Text(st.exitConfirmText) },
        )
    }
    if (showGuide) {
        Dialog(onDismissRequest = { showGuide = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GuideContent(onClose = { showGuide = false }, startTab = GuideTab.GLOSSARY)
        }
    }
}

@Composable
fun TableContent(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    onPractice: (Boolean) -> Unit,
    onExit: () -> Unit,
    onHelp: () -> Unit,
    animate: Boolean = true,
    onSound: () -> Unit = {},
    onStar: () -> Unit = {},
    onReview: (Boolean) -> Unit = {},
    onHud: (Boolean) -> Unit = {},
    onDeviation: (Boolean) -> Unit = {},
    onReplayLast: () -> Unit = {},
) {
    val st = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val turnKey = ui.legal?.let { "${ui.handNumber}:${ui.log.size}" }
    LaunchedEffect(turnKey) {
        if (turnKey != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Bg)) {
        val panelWidth = (maxWidth * 0.38f).coerceIn(260.dp, 360.dp)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, top = 4.dp, bottom = 2.dp)) {
                val t = ui.table
                if (t != null) PokerTable(t, fmt, st.pot, Modifier.fillMaxSize(), animateDeal = animate)
                androidx.compose.animation.AnimatedVisibility(
                    ui.notice != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                    enter = androidx.compose.animation.slideInVertically { -it } + fadeIn(),
                    exit = fadeOut(tween(400)),
                ) {
                    Text(
                        ui.notice ?: "", color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Gold)
                            .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            SidePanel(
                ui, fmt, onMove, onNext, onFastForward, onPractice, onExit, onHelp, onSound, onStar, onReview,
                onHud, onDeviation, onReplayLast, panelWidth,
            )
        }
    }
}

@Composable
private fun SidePanel(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    onPractice: (Boolean) -> Unit,
    onExit: () -> Unit,
    onHelp: () -> Unit,
    onSound: () -> Unit,
    onStar: () -> Unit,
    onReview: (Boolean) -> Unit,
    onHud: (Boolean) -> Unit,
    onDeviation: (Boolean) -> Unit,
    onReplayLast: () -> Unit,
    panelWidth: androidx.compose.ui.unit.Dp,
) {
    val st = LocalStrings.current
    val heroTurn = ui.legal != null
    val glow = if (heroTurn) pulse() else 0f
    Column(
        Modifier.width(panelWidth).fillMaxHeight().background(Palette.Panel)
            .border(if (heroTurn) 2.dp else 0.dp, Palette.Accent.copy(alpha = 0.25f + 0.6f * glow), RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // header: hand number, then star / last hand / table menu / leave. The switches live in the
        // menu so the practice info and the action log keep their room on short screens.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.handNo(ui.handNumber) + if (heroTurn) " · ${st.yourTurn}" else "", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                RoundIcon(if (ui.starred) "★" else "☆", st.starHand, onStar)
                // the previous hand of this session, e.g. after skipping its review by mistake
                if (ui.canReplayLast) {
                    Spacer(Modifier.width(4.dp))
                    RoundIcon("⟲", st.replayLast, onReplayLast)
                }
                Spacer(Modifier.width(4.dp))
                TableMenu(ui, onPractice, onReview, onHud, onDeviation, onSound, onHelp)
                Spacer(Modifier.width(4.dp))
                RoundIcon("✕", st.exit, onExit)
            }
            Text(ui.blindsText, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
            if (ui.levelText != null) Text(ui.levelText, color = Palette.Gold, fontSize = 11.sp, maxLines = 1)
        }
        // middle: feedback on your last decision, then results, or practice info + action log
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val fb = ui.feedback
            // feedback on your last decision; hidden while you make the next one (it would be stale and the
            // controls need the room)
            if (ui.showDeviation && fb != null && ui.legal == null) FeedbackCard(fb, fmt)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val over = ui.handOver
                if (over != null) {
                    HandOverPanel(over, fmt)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val info = ui.practiceInfo
                        if (heroTurn) AdviceCard(ui.livePolicy)
                        else if (ui.practice && info != null) PracticeHud(info, fmt)
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            HistoryPanel(ui.log, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        // bottom: controls
        AnimatedContent(
            targetState = when {
                ui.legal != null -> 0
                ui.handOver != null -> 1
                else -> 2
            },
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
            },
            label = "controls",
        ) { mode ->
            when (mode) {
                0 -> ui.legal?.let { HeroControls(it, fmt, ui.table?.bigBlind ?: 100, onMove) }
                1 -> {
                    // same spot as the action buttons a moment ago: a late tap must not skip the result
                    val armed by rememberArmed(ui.handOver)
                    ActionButton(
                        if (ui.sessionOver) st.viewResults else st.nextHand + "  ▶", Palette.CallBtn,
                        modifier = Modifier.fillMaxWidth().height(46.dp), onClick = { if (armed) onNext() },
                    )
                }
                else -> WaitingBar(ui, onFastForward)
            }
        }
    }
}

@Composable
private fun pulse(): Float {
    return settlingPulse()
}

/** ⚙ menu with the table switches (practice info, review each hand, HUD, feedback, sound) and the guide. */
@Composable
private fun TableMenu(
    ui: TableScreenUi,
    onPractice: (Boolean) -> Unit,
    onReview: (Boolean) -> Unit,
    onHud: (Boolean) -> Unit,
    onDeviation: (Boolean) -> Unit,
    onSound: () -> Unit,
    onHelp: () -> Unit,
) {
    val st = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIcon("⚙", st.settings) { open = true }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            containerColor = Palette.PanelHi, border = BorderStroke(1.dp, Palette.Line),
        ) {
            // switches keep the menu open so several can be changed at once
            MenuSwitch(st.chipPractice, ui.practice) { onPractice(!ui.practice) }
            MenuSwitch(st.chipReview, ui.reviewEach) { onReview(!ui.reviewEach) }
            MenuSwitch(st.chipHud, ui.showHud) { onHud(!ui.showHud) }
            MenuSwitch(st.chipDeviation, ui.showDeviation) { onDeviation(!ui.showDeviation) }
            MenuSwitch(st.sound, ui.soundOn) { onSound() }
            DropdownMenuItem(
                text = { Text(st.guide, color = Palette.Text, fontSize = 14.sp) },
                onClick = { open = false; onHelp() },
            )
        }
    }
}

@Composable
private fun MenuSwitch(label: String, on: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = Palette.Text, fontSize = 14.sp) },
        trailingIcon = {
            Text(
                if (on) "●" else "○", color = if (on) Palette.Accent else Palette.TextDim, fontSize = 16.sp,
                modifier = Modifier.padding(start = 16.dp),
            )
        },
        onClick = onToggle,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (on) "on" else "off"
        },
    )
}

/** GTO feedback on your last decision: verdict, then the GTO action mix and bet sizes. */
@Composable
private fun FeedbackCard(f: DecisionFeedback, fmt: (Int) -> String) {
    val st = LocalStrings.current
    val verdict = if (f.verdict == Deviation.Verdict.OK) "✓ ${st.inLine}" else "⚠ ${st.deviation}"
    AdviceCard(f.policy, "$verdict · ${TableMapper.actionLabel(st, f.action, fmt)}")
}

@Composable
private fun RoundIcon(text: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).pressable(onClick = onClick).clip(CircleShape).background(Palette.PanelHi)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PracticeHud(info: PracticeInfo, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        HudRow(st.pot, fmt(info.pot), st.toCall, if (info.toCall > 0) fmt(info.toCall) else "0")
        if (info.toCall > 0) {
            HudRow(st.potOdds, "1 : " + Fmt.fixed(info.pot.toDouble() / info.toCall, 1), st.needEquity, Format.pct(info.potOdds))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(info.handText, color = Palette.Gold, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${st.spr} ", color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
            Text(Fmt.fixed(info.spr, 1), color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HudRow(l1: String, v1: String, l2: String?, v2: String?) {
    Row(Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f)) {
            Text(l1, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(v1, color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f)) {
            if (l2 != null && v2 != null) {
                Text(l2, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(v2, color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun ActionLog(lines: List<LogLine>, modifier: Modifier = Modifier, highlightIndex: Int = -1, onClick: ((Int) -> Unit)? = null) {
    val state = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) followTail = false
            else if (state.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                followTail = (state.layoutInfo.visibleItemsInfo.last().index >= state.layoutInfo.totalItemsCount - 2)
            }
        }
    }
    LaunchedEffect(lines.size, highlightIndex, state.layoutInfo.viewportSize) {
        val target = if (highlightIndex >= 0) highlightIndex else lines.size - 1
        if (target >= 0 && (highlightIndex >= 0 || (followTail && !state.isScrollInProgress)))
            state.scrollToItem(target)
    }
    LazyColumn(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.Bg).padding(horizontal = 6.dp, vertical = 3.dp),
        state = state,
    ) {
        itemsIndexed(lines) { i, l ->
            val hl = i == highlightIndex
            val mod = Modifier.fillMaxWidth()
                .then(if (hl) Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.AccentDim.copy(alpha = 0.6f)) else Modifier)
                .then(if (onClick != null) Modifier.pressable { onClick(i) } else Modifier)
                .padding(vertical = 1.dp)
            when (l.kind) {
                LogKind.STREET -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Palette.TextDim, fontWeight = FontWeight.Bold)) { append("${l.text}  ") }
                        for (c in l.cards) {
                            withStyle(SpanStyle(color = logSuitColor(c), fontWeight = FontWeight.Bold)) { append(rankText(c) + suitText(c) + " ") }
                        }
                    },
                    fontSize = 12.sp, modifier = mod.padding(top = 4.dp),
                )
                LogKind.ACTION -> Text(l.text, color = if (hl) Palette.Accent else Palette.Text, fontSize = 12.sp, maxLines = 2, modifier = mod)
                LogKind.RESULT -> Text(l.text, color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = mod)
            }
        }
    }
}

private fun logSuitColor(card: Int) = when (Cards.suit(card)) {
    0 -> Color(0xFF6FD08C); 1 -> Color(0xFF7DB2F0); 2 -> Color(0xFFF07C7C); else -> Color(0xFFE8E8E8)
}

@Composable
internal fun HandOverPanel(over: HandOverUi, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Palette.Bg).padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (st.isZh) "结算 · 你的净收益" else "Result · your net", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val net = over.heroNet
            Text(
                (if (net > 0) "+" else if (net < 0) "−" else "") + fmt(kotlin.math.abs(net)),
                color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                fontSize = 17.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(if (st.isZh) "本手公共牌" else "Community cards", color = Palette.TextDim, fontSize = 11.sp)
        if (over.board.isEmpty()) Text(if (st.isZh) "未发公共牌：翻牌前已结束" else "Ended before the flop", color = Palette.Text, fontSize = 12.sp)
        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            over.board.forEach { PlayingCard(it, 25.dp) }
        }
        for (w in over.winners) {
            Text(if (st.isZh) "${w.name} · 收下底池 ${fmt(w.amount)}" else "${w.name} · collected ${fmt(w.amount)}", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text((if (st.isZh) "本手净收益 " else "Hand net ") + (if (w.net > 0) "+" else if (w.net < 0) "−" else "") + fmt(kotlin.math.abs(w.net)) +
                (w.handName?.let { " · $it" } ?: ""), color = Palette.Text, fontSize = 12.sp)
        }
        if (over.byFold) Text(st.othersFolded, color = Palette.TextDim, fontSize = 11.sp)
        if (over.showdown.isNotEmpty()) {
            Text(st.showdown, color = Palette.TextDim, fontSize = 11.sp)
            for (s in over.showdown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name, color = if (s.won > 0) Palette.Green else Palette.Text, fontSize = 12.sp,
                        fontWeight = if (s.won > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.width(52.dp),
                    )
                    PlayingCard(s.cards[0], 18.dp)
                    Spacer(Modifier.width(2.dp))
                    PlayingCard(s.cards[1], 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(s.handName, color = if (s.won > 0) Palette.Green else Palette.TextDim, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WaitingBar(ui: TableScreenUi, onFastForward: () -> Unit) {
    val st = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingDots()
            Spacer(Modifier.width(6.dp))
            Text(
                ui.actingName?.let { st.thinking(it) } ?: st.dealing, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1,
            )
        }
        ActionButton(
            (if (ui.heroInHand) st.fastForward else st.skipHand) + "  ⏩",
            if (ui.fastForward) Palette.AccentDim else Palette.PanelHi2,
            modifier = Modifier.fillMaxWidth().height(40.dp), onClick = onFastForward,
        )
    }
}

@Composable
private fun ThinkingDots() {
    var p by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(300); p = (p + 1) % 3 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 3) {
            val on = p == i
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Palette.Accent else Palette.PanelHi2))
        }
    }
}

// ------------------------------------------------------------------ betting controls

private fun presets(st: Strings, la: LegalActions, bb: Int): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    when {
        la.isOpeningBet -> {
            out += "1/3" to (la.pot / 3.0).roundToInt()
            out += "1/2" to (la.pot / 2.0).roundToInt()
            out += "3/4" to (la.pot * 0.75).roundToInt()
            out += st.potShort to la.pot
        }
        la.currentBet <= bb -> {
            // unopened or limped pot preflop
            val limps = ((la.pot - la.currentBet * 2) / bb).coerceAtLeast(0)
            out += "2.5x" to (bb * 2.5).roundToInt() + limps * bb
            out += "3x" to bb * 3 + limps * bb
            out += "4x" to bb * 4 + limps * bb
        }
        else -> {
            out += "2.5x" to (la.currentBet * 2.5).roundToInt()
            out += "3x" to la.currentBet * 3
            out += st.potShort to la.currentBet + la.pot + la.toCall
        }
    }
    val cleaned = out.map { it.first to it.second.coerceIn(la.minRaiseTo, la.maxRaiseTo) }
        .filter { it.second < la.maxRaiseTo }
        .distinctBy { it.second }
    return cleaned + (st.allIn to la.maxRaiseTo)
}

@Composable
private fun HeroControls(la: LegalActions, fmt: (Int) -> String, bb: Int, onMove: (Move) -> Unit) {
    val st = LocalStrings.current
    var target by remember(la) { mutableIntStateOf(defaultTarget(la, bb)) }
    // These buttons appear where "fast forward" was: ignore taps for a moment so a repeated tap
    // on fast forward cannot fold, call or raise.
    val armed by rememberArmed(la)
    val move: (Move) -> Unit = { if (armed) onMove(it) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (la.canRaise) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    for ((label, v) in presets(st, la, bb)) {
                        val sel = v == target
                        Box(
                            Modifier.weight(1f).height(32.dp).pressable { target = v }.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Palette.AccentDim else Palette.PanelHi)
                                .then(if (sel) Modifier.border(1.dp, Palette.Accent, RoundedCornerShape(6.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = Palette.Text, fontSize = 11.sp, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                if (la.maxRaiseTo > la.minRaiseTo) {
                    val step = (bb / 2).coerceAtLeast(1)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−") { target = (target - step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                        BetSlider(
                            fraction = (target - la.minRaiseTo).toFloat() / (la.maxRaiseTo - la.minRaiseTo),
                            onChange = { f ->
                                val raw = la.minRaiseTo + f * (la.maxRaiseTo - la.minRaiseTo)
                                val snapped = if (f >= 0.995f) la.maxRaiseTo else ((raw / step).roundToInt() * step)
                                target = snapped.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        )
                        StepButton("+") { target = (target + step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
            if (la.canCheck) {
                ActionButton(st.check, Palette.CallBtn, modifier = Modifier.weight(1f).fillMaxHeight()) { move(Move.CHECK) }
            } else {
                ActionButton(st.fold, Palette.FoldBtn, modifier = Modifier.weight(1f).fillMaxHeight()) { move(Move.FOLD) }
                val callAllIn = la.toCall >= la.maxRaiseTo - la.myStreetBet
                ActionButton(
                    if (callAllIn) st.allIn else st.call, if (callAllIn) Palette.AllInBtn else Palette.CallBtn,
                    sub = fmt(la.toCall), modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { move(Move.CALL) }
            }
            if (la.canRaise) {
                val isAllIn = target >= la.maxRaiseTo
                ActionButton(
                    when {
                        isAllIn -> st.allIn
                        la.isOpeningBet -> st.bet
                        else -> st.raiseTo
                    },
                    if (isAllIn) Palette.AllInBtn else Palette.RaiseBtn,
                    sub = fmt(target),
                    textColor = if (isAllIn) Palette.Text else Palette.Ink,
                    modifier = Modifier.weight(if (la.canCheck) 1.4f else 1.2f).fillMaxHeight(),
                ) { move(Move.raiseTo(target)) }
            }
        }
    }
}

private fun defaultTarget(la: LegalActions, bb: Int): Int {
    val t = when {
        la.isOpeningBet -> (la.pot * 0.5).roundToInt()
        la.currentBet <= bb -> (bb * 2.5).roundToInt()
        else -> la.currentBet * 3
    }
    return t.coerceIn(la.minRaiseTo, la.maxRaiseTo)
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).pressable(onClick = onClick).clip(RoundedCornerShape(6.dp)).background(Palette.PanelHi),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BetSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Box(
        modifier.height(34.dp)
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = fraction.coerceIn(0f, 1f)
            val r = size.height * 0.33f
            val trackH = 6.dp.toPx()
            val y = size.height / 2
            val left = r
            val right = size.width - r
            drawRoundRect(Palette.PanelHi2, Offset(left, y - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))
            val x = left + (right - left) * f
            drawRoundRect(Palette.Accent, Offset(left, y - trackH / 2), Size(x - left, trackH), CornerRadius(trackH / 2))
            drawCircle(Color.White, r + 2.dp.toPx(), Offset(x, y))
            drawCircle(Palette.Accent, r, Offset(x, y))
        }
    }
}


@Composable
internal fun HistoryPanel(lines: List<LogLine>, modifier: Modifier = Modifier,
                         highlightIndex: Int = -1, onClick: ((Int) -> Unit)? = null) {
    val zh = LocalStrings.current.isZh
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (zh) "操作记录" else "Action history", color = Palette.TextDim,
                fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (zh) "展开 ↗" else "Expand ↗", color = Palette.Accent, fontSize = 12.sp,
                modifier = Modifier.pressable { expanded = true }.padding(horizontal = 8.dp, vertical = 7.dp))
        }
        ActionLog(lines, Modifier.weight(1f), highlightIndex, onClick)
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f)
            .clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (zh) "操作记录" else "Action history", color = Palette.Text,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                SmallButton(if (zh) "关闭" else "Close", { expanded = false })
            }
            ActionLog(lines, Modifier.weight(1f), highlightIndex, onClick)
        }
    }
}
