"""Replay a local private NSG capture; emit aggregate errors, never payloads/IDs.

Requires the research repo's shannondm-analysis/survey_nsg.py. The helper is
executed locally; --wsl selects a Linux helper through WSL on Windows.
"""
import argparse
import datetime
import io
import json
from pathlib import Path
import statistics
import struct
import subprocess
import sys

p = argparse.ArgumentParser()
p.add_argument('--capture', required=True)
p.add_argument('--oracle', required=True)
p.add_argument('--nsg-reader', required=True)
p.add_argument('--helper', required=True)
p.add_argument('--wsl', action='store_true')
a = p.parse_args()
sys.path.insert(0, str(Path(a.nsg_reader).parent))
from survey_nsg import frames, dm_header

stream = io.BytesIO(b'SDMRv1\n\0')
stream.seek(0, io.SEEK_END)
origin = None
for _, _, timestamp, kind, data in frames(Path(a.capture)):
    if kind != 24:
        continue
    header = dm_header(data)
    if not header or header[2] != 0xa1:
        continue
    if origin is None:
        origin = timestamp // 1_000_000
    stream.write(struct.pack('<QI', timestamp, len(data)))
    stream.write(data)
stream.write(struct.pack('<QI', 2**64 - 1, 0))
command = (['wsl', '-u', 'root', '--'] if a.wsl else []) + [a.helper, '--records', '-']
run = subprocess.run(command, input=stream.getvalue(), capture_output=True, check=True)
snapshots = {origin + row['window_s']: row for row in
             (json.loads(line) for line in run.stdout.splitlines())}
oracle = json.loads(Path(a.oracle).read_text(encoding='utf-8-sig'))
comparisons = [
    ('dl_mbps', 'HSDPA', 'Throughput_DL', 1e-5),
    ('ul_mbps', 'HSUPA', 'Throughput_UL', 1e-5),
    ('qpsk_pct', 'HSDPA', 'QPSKUSAGE', 1e-4),
    ('qam16_pct', 'HSDPA', 'Q16USAGE', 1e-4),
    ('qam64_pct', 'HSDPA', 'Q64USAGE', 1e-4),
    ('hs_scch_success_pct', 'HSDPA', 'Demod_SCCH_SuccessfulRate_Carrier_0', 1e-4),
]
result = {}
failed = False
for field, section, name, tolerance in comparisons:
    errors = []
    for entry in oracle['data']:
        time = int(datetime.datetime.fromisoformat(entry['Timestamp']).timestamp())
        # This export publishes each completed radio window two seconds later.
        actual = snapshots.get(time - 2, {}).get('wcdma', {}).get(field)
        expected = entry.get('WCDMA', {}).get(section, {}).get(name)
        if actual is not None and expected is not None:
            errors.append(abs(actual - expected))
    passed = bool(errors) and max(errors) <= tolerance
    failed |= not passed
    result[field] = dict(windows=len(errors), max_error=max(errors) if errors else None,
                         mean_error=statistics.mean(errors) if errors else None, passed=passed)
print(json.dumps(result, indent=2))
weighted_checks = []
for entry in oracle['data']:
    time = int(datetime.datetime.fromisoformat(entry['Timestamp']).timestamp())
    actual = snapshots.get(time - 2, {}).get('wcdma', {}).get('cqi')
    expected = entry.get('WCDMA', {}).get('HSDPA', {})
    if actual is not None and 'CQIMIN' in expected and 'CQIMAX' in expected:
        weighted_checks.append(expected['CQIMIN'] <= actual <= expected['CQIMAX'])
print(json.dumps({'weighted_cqi_within_nsg_range': dict(windows=len(weighted_checks),
    passed=bool(weighted_checks) and all(weighted_checks))}))
failed |= not weighted_checks or not all(weighted_checks)
sys.exit(int(failed))
