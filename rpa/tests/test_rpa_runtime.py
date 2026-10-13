"""외부 수집/실제 DB 변경 없이 실패 판정과 연결 설정을 검증합니다."""
import argparse
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import rpa_runtime as runtime
import finance_policy_crawler as finance
import happy_housing_crawler as housing
import property_listing_importer as listing
import property_listing_batch as batch


class RuntimeTests(unittest.TestCase):
    def test_jdbc_tidb_uses_verified_tls_and_password_without_logging(self):
        settings = {'DB_URL':'jdbc:mysql://gateway.example:4000/zipai?sslMode=VERIFY_IDENTITY',
                    'DB_USERNAME':'test', 'DB_PASSWORD':'private-test'}
        with patch.dict(os.environ, settings, clear=True), patch.object(runtime, 'load_env'):
            config = runtime.db_config()
        self.assertEqual(4000, config['port'])
        self.assertEqual('gateway.example', config['host'])
        self.assertTrue(config['ssl_verify_identity'])
        self.assertTrue(config['ssl_verify_cert'])
        self.assertEqual('private-test', config['password'])

    def test_explicit_db_host_has_precedence(self):
        with patch.dict(os.environ, {'DB_URL':'jdbc:mysql://gateway.example:4000/zipai',
                                    'ZIPAI_DB_HOST':'localhost','ZIPAI_DB_PORT':'3306'}, clear=True), patch.object(runtime,'load_env'):
            config=runtime.db_config()
        self.assertEqual('localhost',config['host'])
        self.assertEqual(3306,config['port'])

    def test_completed_partial_failure_and_cancel_are_distinct(self):
        for code,status in [(0,'completed'),(3,'partial'),(1,'failed')]:
            run=Mock(partial=False, payload={'errors':0})
            self.assertEqual(code,runtime.execute(run,lambda:code))
            run.finished.assert_called_once_with(status)
        run=Mock()
        def cancel():raise KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):runtime.execute(run,cancel)
        run.finished.assert_called_once_with('cancelled')

    def test_finance_partial_returns_three(self):
        with tempfile.TemporaryDirectory() as tmp:
            sources=Path(tmp)/'sources.json';out=Path(tmp)/'result.json'
            sources.write_text(json.dumps([dict(policyName=n,sourceName='공식',sourceUrl='https://example.test/detail',keyword=n) for n in ['정책1','정책2']]))
            args=argparse.Namespace(sources=str(sources),output=str(out),check=False,no_upload=True)
            tracker=Mock()
            with patch.object(finance,'load_dotenv'),patch.object(finance,'fetch',side_effect=[{'policyName':'정책1'},RuntimeError('실패')]),patch.object(finance,'ACTIVE_HISTORY',tracker):
                self.assertEqual(3,finance._main(args))
            self.assertTrue(tracker.partial)
            self.assertEqual(1,len(json.loads(out.read_text(encoding="utf-8"))['failures']))

    def test_finance_homepage_uses_unique_same_host_detail(self):
        responses=[]
        for url,html in [('https://example.test/','<a href="/detail">내집마련 디딤돌</a>'),('https://example.test/detail','<main>내집마련 디딤돌 금리 및 신청 대상 안내</main>')]:
            response=Mock(url=url,content=html.encode(),encoding='utf-8',headers={'Content-Type':'text/html'})
            responses.append(response)
        source=dict(policyName='디딤돌',sourceName='공식',sourceUrl='https://example.test/',keyword='내집마련 디딤돌')
        with patch.object(finance.requests,'get',side_effect=responses):
            result=finance.fetch(source,10)
        self.assertEqual('https://example.test/detail',result['sourceUrl'])
        self.assertIn('금리',result['snapshotExcerpt'])

    def test_finance_ambiguous_detail_is_not_success(self):
        response=Mock(url='https://example.test/',encoding='utf-8',headers={'Content-Type':'text/html'},content=b'<html></html>')
        with patch.object(finance.requests,'get',return_value=response):
            with self.assertRaises(RuntimeError):
                finance.fetch(dict(policyName='정책',sourceName='공식',sourceUrl='https://example.test/',keyword='정책'),10)

    def test_upload_error_never_finalizes_snapshot(self):
        response=Mock(status_code=200,text='')
        response.json.return_value={'collected':1,'inserted':0,'updated':0,'errors':1}
        with patch.object(listing,'validate_backend'),patch.object(listing.SESSION,'post',return_value=response) as post:
            with self.assertRaises(RuntimeError):listing.upload('TEST',[{'sourceId':'one'}],500,False,True)
        self.assertEqual(1,post.call_count)
        self.assertNotIn('finalize',post.call_args.args[0])

    def test_bad_input_never_uploads_complete_snapshot(self):
        argv=['importer','--file','dummy.csv','--upload','--complete-snapshot','--no-geocode']
        with patch.object(sys,'argv',argv),patch.object(listing,'load_rows',return_value=[{}]),patch.object(listing,'normalize_row',side_effect=ValueError('불완전')),patch.object(listing,'save'),patch.object(listing,'upload') as upload:
            self.assertEqual(2,listing.main())
        upload.assert_not_called()

    def test_multiple_snapshot_files_are_rejected_before_import(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);incoming=root/'incoming';incoming.mkdir()
            for name in ('one.csv','two.csv'):(incoming/name).write_text('header\n')
            args=argparse.Namespace(incoming=incoming,archive=root/'archive',errors=root/'errors')
            with patch.dict(os.environ,{'PROPERTY_LISTING_COMPLETE_SNAPSHOT':'true'}),patch.object(batch,'load_project_env'),patch.object(batch,'run') as run:
                self.assertEqual(2,batch._main(args))
            run.assert_not_called()

    def test_lifestyle_pipeline_builds_and_records_committed_regions(self):
        import lifestyle_pipeline as pipeline
        import lifestyle_public_data_builder as builder
        import lifestyle_score_importer as importer
        run=Mock(payload={'errors':0},partial=False)
        with patch.object(sys,'argv',['pipeline','--trigger','manual']),patch.object(pipeline,'load_env'),patch.object(pipeline,'start_run',return_value=run),patch.dict(os.environ,{'LIFESTYLE_SOURCE_DATE':'2026-09-30'}),patch.object(builder,'build') as build,patch.object(importer,'import_csv',return_value=31):
            self.assertEqual(0,pipeline.main())
        build.assert_called_once()
        run.emit.assert_any_call(collected=31)
        run.finished.assert_called_once_with('completed')

    def test_finance_check_uses_get_without_import_history(self):
        response=Mock(status=200)
        response.read.return_value=b'{"success":true}'
        context=Mock();context.__enter__=Mock(return_value=response);context.__exit__=Mock(return_value=False)
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'sources.json'
            path.write_text(json.dumps([dict(policyName='정책',sourceName='공식',sourceUrl='https://example.test/detail',keyword='정책')]))
            args=argparse.Namespace(sources=str(path),output='',check=True,no_upload=False)
            with patch.object(finance,'load_dotenv'),patch.dict(os.environ,{'FINANCE_IMPORT_TOKEN':'test'}),patch.object(finance.urllib.request,'urlopen',return_value=context) as urlopen:
                self.assertEqual(0,finance._main(args))
            request=urlopen.call_args.args[0]
            self.assertEqual('GET',request.get_method())
            self.assertIn('/api/collection-runs/check',request.full_url)

    def test_lh_partial_aggregates_all_stages_and_skips_failed_detail(self):
        tracker=Mock();session=Mock()
        patches=dict(create_session=Mock(return_value=session),get_happy_housing_list=Mock(return_value=[{'title':'공고1'},{'title':'공고2'}]),
            get_detail=Mock(side_effect=[{'title':'공고1'},RuntimeError('상세 오류')]),print_notice_summary=Mock(),
            download_pdf_files=Mock(return_value=(1,1)),extract_downloaded_pdf_texts=Mock(return_value=(1,2)),
            extract_rule_sections_from_downloaded_pdfs=Mock(return_value=(1,5,3)),_reset_current_rule_value_folders=Mock(),
            extract_all_youth_rule_values=Mock(return_value={'failed_count':4}),extract_remaining_rule_values=Mock(return_value={'student':{'failed':5}}),
            build_processed_notice=Mock(side_effect=lambda notice:notice),save_json=Mock(),save_csv=Mock(),
            save_crawl_to_mysql=Mock(return_value={'saved':True}),ACTIVE_HISTORY=tracker,RUN_ERRORS=0)
        with patch.multiple(housing,**patches),patch.object(housing.time,'sleep'):
            self.assertEqual(3,housing._main())
            self.assertEqual(16,housing.RUN_ERRORS)
        tracker.emit.assert_any_call(collected=1,errors=16,stage='upload')
        session.close.assert_called_once()


if __name__=='__main__':unittest.main()
