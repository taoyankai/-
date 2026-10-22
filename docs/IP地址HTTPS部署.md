# IP地址 HTTPS 部署与续期

目标入口：`https://118.89.27.5/`，后台：`https://118.89.27.5/admin/`。本次采用IP证书，无需先购买域名；HTTPS配置随源码版本发布，证书与私钥独立存放在服务器。

## 证书方式

根据 [Let's Encrypt IP证书说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot/)，Certbot 5.4及以上支持webroot方式申请IP证书，必须选择shortlived配置。证书有效期约160小时，需自动续期。本项目使用已核对的 `certbot/certbot:v5.8.0`，不使用Ubuntu旧版Certbot包。

`deploy/server/manage-ip-tls.py` 在宿主机以root运行，调用临时Certbot容器。证书签发账号不提供个人邮箱；到期维护依赖systemd定时器与服务日志。申请命令接受证书机构服务条款；不要在不同机器间共享账户私钥。

## 文件与权限

| 文件或目录 | 用途 |
| --- | --- |
| `deploy/nginx/nginx.https.conf` | HTTP验证/跳转与HTTPS应用入口，保留原有业务路由、限速及转发头 |
| `deploy/server/compose.https.yml` | 在2GB配置之后叠加，发布80、443端口；只读挂载证书和验证目录 |
| `deploy/server/manage-ip-tls.py` | 申请、检查、续期演练及安装定时任务 |
| `/var/lib/hz-delivery/tls/letsencrypt/` | 正式证书、账户及续期配置，root私密目录 |
| `/var/lib/hz-delivery/tls/letsencrypt-staging/` | 独立测试证书和账户，与正式签发分开 |
| `/var/lib/hz-delivery/tls/webroot/` | HTTP-01公开验证文件，目录755 |
| `/usr/local/lib/hz-delivery/manage-ip-tls.py` | 定时任务使用的稳定脚本副本 |
| `~/.config/hz-delivery/server.env` | 原生产私密配置；追加 `TLS_ENABLED=true` |
| `~/hz-delivery/tls-enabled` | 成功开启HTTPS的记录；阻止后续版本隐式退回HTTP |

Nginx只读挂载证书的 `live/` 与 `archive/`，不挂载证书账户目录。私钥仅在服务器生成，不上传GitHub、不放入Docker镜像或公开静态目录。

## 首次启用顺序

先按 [2GB部署说明](../deploy/server/SERVER-2GB.md)运行回环地址上的应用，保留原业务数据和配置。公网必须允许TCP80和443；HTTP-01验证依赖80端口，不能在证书签发后永久关闭。

1. 先准备仅提供 `/.well-known/acme-challenge/` 的临时HTTP验证入口，并从服务器外实际请求验证文件。原回环业务入口保持运行。
2. 从GitHub拉取包含HTTPS配置的新版本，核对提交。
3. 以独立staging存储验证签发流程。测试证书不受普通浏览器信任。
4. 申请正式证书，核对IP SAN、签发者与到期时间。
5. 在服务器私密配置中设置不加引号的 `TLS_ENABLED=true`，随后按标签执行部署脚本。切换前停止临时验证监听，避免80端口冲突。
6. 脚本先检查证书IP与至少1小时剩余有效期，使用临时Nginx容器验证配置，再构建、备份并更新服务。后续发布会自动合并HTTPS覆盖。
7. 用正式信任链验证公网HTTPS、同源后台登录、HTTP跳转和验证路径，再执行续期演练、安装定时器。

证书管理命令在服务器最新源码根目录执行，每段成功后再进入下一段：

```bash
sudo python3 deploy/server/manage-ip-tls.py issue --staging
```

```bash
sudo python3 deploy/server/manage-ip-tls.py issue
```

应用已成功切换为HTTPS后：

```bash
sudo python3 deploy/server/manage-ip-tls.py renew
```

```bash
sudo python3 deploy/server/manage-ip-tls.py dry-run
```

```bash
sudo python3 deploy/server/manage-ip-tls.py install-timer
```

不能仅执行证书命令就认为业务入口已经启用HTTPS；入口切换仍由已发布标签对应的 `deploy-version.sh` 完成。

## 自动续期

定时器 `hz-delivery-cert-renew.timer` 每天检查4次，附加最多15分钟随机延迟，启动后补做错过的任务。Certbot只在需要时续期，不会每次强制重新签发。服务失败按15分钟间隔重试，并有限制，避免连续请求。

证书更新的deploy hook会写入持久标记。宿主脚本发现标记后先执行 `nginx -t`，再平滑重载Nginx；成功才删除标记。若重载失败，标记保留到下次重试。续期与发布共用部署锁，避免同时切换入口。

`dry-run` 使用测试签发验证续期及重载hook，不替换正式证书。安装后还应实际运行一次systemd服务并检查结果。以后修改证书维护脚本时，从新版本源码重新运行 `install-timer` 更新稳定副本。

```bash
sudo systemctl list-timers --all hz-delivery-cert-renew.timer
```

```bash
sudo systemctl status hz-delivery-cert-renew.service --no-pager
```

```bash
sudo journalctl -u hz-delivery-cert-renew.service -n 50 --no-pager
```

## 后续发布与排错

服务器启用HTTPS后，保持私密配置的 `TLS_ENABLED=true`，从GitHub获取新标签并照常执行版本部署脚本。证书目录和续期任务不随release目录变动，不需要复制私钥到新版本。

| 现象 | 排查 |
| --- | --- |
| IP证书验证超时 | 从外部检查公网80及HTTP验证路径，检查云防火墙、临时验证入口或Nginx配置 |
| HTTPS连接超时 | 检查公网443入站规则与容器端口映射；80正常不能证明443可达 |
| 浏览器证书不受信任 | 检查是否误用staging证书、系统时间、完整证书链和IP SAN |
| 证书已更新但网页仍用旧证书 | 检查持久重载标记、`nginx -t`和续期服务日志 |
| 更新被阻止、提示HTTP降级 | 检查私密配置是否漏掉TLS_ENABLED，不要删除tls-enabled记录来绕过 |
| 修改变量后端口没变 | 配置通过Compose重建容器生效，仅restart不会读取新环境变量 |

首次验收至少覆盖：不跳过校验的公网HTTPS请求、IP SAN与有效期、HTTP到HTTPS跳转、ACME验证文件返回、后台同源POST及注销、四个容器状态、续期演练、systemd服务执行及下一次触发时间。本次服务器签发与运行结果以实际验收记录为准。

IP证书不等同于已完成微信原生小程序的服务器域名配置；微信端仍按其独立发布流程处理。
