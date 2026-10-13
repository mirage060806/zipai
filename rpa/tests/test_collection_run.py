import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from collection_run import CollectionRun
import requests
import property_sale_crawler as crawler


class CollectionRunTests(unittest.TestCase):
    def response(self):
        response = Mock()
        response.json.return_value = {'success': True}
        return response

    def test_offline_finish_is_saved_without_secret_and_replayed(self):
        with tempfile.TemporaryDirectory() as tmp:
            tracker = CollectionRun(tmp, 'http://localhost:8080', 'SECRET_TEST_TOKEN', 'molit', 'manual', 'crawl')
            with patch('collection_run.requests.post', side_effect=requests.RequestException('offline')):
                tracker.emit()
                tracker.finished('failed')
            saved = tracker.path.read_text(encoding='utf-8')
            self.assertNotIn('SECRET_TEST_TOKEN', saved)
            self.assertEqual('failed', json.loads(saved)['payload']['status'])
            tracker.next_retry = 0
            with patch('collection_run.requests.post', return_value=self.response()) as post:
                tracker.flush()
                self.assertEqual('failed', post.call_args.kwargs['json']['status'])
            self.assertFalse(tracker.path.exists())

    def test_pending_finance_uses_finance_token_during_property_replay(self):
        with tempfile.TemporaryDirectory() as tmp:
            finance = CollectionRun(tmp, 'http://localhost:8080', '', 'finance', 'scheduled', 'crawl')
            finance.finished('partial')
            market = CollectionRun(tmp, 'http://localhost:8080', 'PROPERTY_TEST_TOKEN', 'molit', 'manual', 'crawl')
            with patch.dict(os.environ, {'FINANCE_IMPORT_TOKEN':'FINANCE_TEST_TOKEN'}), patch('collection_run.requests.post',return_value=self.response()) as post:
                market.flush()
            self.assertEqual({'X-Finance-Import-Token':'FINANCE_TEST_TOKEN'},post.call_args.kwargs['headers'])
            self.assertFalse(finance.path.exists())

    def test_same_run_progress_uses_one_file_and_increasing_sequence(self):
        with tempfile.TemporaryDirectory() as tmp:
            tracker = CollectionRun(tmp, 'http://localhost:8080', '', 'html', 'unknown', 'crawl')
            tracker.emit(); first = tracker.payload['seq']
            tracker.progress(collected=30); tracker.finished('partial')
            self.assertEqual(1, len(list(tracker.directory.glob('*.json'))))
            self.assertGreater(tracker.payload['seq'], first)
            self.assertEqual(30, tracker.payload['collected'])
            self.assertEqual('unknown', tracker.payload['trigger'])

    def test_partial_regions_are_not_reported_as_complete(self):
        events = []
        def send(*args, **kwargs):
            events.append(json.loads(json.dumps(kwargs['json'])))
            return self.response()
        with tempfile.TemporaryDirectory() as tmp, patch('collection_run.requests.post', side_effect=send), patch.object(sys, 'argv', ['crawler', '--upload', '--trigger', 'scheduled']), patch.multiple(crawler, BASE_DIR=Path(tmp), MODE='molit', TOKEN='TOKEN', MOLIT_DEAL_YMD='202610', MOLIT_REGION_SCOPE='single'), patch.object(crawler, 'validate_import_token'), patch.object(crawler, 'resolve_molit_regions', return_value=[('41117','영통구')]), patch.object(crawler, 'crawl_molit', return_value=([{'sourceId':'example'}], crawler.CrawlStats(failed_regions=1))), patch.object(crawler, 'save'), patch.object(crawler, 'upload', return_value={'success':True}):
            self.assertEqual(0, crawler.main())
        self.assertEqual('partial', events[-1]['status'])
        self.assertEqual(1, events[-1]['failedRegions'])
        self.assertEqual('scheduled', events[-1]['trigger'])

    def test_preflight_failure_is_recorded(self):
        events = []
        def send(*args, **kwargs):
            events.append(json.loads(json.dumps(kwargs['json'])))
            return self.response()
        with tempfile.TemporaryDirectory() as tmp, patch('collection_run.requests.post', side_effect=send), patch.object(sys, 'argv', ['crawler', '--upload', '--preflight']), patch.multiple(crawler, BASE_DIR=Path(tmp), TOKEN='TOKEN'), patch.object(crawler, 'check_backend', side_effect=RuntimeError('preflight')):
            with self.assertRaises(RuntimeError): crawler.main()
        self.assertEqual('failed', events[-1]['status'])
        self.assertEqual('preflight', events[-1]['stage'])
    def test_resume_counts_only_newly_acknowledged_batch(self):
        rows=[{'sourceId':'one'},{'sourceId':'two'}]
        with tempfile.TemporaryDirectory() as tmp:
            tracker=CollectionRun(tmp, 'http://localhost:8080', '', 'molit', 'manual', 'upload_only')
            state=Path(tmp)/'upload.json'
            state.write_text(json.dumps({'fingerprint':crawler.upload_fingerprint(rows,'MOLIT_APT_TRADE','molit'), 'completedBatches':[1], 'totals':{'collected':1,'inserted':1,'updated':0,'errors':0}}))
            response=self.response();response.status_code=200;response.text='';response.json.return_value={'success':True,'collected':1,'inserted':0,'updated':1,'errors':0}
            with patch.multiple(crawler, ACTIVE_HISTORY=tracker, UPLOAD_STATE_OUT=state, IMPORT_BATCH_SIZE=1), patch.object(crawler,'validate_import_token'), patch.object(crawler.SESSION,'post',return_value=response,create=True):
                result=crawler.upload(rows,'MOLIT_APT_TRADE','molit')
            self.assertEqual(0,tracker.payload['inserted'])
            self.assertEqual(1,tracker.payload['updated'])
            self.assertEqual(1,tracker.payload['reusedBatches'])
            self.assertEqual(1,result['inserted'])



if __name__ == '__main__':
    unittest.main()
