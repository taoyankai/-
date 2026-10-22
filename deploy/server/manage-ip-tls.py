#!/usr/bin/env python3
"""Host-side IP certificate lifecycle. Run with sudo; private keys never enter Git."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path('/var/lib/hz-delivery/tls')
DEPLOY_ROOT = Path('/home/ubuntu/hz-delivery')
IMAGE = 'certbot/certbot:v5.8.0'
IP = '118.89.27.5'
NAME = 'hz-delivery-ip'
MARKER = '.hz-reload-needed'


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def directories(staging=False):
    ROOT.mkdir(parents=True, mode=0o700, exist_ok=True)
    ROOT.chmod(0o700)
    suffix = '-staging' if staging else ''
    paths = {key: ROOT / (key + suffix) for key in ('letsencrypt', 'work', 'logs')}
    for path in paths.values():
        path.mkdir(mode=0o700, exist_ok=True)
        path.chmod(0o700)
    webroot = ROOT / 'webroot'
    challenge = webroot / '.well-known/acme-challenge'
    challenge.mkdir(mode=0o755, parents=True, exist_ok=True)
    for path in (webroot, webroot / '.well-known', challenge):
        path.chmod(0o755)
    paths['webroot'] = webroot
    return paths


def client(paths, arguments):
    args = ['docker', 'run', '--rm', '--name', 'hz-ip-certbot',
            '--label', 'hz.tls.project=hz-delivery', '--memory', '128m', '--cpus', '1']
    for key, target in [('letsencrypt', '/etc/letsencrypt'), ('work', '/var/lib/letsencrypt'),
                        ('logs', '/var/log/letsencrypt'), ('webroot', '/var/www/acme')]:
        args += ['--mount', f'type=bind,source={paths[key]},target={target}']
    run(args + [IMAGE] + arguments)


def issue(staging):
    paths = directories(staging)
    args = ['certonly', '--non-interactive', '--agree-tos', '--register-unsafely-without-email',
            '--preferred-profile', 'shortlived', '--preferred-challenges', 'http',
            '--webroot', '--webroot-path', '/var/www/acme', '--ip-address', IP,
            '--cert-name', NAME, '--keep-until-expiring',
            '--deploy-hook', f'touch /etc/letsencrypt/{MARKER}']
    if staging:
        args += ['--staging']
    client(paths, args)
    status(staging)


def assert_gateway():
    result = subprocess.check_output(['docker', 'inspect', 'hz-nginx'], text=True)
    container = json.loads(result)[0]
    if container['Config']['Labels'].get('com.docker.compose.project') != 'hz-delivery':
        raise RuntimeError('Refusing to manage a gateway owned by another project')
    if not container['State']['Running']:
        raise RuntimeError('The HTTPS gateway is not running')
    if not container['HostConfig']['PortBindings'].get('443/tcp'):
        raise RuntimeError('The running gateway has no HTTPS port binding')


def renew(dry_run):
    paths = directories()
    assert_gateway()
    args = ['renew', '--cert-name', NAME, '--non-interactive', '--no-random-sleep-on-renew',
            '--deploy-hook', f'touch /etc/letsencrypt/{MARKER}']
    if dry_run:
        args += ['--dry-run', '--run-deploy-hooks']
    client(paths, args)
    if (paths['letsencrypt'] / MARKER).exists():
        # A failed reload leaves this marker for the next attempt, even when renewal is not due.
        run(['docker', 'exec', 'hz-nginx', 'nginx', '-t'])
        run(['docker', 'exec', 'hz-nginx', 'nginx', '-s', 'reload'])
        (paths['letsencrypt'] / MARKER).unlink()
        print('Renewal hook verified: Nginx configuration checked and reloaded.', flush=True)
    status(False)


def status(staging):
    directory = ROOT / ('letsencrypt-staging' if staging else 'letsencrypt')
    certificate = directory / 'live' / NAME / 'fullchain.pem'
    if not certificate.exists():
        raise RuntimeError('Certificate is missing')
    if not staging:
        run(['openssl', 'x509', '-in', str(certificate), '-noout', '-checkend', '3600'])
    run(['openssl', 'x509', '-in', str(certificate), '-noout', '-checkip', IP])
    run(['openssl', 'x509', '-in', str(certificate), '-noout', '-issuer', '-dates', '-ext', 'subjectAltName'])
    print(json.dumps({'staging': staging, 'ip': IP, 'cert_name': NAME,
                      'leaf_chain_sha256': hashlib.sha256(certificate.read_bytes()).hexdigest()}), flush=True)


def install_timer():
    assert_gateway()
    status(False)
    destination = Path('/usr/local/lib/hz-delivery/manage-ip-tls.py')
    destination.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
    shutil.copyfile(Path(__file__).resolve(), destination)
    destination.chmod(0o644)
    unit = Path('/etc/systemd/system/hz-delivery-cert-renew.service')
    timer = Path('/etc/systemd/system/hz-delivery-cert-renew.timer')
    unit.write_text('''[Unit]
Description=Renew hz-delivery IP certificate and reload Nginx
Wants=network-online.target docker.service
After=network-online.target docker.service
StartLimitIntervalSec=6h
StartLimitBurst=3

[Service]
Type=oneshot
UMask=0077
ExecStart=/usr/bin/python3 /usr/local/lib/hz-delivery/manage-ip-tls.py renew
TimeoutStartSec=300
Restart=on-failure
RestartSec=15min
''', encoding='utf-8')
    timer.write_text('''[Unit]
Description=Check hz-delivery IP certificate four times daily

[Timer]
OnCalendar=*-*-* 00,06,12,18:00:00
RandomizedDelaySec=15min
Persistent=true
Unit=hz-delivery-cert-renew.service

[Install]
WantedBy=timers.target
''', encoding='utf-8')
    unit.chmod(0o644)
    timer.chmod(0o644)
    run(['systemd-analyze', 'verify', str(unit), str(timer)])
    run(['systemctl', 'daemon-reload'])
    run(['systemctl', 'enable', '--now', timer.name])
    run(['systemctl', 'list-timers', '--all', timer.name])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['issue', 'renew', 'dry-run', 'install-timer', 'status'])
    parser.add_argument('--staging', action='store_true', help='Use separate staging storage when issuing/status')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run this host-side certificate manager with sudo')
    if args.staging and args.action not in ('issue', 'status'):
        parser.error('--staging is supported only for issue and status')
    os.umask(0o077)
    if not DEPLOY_ROOT.is_dir():
        raise RuntimeError('Deploy the loopback application before configuring its IP certificate')
    with (DEPLOY_ROOT / 'deploy.lock').open('a') as lock:
        owner = DEPLOY_ROOT.stat()
        os.fchown(lock.fileno(), owner.st_uid, owner.st_gid)
        fcntl.flock(lock, fcntl.LOCK_EX)
        if args.action == 'issue':
            issue(args.staging)
        elif args.action in ('renew', 'dry-run'):
            renew(args.action == 'dry-run')
        elif args.action == 'install-timer':
            install_timer()
        else:
            status(args.staging)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f'IP TLS operation failed: {error}', file=sys.stderr)
        sys.exit(1)
