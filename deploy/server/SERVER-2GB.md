# 118.89.27.5：GitHub 指定版本与服务器构建

当前路线由用户确定：**本地修改并测试 → 提交 GitHub → 发布版本标签 → 服务器拉取指定标签 → Docker 构建 → 更新容器**。原离线镜像上传方案已停用，不运行旧的 upload.ps1。

服务器为 Ubuntu 24.04.4、x86_64、2核、约2GB内存、约2GB swap，已安装 Docker 29.1.3 与 Compose 2.40.3。2026-10-22 发布准备阶段已登录检查：腾讯云镜像加速已配置，项目需要的5个基础镜像均已实际下载；GitHub HTTPS（HTTP/1.1）读取及克隆成功，阿里云 Maven 实际依赖文件下载成功。业务容器在发布前尚未启动，Alpine 系统包和完整后端编译在实际部署时继续验证。

## 本地发布

先检查差异、完成对应测试，确认提交范围包含源码、部署配置与文档，排除 .env、密钥、编译缓存、镜像包、备份和本地测试数据。提交到现有仓库 `taoyankai/-`，创建明确版本标签，再将提交与标签推送 GitHub。

下文 `v1.0.0` 是待发布的示例标签，不表示该版本已经创建或推送。正式操作使用最终确认的标签，不在服务器部署浮动的“最新代码”。标签发布后不移动、不强推。

## 网络前提

服务器必须能取得 GitHub 源码、Docker 基础镜像和运行镜像、阿里云 Maven 依赖及 Alpine 软件包。Docker 安装成功不能证明这些地址可访问。先通过实际连通与下载检查，再开始部署。

可用云厂商支持的镜像加速方式解决 Docker 拉取；GitHub 的 HTTPS 与 SSH 443 入口应分别验证。私有仓库另需配置只读 Deploy Key 或其他授权，密钥不进入仓库和聊天。仅有 HTTP 404 或仓库根目录能返回响应，不等于具体依赖下载成功。

本机与目标服务器的网络情况可能不同。当前服务器使用 `https://mirror.ccs.tencentyun.com`，仓库为公开仓库，HTTPS 拉取无需 GitHub 密钥。首次克隆可使用 `git -c http.version=HTTP/1.1 clone ...`，之后在服务器 repo 内执行 `git config http.version HTTP/1.1`。网络超时与 SSH 公钥未授权是不同问题；镜像加速也不代理 GitHub、Maven 或 Alpine 软件包下载。

## 2GB 配置

每次启动均合并 `docker-compose.yml` 与 `compose.2gb.yml`，项目名固定 `hz-delivery`。

| 服务 | 内存上限 | 配套限制 |
| --- | --- | --- |
| MySQL | 512MiB | 缓冲池128MiB，最大连接50 |
| API | 640MiB | 运行堆320MiB，Tomcat线程40，数据库池8/2 |
| Redis | 96MiB | 数据64MiB，noeviction，达到上限拒绝新增写入 |
| Nginx | 96MiB | 初次入口127.0.0.1:80，通过SSH隧道验收 |

总运行容器上限1344MiB，不会预分配全部内存。构建阶段另外限制 Maven 堆384MiB、元空间192MiB、代码缓存64MiB，降低2GB服务器编译时的峰值。构建进程、Docker守护进程和运行服务仍共享主机内存，构建/更新应安排维护窗口并观察资源，尚未进行目标服务器并发压测。

## 目录与私密配置

```text
~/hz-delivery/
├── repo/                       GitHub工作副本，只用于获取已发布版本
├── releases/                   每次按Git标签导出的独立源码目录
├── backups/                    更新前的数据库备份，权限受限
├── current-release             最近成功版本、提交SHA和源码目录（3行）
└── previous-release            上一个成功版本记录
~/.config/hz-delivery/server.env 独立私密配置，不随Git版本切换
```

服务器配置可由先前已生成的私密配置单独迁移，不上传镜像压缩包。数据库和后台初始密码独立于本地验证库，PNVS参数沿用已验证配置。将私密文件通过SSH/SCP传入服务器，再以600权限保存到 `~/.config/hz-delivery/server.env`。不把密钥粘贴进 Git 命令、聊天或文档。

脚本从版本标签注入 APP_VERSION，用相同标签命名镜像；私密配置中的旧 APP_VERSION 不会覆盖脚本指定版本。

## 首次获取源码

网络和仓库权限验证后，在服务器执行，每段成功后再进入下一段：

```bash
mkdir -p ~/hz-delivery
```

```bash
git clone https://github.com/taoyankai/-.git ~/hz-delivery/repo
```

私有仓库需使用已配置的授权方式；若采用GitHub的SSH443入口，克隆URL改为 `ssh://git@ssh.github.com:443/taoyankai/-.git`，并先核对GitHub主机指纹及配置只读部署密钥。

```bash
cd ~/hz-delivery/repo
```

```bash
git fetch --tags origin
```

```bash
git switch --detach v1.0.0
```

确认私密配置已到位，然后执行：

```bash
bash deploy/server/deploy-version.sh v1.0.0
```

脚本拒绝脏工作区、未知标签、被Git跟踪的私密.env，以及其他项目占用的同名容器/数据卷。它用 `git archive` 建立标签对应的独立源码目录，在服务器拉取运行依赖并构建后端；已有运行版本的页面不会因源码准备而改变。

依赖拉取及构建失败时不更新原容器。有旧MySQL时先完成一致性备份；构建和备份成功后才短暂停止旧入口并更新服务。健康检查通过后记录版本。旧镜像、源码和数据卷保留，脚本不执行 down -v、不自动恢复数据库。

## 后续发布

本地修改、测试、提交与推送新标签（例如v1.0.1）后，服务器先进入repo目录获取并切到该标签，再执行相同部署脚本：

```bash
git fetch --tags origin
```

```bash
git switch --detach v1.0.1
```

```bash
bash deploy/server/deploy-version.sh v1.0.1
```

每次发布使用新标签。服务器不直接修改业务源码，不在含未提交变更的目录强行切换，不用 git reset --hard 清理问题。新增环境变量按版本说明加入独立 server.env，已有数据库口令保持一致。

## 验收

在本机PowerShell保持下面的SSH隧道运行：

```powershell
ssh -N -L 18082:127.0.0.1:80 ubuntu@118.89.27.5
```

访问 `http://127.0.0.1:18082/`、`/admin/`、`/actuator/health`。后台初始密码在私密配置的 ADMIN_INIT_PASSWORD 中，首次登录必须改密；正式空库只有表和承运商基础数据，无测试人员、套餐或订单。配置域名和HTTPS后再安排正式公网入口。

## 应用回滚

应用回滚不恢复数据库；先确认表结构与旧版本兼容，必要时单独安排数据库恢复。读取 previous-release 的三行，第一行为版本，第三行为旧源码目录，不要 source 该文件：

```bash
cat ~/hz-delivery/previous-release
```

核对旧镜像存在后，将下面示例路径替换为该记录中的真实值，用旧目录的两份Compose配置恢复：

```bash
sudo docker image inspect hz-delivery-server:v1.0.0 --format '{{.Id}}'
```

```bash
sudo env APP_VERSION=v1.0.0 docker compose -p hz-delivery --env-file "$HOME/.config/hz-delivery/server.env" -f "$HOME/hz-delivery/releases/实际旧目录/deploy/server/docker-compose.yml" -f "$HOME/hz-delivery/releases/实际旧目录/deploy/server/compose.2gb.yml" up -d --no-build --pull never --wait
```

执行前替换版本和路径。回滚后核对健康、页面与业务，并记录人工回滚情况；脚本不会假设数据库可以跟随镜像安全倒退。

## 已有验证边界

2GB运行覆盖已在独立本地容器验证：四项服务健康，实际内存上限及数据库池8/2生效，MySQL12张表且人员/套餐/订单为0。阿里云真实收码与核验已另行通过。服务器构建方式及新部署脚本的验证结果另见本次报告；这些本地结果不代表目标服务器已部署成功。
