#!/usr/bin/env bash
# Container-only regression: real Git/archive/OpenSSL; Docker operations are simulated.
# /fixtures contains only public scripts and generated self-signed test certificates.
set -euo pipefail
root=$(mktemp -d)
mkdir -p "$root/bin" "$root/repo/deploy/server"
cp /fixtures/deploy-version.sh "$root/repo/deploy/server/deploy-version.sh"
for file in docker-compose.yml compose.2gb.yml compose.https.yml; do
    printf 'services: {}\n' >"$root/repo/deploy/server/$file"
done
git -C "$root/repo" init -q
git -C "$root/repo" config user.email test@example.invalid
git -C "$root/repo" config user.name 'Isolated deployment test'
git -C "$root/repo" add .
git -C "$root/repo" commit -qm fixture
git -C "$root/repo" tag v1.1.3
git -C "$root/repo" remote add origin "$root/repo"
cat >"$root/bin/sudo" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == openssl ]]; then
    shift
    exec /usr/bin/openssl "$@"
fi
if [[ "$1" == env ]]; then
    shift
    exec env "$@"
fi
exec "$@"
STUB
cat >"$root/bin/docker" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$EVENTS"
case "$*" in
    *'com.docker.compose.project'*) printf 'hz-delivery\n' ;;
    'container inspect '*|'volume inspect '*) exit 0 ;;
    *'.State.Running'*) printf 'true\n' ;;
    'exec hz-mysql '*)
        [[ "$SCENARIO" != dbfail ]] || exit 1
        printf '%s\n' '-- synthetic database backup' ;;
    'exec hz-nginx cat /etc/nginx/nginx.conf')
        [[ "$SCENARIO" != nginxbackupfail ]] || exit 1
        [[ "$SCENARIO" != nginxempty ]] || exit 0
        printf '%s\n' '# synthetic existing domain config' ;;
    *'nginx nginx -t') [[ "$SCENARIO" != nginxtestfail ]] ;;
    *'build api') [[ "$SCENARIO" != buildfail ]] ;;
    *'up -d '*) [[ "$SCENARIO" != upfail ]] ;;
    *) exit 0 ;;
esac
STUB
chmod +x "$root/bin/sudo" "$root/bin/docker"
export PATH="$root/bin:$PATH"
export SSL_CERT_FILE=/fixtures/trusted.pem
export HZ_ENV_FILE="$root/server.env"
printf 'TLS_ENABLED=true\n' >"$HZ_ENV_FILE"
certificate_root=/var/lib/hz-delivery/tls/letsencrypt/live
mkdir -p "$certificate_root/hz-delivery-ip" "$certificate_root/heytao.top"

passed=0
for SCENARIO in valid wrongip wrongdomain missingwww untrusted expiring expired missingdomain nginxtestfail buildfail dbfail nginxbackupfail nginxempty upfail; do
    export SCENARIO
    export HZ_DEPLOY_ROOT="$root/state-$SCENARIO"
    export EVENTS="$root/events-$SCENARIO"
    : >"$EVENTS"
    mkdir -p "$HZ_DEPLOY_ROOT"
    printf 'v1.1.1\nprevious-sha\n/previous-release\n' >"$HZ_DEPLOY_ROOT/current-release"
    cp /fixtures/certs/hz-delivery-ip/fullchain.pem "$certificate_root/hz-delivery-ip/fullchain.pem"
    cp /fixtures/certs/heytao.top/fullchain.pem "$certificate_root/heytao.top/fullchain.pem"
    case "$SCENARIO" in
        wrongip) cp /fixtures/wrongip.pem "$certificate_root/hz-delivery-ip/fullchain.pem" ;;
        wrongdomain|missingwww|untrusted|expiring|expired)
            cp "/fixtures/$SCENARIO.pem" "$certificate_root/heytao.top/fullchain.pem" ;;
        missingdomain) rm "$certificate_root/heytao.top/fullchain.pem" ;;
    esac
    result=0
    bash "$root/repo/deploy/server/deploy-version.sh" v1.1.3 >"$root/$SCENARIO.log" 2>&1 || result=$?
    if [[ "$SCENARIO" == valid ]]; then
        [[ "$result" == 0 ]] || { cat "$root/$SCENARIO.log"; exit 1; }
        grep -qx v1.1.3 "$HZ_DEPLOY_ROOT/current-release"
        grep -qx v1.1.1 "$HZ_DEPLOY_ROOT/previous-release"
        [[ $(find "$HZ_DEPLOY_ROOT/backups" -name '*.sql' | wc -l) == 1 ]]
        [[ $(find "$HZ_DEPLOY_ROOT/backups" -name '*.conf' | wc -l) == 1 ]]
        # The live Nginx config must be saved before its container is stopped.
        backup_line=$(grep -n '^exec hz-nginx cat' "$EVENTS" | cut -d: -f1)
        stop_line=$(grep -n '^stop hz-nginx$' "$EVENTS" | cut -d: -f1)
        [[ "$backup_line" -lt "$stop_line" ]]
        [[ $(stat -c '%a' "$HZ_DEPLOY_ROOT/backups/"*.conf) == 600 ]]
    else
        [[ "$result" != 0 ]] || { printf 'Unexpected success: %s\n' "$SCENARIO"; exit 1; }
        grep -qx v1.1.1 "$HZ_DEPLOY_ROOT/current-release"
        [[ ! -e "$HZ_DEPLOY_ROOT/previous-release" ]]
        if [[ "$SCENARIO" == upfail ]]; then
            grep -q '^stop hz-nginx$' "$EVENTS"
            grep -q 'Previous release metadata, images, backups, and volumes are retained' "$root/$SCENARIO.log"
        else
            ! grep -q '^stop hz-nginx$' "$EVENTS"
            ! grep -q ' up -d ' "$EVENTS"
        fi
    fi
    printf 'PASS deployment: %s\n' "$SCENARIO"
    passed=$((passed + 1))
done
printf '%s deployment cases passed (simulated Docker, real certificate checks).\n' "$passed"
