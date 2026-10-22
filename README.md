# 华中商贸配送中心

教职工慰问品领取与配送管理项目，包含名单管理、套餐选择、配送信息采集、订单发货、物流查询、SLA 预警和操作审计。

采用 **本地修改和测试 → 提交 GitHub 并发布标签 → 服务器拉取指定版本 → Docker 构建 → 更新容器**。部署配置位于 [`deploy/server/`](deploy/server/README.md)；目标服务器 `118.89.27.5` 按 [2GB 主机部署说明](deploy/server/SERVER-2GB.md) 操作。

## 项目目录

| 路径 | 用途 | 部署去向 |
| --- | --- | --- |
| `server/` | Java 17 / Spring Boot 3.2.5 后端、Maven 配置、测试和 Dockerfile | 构建为后端镜像 |
| `admin/` | 原生 HTML / JavaScript 管理后台 | Nginx 静态目录，入口 `/admin/` |
| `miniapp/` | 教职工端 H5 页面 | Nginx 静态目录，入口 `/` |
| `miniprogram/` | 微信原生小程序页面与配置 | 微信开发者工具上传 |
| `cloudfunctions/api/` | 小程序请求转发云函数 | 微信云开发单独部署 |
| `db/init-production.sql` | 正式环境空库建表及承运商基础数据 | MySQL 空数据卷首次启动执行 |
| `db/init.sql` | 原有演示脚本，包含删表与演示数据 | 仅隔离演示库使用 |
| `deploy/server/` | 统一 Compose、配置模板和部署手册 | 本地验证 / 服务器部署 |
| `deploy/nginx/` | 静态页面与 API 反向代理配置 | Nginx 容器 |
| `docs/` | 文档导航、业务解析、交接和历史说明 | 开发维护参考 |
| `docs/archive/` | 旧版方案与旧环境部署记录 | 历史追溯 |
| `project.config.json` | 从仓库根目录导入微信项目的配置 | 开发工具使用 |

`miniapp/` 和 `miniprogram/` 分别是 H5 与微信原生小程序，均须保留。源码目录不做重命名。

## 从哪里开始

1. 阅读[文档导航](docs/README.md)，区分当前操作手册与历史记录。
2. 按[部署手册](deploy/server/README.md)创建 `deploy/server/.env` 并配置密码、发布版本和短信通道。
3. 在 `deploy/server/` 执行：

   ```bash
   docker compose config --quiet
   docker compose build api
   docker compose up -d --no-build --wait
   ```

   首次构建需要联网下载镜像、Maven 依赖和系统包；宿主机无需另装 Java 或 Maven。Dockerfile 当前跳过测试，构建成功不能代替测试通过。
4. 检查 `/healthz`、`/actuator/health` 和管理后台。首次登录修改初始密码，再录入正式套餐、公告并导入人员名单。
5. 微信小程序和云函数按各自目录的说明配置、上传，它们不由 Compose 自动发布。

## 配置边界

- **统一部署配置：**`deploy/server/.env`，从同目录 `.env.example` 复制。密码默认留空，填入后才可通过 Compose 校验。
- **根目录 `.env`：**原有本地联调配置，继续保留；不自动供统一部署入口读取。根目录 `.env.example` 作为旧联调模板保留。
- **小程序：**`miniprogram/config.js`；云函数后端地址由云端 `BACKEND_BASE_URL` 配置，根地址不带 `/api`。
- `.env`、IDE 配置、编译输出、日志和备份不进入版本库。`.gitignore` 不会自动移除已经被跟踪的文件，上传前还需核对跟踪清单。
- 当前网关模板提供 HTTP；公网正式使用前需在服务器部署阶段配置 HTTPS。已有 IP、AppID、云环境 ID 和云函数兜底地址须按目标环境核对。

## 数据与更新

默认初始化脚本不包含人员、套餐、公告、订单或轨迹演示数据，具体见[数据库说明](db/README.md)。已有数据卷不会重新执行初始化脚本，也不会自动移除原有演示数据。

每次发布使用独立 Git 标签和 `APP_VERSION`。先备份，再构建并更新服务；回滚代码或镜像不会回滚数据库。操作步骤见[更新与回滚](deploy/server/README.md#更新与回滚)。

本地后端测试、镜像构建、独立环境业务和重启检查已完成，阿里云真实短信收码及核验也已通过，见[本地部署验证记录](docs/本地部署验证记录-20261022.md)、[短信接入记录](docs/阿里云短信认证接入.md)和[验证环境说明](deploy/verify/README.md)。目标服务器发布、真实物流及微信端发布仍需后续验证。
