# 统一部署与维护手册

当前采用 **GitHub 拉取指定版本源码 → Docker Compose 构建后端 → Nginx 提供页面与 API**。本手册用于后续本地验证和服务器部署，不表示已经完成上线。

`heytao.top` 正式域名的配置、续期及后续更新见[域名HTTPS发布与更新](../../docs/域名HTTPS发布与更新.md)。发布配置与业务代码一起进入版本标签；证书、私钥和 `server.env` 独立保存在服务器。

目标服务器 `118.89.27.5` 为 2GB 内存，按用户选定的 **本地修改 → 提交 GitHub 并打版本标签 → 服务器拉取指定标签 → 服务器构建镜像 → 更新容器** 流程部署。具体命令见 [2GB 主机部署说明](SERVER-2GB.md)；该主机必须合并 `compose.2gb.yml`。GitHub、镜像仓库及 Maven 下载需先通过目标主机网络检查。

原服务器地址、网络连通性和历史部署记录移至[旧环境记录](../../docs/archive/服务器部署记录-旧环境.md)，本手册不预设目标服务器 IP。

## 目录与运行方式

保留完整仓库结构。所有 Compose 命令在仓库的 `deploy/server/` 目录执行：

```text
仓库根目录/
├── server/                         Dockerfile、pom.xml、settings.xml、src/
├── admin/index.html                管理后台
├── miniapp/index.html              教职工端 H5
├── db/init-production.sql          正式空库初始化
└── deploy/
    ├── nginx/nginx.conf
    └── server/
        ├── docker-compose.yml     唯一 Compose 入口
        ├── .env.example           配置模板
        ├── .env                   本机/服务器单独创建，不提交 Git
        └── README.md
```

| 服务 | 镜像/来源 | 对宿主机映射端口 | 持久化 |
| --- | --- | --- | --- |
| MySQL | `mysql:8.0` | 无 | `hz-mysql-data` |
| Redis | `redis:7.4-alpine` | 无 | `hz-redis-data`，AOF |
| API | 从 `../../server` 构建 `hz-delivery-server:<APP_VERSION>` | 无 | `hz-api-logs` 挂载 `/app/logs` |
| Nginx | `nginx:1.27-alpine` | `WEB_PORT`，默认 80 | 页面、配置为只读绑定挂载 |

容器名仍为 `hz-mysql`、`hz-redis`、`hz-api`、`hz-nginx`，卷名与原配置一致。四个容器均配置 stdout/stderr 日志轮转，每个容器最多 3 个 10 MB 日志文件。应用主要输出到容器日志；`/app/logs` 挂载不代表应用已配置文件日志。

MySQL、Redis 健康后 API 才启动；API 健康后 Nginx 才启动。页面文件直接挂载源码目录，所以切换 Git 版本时页面内容也会变化，更新流程按短暂停服安排。

## 首次部署准备

宿主机需有 Git、Docker Engine 和支持本配置的 Docker Compose v2（建议 v2.20 或更新版本，使用 `docker compose`）。服务器无需单独安装 Java、Maven 或 Node。

首次构建需要联网获取 Docker 基础镜像、阿里云 Maven 依赖以及 Alpine 系统包。镜像仓库、Maven 地址的连通性要在目标服务器单独验证；旧环境的镜像加速配置不能直接视为当前可用。

Linux 示例中的仓库地址和版本号需替换为实际值，当前尚未创建发布标签：

```bash
git clone <GitHub仓库地址> /opt/hz-delivery
cd /opt/hz-delivery
git fetch --tags
git switch --detach v1.0.0
cd deploy/server
cp .env.example .env
chmod 600 .env
```

Windows 本地验证在项目根目录执行：

```powershell
Set-Location deploy/server
Copy-Item .env.example .env
```

仅在目标 `.env` 不存在时复制，已有配置直接编辑，不用模板覆盖。根目录原有 `.env` 不会自动供此入口读取，也不要把开发密码直接沿用到服务器。

## 配置 `.env`

| 配置项 | 处理方式 |
| --- | --- |
| `APP_VERSION` | 本地验证用 `local`；服务器用对应 Git 发布标签，例如 `v1.0.0`。不要把不同代码反复构建成同一个发布版本 |
| `MYSQL_ROOT_PASSWORD` | 必填。留空会拒绝启动；新建库时使用，不能通过改此项直接修改已有数据库密码 |
| `ADMIN_INIT_PASSWORD` | 必填。用于应用启动时补齐缺失账号，不重置已有账号密码 |
| `WEB_PORT` | 默认 80；若占用则修改，并同步实际访问地址 |
| `MYSQL_DATABASE` | 默认 `hz_delivery`；已有数据卷沿用原库名，不能靠改变量迁移数据库 |
| `LOGIN_MODE` | 正式环境保持 `sms`；`name` 仅用于隔离演示 |
| `SMS_AUTH_PROVIDER` | `local` 兼容旧通道；阿里云登录验证码选 `aliyun-pnvs`，配置密钥、PNVS 签名与模板，详见[阿里云认证接入](../../docs/阿里云短信认证接入.md) |
| `SMS_PROVIDER` | 独立的通知短信通道；`mock` 不真实发送，`hbu` 需配置平台密钥。选择 PNVS 不会自动开通通知短信 |
| `MOCK_SMS_CODE` | 模板留空；本地 mock 联调若需固定验证码可显式设置，正式使用时留空 |
| `SMS_TEST_ENABLED` | 默认 `false` |
| `LOGISTICS_PROVIDER` | 默认 `disabled`；开通真实查询后再填密钥并切换 |

模板中的两个密码故意留空，避免用占位密码直接启动。配置语法为 `KEY=value`；含 `$` 等特殊字符的字面值建议使用单引号，避免 Compose 插值。不要把真实配置提交 Git 或粘贴到文档。

其他业务参数沿用模板说明。云函数与小程序配置不受此 `.env` 自动控制。

## 检查、构建与启动

以下命令均在 `deploy/server/` 执行：

```bash
# 仅校验配置，不输出包含密码的完整配置
docker compose config --quiet

# 构建阶段使用 Java 17，无需修改本机默认 Java
docker compose build api

# 构建成功后启动；等待服务就绪
docker compose up -d --no-build --wait
docker compose ps
```

API 配置 `pull_policy: never`，后端镜像由上一步本地构建；不会尝试从 Docker Hub 拉取同名业务镜像。MySQL、Redis、Nginx 镜像仍需首次拉取。已有离线业务镜像也可 `docker load` 后运行，但镜像标签必须与 `APP_VERSION` 一致。

Dockerfile 默认跳过 Maven 测试，发布前应先在本地运行对应测试；配置解析成功不等于服务器构建或 HTTP 验证成功。当前本地测试记录见[本地部署验证记录](../../docs/本地部署验证记录-20261022.md)，目标服务器仍需独立验收。

首次空库只建立表和承运商基础数据，无演示人员、套餐、公告或订单。首次登录后台修改密码，然后建立正式套餐、公告，导入人员名单，最后开放领取。详见[数据库说明](../../db/README.md)。

## 访问与验证

将 `<服务器地址>` 替换为 IP 或域名；`WEB_PORT` 不是 80 时带上端口。

| 路径 | 含义 |
| --- | --- |
| `http://<服务器地址>/` | 教职工端 H5 |
| `http://<服务器地址>/admin/` | 管理后台 |
| `http://<服务器地址>/api/…` | 实际业务接口路径，`/api/` 本身不是健康端点 |
| `http://<服务器地址>/healthz` | Nginx 存活，应返回 `ok`，不能证明后端正常 |
| `http://<服务器地址>/actuator/health` | 后端健康，应返回 `status: UP`，含数据源/Redis 健康检查 |

当前 Nginx 模板只提供 HTTP；公网正式部署时还需配置 HTTPS。后台账号为 `admin`、`operator`、`viewer`，初始密码取自配置，首次登录需修改。

微信原生小程序与云函数需另外发布：核对 `miniprogram/config.js` 中的云环境 ID、AppID 和调试地址，在云函数配置 `BACKEND_BASE_URL` 为新后端根地址（不带 `/api`）。当前源码仍有旧环境地址作为注释或兜底，本次不自动改为未知服务器地址。详见[小程序说明](../../miniprogram/README.md)与[云函数说明](../../cloudfunctions/api/README.md)。

## 数据库备份

在 Linux/Bash 中，于 `deploy/server/` 执行。密码从容器环境读取，无需把 `.env` 导出到宿主 shell：

```bash
umask 077
mkdir -p ../../backups
backup_file="../../backups/hz_$(date +%F_%H%M%S).sql"
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --routines --triggers --events "$MYSQL_DATABASE"' > "$backup_file"
```

确认命令退出码为 0、文件非空，并定期在隔离数据库演练恢复。失败命令可能留下不完整 SQL 文件。备份包含业务与个人数据，`backups/` 已忽略，不提交 Git；应另存到服务器之外。Windows PowerShell 不直接照搬上述重定向命令，避免旧版本 PowerShell 改写 SQL 编码。

## 更新与回滚

以下为后续发布路线，版本号只是示例。先记录当前 Git 标签、`APP_VERSION`，完成数据库备份并核对 `git status --short`，不要在服务器直接修改业务源码。

由于静态页面绑定源码目录，安排维护窗口，先停止入口再切换代码：

```bash
# deploy/server/ 下先执行备份，再停止访问入口
docker compose stop nginx
cd ../..
git fetch --tags
git switch --detach v1.0.1
cd deploy/server
# 编辑 .env：APP_VERSION=v1.0.1；同步新版本新增的配置项，保留原密码
docker compose config --quiet
docker compose build api
docker compose up -d --no-build --wait
docker compose ps
```

每条命令成功后再执行下一条。构建失败时停止更新，排查后重试或按下面步骤回滚。更新后核对后端健康、页面、登录和关键业务，再结束维护窗口；配置修改通过 `up -d` 重建相关容器生效，仅 `restart` 不会读取新的环境变量。

回滚时保持相同部署目录、容器名和数据卷：停止 Nginx，Git 切回上一标签，将 `.env` 中 `APP_VERSION` 改回原版本，确认旧镜像仍存在后执行 `docker compose up -d --no-build --wait`。静态页面与后端应切回同一发布版本；若旧镜像已清理，需要从旧版源码重新构建。

回滚应用不会还原数据库。涉及表结构变化时，先明确迁移和恢复兼容性再发布。普通更新不执行初始化 SQL，不使用 `down -v` 或删除数据卷。

## 常用维护与排错

IP地址HTTPS及证书自动续期见[IP地址HTTPS部署](../../docs/IP地址HTTPS部署.md)。2GB版本部署脚本通过私密配置的 `TLS_ENABLED=true` 保留HTTPS覆盖，证书不进入Git。

首次服务器部署遇到的 SSH、镜像下载、Buildx、APK 源、源码解包权限和空库初始化问题，见[部署问题与排查记录](../../docs/部署问题与排查记录.md)，其中包含已完成的 `v1.0.1` 运行验收结果。

```bash
docker compose logs --tail=100 api
docker compose logs --tail=100 nginx
docker compose logs --tail=100 mysql
docker compose stop
docker compose up -d --no-build --wait
```

| 现象 | 处理 |
| --- | --- |
| 提示缺少密码或 `APP_VERSION` | 检查当前目录及 `deploy/server/.env`，填好后重新校验 |
| 后端镜像不存在 | 核对版本号，先执行 `docker compose build api` |
| 镜像/Maven 下载失败 | 核对目标服务器到对应仓库的网络；不要假定旧代理或镜像源仍有效 |
| 绑定挂载文件不存在 | 确保完整仓库目录存在；配置故意不自动创建缺失的文件路径 |
| API 不健康、Nginx 等待 | 查看 API、MySQL、Redis 日志及内存；不要只检查 `/healthz` |
| 改密码变量后数据库连接失败 | 旧数据卷的数据库密码未随变量改变，恢复一致配置或单独安排数据库改密 |
| 初始化中断 | 先查 MySQL 日志和已有表，按实际状态处理，不直接删卷 |

旧材料引用的 `.workbuddy/tools/` 脚本当前未交付，不作为本手册的依赖。仓库根目录旧 Compose 文件在本次整理前已删除，当前统一使用此目录。
