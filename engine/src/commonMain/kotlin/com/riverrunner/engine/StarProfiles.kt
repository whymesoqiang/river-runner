package com.riverrunner.engine

/** Training presets inspired by public appearances, not measured replicas of real players. */
data class BotTraits(
    val looseness: Double, val aggression: Double, val bluff: Double,
    val callBias: Double, val preflopCall: Double,
    val limp: Boolean = false,
    val shortStackPushBB: Double = 0.0,
    val lateBarrel: Double = 1.0,
) {
    companion object {
        fun from(style: BotStyle) = BotTraits(style.looseness, style.aggression, style.bluff,
            style.callBias, style.preflopCall, style == BotStyle.STATION || style == BotStyle.MANIAC)
    }
}

data class StarProfile(
    val id: String, val name: String, val displayZh: String,
    val tagZh: String, val tagEn: String,
    val descriptionZh: String, val descriptionEn: String,
    val minBB: Int, val maxBB: Int, val baseStyle: BotStyle, val traits: BotTraits,
    val sourceUrl: String,
) {
    fun display(zh: Boolean) = if (zh) displayZh else name
    fun tag(zh: Boolean) = if (zh) tagZh else tagEn
    fun description(zh: Boolean) = if (zh) descriptionZh else descriptionEn
}

object StarProfiles {
    val all = listOf(
        StarProfile("peter", "Peter", "Peter", "深码宽防守", "Deep, wide defense",
            "模拟宽范围入池与较黏的跟注，深码下愿意接住大底池压力。",
            "Wide entries and sticky calls, willing to defend deep-stacked pots.",
            150, 300, BotStyle.LAG, BotTraits(1.85, 1.15, 1.25, 0.075, 1.8, limp = true),
            "https://highrollpoker.com/tracker/players/308"),
        StarProfile("dwan", "Tom Dwan", "Tom Dwan", "深码极化施压", "Deep polarized pressure",
            "模拟宽范围与主动再加注，后两条街增加持续施压，同时保留抓诈唬能力。",
            "Wide ranges, proactive re-raises and late-street pressure, with bluff-catching calls.",
            150, 300, BotStyle.LAG, BotTraits(1.4, 1.45, 1.75, 0.025, 1.15, lateBarrel = 1.3),
            "https://www.pokernews.com/strategy/biggest-pot-in-tv-poker-history-45909.htm"),
        StarProfile("britney", "Britney", "Britney · 小甜心", "All in 姐 · 短码推牌", "Short-stack shove",
            "按短码推牌形象设计：20–35 BB 买入，短码时优先使用全下或弃牌；翻倍变深后恢复常规决策。",
            "A short-stack training persona: buys in for 20–35 BB, favors shove/fold while short, and returns to regular play after building a deeper stack.",
            20, 35, BotStyle.LAG, BotTraits(1.4, 1.6, 1.25, 0.02, 0.8, shortStackPushBB = 35.0),
            "https://www.pokernews.com/poker-players/britney-jing/"),
        StarProfile("texas_mike", "Texas Mike", "Texas Mike", "超宽范围进攻", "Very wide aggression",
            "模拟高入池、频繁加注的比赛观感，弱牌也更常参与，主动制造大底池。",
            "Very wide participation and frequent raises, building pots with a broad range.",
            200, 400, BotStyle.MANIAC, BotTraits(2.2, 1.85, 2.0, 0.05, 1.6, limp = true),
            "https://www.pokernews.com/news/2024/04/poker-pro-vpip-hustler-casino-live-45778.htm"),
        StarProfile("wang_ye", "Wang Ye", "王烨", "主动再加注", "Proactive re-raising",
            "模拟积极进攻与价值再加注，比超松型选手更重视入池质量。",
            "Aggressive value raises with more selective entry than the very loose personas.",
            100, 220, BotStyle.LAG, BotTraits(1.25, 1.6, 1.35, 0.0, 0.95),
            "https://tritonpokerseries.com/news/thetritontimes/2nd"),
        StarProfile("tan_xuan", "Tan Xuan", "谭轩", "多轮偷鸡施压", "Multi-street bluffs",
            "提高半诈唬和后两条街的诈唬倾向，尤其在单挑且持有主动权时；不是无条件乱推。",
            "More semi-bluffs and late barrels, especially heads-up with initiative; not indiscriminate shoving.",
            200, 350, BotStyle.LAG, BotTraits(1.5, 1.4, 2.2, 0.015, 1.15, lateBarrel = 1.65),
            "https://tritonpokerseries.com/en-US/news/headlines/tan-xuan-completes-short-deck-double-with-jeju-main-event-success"),
        StarProfile("wesley", "Wesley", "Wesley", "择机大幅施压", "Selective big pressure",
            "相对挑选起手牌，但进入进攻线后会增加再加注和后街诈唬，练习识别极化范围。",
            "More selective preflop, but stronger re-raising and late bluff pressure once on an aggressive line.",
            100, 250, BotStyle.TAG, BotTraits(1.05, 1.45, 1.65, -0.01, 0.9, lateBarrel = 1.5),
            "https://www.pokernews.com/strategy/biggest-pot-in-tv-poker-history-45909.htm"),
        StarProfile("st_wang", "ST Wang", "ST Wang", "深码反击", "Deep re-raise pressure",
            "模拟深码下的积极反击与较窄的平跟，强调再加注压力；公开样本有限，参数为训练设定。",
            "Deep-stack re-raise pressure with selective flat calls. Public samples are limited; parameters are training choices.",
            150, 300, BotStyle.LAG, BotTraits(1.2, 1.7, 1.5, 0.005, 0.85, lateBarrel = 1.15),
            "https://tritonpokerseries.com/en-US/news/headlines/cao-and-wang-make-a-million-as-cash-game-invitational-concludes-with-fireworks"),
        StarProfile("keating", "Alan Keating", "Alan Keating", "超深码宽跟注", "Very deep, sticky calls",
            "模拟超宽入池与高跟注意愿，带入更深筹码；可练习薄价值下注和减少无效诈唬。",
            "Very wide entries, sticky calls and deeper buy-ins: practice thin value and selective bluffing.",
            250, 500, BotStyle.LAG, BotTraits(2.4, 1.1, 1.4, 0.12, 2.2, limp = true),
            "https://www.cardplayer.com/cardplayer-poker-magazines/66564-global-poker-x-uspo-2025-38-7/articles/25347-the-gutsiest-hand-in-high-stakes-poker-history"),
    )
    fun byId(id: String?): StarProfile? = all.firstOrNull { it.id == id }
}
