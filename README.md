# 《场地弈境》全栈项目

> 赢的不是伤害，是掌控域面的主动权。

本项目是可直接运行的 **Spring Boot + Vue 3** 原创控场卡牌游戏。核心不是扣血，而是用「边长 = 灵力」的可变棋盘部署场地、驻扎单位、射程争夺，并通过**绝杀**或**终局积分**取胜。

## 已完成内容

### 可玩的核心对局

- **可变棋盘**：3×3 / 4×4 / 5×5；灵力 = 边长；总回合 = 边长 × 3。
- 核心场地积分 ×2；四角**边陲**部署费 -1、积分计 0。
- 初始手牌 4 张（保证至少 1 张场地）；每回合抽 2；手牌上限 7。
- 场地 / 单位 / 瞬发术式 / SSR 秘策四类卡牌。
- 单场最多 2 单位；永不阵亡；支持**调防**（1 灵力邻接移动）、覆盖改造场地。
- **邻接协同**（守力+1、结算+1）、**夹击**（≥2 邻接己方时战力+1）。
- 单位状态：封印、疲惫、动摇、扎根、行军（新驻表现）。
- **气势** 0～3：争夺成败增减；满层免费 1 费术式。
- **筛牌**（弃 1 抽 1 / 回合）与弃牌收益。
- 壁垒需连续两次突破；绝杀进度 1/2 打断即重置（HUD 可见）。
- 终局回合不可新部署场地。
- AI 难度：入门 / 标准 / 困难（保核心、破绝杀威胁）。
- 教程局、残局「核心突破」、主题卡组流派。
- 人机阶段 90 秒、排位/PVP 60 秒；WebSocket 同步。

### 卡牌与外围系统

- 约 30 张卡牌 + 6 套主题 40 张构筑（均衡/壁垒/游猎/锻场/运营/绝杀）。
- 构筑约束：40 张、同名≤2、SSR≤1、场地≥10、单位≥12。
- 首页、图鉴（流派标签）、卡组、规则、天梯、档案、管理端。
- 响应式布局；Docker / Nginx 部署配置。

## 技术结构

```text
car game/
├─ frontend/                 Vue 3 + Vite + Pinia + Tailwind CSS
│  ├─ src/views/             大厅、对战、图鉴、卡组、天梯、档案、规则
│  ├─ src/components/        卡牌、场地、单位组件
│  └─ src/services/api.js    与视图解耦的统一请求层
├─ backend/                  Spring Boot 3 + WebSocket + MyBatis-Plus
│  ├─ domain/                卡牌、玩家、单位、场地、对局状态
│  ├─ service/               卡牌目录与权威规则引擎
│  └─ controller/            REST 对局与外围数据接口
├─ deploy/nginx.conf         生产反向代理配置
├─ docker-compose.yml        MySQL + 后端 + 前端
├─ start-dev.ps1             Windows 一键启动
└─ stop-dev.ps1              Windows 一键停止
```

## 本地启动（推荐验收方式）

环境要求：Node.js 20+、npm、Java 17、Maven 3.9+。

### 一键启动

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

等待数秒后访问：

```text
http://localhost:5180
```

停止服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
```

### 分别启动

后端：

```powershell
cd backend
mvn spring-boot:run
```

前端（另开终端）：

```powershell
cd frontend
npm install
npm run dev
```

## 生产构建

```powershell
cd backend
mvn clean package

cd ..\frontend
npm ci
npm run build
```

产物：

- 后端：`backend/target/field-realm-server-1.0.0.jar`
- 前端：`frontend/dist/`

Docker 部署：

```powershell
docker compose up --build
```

然后访问 `http://localhost`。

## 对局操作

1. 点击手牌中的场地卡，再点击无主或己方场地部署/覆盖。
2. 点击单位卡，再点击己方场地驻扎。
3. 部署阶段点击己方单位进入**调防**，再点相邻己方空位（耗 1 灵力）。
4. 使用「筛牌」弃 1 抽 1（每回合限 1 次）。
5. 术式选目标；满气势可免费打 1 费术式。
6. 点击「进入争夺」，选单位再点射程内敌方场地。
7. 「结束回合」结算积分与绝杀进度，并执行 AI。
8. 快捷键：`C` 进入争夺/完成部署、`E` 结束回合、`Esc` 取消选择。

## 创建对局可选参数

`POST /api/matches` body 示例：

```json
{
  "mode": "AI",
  "boardSize": 3,
  "aiDifficulty": "normal",
  "scenario": "standard",
  "deckArchetype": "bastion",
  "puzzleId": null
}
```

- `scenario`: `standard` | `tutorial` | `puzzle`
- `aiDifficulty`: `easy` | `normal` | `hard`
- `deckArchetype`: `balanced` | `bastion` | `ranger` | `forge` | `draw` | `dominion`

## 核心接口

| 方法 | 地址 | 用途 |
|---|---|---|
| GET | `/api/cards` | 获取全部卡牌 |
| POST | `/api/matches` | 创建 AI/PVP 对局 |
| GET | `/api/matches/{id}` | 获取权威对局状态 |
| POST | `/api/matches/{id}/cards` | 打出卡牌 |
| POST | `/api/matches/{id}/contest` | 进入争夺阶段 |
| POST | `/api/matches/{id}/attacks` | 发起场地争夺 |
| POST | `/api/matches/{id}/end-turn` | 结算并结束回合 |
| POST | `/api/matches/{id}/discard` | 手牌超限时弃牌 |
| WS | `/ws` + `/topic/matches/{id}` | 对局状态实时广播 |

## 数据库切换

默认使用内存 H2，开箱即可运行。使用 MySQL 时设置：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/field_realm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='fieldrealm'
$env:DB_PASSWORD='your_password'
$env:DB_DRIVER='com.mysql.cj.jdbc.Driver'
```

## 验证命令

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
npm audit
```

当前自动化测试覆盖卡组合法性、开局必有场地、初始资源、五格棋盘和场地部署规则。

## 后续正式运营扩展点

当前版本重点是完整可验收的核心玩法与全栈架构。正式上线时可在既有接口上继续接入 JWT 登录、匹配队列、好友房间、数据库对局快照、卡牌收藏持久化、赛季结算、回放存档、支付与小游戏平台授权；核心规则服务与前端请求层无需重写。

## 邮箱验证码登录

管理员登录后进入 **管理后台 → 邮件配置**，可直接填写 SMTP 参数、保存并发送测试邮件。QQ 邮箱推荐配置：

- SMTP 服务器：`smtp.qq.com`
- 端口：`465`
- SSL：开启
- 发件邮箱：完整 QQ 邮箱地址
- SMTP 授权码：在 QQ 邮箱设置中开启 SMTP 服务后生成，**不是邮箱登录密码**

启用后：

1. 新用户注册时必须接收并填写 6 位邮箱验证码；
2. 已绑定邮箱的用户可在登录页选择“邮箱验证码”；
3. 验证码 5 分钟有效、60 秒内不可重复发送，验证成功后立即失效；
4. 管理后台的“用户列表”可查看账号、邮箱绑定状态、角色、积分和战绩。

也可以通过环境变量提供初始配置：

```powershell
$env:MAIL_ENABLED='true'
$env:MAIL_HOST='smtp.qq.com'
$env:MAIL_PORT='465'
$env:MAIL_SSL='true'
$env:MAIL_USERNAME='your-account@qq.com'
$env:MAIL_PASSWORD='your-smtp-authorization-code'
$env:MAIL_FROM_NAME='场地弈境'
```

后台保存的配置位于 `backend/data/mail-settings.json`。请限制该文件的访问权限，不要提交真实 SMTP 授权码。
