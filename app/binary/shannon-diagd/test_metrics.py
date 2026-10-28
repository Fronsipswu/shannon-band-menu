"""Synthetic app-socket lifecycle/authentication checks; never opens a modem."""
import argparse
import json
import os
import socket
import subprocess
import time
import unittest

BINARY = None
APP_UID = os.getuid()


class MetricsTests(unittest.TestCase):
    def setUp(self):
        self.process = subprocess.Popen(
            [BINARY, '--input', '-', '--serve', str(APP_UID)],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        self.client = socket.socket(socket.AF_UNIX)
        self.client.settimeout(3)
        for _ in range(100):
            try:
                self.client.connect('\0sbm_diagd_' + str(APP_UID))
                break
            except OSError:
                self.assertIsNone(self.process.poll())
                time.sleep(.02)
        else:
            self.fail('No app socket')
        self.stream = self.client.makefile('rwb', buffering=0)

    def request(self, command):
        self.stream.write(json.dumps({'cmd': command}, separators=(',', ':')).encode() + b'\n')
        return json.loads(self.stream.readline())

    def test_stats_and_clean_stop(self):
        result = self.request('stats')
        self.assertTrue(result['ok'])
        self.assertEqual(result['version'], '0.4.0')
        time.sleep(1.2)
        self.assertEqual(self.request('stats')['snapshot']['schema'], 1)
        self.assertTrue(self.request('stop')['ok'])
        self.assertEqual(self.process.wait(timeout=3), 0)

    def test_arbitrary_commands_are_rejected(self):
        self.assertFalse(self.request('activate')['ok'])
        self.assertTrue(self.request('stats')['ok'])

    def test_duplicate_helper_refused_before_transport_open(self):
        other = subprocess.run([BINARY, '--auto', '--serve', str(APP_UID)], capture_output=True, timeout=3)
        self.assertNotEqual(other.returncode, 0)
        self.assertIn(b'metrics socket', other.stderr)
        self.assertNotIn(b'activation', other.stderr)

    @unittest.skipUnless(os.getuid() == 0, 'Cross-UID test requires root on the Linux host')
    def test_wrong_uid_cannot_read_metrics(self):
        self.stream.close()
        self.client.close()
        time.sleep(.2)
        script = """import os,socket,sys
os.setgid(65534)
os.setuid(65534)
s=socket.socket(socket.AF_UNIX)
s.settimeout(3)
s.connect('\\0sbm_diagd_' + sys.argv[1])
assert s.recv(1)==b''
"""
        result = subprocess.run(['python3', '-c', script, str(APP_UID)], capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_app_loss_watchdog_exits(self):
        self.assertTrue(self.request('stats')['ok'])
        self.stream.close()
        self.client.close()
        self.assertEqual(self.process.wait(timeout=18), 0)

    def tearDown(self):
        self.stream.close()
        self.client.close()
        if self.process.poll() is None:
            self.process.terminate()
        self.process.wait(timeout=4)
        self.process.stdin.close()
        errors = self.process.stderr.read().decode()
        self.process.stderr.close()
        self.assertNotIn('AddressSanitizer', errors)
        self.assertNotIn('runtime error:', errors)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--binary', required=True)
    args = parser.parse_args()
    BINARY = os.path.abspath(args.binary)
    unittest.main(argv=[__file__], verbosity=2)
