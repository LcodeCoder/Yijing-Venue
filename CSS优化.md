# 《场地弈境》CSS / 样式优化建议

> 针对 `frontend/src/assets/main.css` 与战场/卡牌相关样式的专项建议。  
> 与 `优化建议.md`（产品/玩法）互补：本文只谈视觉、结构、可维护性与性能。  
> 日期：2026-07-19

---

## 1. 现状速览

| 项目 | 现状 |
|------|------|
| 主样式文件 | `frontend/src/assets/main.css`（约 120KB+，近千行，含多层历史补丁） |
| 工具链 | Tailwind 已接入（`@tailwind base/components/utilities`），但主体仍是手写全局 CSS |
| 设计 token | `:root` 仅有少量变量：`--ink/--muted/--bg/--panel/--jade/--gold/--rival` 等 |
| 分层历史 | 早期五格战场 → Interaction layer → 九域棋盘 → 2026 tactical refresh **叠写覆盖** |
| 响应式 | 断点约 `1050 / 1000 / 720 / 410`，战场 `nth-child` 布局被多次重写 |
| 无障碍 | 有部分 `:focus-visible`、`prefers-reduced-motion`，但不完整 |
| 气质方向 | 墨绿雾海 + 金玉强调，整体正确，宜保持 |

### 1.1 主要问题（按影响排序）

1. **单文件巨型、前后规则互相覆盖**（同一选择器定义 3～5 次），难改、易回归。  
2. **设计 token 不完整**，大量 `rgba(99,210,165,.xx)` 散落，改主题成本高。  
3. **战场信息字号普遍 6～9px**，手机上可读性与触控目标偏弱。  
4. **大厅示意仍是五格菱形样式**，与 3×3 九域战场视觉叙事不一致。  
5. **动画/滤镜/backdrop-filter 偏多**，中低端机与「减少动态」场景负担大。  
6. **z-index 分散**，overlay 层级靠约定而非体系。  
7. **Tailwind 与手写 CSS 双轨**，收益未吃满，反而增加心智负担。

---

## 2. 优化原则

1. **可读优先于装饰**（对齐 `PRODUCT.md`）：归属、射程、阶段、绝杀进度 > 光晕粒子。  
2. **Token 驱动**：颜色、间距、字号、圆角、阴影、时长只从变量出。  
3. **一层真相**：每个选择器在源码中只维护一处最终外观，历史补丁合并删除。  
4. **大厅华丽、战场克制**：大厅可仪式感；战斗内减少常驻动画。  
5. **双编码**：归属/敌我/核心不只靠颜色，配合描边、角标、图案。  
6. **动效短暂回落**：juice 是瞬时的，禁止成为新常态。  
7. **移动端先保证可点可读**，再谈精致。

---

## 3. 架构重构（强烈建议）

### 3.1 拆分文件（推荐目录）

```text
frontend/src/assets/styles/
  tokens.css          /* 设计变量、语义色、z-index、时长 */
  base.css            /* reset、body、排版、焦点环 */
  layout.css          /* topbar、footer、section-wrap、网格 */
  components.css      /* button、game-card、glass-panel、表单 */
  pages/
    home.css
    collection.css
    deck.css
    ranking.css
    profile.css
    rules.css
    auth.css
  battle/
    shell.css         /* battle-page 骨架、strip、hand */
    board.css         /* sites-grid、realm-site、connections */
    units.css         /* unit-chip、retreat */
    overlays.css      /* draw/phase/combat/initiative/victory */
    motion.css        /* keyframes、placement-flight */
  responsive.css      /* 或按文件内 media  closing，二选一避免双写 */
  reduced-motion.css
```

入口：

```css
/* main.css */
@import "./styles/tokens.css";
@import "./styles/base.css";
/* ... */
```

或在 `main.js` 按序 import。Vite 均可处理。

### 3.2 清理历史叠层（合并顺序）

当前逻辑层大致为：

| 层 | 内容 | 处理 |
|----|------|------|
| L0 | 五格 `nth-child` 菱形布局 | **删除**，只保留九宫 / 动态 boardSize |
| L1 | Interaction layer（引导、抽牌、争夺 FX） | 保留语义，去掉与 L3 冲突的位移/脉冲 |
| L2 | Nine-realm board（3×3 定位、射程、撤退） | 合并为 `board.css` 唯一真相 |
| L3 | 2026 tactical refresh | 以 L3 视觉为准，吸收进 board，删重复 |

**验收：** 全局搜索 `.realm-site:nth-child` 与 `.sites-grid`，每个属性在源码中只出现「一处桌面 + 必要 media」。

### 3.3 与 Tailwind 的分工建议

| 用 Tailwind | 用手写 CSS |
|-------------|------------|
| 间距、flex/grid 工具类（新页面） | 卡牌、场地、复杂 clip-path |
| 快速后台/表单 | 关键动画与游戏态 class |
| 响应式显示隐藏 `md:` | 品牌与战场视觉系统 |

不必强行重写全部为 utility；优先 **token + 组件 class 稳定**，新 UI 可混用 Tailwind。

---

## 4. Design Tokens 建议

### 4.1 扩展 `:root`（示例）

```css
:root {
  /* Brand */
  --color-ink: #e9f3ed;
  --color-muted: #8da29a;
  --color-bg: #06110e;
  --color-panel: #0c1c17;
  --color-panel-2: #10251e;
  --color-line: rgba(174, 215, 197, 0.14);
  --color-jade: #63d2a5;
  --color-jade-dark: #2a8f6c;
  --color-gold: #efc36f;
  --color-rival: #d16f6f;
  --color-ssr: #d59cff;
  --color-danger: #ef9a9a;
  --color-range: #9ecfff;

  /* Semantic game states */
  --owner-self-bg: linear-gradient(155deg, rgba(17, 55, 41, 0.98), rgba(7, 25, 19, 0.98));
  --owner-self-border: rgba(99, 210, 165, 0.58);
  --owner-rival-bg: linear-gradient(155deg, rgba(59, 27, 29, 0.97), rgba(25, 14, 16, 0.98));
  --owner-rival-border: rgba(209, 111, 111, 0.55);
  --owner-core-bg: linear-gradient(155deg, rgba(54, 43, 22, 0.98), rgba(25, 22, 14, 0.98));
  --owner-core-border: rgba(239, 195, 111, 0.64);
  --owner-neutral-bg: linear-gradient(155deg, rgba(15, 35, 28, 0.97), rgba(7, 20, 16, 0.98));

  /* Type colors (cards) */
  --type-site: #64d4a7;
  --type-unit: #f0c778;
  --type-spell: #8cb8ff;
  --type-secret: #d59cff;

  /* Typography scale（禁止战斗关键信息低于 10px 桌面 / 11px 触控） */
  --text-2xs: 0.6875rem; /* 11px */
  --text-xs: 0.75rem;    /* 12px */
  --text-sm: 0.8125rem;  /* 13px */
  --text-md: 0.875rem;   /* 14px */
  --text-lg: 1rem;
  --text-xl: 1.25rem;
  --font-display: "Noto Serif SC", "Microsoft YaHei", serif;
  --font-body: Inter, "Microsoft YaHei", system-ui, sans-serif;

  /* Space / radius */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-pill: 999px;

  /* Elevation */
  --shadow-sm: 0 8px 20px rgba(0, 0, 0, 0.28);
  --shadow-md: 0 22px 60px rgba(0, 0, 0, 0.35);
  --shadow-glow-jade: 0 0 24px rgba(99, 210, 165, 0.18);
  --shadow-glow-gold: 0 0 28px rgba(239, 195, 111, 0.2);

  /* Motion */
  --ease-out: cubic-bezier(0.2, 0.9, 0.25, 1);
  --ease-pop: cubic-bezier(0.16, 1.2, 0.27, 1);
  --dur-fast: 140ms;
  --dur-med: 240ms;
  --dur-slow: 420ms;

  /* Z-index scale */
  --z-base: 1;
  --z-board-fx: 10;
  --z-hand: 20;
  --z-chrome: 30;
  --z-drawer: 40;
  --z-toast: 50;
  --z-overlay: 70;
  --z-modal: 80;
  --z-top: 100;

  /* Layout */
  --content-max: 1180px;
  --battle-hand-h: 232px;
  --touch-min: 44px;
}
```

### 4.2 迁移策略

1. 先声明新变量，旧名 `--jade` 等 alias 兼容：`--jade: var(--color-jade)`。  
2. 用查找替换分批：战场归属色 → 卡牌 type → 按钮 → 其余。  
3. 禁止新增硬编码品牌色；Code review 卡点。

---

## 5. 战场样式专项

### 5.1 棋盘布局

**现状问题：**  
- 早期五格 `nth-child` 与九格定位并存。  
- 移动端曾在五格布局与九格布局间切换。  
- `placement-flight.to-site-N` 用 vw/vh 魔法数，4×4/5×5 会失准。

**建议：**

1. **只用 CSS Grid + 数据驱动位置**  
   - `grid-template-columns: repeat(var(--board-size, 3), minmax(0, 1fr))`  
   - 场地 `style="grid-column/row"` 或 class `site-r{row}-c{col}` 由 Vue 生成，**删除固定 nth-child 地图**。

2. **连线层 `.realm-connections`**  
   - 选中单位时高亮射程内边：`stroke` 提到 `--color-gold`，线宽略增。  
   - 非常驻全亮，避免「线网噪音」。

3. **placement 飞行**  
   - 优先用 Web Animations / FLIP 从手牌 DOM → 目标场地 DOM，替代 9 组 `--dx/--dy`。  
   - 若暂保留 CSS，按 `boardSize` 生成变量表，勿写死 0～8。

### 5.2 场地卡片 `.realm-site` 信息层级

建议固定信息层级（上→下）：

```text
[位置角标]              [距离/起点]
[归属徽章 己/敌/空]
        [纹章 + 名称]
        [效果一行]
   [积分 | 守力 | 容量 | 壁垒 n/2]
        [单位槽]
```

CSS 要点：

| 元素 | 建议 |
|------|------|
| 名称 | 桌面 ≥12px，移动 ≥11px，`font-display` |
| 效果 | 桌面可显示 1 行；移动用「ⓘ」+ 长按/点击展开，勿纯 `display:none` 无入口 |
| 指标 | 图标 + 数字，数字用 `tabular-nums` |
| 核心 | 常驻细金边 + 极慢呼吸（opacity 0.08～0.14），结算时短暂加强 |
| 可攻击 | 金虚线 + `选择` 角标即可；**减少整格 translate 脉冲**（易晕、挡阅读） |
| 超出射程 | 降饱和 + `超出射程` 文案保留；`pointer-events` 仍可点出说明 toast |
| 空槽 | 保持 2 条 slot，示意容量 |

### 5.3 归属双编码（必做）

```css
.realm-site.owner-player::before {
  content: "己";
  /* 角标样式：左上或右上小章 */
}
.realm-site.owner-rival::before { content: "敌"; }
.realm-site.owner-neutral::before { content: "空"; }
.realm-site.core .site-emblem { /* 冠冕已有 Crown 时，CSS 再加双线描边 */ }
```

色盲友好：己=实心三角/菱形，敌=斜线底纹（低对比纹理，勿花）。

### 5.4 单位条 `.unit-chip`

| 问题 | 建议 |
|------|------|
| 字号 5～7px | 名称 ≥10px，属性 ≥10px；窄屏可缩名称保留战/守/射 |
| 触控 | 高度 ≥36px（移动），撤退按钮常显或与选中态绑定 |
| 状态 | `.sealed` 不只 grayscale：加「封」字角标；`.exhausted` 加斜纹或「已动」 |
| 增益 | `.buff-power` / `.buff-range` 左边 2px 色条（金/蓝） |

### 5.5 绝杀 / 比分主信息

在 `player-strip` 或 `round-meter` 增加：

```css
.dominion-meter { /* 绝杀 1/2 */ }
.dominion-meter[data-level="1"] { /* 警告色 */ }
.dominion-meter[data-level="2"] { /* 危险脉冲，reduced-motion 下静态 */ }
```

与积分球视觉权重接近，避免关键信息只在日志里。

---

## 6. 卡牌样式 `.game-card`

### 6.1 现状

- 符号 + 径向渐变，无真正插画槽。  
- 类型靠 `--type-color`，稀有度仅边框微调。  
- compact 手牌字极小，效果在移动端直接隐藏。

### 6.2 建议

1. **统一卡面模板（纯 CSS 可先做）**

```css
.game-card[data-type="SITE"]  .card-art { /* 地形纹：斜切+菱形网格 */ }
.game-card[data-type="UNIT"]  .card-art { /* 竖向剪影光 */ }
.game-card[data-type="SPELL"] .card-art { /* 同心符环 */ }
.game-card[data-type="SECRET"].card-art { /* 星点噪点+紫雾 */ }
```

2. **稀有度系统**

| 稀有度 | CSS |
|--------|-----|
| C | 默认边框 |
| R | 边框略亮 + 费用环微光 |
| SR | 金边 + 顶部细扫光（`::after` 慢扫，reduced 关闭） |
| SSR | 紫边 + 外辉 + 抽牌时单独 class `.reveal-ssr` |

3. **手牌 compact**  
   - 保证费用、名称、类型 glyph 三者永可见。  
   - 效果可隐藏，但 **长按/右键详情** 样式与桌面一致（已有详情逻辑则只调 popup 层级与对比度）。

4. **选中态**  
   - 保持上浮 + 金边；手牌区注意 `overflow` 勿裁切上浮（`padding-top` 已有，检查移动 fixed hand）。

5. **费用不足 `.unaffordable`**  
   - 已有降饱和；可加费用数字变红，比整卡变灰更可读。

---

## 7. 大厅与外围页面

### 7.1 首页 Hero

| 项 | 建议 |
|----|------|
| `hero-board` | 改为 3×3 迷你九宫，去掉五格绝对定位类（`.site-a`～`.site-d`） |
| 交互 tilt | 保留，但 `prefers-reduced-motion` 下禁用 pointer tilt |
| 统计条 | 样式可保留，内容与真实规则对齐（属内容，CSS 负责对齐与换行） |
| `mode-grid` | 三卡已 `expanded`：统一卡片高度、主 CTA 位置，避免 absolute 按钮在多行文案时重叠 |

### 7.2 图鉴 / 卡组

- 图鉴网格：`auto-fill, minmax(160px, 1fr)` 替代固定 5 列，减少断点分支。  
- 卡组列表行高与 hover 态统一用 token。  
- 空状态 `.empty-state` 增加插画位 min-height，避免塌陷。

### 7.3 天梯 / 档案

- 领奖台在窄屏改为纵向列表（已有部分），注意 `order` 与无障碍阅读顺序一致。  
- 表格横向滚动时加渐变遮罩提示「可横滑」。

### 7.4 规则页

- `turn-flow` 五列在移动改时间线已有；注意连接线颜色对比。  
- `rule-callout` 作为唯一「金」强调块，其它卡片保持 jade，建立层级。

---

## 8. 动效与性能

### 8.1 分级（Importance tiers）

| 级 | 场景 | 允许 |
|----|------|------|
| 0 常驻 | 背景雾、极慢轨道 | opacity 动画，无 layout |
| 1 交互 | hover、选中、合法目标 | transform/border 140～240ms |
| 2 事件 | 部署落场、夺场 impact | 单次 300～500ms |
| 3 高潮 | 抽 SSR、绝杀、胜负 | overlay + 可选短震，≤1.2s |

**减负建议：**

- 取消单位/目标的 **无限 alternate 位移动画**，改为静态高亮 + 边框呼吸（仅 border-color）。  
- `filter: blur` + `backdrop-filter` 同时大面积使用时，overlay 蒙层用半透明纯色优先。  
- `box-shadow` 动画改为 `opacity` 叠层，避免每帧重绘大阴影。  
- 动画属性尽量只动 `transform` / `opacity`。

### 8.2 `prefers-reduced-motion` 补全清单

```css
@media (prefers-reduced-motion: reduce) {
  .orbit-b, .orb-two, .hint-dot,
  .realm-site.targetable,
  .unit-chip.selected,
  .unit-chip.targetable,
  .round-meter b.urgent,
  .placement-flight,
  .combat-overlay:before { animation: none !important; }

  .game-card:hover,
  .realm-site:hover { transform: none; }

  /* 保留瞬间状态色，去掉位移 */
}
```

抽牌/阶段切换：直接显示终态，`transition: opacity var(--dur-fast)` 即可。

### 8.3 含 `will-change` 的克制使用

仅在动画开始时加、结束时移除（JS class），避免长期 `will-change: transform` 占合成层。

---

## 9. 排版与无障碍

### 9.1 字号底线

| 场景 | 最小字号 |
|------|----------|
| 战场辅助标签 | 11px |
| 场地名 / 单位名 | 12px（移动 11px） |
| 正文 / 按钮 | 14px |
| 标题 | clamp 体系保持 |

当前大量 `7px/8px/9px` 建议分两期抬升，先战场与手牌。

### 9.2 对比度

- `--muted` 在 `--bg` 上约偏灰绿，小字可能不达标。  
- 关键路径文字（指令条、错误、可点按钮）使用 `--color-ink` 或提高 opacity 至 ≥0.72。  
- 敌方红字在深红底上检查 WCAG；必要时敌方用浅珊瑚 `#f0a8a8`。

### 9.3 焦点与触控

```css
:focus-visible {
  outline: 2px solid var(--color-gold);
  outline-offset: 2px;
}
button, .realm-site, .unit-chip, .game-card {
  /* 交互控件最小点击区域 */
  min-height: var(--touch-min); /* 桌面卡牌可豁免，移动手牌用 padding 扩大热区 */
}
```

- 撤退按钮移动端 **常显**（已有 opacity:1 分支，保持并加大到 20×20）。  
- 禁用态 `cursor: not-allowed` + `aria-disabled` 样式统一。

### 9.4 语义色不只靠色

合法目标：图标「◎」或虚线框。  
非法：文案芯片，不只靠变暗。

---

## 10. Z-Index 与层叠上下文

建议统一使用 token，并文档化：

| 层 | 用途 | z-index |
|----|------|---------|
| board | 场地、连线 | 1～5 |
| board-fx | placement、site impact | 10～18 |
| hand / strips | 手牌、玩家条 | 20～30 |
| drawer | 战斗日志 | 40 |
| toast / hint | 提示条 | 50 |
| fullscreen FX | draw/phase/combat/initiative | 70 |
| result | victory | 80 |
| system | 退出确认等 | 100 |

检查：`.battle-log`、`.hand-area`（移动 fixed）、`.victory-overlay` 是否击穿。

---

## 11. 响应式策略

### 11.1 断点收敛

建议统一为：

| Token | 宽度 | 用途 |
|-------|------|------|
| `--bp-sm` | 480px | 极窄手机 |
| `--bp-md` | 768px | 手机 / 竖屏 |
| `--bp-lg` | 1024px | 平板 / 小笔电 |
| `--bp-xl` | 1280px | 桌面 |

合并现有 410/720/1000/1050，减少「改一处漏三处」。

### 11.2 战场移动端布局原则

1. 顶部条 fixed 高度 token 化（`--battle-top-h`）。  
2. 手牌 fixed 底部，高度 token（`--battle-hand-h`）。  
3. 中间战场 `height: calc(100dvh - top - strips - hand)`，**避免页面整体滚动抢手势**。  
4. 九宫始终 3×3（或 N×N），不要退回五格特殊 grid。  
5. 次要文案进详情，不靠缩小到 5px。

### 11.3 横屏手机

可选：`@media (max-height: 500px) and (orientation: landscape)`  
压缩 strip、手牌高度，棋盘优先。

---

## 12. 可落地的 CSS 片段示例

### 12.1 合法目标（静态优先）

```css
.realm-site.targetable {
  border-color: var(--color-gold);
  box-shadow:
    0 0 0 1px rgba(239, 195, 111, 0.35),
    var(--shadow-glow-gold);
  animation: none; /* 去掉位移动画 */
}
.realm-site.targetable::after {
  content: "可选";
  /* 角标 */
}
@media (prefers-reduced-motion: no-preference) {
  .realm-site.targetable {
    animation: targetBorder 1.2s ease-in-out infinite;
  }
}
@keyframes targetBorder {
  50% { border-color: rgba(255, 220, 142, 0.95); }
}
```

### 12.2 归属角标

```css
.realm-site .owner-badge {
  position: absolute;
  left: 8px;
  top: 22px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 2px;
  font-size: var(--text-2xs);
  font-weight: 800;
  line-height: 18px;
  text-align: center;
}
.owner-player .owner-badge {
  color: #062019;
  background: var(--color-jade);
}
.owner-rival .owner-badge {
  color: #2a1214;
  background: #e89a9a;
}
.owner-neutral .owner-badge {
  color: var(--color-muted);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--color-line);
}
```

### 12.3 动态棋盘

```css
.sites-grid {
  --board-size: 3;
  display: grid;
  grid-template-columns: repeat(var(--board-size), minmax(0, 1fr));
  grid-template-rows: repeat(var(--board-size), minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
}
/* Vue: :style="{ '--board-size': game.boardSize }" */
```

### 12.4 安全区（刘海屏）

```css
.battle-topbar {
  padding-top: env(safe-area-inset-top);
}
.hand-area {
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}
```

---

## 13. 实施优先级

| 优先级 | 事项 | 预估 | 收益 |
|--------|------|------|------|
| **P0** | 合并重复战场规则，九宫为唯一布局源 | 中 | 可维护、修 bug 不回归 |
| **P0** | 抬升战场/手牌关键字号 + 触控热区 | 小 | 可玩可读 |
| **P0** | 归属角标双编码 | 小 | 策略可读 / 无障碍 |
| **P1** | tokens.css + 颜色替换 | 中 | 主题与一致性 |
| **P1** | 削弱无限位移动画，补全 reduced-motion | 小 | 舒适度 / 性能 |
| **P1** | 首页 hero 改为 3×3 示意样式 | 小 | 叙事一致 |
| **P1** | z-index token 化 | 小 | 叠层可控 |
| **P2** | 拆分 main.css 多文件 | 中大 | 长期协作 |
| **P2** | 卡面 type/rarity 模板强化 | 中 | 品质感 |
| **P2** | placement FLIP 替代魔法数 | 中 | 4×4/5×5 正确 |
| **P2** | 图鉴 `auto-fill` 网格 | 小 | 响应式简化 |
| **P3** | 主题切换（赛季皮肤）基于 token | 大 | 运营扩展 |

---

## 14. 验收清单

### 视觉 / UX

- [ ] 3×3 / 4×4 / 5×5 棋盘无错位、无残留五格定位  
- [ ] 3 米外能分清己方场 / 敌方场 / 核心（不只靠色相）  
- [ ] 选中单位后，射程内场地与超出场地差异一眼可辨  
- [ ] 手牌费用、名称在 375px 宽屏幕可读  
- [ ] 绝杀进度或等价主信息在战斗 HUD 可见（若玩法已实装）  

### 工程

- [ ] `main.css`（或拆分后）无「后段整段覆盖前段」的重复块  
- [ ] 新增颜色均来自 token  
- [ ] `prefers-reduced-motion: reduce` 下无持续位移动画  
- [ ] Lighthouse / 低端安卓：战斗页滚动与点选无明显掉帧  

### 无障碍

- [ ] 键盘 Tab 可见焦点环  
- [ ] 对比度：主按钮、错误、阶段指令达标  
- [ ] 移动触控目标 ≥ 44px 或等效 padding  

---

## 15. 不建议做的样式方向

| 避免 | 原因 |
|------|------|
| 全局玻璃拟态 + 重 blur | 性能差，信息发灰 |
| 战场常驻粒子 canvas/CSS 雪花 | 干扰读局 |
| 再缩小字号塞更多文案 | 损害可玩性 |
| 每张卡不同圆角/字体 | 破坏系列感 |
| `!important` 盖补丁 | 加速腐化（现有 `.inline-error` 等逐步消掉） |
| 亮色主题未先 token 化就硬写 | 必返工 |

---

## 16. 建议排期（示例 5 天）

| 日 | 任务 |
|----|------|
| D1 | 梳理并删除 L0 五格规则；棋盘改 `--board-size` |
| D2 | tokens + 归属角标 + 字号抬升 |
| D3 | 动效降噪 + reduced-motion + z-index |
| D4 | 首页 3×3 示意 + 卡面 type 模板 |
| D5 | 移动端 battle shell 高度公式 + 安全区 + 回归 |

---

## 17. 文档关系

| 文档 | 内容 |
|------|------|
| `PRODUCT.md` | 品牌与体验原则 |
| `优化建议.md` | 玩法 / 产品 / 优先级总览 |
| `CSS优化.md`（本文） | 样式架构、token、战场/卡牌/动效/无障碍 |

---

## 18. 总结

当前样式**气质正确、完成度高**，问题集中在：

1. **工程腐化**（叠写、巨型单文件）  
2. **信息设计**（过小字号、颜色单编码）  
3. **动效过满**（常驻脉冲与战场阅读抢权）  
4. **叙事不一致**（五格示意 vs 九域实战）  

优先做：**合并战场 CSS 真相源 → 抬字号与双编码 → token 化 → 动效降噪**。  
这四步不增加玩法代码，也能明显提升「像一款打磨过的战术卡牌」的观感与可维护性。

---

*数值与断点为建议稿，实装后以真机（375 / 390 / 768 / 1440）截图对比验收。*
