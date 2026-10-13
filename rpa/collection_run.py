"""수집 실행 기록: 비밀 값 없이 저장하고 연결 복구 시 다시 전송합니다."""
import json
import os
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

import requests


def utc_now():
    return datetime.now(timezone.utc).isoformat()


class CollectionRun:
    def __init__(self, base_dir, backend, token, mode, trigger, operation):
        self.backend = backend.rstrip('/')
        self.token = token
        self.directory = Path(base_dir) / 'data' / 'history_pending'
        self.run_id = str(uuid.uuid4())
        self.path = self.directory / (self.run_id + '.json')
        self.next_retry = 0
        self.partial = False
        self.payload = dict(runId=self.run_id, category={'molit':'market','html':'property'}.get(mode, mode),
                            trigger=trigger, operation=operation, stage='preflight', regionScope='none',
                            regionCount=0, dealYmd='', startedAt=utc_now(), finishedAt=None,
                            status='running', seq=0, collected=0, inserted=0, updated=0, errors=0,
                            skipped=0, failedRegions=0, reusedBatches=0)

    def emit(self, **values):
        self.payload.update(values)
        self.payload['seq'] += 1
        try:
            self.directory.mkdir(parents=True, exist_ok=True)
            temporary = self.path.with_suffix('.tmp')
            temporary.write_text(json.dumps(dict(backend=self.backend, payload=self.payload), ensure_ascii=False), encoding='utf-8')
            os.replace(temporary, self.path)
        except OSError:
            print('history_local_save=failed 이력 저장 경로를 확인하세요.')
        self.flush()

    def flush(self):
        if not self.token or time.monotonic() < self.next_retry:
            return
        files = [self.path] + [p for p in sorted(self.directory.glob('*.json')) if p != self.path][:5]
        for path in files:
            if not path.exists():
                continue
            try:
                envelope = json.loads(path.read_text(encoding='utf-8'))
                if envelope.get('backend') != self.backend:
                    continue
                finance = envelope['payload'].get('category') == 'finance'
                own_finance = self.payload['category'] == 'finance'
                token = self.token if finance == own_finance else os.getenv('FINANCE_IMPORT_TOKEN' if finance else 'PROPERTY_IMPORT_TOKEN', '')
                if not token:
                    continue
                response = requests.post(self.backend + '/api/collection-runs',
                                         headers={('X-Finance-Import-Token' if finance else 'X-Property-Import-Token'): token},
                                         json=envelope['payload'], timeout=3)
                response.raise_for_status()
                result = response.json()
                if result.get('success') is not True:
                    raise ValueError('이력 응답 형식')
                # 서버가 받은 스냅샷은 삭제합니다. 이후 진행·종료 시 새 스냅샷을 저장합니다.
                path.unlink(missing_ok=True)
            except (requests.RequestException, ValueError, OSError):
                self.next_retry = time.monotonic() + 60
                print('history_send=pending 서버·토큰 확인 후 다음 실행에서 재전송됩니다.')
                break

    def progress(self, **values):
        self.payload.update(values)
        if time.monotonic() >= getattr(self, 'next_progress', 0):
            self.next_progress = time.monotonic() + 30
            self.emit()

    def finished(self, status):
        self.emit(status=status, finishedAt=utc_now(), stage='done' if status in ('completed', 'partial') else self.payload['stage'])
