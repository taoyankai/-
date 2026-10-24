"""Isolated integration checks. Requires Python cryptography and existing local Docker images.

Run from the repository root: python -B deploy/server/tests/verify_domain_release.py
Uses only synthetic certificates, a fake API and loopback ports; never contacts production.
"""
from datetime import datetime, timedelta, timezone
import http.client
import ipaddress
import json
import re
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import time
import uuid

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

ROOT = Path(__file__).resolve().parents[3]
REPORT = ROOT / 'server/target/domain-release-verification'
FIXTURES = REPORT / ('integration-' + uuid.uuid4().hex[:8])
NGINX = 'nginx:1.27-alpine'
BUILD = 'maven:3.9-eclipse-temurin-17'
PREFIX = 'hz-domain-test-' + uuid.uuid4().hex[:8]


def docker(*args, check=True):
    result = subprocess.run(['docker', *args], text=True, encoding='utf-8', capture_output=True)
    if check and result.returncode:
        raise RuntimeError(f'Docker command failed: {args!r}\n{result.stdout}\n{result.stderr}')
    return result


def certificate(path, names, *, until=None):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, names[0])])
    now = datetime.now(timezone.utc)
    san = []
    for name in names:
        try:
            san.append(x509.IPAddress(ipaddress.ip_address(name)))
        except ValueError:
            san.append(x509.DNSName(name))
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(days=2))
            .not_valid_after(until or now + timedelta(days=2))
            .add_extension(x509.SubjectAlternativeName(san), critical=False)
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .sign(key, hashes.SHA256()))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    path.with_name('privkey.pem').write_bytes(key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption()))


def mounts(config, certs):
    return ['--mount', f'type=bind,source={config},target=/etc/nginx/nginx.conf,readonly',
            '--mount', f'type=bind,source={certs},target=/etc/letsencrypt/live,readonly',
            '--mount', f'type=bind,source={FIXTURES / "webroot"},target=/var/www/acme,readonly',
            '--mount', f'type=bind,source={ROOT / "admin"},target=/usr/share/nginx/html/admin,readonly',
            '--mount', f'type=bind,source={ROOT / "miniapp"},target=/usr/share/nginx/html/h5,readonly']


def request(host, port, path, *, secure=False):
    raw = socket.create_connection(('127.0.0.1', port), timeout=10)
    if secure:
        name = 'hz-delivery-ip' if host == '118.89.27.5' else 'heytao.top'
        context = ssl.create_default_context(cafile=str(FIXTURES / 'certs' / name / 'fullchain.pem'))
        raw = context.wrap_socket(raw, server_hostname=host)
    with raw:
        raw.sendall(f'GET {path} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n'.encode())
        response = http.client.HTTPResponse(raw)
        response.begin()
        return response.status, dict(response.getheaders()), response.read().decode('utf-8')


def main():
    FIXTURES.mkdir(parents=True)
    certificate(FIXTURES / 'certs/hz-delivery-ip/fullchain.pem', ['118.89.27.5'])
    certificate(FIXTURES / 'certs/heytao.top/fullchain.pem', ['heytao.top', 'www.heytao.top'])
    for name, hosts, until in [
        ('wrongip', ['118.89.27.6'], None),
        ('wrongdomain', ['example.invalid'], None),
        ('missingwww', ['heytao.top'], None),
        ('untrusted', ['heytao.top', 'www.heytao.top'], None),
        ('expiring', ['heytao.top', 'www.heytao.top'], datetime.now(timezone.utc) + timedelta(minutes=30)),
        ('expired', ['heytao.top', 'www.heytao.top'], datetime.now(timezone.utc) - timedelta(hours=1)),
    ]:
        certificate(FIXTURES / 'variants' / name / 'fullchain.pem', hosts, until=until)
        shutil.copyfile(FIXTURES / 'variants' / name / 'fullchain.pem', FIXTURES / f'{name}.pem')
    trusted = [FIXTURES / 'certs/hz-delivery-ip/fullchain.pem', FIXTURES / 'certs/heytao.top/fullchain.pem']
    trusted.extend(path for path in (FIXTURES / 'variants').glob('*/fullchain.pem') if path.parent.name != 'untrusted')
    (FIXTURES / 'trusted.pem').write_bytes(b''.join(path.read_bytes() for path in trusted))
    challenge = FIXTURES / 'webroot/.well-known/acme-challenge/fixture'
    challenge.parent.mkdir(parents=True)
    challenge.write_text('synthetic-acme-proof', encoding='utf-8')
    (FIXTURES / 'api.conf').write_text('''events {}\nhttp { server { listen 8080;
location /actuator/health { return 200 '{"status":"UP"}'; }
location /api/ { return 200 "$http_host|$http_x_forwarded_proto"; }
} }\n''', encoding='utf-8')
    for source, target in [
        (ROOT / 'deploy/nginx/nginx.https.conf', FIXTURES / 'nginx.conf'),
        (ROOT / 'deploy/server/deploy-version.sh', FIXTURES / 'deploy-version.sh'),
        (ROOT / 'deploy/server/tests/test-deploy-version.sh', FIXTURES / 'test-deploy-version.sh'),
    ]:
        target.write_text(source.read_text(encoding='utf-8'), encoding='utf-8', newline='\n')
    # Fail instead of silently downloading missing images.
    for image in (NGINX, BUILD):
        docker('image', 'inspect', image)
    network, api, web = PREFIX + '-net', PREFIX + '-api', PREFIX + '-web'
    results = []

    def expect(label, actual, expected):
        if actual != expected:
            raise AssertionError(f'{label}: expected {expected!r}, got {actual!r}')
        results.append(label)
        print('PASS HTTPS:', label, flush=True)

    try:
        docker('network', 'create', network)
        docker('run', '-d', '--pull=never', '--name', api, '--network', network, '--network-alias', 'api',
               '--mount', f'type=bind,source={FIXTURES / "api.conf"},target=/etc/nginx/nginx.conf,readonly', NGINX)
        shared = mounts(FIXTURES / 'nginx.conf', FIXTURES / 'certs')
        checked = docker('run', '--rm', '--pull=never', '--network', network, *shared, NGINX, 'nginx', '-t')
        expect('nginx configuration syntax', checked.returncode, 0)
        missing = FIXTURES / 'missing-domain-certs'
        shutil.copytree(FIXTURES / 'certs/hz-delivery-ip', missing / 'hz-delivery-ip')
        invalid = docker('run', '--rm', '--pull=never', '--network', network,
                         *mounts(FIXTURES / 'nginx.conf', missing), NGINX, 'nginx', '-t', check=False)
        expect('missing domain certificate blocks nginx', invalid.returncode != 0, True)
        docker('run', '-d', '--pull=never', '--name', web, '--network', network,
               '-p', '127.0.0.1::80', '-p', '127.0.0.1::443', *shared, NGINX)
        bindings = json.loads(docker('inspect', web).stdout)[0]['NetworkSettings']['Ports']
        http_port = int(bindings['80/tcp'][0]['HostPort'])
        https_port = int(bindings['443/tcp'][0]['HostPort'])
        for attempt in range(30):
            try:
                request('heytao.top', https_port, '/healthz', secure=True)
                break
            except (OSError, http.client.HTTPException):
                if attempt == 29:
                    raise
                time.sleep(0.2)
        for host in ['heytao.top', 'www.heytao.top', '118.89.27.5']:
            status, headers, body = request(host, http_port, '/.well-known/acme-challenge/fixture')
            expect(f'{host} HTTP ACME proof', (status, body), (200, 'synthetic-acme-proof'))
            status, headers, _ = request(host, http_port, '/admin/?source=test')
            expect(f'{host} HTTP canonical redirect', (status, headers.get('Location')),
                   (308, 'https://heytao.top/admin/?source=test'))
        for host in ['heytao.top', '118.89.27.5']:
            status, headers, body = request(host, https_port, '/admin/', secure=True)
            expect(f'{host} TLS admin page', status == 200 and 'confirm-receipt' in body, True)
        status, headers, _ = request('www.heytao.top', https_port, '/admin/?source=test', secure=True)
        expect('www TLS canonical redirect', (status, headers.get('Location')),
               (308, 'https://heytao.top/admin/?source=test'))
        status, headers, body = request('heytao.top', https_port, '/', secure=True)
        expect('domain H5 page', status == 200 and 'clientReceiptBtn' in body, True)
        expect('security headers', (headers.get('X-Frame-Options'), headers.get('X-Content-Type-Options')),
               ('DENY', 'nosniff'))
        status, _, body = request('heytao.top', https_port, '/api/fixture', secure=True)
        expect('API Host and HTTPS forwarding', (status, body), (200, 'heytao.top|https'))
        status, _, body = request('heytao.top', https_port, '/actuator/health', secure=True)
        expect('health proxy', (status, json.loads(body)), (200, {'status': 'UP'}))
        checked = docker('run', '--rm', '--pull=never', '--network', 'none',
                         '--mount', f'type=bind,source={FIXTURES},target=/fixtures,readonly',
                         BUILD, 'bash', '/fixtures/test-deploy-version.sh')
        print(checked.stdout, end='', flush=True)
        deployment_count = int(re.search(r'(\d+) deployment cases passed', checked.stdout).group(1))
        report = {'https_checks': results, 'deployment_cases': deployment_count,
                  'deployment_output': checked.stdout, 'production_modified': False,
                  'fixture_directory': str(FIXTURES)}
        (REPORT / 'integration-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'{len(results)} HTTPS checks and {deployment_count} deployment cases passed.', flush=True)
    finally:
        for name in [web, api]:
            docker('rm', '-f', name, check=False)
        docker('network', 'rm', network, check=False)


if __name__ == '__main__':
    main()
