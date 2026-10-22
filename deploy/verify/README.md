# 本地独立验证环境

入口：[H5](http://127.0.0.1:18081/) / [管理后台](http://127.0.0.1:18081/admin/)。容器、网络和数据卷均以 `hz-verify-` 命名，与旧 `hz-*` 环境分开。

本目录的 `compose.override.yml` 与服务器基础配置合并使用。所有命令在仓库根目录执行，不能单独运行覆盖文件。

```powershell
docker compose -p hz-verify --env-file deploy/verify/.env -f deploy/server/docker-compose.yml -f deploy/verify/compose.override.yml config --quiet
docker compose -p hz-verify --env-file deploy/verify/.env -f deploy/server/docker-compose.yml -f deploy/verify/compose.override.yml build api
docker compose -p hz-verify --env-file deploy/verify/.env -f deploy/server/docker-compose.yml -f deploy/verify/compose.override.yml up -d --no-build --wait
```

## 配置与当前密码

本地 `.env` 已创建并被 Git 忽略。其他机器需从 `deploy/server/.env.example` 复制后自行配置，不依赖本机的密码文件。

| 字段 | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | 验证数据库密码，已初始化的库不能仅靠改变量变更密码 |
| `ADMIN_INIT_PASSWORD` | 创建缺失后台账号时的初始密码；不是本次改密后的当前密码 |
| `VERIFY_ADMIN_PASSWORD` | 本次验证后 `admin` 的当前密码，本地记录 |
| `VERIFY_OPERATOR_PASSWORD` | 本次验证后 `operator` 的当前密码，本地记录 |
| `VERIFY_VIEWER_PASSWORD` | 本次验证后 `viewer` 的当前密码，本地记录 |

`VERIFY_*_PASSWORD` 不会自动传入应用，也不会自动修改数据库；它们只是本次经正式改密接口改密后留下的本地记录。创建全新数据库时，仍使用 `ADMIN_INIT_PASSWORD` 初始化账号。以后手动改密时需同步自己的密码记录。

## 当前通道和测试数据

- `APP_VERSION=verify-local`。
- 当前已切换为 `SMS_AUTH_PROVIDER=aliyun-pnvs`、`MOCK_SMS_CODE=`，用户已配置本地密钥、签名和模板；验证码会真实发送并消耗阿里云额度。`SMS_PROVIDER=mock` 仅代表通知短信仍模拟，不能据此判断登录验证码是模拟的。
- 初次业务验证使用 `local + mock + 123456`；当前已停用固定演示验证码，原两个虚构号码不能继续使用 `123456` 登录，也不要对其发真实短信。
- `LOGISTICS_PROVIDER=disabled`、`LOGISTICS_VALIDATE_ON_SHIP=false`：仅用于测试本地发货状态流转，不验证真实运单，不查询真实物流。
- 正式配置模板仍保留 `LOGISTICS_VALIDATE_ON_SHIP=true`，实际发货前应配置真实通道。
- 验证库保留原 2 个模拟测试人员，另有用户指定真实号码对应的“短信认证测试”人员，领取额度为 0。原 2 个测试套餐、1 条测试公告和 3 笔订单保留，短信验收未新增订单。
- 测试人员手机号 `19900000001`、`19900000002`，仅用于 mock 登录，不代表真实人员。

验证记录见[本地部署验证记录](../../docs/本地部署验证记录-20261022.md)及[阿里云认证接入与验收](../../docs/阿里云短信认证接入.md)。真实短信已完成指定号码的收码与登录核验；真实物流、微信原生小程序及服务器发布仍未进行。

## 停止与再次启动

```powershell
docker compose -p hz-verify --env-file deploy/verify/.env -f deploy/server/docker-compose.yml -f deploy/verify/compose.override.yml stop
docker compose -p hz-verify --env-file deploy/verify/.env -f deploy/server/docker-compose.yml -f deploy/verify/compose.override.yml up -d --no-build --wait
```

停止服务保留数据卷。本次已实际停止并重新启动整套验证服务，核对关键业务数据、密码及 Redis 数据保留。
