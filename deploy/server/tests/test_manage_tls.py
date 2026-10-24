"""Run: python -m unittest discover -s deploy/server/tests -p 'test_*.py' -v"""
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

# These tests exercise lifecycle decisions without acquiring a host deployment lock.
if sys.platform == 'win32':
    sys.modules.setdefault('fcntl', types.ModuleType('fcntl'))
spec = importlib.util.spec_from_file_location('manage_tls', Path(__file__).parents[1] / 'manage-ip-tls.py')
tls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tls)


class RenewalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.paths = {'letsencrypt': self.root}
        (self.root / 'renewal').mkdir()
        for name in (tls.NAME, tls.DOMAIN_NAME):
            (self.root / 'renewal' / f'{name}.conf').write_text('fixture', encoding='utf-8')
        for name, value in [('directories', self.paths), ('assert_gateway', None), ('status', None)]:
            mock = patch.object(tls, name, return_value=value)
            mock.start()
            self.addCleanup(mock.stop)
        self.client = patch.object(tls, 'client').start()
        self.run = patch.object(tls, 'run').start()
        self.addCleanup(patch.stopall)
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def test_both_lineages_override_manual_dns_with_http_webroot(self):
        tls.renew(False)
        args = [call.args[1] for call in self.client.call_args_list]
        self.assertEqual([a[a.index('--cert-name') + 1] for a in args], [tls.NAME, tls.DOMAIN_NAME])
        for a in args:
            self.assertEqual(a[a.index('--authenticator') + 1], 'webroot')
            self.assertEqual(a[a.index('--preferred-challenges') + 1], 'http')
            self.assertEqual(a[a.index('--webroot-path') + 1], '/var/www/acme')
            self.assertNotIn('--force-renewal', a)
        self.run.assert_not_called()

    def test_dry_run_exercises_hook_for_both_lineages(self):
        tls.renew(True)
        for call in self.client.call_args_list:
            self.assertIn('--dry-run', call.args[1])
            self.assertIn('--run-deploy-hooks', call.args[1])

    def test_successful_renewal_checks_and_reloads_then_removes_marker(self):
        marker = self.root / tls.MARKER
        marker.touch()
        tls.renew(False)
        self.assertEqual([c.args[0][-1] for c in self.run.call_args_list], ['-t', 'reload'])
        self.assertFalse(marker.exists())

    def test_reload_failure_retains_marker_for_retry(self):
        marker = self.root / tls.MARKER
        marker.touch()
        self.run.side_effect = [None, subprocess.CalledProcessError(1, 'reload')]
        with self.assertRaises(subprocess.CalledProcessError):
            tls.renew(False)
        self.assertTrue(marker.exists())

    def test_invalid_nginx_never_reloads_and_retains_marker(self):
        marker = self.root / tls.MARKER
        marker.touch()
        self.run.side_effect = subprocess.CalledProcessError(1, 'nginx -t')
        with self.assertRaises(subprocess.CalledProcessError):
            tls.renew(False)
        self.assertEqual(self.run.call_count, 1)
        self.assertTrue(marker.exists())

    def test_domain_failure_still_reloads_a_successfully_renewed_ip(self):
        (self.root / tls.MARKER).touch()
        self.client.side_effect = [None, subprocess.CalledProcessError(1, 'certbot')]
        with self.assertRaisesRegex(RuntimeError, tls.DOMAIN_NAME):
            tls.renew(False)
        self.assertEqual(self.client.call_count, 2)
        self.assertEqual(self.run.call_count, 2)
        self.assertFalse((self.root / tls.MARKER).exists())

    def test_ip_failure_does_not_skip_domain_renewal(self):
        self.client.side_effect = [subprocess.CalledProcessError(1, 'certbot'), None]
        with self.assertRaisesRegex(RuntimeError, tls.NAME):
            tls.renew(False)
        self.assertEqual(self.client.call_count, 2)

    def test_missing_domain_renewal_configuration_is_an_error(self):
        (self.root / 'renewal' / f'{tls.DOMAIN_NAME}.conf').unlink()
        with self.assertRaisesRegex(RuntimeError, tls.DOMAIN_NAME):
            tls.renew(False)
        self.assertEqual(self.client.call_count, 1)


class CertificateStatusTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        mock = patch.object(tls, 'ROOT', self.root)
        mock.start()
        self.addCleanup(mock.stop)
        self.run = patch.object(tls, 'run').start()
        self.addCleanup(patch.stopall)
        for name in (tls.NAME, tls.DOMAIN_NAME):
            directory = self.root / 'letsencrypt' / 'live' / name
            directory.mkdir(parents=True)
            (directory / 'fullchain.pem').write_bytes(b'public certificate fixture')

    def test_status_checks_ip_apex_www_and_both_expiry_times(self):
        with contextlib.redirect_stdout(io.StringIO()):
            tls.status(False)
        args = [c.args[0] for c in self.run.call_args_list]
        self.assertEqual(sum('-checkend' in a for a in args), 2)
        self.assertEqual([a[a.index('-verify_hostname') + 1] for a in args if '-verify_hostname' in a], list(tls.DOMAIN_HOSTS))
        self.assertEqual([a[a.index('-verify_ip') + 1] for a in args if '-verify_ip' in a], [tls.IP])

    def test_missing_domain_certificate_fails(self):
        (self.root / 'letsencrypt' / 'live' / tls.DOMAIN_NAME / 'fullchain.pem').unlink()
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(RuntimeError, tls.DOMAIN_NAME):
            tls.status(False)

    def test_invalid_certificate_is_not_reported_as_healthy(self):
        self.run.side_effect = subprocess.CalledProcessError(1, 'openssl')
        with self.assertRaises(subprocess.CalledProcessError):
            tls.status(False)

    def test_initial_ip_issuance_does_not_require_domain_certificate(self):
        (self.root / 'letsencrypt' / 'live' / tls.DOMAIN_NAME / 'fullchain.pem').unlink()
        with contextlib.redirect_stdout(io.StringIO()):
            tls.status(False, include_domain=False)
        self.assertFalse(any('-verify_hostname' in c.args[0] for c in self.run.call_args_list))

    def test_staging_uses_separate_store_and_still_checks_identity(self):
        directory = self.root / 'letsencrypt-staging' / 'live' / tls.NAME
        directory.mkdir(parents=True)
        (directory / 'fullchain.pem').write_bytes(b'staging public certificate fixture')
        with contextlib.redirect_stdout(io.StringIO()):
            tls.status(True)
        args = [c.args[0] for c in self.run.call_args_list]
        identity = [a for a in args if '-verify_ip' in a]
        self.assertEqual(len(identity), 1)
        self.assertIn('-trusted', identity[0])
        self.assertIn('-partial_chain', identity[0])
        self.assertIn('letsencrypt-staging', identity[0][-1])
        self.assertFalse(any('-verify_hostname' in a or '-checkend' in a for a in args))


if __name__ == '__main__':
    unittest.main()
