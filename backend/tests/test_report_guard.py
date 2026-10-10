import unittest
from concurrent.futures import ThreadPoolExecutor
from threading import Event
from fastapi import HTTPException
from backend.app.core.report_guard import guard_preparation, _owners


class ReportGuardTests(unittest.TestCase):
    def test_concurrent_same_account_rejected_before_paid_work_but_reads_and_other_accounts_work(self):
        started, release = Event(), Event()
        calls = []

        @guard_preparation('prepare')
        def run(owner, prepare=False):
            if owner == 'one' and prepare:
                calls.append(owner)
                started.set()
                self.assertTrue(release.wait(3))
            return owner

        with ThreadPoolExecutor(max_workers=1) as pool:
            first = pool.submit(run, 'one', True)
            try:
                self.assertTrue(started.wait(3))
                with self.assertRaises(HTTPException) as raised:
                    run('one', prepare=True)
                self.assertEqual(409, raised.exception.status_code)
                self.assertEqual('one', run('one'))
                self.assertEqual('two', run('two', True))
            finally:
                release.set()
            self.assertEqual('one', first.result())
        self.assertEqual(['one'], calls)
        self.assertEqual({}, _owners)

    def test_nested_chapter_and_exception_release_the_account(self):
        @guard_preparation('prepare')
        def child(owner, prepare=False):
            raise ValueError('fixture')

        @guard_preparation('prepare')
        def parent(owner, prepare=False):
            return child(owner, True)

        for _ in range(2):
            with self.assertRaises(ValueError):
                parent('one', True)
            self.assertEqual({}, _owners)
