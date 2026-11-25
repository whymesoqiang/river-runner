# 更新日志 | Changelog

每个版本一节，最新的在最上面。每条写成 `中文 | English`。
CI 从最上面一节读取版本号（例如 `## 0.4` 会构建出 0.4.x），并把这一节贴进 GitHub 发布说明；
App 内的“更新内容”也读取这个文件。

One section per version, newest first. Each line is `Chinese | English`.
CI takes the version from the top section (`## 0.4` builds 0.4.x) and copies that section into
the GitHub release notes; the in-app "What's new" reads this file too.

## 0.10
- 首页显示版本，提示检查更新，保留记录和收藏。 | Show current version and explicit updates without deleting training data.
- 手机竖排锁定时，页面内默认横屏并适配触控。 | Rotate the page in portrait phone viewports, including touch coordinates.

## 0.9
- 长时间训练改用累计数据，避免每手重新计算全部历史。 | Update session HUD incrementally instead of rescanning every previous hand.
- 历史记录复用编码，收藏和保存数量保持不变。 | Reuse encoded history rows while preserving favorites and retention rules.
- 复用音效资源并限制数量，切到后台时释放资源。 | Bound and reuse audio players and release them when backgrounded.

## 0.8
- 网页将策略、复盘胜率及成就计算移到单一后台线程，切换复盘时取消过期任务。 | Run web calculations in one cancellable background worker.
- 每手结束默认停在结算帧，展示公共牌、底池获胜者和净收益，保留操作记录入口。 | Open each hand review at settlement, with board, pot recipients, net results and action history.
- 策略解答增加底牌与牌面、跟注价格算式、行动含义和新手术语说明。 | Explain saved cards, call prices, actions and terminology for beginners.

## 0.7 · 2026-11-22

### 新增 | Added
- 网页版：iPhone、iPad 和电脑用浏览器打开网址就能练习，功能和安卓版一样；添加到主屏幕后像 App 一样打开，断网也能用 | Web version: on iPhone, iPad and computers the app runs in the browser with the same features as on Android; added to the home screen it opens like an app and works offline

### 改进 | Changed
- 安卓版和网页版共用同一套界面，以后两边同步更新；安卓版的存档不受影响 | Android and the web version now share one UI and update together; Android saves are unchanged

### 修复 | Fixed
- 连点“快进”时，轮到你后的弃牌、跟注、加注按钮可能被误触；“下一手”按钮同样处理 | Tapping fast forward repeatedly could hit the fold, call or raise button that appears in its place when your turn comes; the same for "Next hand"
- iPhone 网页版：按钮跑到屏幕外、底部黑边、侧面留空 | iPhone web version: buttons off screen, a black band at the bottom, unused space at the side
- 网页版：分屏时不再一直提示“请横过手机”；粗体字改用真正的粗体字体，牌面上的 Q 不再被涂满 | Web version: no rotate prompt in split view; a real bold font, so the Q on cards is no longer filled in

## 0.6 · 2026-11-20

### 改进 | Changed
- 界面用词改为练习软件的说法：开始练习、练习记录、再练一组、里程碑、练习档案；“筹码局 / 锦标赛”改为“固定盲注 / 涨盲·ICM”，固定盲注结束时显示“本组结束” | Practice wording throughout: start practice, practice log, session complete, milestones, practice profiles; cash and tournament modes are now fixed blinds and rising blinds (ICM)
- 牌桌侧栏的开关、音效和新手指南收进 ⚙ 菜单，“复盘上一手”改成标题行的 ⟲ 图标，练习信息和行动记录不再被挤压 | The table switches, sound and guide move into a ⚙ menu and "last hand" becomes a ⟲ icon, so practice info and the action log keep their room

### 修复 | Fixed
- 涨盲模式下开启练习信息时，侧栏的底池、牌型和 SPR 被遮挡 | With rising blinds and practice info on, the pot, hand and SPR lines in the side panel were cut off

## 0.5 · 2026-11-19

### 新增 | Added
- 牌桌阵容：混合桌可以选“随机、鱼塘、凶桌、紧桌”，或自定义每种风格各几个人，其余座位是 GTO | Table line-up: pick random, fish pond, aggressive or tight for a mixed table, or set how many bots play each style; the other seats play GTO
- 数据 HUD：打开后每个机器人旁显示本局的入池率、加注率和手数，靠数据判断对手风格，不直接给标签 | HUD: shows each bot's VPIP, PFR and hand count this session, so you read their style from the numbers instead of a label
- 偏离提示：打开后，你每做一个决定，侧栏会显示 GTO 的动作和尺寸分布，以及你是否偏离；提示在你行动之后才出现 | Live feedback: after each of your decisions the side panel shows the GTO action and size mix and whether you deviated; it only appears after you act
- 复盘上一手：打牌时随时回看上一手，看完回到当前这手；误点跳过每手复盘也能补看 | Replay the last hand at any time during a session and return to the hand in play, also after skipping a review by mistake
- 本局报告：结束后显示你的偏离率，按街和按位置分开统计，并揭晓每个机器人的真实风格 | Session report: your deviation rate overall, by street and by position, plus each bot's true style

### 改进 | Changed
- 牌桌侧栏的开关改为四个紧凑按钮：练习、每手复盘、数据、偏离提示 | The side panel switches are now four compact toggles: practice, review, HUD and feedback
- 复盘和牌桌使用同一套偏离判定标准 | Replay and the table use the same rules to judge a deviation

## 0.4 · 2026-11-11

### 新增 | Added
- 每手复盘：牌桌侧栏打开“每手复盘”，每手结束后直接进入这手的复盘，看完点“继续下一手” | Review each hand: turn on "Review each hand" at the table and every hand opens in replay when it ends; tap "Next hand" to continue
- 打牌时收藏：牌桌右上角的 ☆ 可以收藏正在打的这一手，打完也能补收藏 | Star a hand while playing with the ☆ at the top of the table, or right after it ends
- 复盘页的收藏按钮更显眼，标题显示第几手，方便在历史里找回来 | A clearer star button in replay, and the hand number in the title, so hands are easy to find in History
- GTO 下注尺寸分五档：轻注、中注、重注、满池、超池；复盘里显示每档的频率，你的尺寸偏离时会提示 | GTO bet sizes in five classes (small, medium, large, pot, overbet); replay shows the mix and flags an unusual size
- 更新内容：升级后首次打开会弹出本次更新，设置页也能查看全部记录 | What's new: shown once after an update, and the full list is in Settings

### 改进 | Changed
- 机器人开池、下注和加注的尺寸会混合使用，大小不再直接暴露牌力 | Bots mix their open, bet and raise sizes, so the size no longer gives away the hand

### 修复 | Fixed
- 修正单挑时机器人几乎不做价值下注、只会诈唬的问题 | Heads-up bots almost never bet for value, only as bluffs

## 0.3 · 2026-11-09

### 改进 | Changed
- 更换了 APK 签名密钥；从 0.2.x 升级需要先卸载旧版本再安装 | New APK signing key; uninstall a 0.2.x build before installing
- 项目主页加入截图、下载入口和安装说明 | Project page with screenshots, a download link and install notes

## 0.2 · 2026-10-30

### 新增 | Added
- 牌面配色设置：四色、四色底、红黑；梅花和黑桃改为矢量图形，小牌上也能分清 | Card colour setting (four-colour, filled, red and black); vector suit shapes so clubs and spades are easy to tell apart
- 6 个存档，每个存档有自己的生涯统计、手牌历史和成就 | Six save slots, each with its own career stats, hand history and achievements
- 35 个成就，分 7 类，包括“趣味”成就（比如拿 AA 输了） | 35 achievements in seven categories, including just-for-fun ones (such as losing with aces)
- 音效和静音开关，盲注上涨时有提示 | Sound effects with a mute button, and a notice when the blinds go up
- 新首页和单独的开局设置页，可以按上次设置快速开始 | New home screen and a separate game setup screen, with quick start using the last settings

### 改进 | Changed
- 绿色牌桌配金色的视觉风格，按钮文字对比度更高 | Green felt and brass look, with higher contrast on buttons
- 下注筹码不再互相遮挡，底池显示在公共牌下方 | Bet chips no longer overlap, and the pot sits below the board
- 练习模式面板更紧凑，不再挡住行动记录 | A more compact practice panel that no longer hides the action log
- 新手指南里的英文术语加上了全称 | Full names for the English terms in the guide

## 0.1 · 2026-10-26

### 新增 | Added
- 单机德州扑克练习：2、5、6、7、8 人桌，现金局和单桌锦标赛（ICM、按手数涨盲） | Offline Hold'em practice: 2, 5, 6, 7 and 8-player tables, cash games and single-table tournaments (ICM, blinds up by hands)
- 近似 GTO 的机器人，以及混合风格牌桌 | Approximate-GTO bots and mixed-style tables
- 练习模式：显示底池赔率、所需胜率、SPR 和当前牌型，不显示胜率 | Practice mode: pot odds, equity needed, SPR and your made hand, never your equity
- 复盘：显示所有底牌、每条街的胜率，以及你每个决策的 GTO 建议 | Replay with every hole card, equity on each street and a GTO suggestion for each of your decisions
- 保留最近 1000 手牌历史，可收藏；统计 VPIP、PFR、3-bet 等数据 | The last 1000 hands kept in History, with stars; stats such as VPIP, PFR and 3-bet
- 新手指南：规则、牌型大小、位置和术语 | Beginner guide: rules, hand rankings, positions and terms
- 牌桌动画、手动进入下一手、行动记录；中英文切换 | Table animations, manual next hand, action log; Chinese and English
