# 《场地弈境》全栈项目

> 赢的不是伤害，是掌控所有场地的主动权。

本项目依据根目录的游戏设计文档完整实现，是一个可直接运行、可验收的 **Spring Boot + Vue 3** 原创卡牌游戏 MVP。核心对局不是削减生命值，而是利用 3 点回合灵力部署场地、驻扎单位、发动术式并争夺五个场地的归属。

## 已完成内容

### 可玩的核心对局

- 固定五格棋盘：四个次级场地 + 一个积分权重 ×2 的核心场地。
- 每回合 3 点灵力，回合结束清空，不跨回合累计。
- 初始手牌 4 张，并保证至少包含 1 张可部署场地卡。
- 每回合抽 2 张，超过 7 张时必须手动弃牌。
- 场地、单位、瞬发术式、SSR 秘策四类卡牌。
- 单场最多驻扎 2 个单位，单位无生命值、不会阵亡、永久驻场。
- 自动计算进攻战力、场地守力、驻场单位守力和场地增益。
- 壁垒场地需要连续两次成功突破，核心场地忽略壁垒额外保护。
- 压倒性争夺、镜潮反制、场地常驻积分等联动。
- 双胜利条件：稳定控制 3 个场地触发场地绝杀；第 8 回合按总积分结算。
- 完整人机对手：自动部署、驻军、选择薄弱场地并发起争夺。
- 对局日志、阶段引导、快捷键、音效反馈、结算动画与一键再战。

### 卡牌与外围系统

- 21 张首发卡牌定义，含场地、单位、术式与秘策。
- 40 张合法新手卡组，符合场地卡、单位卡和稀有度数量约束。
- 首页、藏品图鉴、卡组构筑、规则手册、赛季天梯、个人档案。
- PC 宽屏与手机竖屏响应式布局，支持鼠标和触屏点击。
- WebSocket/STOMP 对局状态广播接口，为双人实时联机继续扩展。
- MySQL、MyBatis-Plus、H2 本地零配置数据库依赖已接入。
- Docker Compose、Nginx 反向代理和前后端容器化配置。

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

1. 点击手牌中的场地卡，再点击无主场地完成部署。
2. 点击单位卡，再点击己方场地完成驻扎。
3. 术式卡按效果选择单位目标，抽牌类术式会立即结算。
4. 点击“进入争夺”，再依次点击己方单位和敌方场地。
5. 点击“结束回合”，系统结算积分并自动执行 AI 回合。
6. 快捷键：`C` 进入争夺、`E` 结束回合、`Esc` 取消选择。

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
