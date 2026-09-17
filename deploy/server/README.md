# 服务器部署与运维手册

**已部署环境**：`10.191.19.25`（Ubuntu 20.04 / 4 核 8G / 85G 可用）
**部署目录**：`/opt/hz-delivery`
**访问入口**：`http://10.191.19.25/`（校园网内）

---

## 一、这次部署做了什么

| 步骤 | 内容 |
| --- | --- |
| 1 | 安装 `docker.io` 26.1.3 + `docker-compose-v2` 2.27.1（apt） |
| 2 | 配置镜像加速 `https://docker.m.daocloud.io` —— 该服务器**直连 Docker Hub 不通**，不加这个拉不到镜像 |
| 3 | 基础镜像：`mysql:8.0`、`redis:7.4-alpine`、`nginx:1.27-alpine` 由服务器自行拉取 |
| 4 | 后端镜像 `hz-delivery-server:1.0.0` 在开发机构建后 `docker save` → SFTP → `docker load`（**服务器上不需要 JDK/Maven/外网依赖**） |
| 5 | 生成生产 `.env`（密码在服务器上随机生成，权限 600） |
| 6 | 启动四个容器，`db/init.sql` 自动建表并导入演示数据 |
| 7 | 验证：网关健康、后台登录、C 端登录、越权拦截、开机自启 |

**初始凭据**在服务器上：`/root/hz-initial-credentials.txt`（权限 600，含后台初始密码与数据库密码）。
用你的 SSH 工具 `cat /root/hz-initial-credentials.txt` 查看。**首次登录后台后请立即修改密码。**

---

## 二、目录结构

```
/opt/hz-delivery/
├── db/init.sql                        建表 + 演示数据（只在数据库卷为空时执行一次）
├── miniapp/index.html                 教职工端 H5
├── admin/index.html                   管理后台
└── deploy/
    ├── nginx/nginx.conf               反向代理（/api → hz-api:8080）
    └── server/
        ├── docker-compose.yml         服务器编排（只暴露 80）
        ├── .env                       ★ 生产配置（含密码，权限 600）
        └── .env.example               配置模板
```

---

## 三、端口与安全

| 容器 | 对外端口 | 说明 |
| --- | --- | --- |
| hz-nginx | **80** | 唯一对外入口 |
| hz-api | 无 | 仅内部网络，由 Nginx 反代 |
| hz-mysql | 无 | 仅内部网络 —— **校园网内其他机器扫不到数据库** |
| hz-redis | 无 | 仅内部网络 |

> 这是与开发机 `docker-compose.yml` 的关键差异。开发机上为了方便调试会映射 3306/6379/8080，
> **服务器上不要这样做**。

---

## 四、常用运维命令

```bash
cd /opt/hz-delivery/deploy/server

# 查看状态与健康
docker compose ps
docker inspect --format '{{.State.Health.Status}}' hz-api

# 看日志
docker compose logs -f api          # 后端
docker logs hz-nginx 2>&1 | tail -50  # Nginx 访问日志（含来源 IP、UA）
docker logs hz-mysql 2>&1 | tail -30

# 重启 / 停止 / 启动
docker compose restart api
docker compose stop
docker compose up -d

# 进数据库
docker exec -it hz-mysql mysql -uroot -p hz_delivery

# 备份数据库（建议加进 crontab）
docker exec hz-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" hz_delivery \
  | gzip > /root/backup/hz_$(date +%F_%H%M).sql.gz
```

---

## 五、更新部署（改了后端代码怎么办）

后端镜像是在**开发机**构建的，服务器不参与编译。所以流程是：

```bash
# ① 开发机：重新构建镜像
docker compose build api
docker save hz-delivery-server:1.0.0 | gzip -1 > hz-api.tar.gz
# 把 hz-api.tar.gz 传到服务器（scp / sftp）
# ② 服务器：加载并重启
docker load -i /tmp/hz-api.tar.gz
cd /opt/hz-delivery/deploy/server && docker compose up -d
```

前端（`miniapp/` / `admin/`）是静态文件，直接覆盖对应目录即可，**无需重启容器**
（Nginx 以只读方式挂载，改完刷新浏览器就生效）。

---

## 六、复位演示数据

⚠️ **会清空所有数据**（包括已提交的领取信息与订单）。

```bash
cd /opt/hz-delivery/deploy/server
docker compose down
docker volume rm hz-mysql-data        # 删掉数据卷，下次启动会重新执行 init.sql
docker compose up -d
docker compose exec mysql mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD"   # 等 healthy
```

> 注意：`db/init.sql` **不含后台账号种子**，后台账号由应用启动时的 `DataInitializer` 创建
> （仅当 `t_admin` 为空）。所以复位后必须让 `hz-api` 也重新启动一次，否则后台登录会失败。

---

## 七、访问入口与上线到外面

**已做端口映射**：内网 `10.191.19.25:80` → 公网 **`http://221.192.236.175/`**
（经 5 个海外节点实测 80 端口连通，接口契约检查在公网地址上 44/44 通过）。

| 场景 | 能否访问 |
| --- | --- |
| 校园网内 / 校外浏览器访问 H5、后台 | ✅ 可以 |
| 开发者工具模拟器、真机调试（勾「不校验合法域名」） | ✅ 可以 |
| 云函数转发后端 | ✅ 可以 |
| **体验版 / 正式版小程序的 `wx.request`** | ❌ **不可以** |

**为什么正式版不行**：微信要求 `wx.request` 的目标必须同时满足
**HTTPS + 已 ICP 备案的域名**，而 **IP 地址不能登记为合法域名**、http 也会被拒。
所以 `http://221.192.236.175` 只能用于开发调试。正式上线二选一：

| 路径 | 做法 | 备案 | HTTPS 证书 |
| --- | --- | --- | --- |
| **① 云函数转发**（推荐） | 小程序 → `wx.cloud.callFunction` → 云函数 → `http://221.192.236.175/api` | 不需要 | 不需要 |
| ② 直连 | 申请域名 → ICP 备案 → 配 HTTPS → 登记「服务器域名」 | 必须 | 必须 |

> 走 ① 时，小程序端只需把 `miniprogram/config.js` 的 `useCloud` 改为 `true` 并填云环境 ID，
> 云函数环境变量 `BACKEND_BASE_URL` 填 `http://221.192.236.175`（不带 `/api`）。

**安全提醒（站点已暴露在公网）**

- 只有 80 端口对外开放，MySQL/Redis/API 均不可从外部直连 ✔
- 后台已用 12 位随机密码，且登录失败 5 次锁定 10 分钟 ✔
- ⚠️ 目前是 **HTTP 明文**，登录凭据在链路上不加密。正式对外使用前建议上 HTTPS
  （需要先有域名；Let's Encrypt 无法给纯 IP 签发证书）

---

## 八、排错

| 现象 | 原因与处理 |
| --- | --- |
| 拉镜像超时 | 镜像加速未生效。检查 `/etc/docker/daemon.json` 后 `systemctl restart docker` |
| MySQL 反复重启、日志报 `01-init.sql: Permission denied` | 挂载的 `init.sql` 权限过严。`chmod 644 /opt/hz-delivery/db/init.sql`，然后删数据卷重启 |
| 后台登录提示密码错误 | 复位数据后 `t_admin` 为空，需重启 `hz-api` 让 `DataInitializer` 建账号 |
| 页面 502 | 后端未就绪。`docker logs hz-api` 看启动是否正常 |
| 手机真机预览打不开接口 | 手机不在校园网内，或开发者工具未勾选「不校验合法域名」 |
| 容器 `unhealthy` 但服务可用 | 检查内存上限：`docker stats`；本机 8G，四个容器上限合计约 4.2G |
| 开发者工具「预览 / 上传 / 自动化」报 `ECONNREFUSED 127.0.0.1:7890` | **开发者工具自己配了手动代理且该代理没运行**（常见于装过 Clash 的机器）。到「设置 → 代理设置」改为**不使用任何代理**，或先把代理软件启动起来 |
| 手机打开页面顶部有一大块演示信息、底部导航被截断 | 已修复（H5 加了手机端全屏适配）。若仍出现，检查浏览器是否命中了 `@media (max-width:640px),(pointer:coarse),(max-height:520px)` |

> **排查小技巧**：想知道小程序/浏览器到底有没有打到后端，看 Nginx 日志即可分辨来源 ——
> `docker logs hz-nginx 2>&1 | tail -30`。
> UA 里带 `wechatdevtools` 的是开发者工具模拟器，带 `MicroMessenger` 的是微信内浏览器，
> `curl` / `Python-urllib` 是我的自动化校验。
