"""실제 SSH·Docker 실행 없이 설정 보존과 자동 적용 조건을 검증합니다."""
import importlib.util
import os
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).resolve().parents[1] / 'sync_ec2_env.py'
spec = importlib.util.spec_from_file_location('sync_ec2_env', SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


def remote_namespace():
    namespace = {'__name__':'unit_test'}
    fake_fcntl=types.ModuleType('fcntl')
    fake_fcntl.flock=Mock()
    fake_fcntl.LOCK_EX=2
    fake_fcntl.LOCK_NB=4
    with patch.dict('sys.modules', {'fcntl':fake_fcntl}):
        exec(sync.REMOTE_CODE,namespace)
    return namespace


class EnvSyncTests(unittest.TestCase):
    def test_reads_file_instead_of_inherited_shell_tokens(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'.env'
            path.write_text('PROPERTY_IMPORT_TOKEN="file-property"\nFINANCE_IMPORT_TOKEN=file-finance # 설명\n',encoding='utf-8-sig')
            with patch.dict(os.environ,{'PROPERTY_IMPORT_TOKEN':'stale-shell'}):
                result=sync.read_tokens(path)
        self.assertEqual('file-property',result['PROPERTY_IMPORT_TOKEN'])
        self.assertEqual('file-finance',result['FINANCE_IMPORT_TOKEN'])

    def test_missing_token_is_rejected_before_ssh(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'.env';path.write_text('PROPERTY_IMPORT_TOKEN=one\n',encoding='utf-8')
            with self.assertRaises(RuntimeError):sync.read_tokens(path)

    def test_only_two_tokens_change_and_duplicates_are_removed(self):
        merge=remote_namespace()['merge_tokens']
        original='DB_PASSWORD=keep-private\r\nNAVER_OAUTH_CLIENT_SECRET=keep-login\r\nPROPERTY_IMPORT_TOKEN=old\r\nPROPERTY_IMPORT_TOKEN=duplicate\r\n# 유지\r\n'
        result=merge(original,dict(PROPERTY_IMPORT_TOKEN='new-property',FINANCE_IMPORT_TOKEN='new-finance'))
        self.assertIn('DB_PASSWORD=keep-private\r\n',result)
        self.assertIn('NAVER_OAUTH_CLIENT_SECRET=keep-login\r\n',result)
        self.assertIn('# 유지\r\n',result)
        self.assertEqual(1,result.count('PROPERTY_IMPORT_TOKEN='))
        self.assertNotIn('duplicate',result)
        self.assertEqual(result,merge(result,dict(PROPERTY_IMPORT_TOKEN='new-property',FINANCE_IMPORT_TOKEN='new-finance')))

    def test_no_final_newline_does_not_join_settings(self):
        merge=remote_namespace()['merge_tokens']
        result=merge('PORT=8080',dict(PROPERTY_IMPORT_TOKEN='p',FINANCE_IMPORT_TOKEN='f'))
        self.assertTrue(result.startswith('PORT=8080\nPROPERTY_IMPORT_TOKEN='))

    def run_remote(self, current, applied):
        namespace=remote_namespace()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);config=root/'zipai-config';config.mkdir()
            (config/'zipai.env').write_text('DB_PASSWORD=keep\n',encoding='utf-8')
            repo=root/'zipai-integrated';repo.mkdir();(repo/'deploy-ec2.sh').write_text('exit 0\n')
            namespace['container_tokens']=Mock(side_effect=[current,applied])
            namespace['check_api']=Mock()
            if not hasattr(os,'fchmod'):
                # Windows에서 원격 POSIX 권한 호출은 대체합니다.
                permission=patch.object(os,'fchmod',create=True)
            else:
                permission=patch.object(os,'fchmod',wraps=os.fchmod)
            with permission,patch.object(namespace['Path'],'home',return_value=root),patch.object(namespace['json'],'load',return_value=applied),patch.object(namespace['subprocess'],'run',return_value=Mock(returncode=0)) as deploy,patch('builtins.print'):
                namespace['main']()
            self.assertIn('DB_PASSWORD=keep\n',(config/'zipai.env').read_text(encoding='utf-8'))
            namespace['check_api'].assert_called_once_with(applied)
            return deploy.call_count

    def test_mismatched_container_deploys_once(self):
        values=dict(PROPERTY_IMPORT_TOKEN='p',FINANCE_IMPORT_TOKEN='f')
        self.assertEqual(1,self.run_remote({},values))

    def test_matching_container_skips_deploy(self):
        values=dict(PROPERTY_IMPORT_TOKEN='p',FINANCE_IMPORT_TOKEN='f')
        self.assertEqual(0,self.run_remote(values,values))


if __name__=='__main__':unittest.main()
