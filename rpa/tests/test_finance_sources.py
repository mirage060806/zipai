"""금융 정책 본문 검증과 주소 공백 처리를 검사합니다."""
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import finance_policy_crawler as crawler


class FinanceSourceTests(unittest.TestCase):
    def source(self):
        return dict(policyName='시험 정책',sourceName='공식',sourceUrl='https://example.test/policy',
                    keyword='시험 정책',resolveDetail=False,contentSelector='#policy',titleSelector='#heading',
                    requiredBodyKeywords=['대출대상','대출금리','대출한도'])

    def response(self, html, url='https://example.test/policy'):
        return Mock(content=html.encode('utf-8'),url=url,encoding='utf-8',headers={'Content-Type':'text/html'})

    def test_url_whitespace_is_removed_before_request(self):
        source=self.source();source['sourceUrl']=' https://example.test/policy '
        html='<h2 id="heading">시험 정책</h2><div id="policy">대출대상 무주택자 대출금리 2% 대출한도 1억원</div>'
        with patch.object(crawler.requests,'get',return_value=self.response(html)) as get:
            crawler.fetch(source,10)
        self.assertEqual('https://example.test/policy',get.call_args.args[0])

    def test_menu_keyword_does_not_hide_missing_body(self):
        html='<nav>시험 정책 대출대상 대출금리 대출한도</nav><h2 id="heading">시험 정책</h2>'
        with patch.object(crawler.requests,'get',return_value=self.response(html)):
            with self.assertRaises(RuntimeError):crawler.fetch(self.source(),10)

    def test_header_or_error_page_is_not_policy_body(self):
        html='<h2 id="heading">시험 정책</h2><div id="policy">잠시 후 다시 접속하세요</div>'
        with patch.object(crawler.requests,'get',return_value=self.response(html)):
            with self.assertRaises(RuntimeError):crawler.fetch(self.source(),10)

    def test_entire_policy_body_is_hashed_beyond_excerpt_limit(self):
        html='<h2 id="heading">시험 정책</h2><div id="policy">대출대상 대출금리 대출한도 '+('내용 '*3000)+' 만기 10년</div>'
        with patch.object(crawler.requests,'get',return_value=self.response(html)):
            first=crawler.fetch(self.source(),10)
        with patch.object(crawler.requests,'get',return_value=self.response(html.replace('만기 10년','만기 20년'))):
            second=crawler.fetch(self.source(),10)
        self.assertEqual(first['snapshotExcerpt'],second['snapshotExcerpt'])
        self.assertNotEqual(first['sourceHash'],second['sourceHash'])

    def test_detail_link_whitespace_is_removed(self):
        source=dict(policyName='시험 정책',sourceName='공식',sourceUrl='https://example.test/',keyword='시험 정책')
        home=self.response('<a href=" /detail.jsp ">시험 정책</a>','https://example.test/')
        detail=self.response('<main>시험 정책 내용</main>','https://example.test/detail.jsp')
        with patch.object(crawler.requests,'get',side_effect=[home,detail]) as get:
            crawler.fetch(source,10)
        self.assertEqual('https://example.test/detail.jsp',get.call_args.args[0])


if __name__=='__main__':unittest.main()
