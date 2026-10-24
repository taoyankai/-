# heytao.top 域名 HTTPS 发布与更新

正式入口为 `https://heytao.top/`，后台为 `https://heytao.top/admin/`。`www.heytao.top` 跳转至主域名，原 `118.89.27.5` 的 HTTPS 入口继续保留。域名规则随源码版本发布，证书、账户和私钥只保存在服务器，不进入 Git、镜像或前端。

## 本次修复的原因与范围

2026-10-24 只读核对发现，服务器运行 v1.1.1，其 release 目录中的 Nginx 配置已增加域名，而 GitHub v1.1.2 的配置仍只有 IP。直接发布旧配置会丢失域名入口。本次将已生效的完整域名规则同步回仓库，并补充发布检查、旧入口配置备份和域名自动续期。

修复不会变更 DNS、账号、数据库结构或业务状态，也不重新签发当前正式证书。部署时仍有原更新脚本安排的短暂入口切换。小程序的上传、审核和发布独立进行。

| 文件或目录 | 用途 |
| --- | --- |
| `deploy/nginx/nginx.https.conf` | HTTP 验证、域名和 IP HTTPS、www 跳转、原业务路由及限速 |
| `deploy/server/deploy-version.sh` | 按标签导出源码，检查两类证书，构建、备份、切换容器 |
| `deploy/server/manage-ip-tls.py` | 保留原脚本路径，管理 IP 与域名两类证书 |
| `/var/lib/hz-delivery/tls/letsencrypt/live/hz-delivery-ip/` | IP 证书；容器内对应 `/etc/letsencrypt/live/hz-delivery-ip/` |
| `/var/lib/hz-delivery/tls/letsencrypt/live/heytao.top/` | 覆盖主域名及 www 的证书；容器内对应 `/etc/letsencrypt/live/heytao.top/` |
| `/var/lib/hz-delivery/tls/letsencrypt/renewal/` | 两类证书的续期配置，保持在服务器私密目录 |
| `/var/lib/hz-delivery/tls/webroot/` | HTTP-01 验证目录；Nginx 和临时 Certbot 容器共享 |
| `~/.config/hz-delivery/server.env` | 生产私密配置，保留原值与 `TLS_ENABLED=true`，权限 600 |

Compose 已只读挂载 `live/`、`archive/` 和验证目录，不需要将证书复制进任何 release。公网 80 和 443 必须继续可达，80 上的 ACME 路径不跳转，其余请求跳转至主域名。

## 发布前检查

先验证修复分支，再合并至默认分支，并从合并后的提交创建新标签，例如 `v1.1.3`。创建 Release 或填写说明本身不会修改代码。已发布的 `v1.1.2` 保留，不移动标签、不强推。

在服务器以 ubuntu 用户执行以下命令，逐段确认成功。检查工作区时如有输出，先保存并处理变更，不执行强制清理。

```bash
cd ~/hz-delivery/repo
git status --short
git fetch --tags origin
git switch --detach v1.1.3
```

核对标签提交及其配置确实含域名：

```bash
git show -s --format='%h %s' HEAD
grep -n 'server_name\|ssl_certificate' deploy/nginx/nginx.https.conf
sudo python3 deploy/server/manage-ip-tls.py status
```

`status` 同时检查两张证书至少还有一小时有效期、IP SAN、主域名和 www 的匹配关系，以及系统信任的证书链，不显示私钥。身份校验使用 `openssl verify -verify_hostname/-verify_ip`，不依赖 `x509 -checkhost/-checkip` 的退出码。任一证书缺失或不匹配时，先处理证书，不能关闭 TLS 或跳过校验。

## 续期迁移与验证

本次只读核对时，域名证书最初使用 manual DNS 验证，而 IP 证书使用 webroot。续期脚本逐一选择 `hz-delivery-ip` 和 `heytao.top`，显式覆盖为 `webroot`、`http` 和 `/var/www/acme`，避免沿用需要人工操作的 DNS 验证偏好，也不操作其他证书。正常续期不会强制重新签发。

先从服务器外确认以下三个地址均能通过 HTTP 访问验证目录；可使用唯一的临时测试文件，核对后只删除该文件：

```text
http://heytao.top/.well-known/acme-challenge/<测试文件名>
http://www.heytao.top/.well-known/acme-challenge/<测试文件名>
http://118.89.27.5/.well-known/acme-challenge/<测试文件名>
```

维护操作前，将现有稳定脚本、service、timer 及证书续期配置备份到服务器私密备份目录，权限限制为 root 可读，不下载或提交到 Git。然后在新版本源码目录执行：

```bash
sudo python3 deploy/server/manage-ip-tls.py dry-run
```

该操作访问 ACME 测试服务，测试两类证书的 HTTP 验证和重载 hook，不替换正式证书；hook 会对当前 Nginx 检查并平滑重载。演练通过后才安装更新的稳定脚本和定时任务：

```bash
sudo python3 deploy/server/manage-ip-tls.py install-timer
sudo systemctl start hz-delivery-cert-renew.service
sudo systemctl status hz-delivery-cert-renew.service --no-pager
sudo systemctl list-timers --all hz-delivery-cert-renew.timer
```

原 service/timer 名称和稳定脚本路径不变，每天检查四次。更新 Git 源码本身不会更新 `/usr/local/lib/` 中的脚本副本，必须重新运行 `install-timer`。单张证书续期失败仍尝试另一张；若一张已经更新，先检查并重载，再以失败状态退出供 systemd 重试。检查或重载失败时保留持久标记。

## 更新业务版本与验收

确认上述准备完成，在已切至新标签的干净仓库执行：

```bash
bash deploy/server/deploy-version.sh v1.1.3
```

脚本先检查两类证书与临时 Nginx 配置，再拉取依赖并构建。构建后备份现有数据库和运行中的 `/etc/nginx/nginx.conf`，任一步失败或备份为空均不停止旧入口。仅全部通过后停止旧入口并更新容器。配置备份为 `~/hz-delivery/backups/nginx-before-<版本>-<时间>.conf`，数据库备份为同目录的 `before-<版本>-<时间>.sql`，保留原数据卷和旧镜像。

更新后核对：

```bash
cat ~/hz-delivery/current-release
sudo docker inspect hz-api --format '{{.Config.Image}} {{.State.Health.Status}}'
sudo docker exec hz-nginx nginx -t
curl --fail --silent --show-error https://heytao.top/healthz
curl --fail --silent --show-error https://heytao.top/actuator/health
curl --head --silent --show-error https://www.heytao.top/admin/
```

还应在浏览器核对后台与 H5 页面、同源登录及收货确认按钮。业务验证应使用约定的测试数据，不通过真实订单确认收货来测试。未完成公网 HTTP-01 演练和正式容器切换时，只能报告本地验证通过，不能报告已上线。

## 回滚

读取 `~/hz-delivery/previous-release`，核对原版本、镜像与 release 目录，按 [2GB 部署说明](../deploy/server/SERVER-2GB.md)恢复旧版本。启用 TLS 的回滚必须继续叠加该旧目录的 `compose.https.yml`，不能使用只含两份配置的 HTTP 示例。使用原 release 中保留的域名配置；若它被修改，先与本次生成的 Nginx 配置备份核对，再恢复。不要清空数据卷或自动恢复数据库。

如需回退续期管理，使用维护前保存的稳定脚本和 systemd 单元，执行 daemon-reload 后重新启用原 timer，并核对状态。回退旧 IP-only 脚本会停止域名续期覆盖，需要单独继续维护域名证书。

## 本地回归

续期决策回归不访问服务器、不签发证书：

```bash
python -m unittest discover -s deploy/server/tests -p 'test_*.py' -v
```

容器回归要求 Python 的 `cryptography` 包，以及本地已有的 `nginx:1.27-alpine`、`maven:3.9-eclipse-temurin-17` 镜像；不会自动拉取镜像。使用自签测试证书、合成接口、独立网络和随机回环端口，不读取生产私钥、数据库或短信配置。退出时清理测试容器与网络，证据留在忽略的 `server/target/domain-release-verification/`：

```bash
python -B deploy/server/tests/verify_domain_release.py
```

2026-10-24 本地验证通过：13 项续期/证书状态回归、15 项实际 Nginx HTTPS 检查、14 项发布回归。发布回归使用真实 Git 归档与 OpenSSL，模拟 Docker 构建和切换，验证错误 IP/域名、缺少 www、不受信任、过期或即将过期证书，以及配置检查、构建、数据库备份、Nginx 备份和启动失败的处理。正式服务器只读检查确认新校验命令兼容现有证书；尚未执行真实 ACME 续期演练、定时器更新或业务版本切换。

参考：[Nginx HTTPS](https://nginx.org/en/docs/http/configuring_https_servers.html)、[OpenSSL verify](https://docs.openssl.org/3.0/man1/openssl-verify/)、[Certbot 续期](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)。
