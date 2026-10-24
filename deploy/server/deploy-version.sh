#!/usr/bin/env bash
# 在服务器 Git 工作副本中执行：bash deploy/server/deploy-version.sh v1.0.0
# 从标签建立独立源码目录，在该目录构建，成功后再更新容器；私密配置和数据卷跨版本保留。
set -euo pipefail

main() {
    local version="${1:-}"
    if [[ ! "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([.-][a-zA-Z0-9.-]+)?$ ]]; then
        printf '%s\n' 'Usage: bash deploy/server/deploy-version.sh v1.0.0' >&2
        return 2
    fi
    local script_dir repo_root state_dir env_file commit release_dir backup_file container volume owner tracked_env tls_enabled cert_root
    script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
    repo_root="$(git -C "$script_dir" rev-parse --show-toplevel)"
    state_dir="${HZ_DEPLOY_ROOT:-$HOME/hz-delivery}"
    env_file="${HZ_ENV_FILE:-$HOME/.config/hz-delivery/server.env}"
    if [[ ! -f "$env_file" ]]; then
        printf '%s\n' 'Missing private server.env. Follow SERVER-2GB.md before deploying.' >&2
        return 1
    fi
    if [[ -n "$(git -C "$repo_root" status --porcelain --untracked-files=normal)" ]]; then
        printf '%s\n' 'Server Git checkout has local changes; preserve and resolve them before deploying.' >&2
        return 1
    fi
    umask 077
    chmod 600 "$env_file"
    mkdir -p "$state_dir/releases" "$state_dir/backups"
    exec 9>"$state_dir/deploy.lock"
    flock -n 9 || { printf '%s\n' 'Another deployment is running.' >&2; return 1; }
    git -C "$repo_root" fetch --tags origin
    commit="$(git -C "$repo_root" rev-parse --verify "refs/tags/${version}^{commit}")"
    tracked_env="$(git -C "$repo_root" ls-tree -r --name-only "$commit" | awk -F/ '$NF == ".env" || ($NF ~ /^\.env\./ && $NF != ".env.example") { print }')"
    if [[ -n "$tracked_env" ]]; then
        printf '%s\n' 'Refusing a release that tracks a private .env file.' >&2
        return 1
    fi
    git -C "$repo_root" cat-file -e "$commit:deploy/server/compose.2gb.yml"
    for container in hz-mysql hz-redis hz-api hz-nginx; do
        if sudo docker container inspect "$container" >/dev/null 2>&1; then
            owner="$(sudo docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$container")"
            [[ "$owner" == hz-delivery ]] || { printf 'Container %s belongs to another deployment.\n' "$container" >&2; return 1; }
        fi
    done
    for volume in hz-mysql-data hz-redis-data hz-api-logs; do
        if sudo docker volume inspect "$volume" >/dev/null 2>&1; then
            owner="$(sudo docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume")"
            [[ "$owner" == hz-delivery ]] || { printf 'Volume %s belongs to another deployment.\n' "$volume" >&2; return 1; }
        fi
    done
    release_dir="$(mktemp -d "$state_dir/releases/${version}-${commit:0:8}.XXXXXX")"
    # 源码需要由 MySQL / Nginx 容器中的非 root 用户读取；仅归档解包用022。
    # 私密配置、备份、版本元数据及release根目录仍沿用外层077。
    (
        umask 022
        git -C "$repo_root" archive "$commit" | tar --no-same-owner --no-same-permissions -x -C "$release_dir"
    )
    printf '%s\n' "$version" "$commit" >"$release_dir/RELEASE"
    local compose=(sudo env "APP_VERSION=$version" COMPOSE_PARALLEL_LIMIT=1 docker compose -p hz-delivery
        --env-file "$env_file" -f "$release_dir/deploy/server/docker-compose.yml"
        -f "$release_dir/deploy/server/compose.2gb.yml")
    tls_enabled="$(sed -n 's/^TLS_ENABLED=//p' "$env_file" | tr -d '\r' | tail -1)"
    case "${tls_enabled:-false}" in
        true)
            [[ -f "$release_dir/deploy/server/compose.https.yml" ]] || { printf '%s\n' 'Release does not support HTTPS.' >&2; return 1; }
            cert_root=/var/lib/hz-delivery/tls/letsencrypt/live
            sudo openssl x509 -in "$cert_root/hz-delivery-ip/fullchain.pem" -noout -checkend 3600
            sudo openssl verify -untrusted "$cert_root/hz-delivery-ip/fullchain.pem" -purpose sslserver -verify_ip 118.89.27.5 "$cert_root/hz-delivery-ip/fullchain.pem"
            sudo openssl x509 -in "$cert_root/heytao.top/fullchain.pem" -noout -checkend 3600
            sudo openssl verify -untrusted "$cert_root/heytao.top/fullchain.pem" -purpose sslserver -verify_hostname heytao.top "$cert_root/heytao.top/fullchain.pem"
            sudo openssl verify -untrusted "$cert_root/heytao.top/fullchain.pem" -purpose sslserver -verify_hostname www.heytao.top "$cert_root/heytao.top/fullchain.pem"
            compose+=(-f "$release_dir/deploy/server/compose.https.yml")
            ;;
        false)
            [[ ! -e "$state_dir/tls-enabled" ]] || { printf '%s\n' 'HTTPS was enabled previously; refusing an implicit HTTP downgrade.' >&2; return 1; }
            ;;
        *) printf '%s\n' 'TLS_ENABLED must be an unquoted true or false.' >&2; return 1 ;;
    esac
    "${compose[@]}" config --quiet
    if [[ "$tls_enabled" == true ]]; then
        "${compose[@]}" run --rm --no-deps --pull never nginx nginx -t
    fi
    # 先完成依赖拉取与编译，期间原版本入口及源码目录保持不变。
    "${compose[@]}" pull mysql redis nginx
    "${compose[@]}" build api
    if [[ "$(sudo docker inspect --format '{{.State.Running}}' hz-mysql 2>/dev/null || true)" == true ]]; then
        backup_file="$state_dir/backups/before-${version}-$(date +%Y%m%d-%H%M%S).sql"
        if ! sudo docker exec hz-mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces --routines --triggers --events "$MYSQL_DATABASE"' >"$backup_file.partial"; then
            printf '%s\n' 'Database backup failed. Existing containers have not been updated.' >&2
            return 1
        fi
        [[ -s "$backup_file.partial" ]] || { printf '%s\n' 'Database backup is empty.' >&2; return 1; }
        mv -- "$backup_file.partial" "$backup_file"
    fi
    # 旧入口可能含尚未同步回Git的域名配置。先独立备份，失败则不停止旧入口。
    if sudo docker container inspect hz-nginx >/dev/null 2>&1; then
        backup_file="$state_dir/backups/nginx-before-${version}-$(date +%Y%m%d-%H%M%S).conf"
        if ! sudo docker exec hz-nginx cat /etc/nginx/nginx.conf >"$backup_file.partial"; then
            printf '%s\n' 'Nginx configuration backup failed. Existing containers have not been updated.' >&2
            return 1
        fi
        [[ -s "$backup_file.partial" ]] || { printf '%s\n' 'Nginx configuration backup is empty.' >&2; return 1; }
        mv -- "$backup_file.partial" "$backup_file"
    fi
    # 短暂维护窗口：构建和备份成功后，才停止旧入口并切换整套服务。
    if sudo docker container inspect hz-nginx >/dev/null 2>&1; then
        sudo docker stop hz-nginx >/dev/null
    fi
    if ! "${compose[@]}" up -d --no-build --pull never --wait --wait-timeout 300; then
        printf '%s\n' 'Update failed. Previous release metadata, images, backups, and volumes are retained; follow the rollback guide.' >&2
        return 1
    fi
    if [[ -f "$state_dir/current-release" ]]; then
        cp -- "$state_dir/current-release" "$state_dir/previous-release"
    fi
    printf '%s\n' "$version" "$commit" "$release_dir" >"$state_dir/current-release.new"
    mv -- "$state_dir/current-release.new" "$state_dir/current-release"
    if [[ "$tls_enabled" == true ]]; then
        printf '%s\n' 'true' >"$state_dir/tls-enabled"
    fi
    "${compose[@]}" ps
    printf 'Deployed %s (%s). Release directory: %s\n' "$version" "$commit" "$release_dir"
}

main "$@"
