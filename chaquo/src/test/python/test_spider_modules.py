import importlib.util
import pathlib
import sys
import tempfile
import types
import unittest
from unittest.mock import patch


APP_PATH = pathlib.Path(__file__).resolve().parents[2] / "main/python/app.py"
SCRIPT = '''
MARKER = {marker!r}
STATE = {{}}
class Spider:
    def init(self, extend):
        STATE["extend"] = extend
    def homeContent(self, filter):
        return {{"marker": MARKER, "extend": STATE.get("extend")}}
    def destroy(self):
        pass
'''


class SpiderModuleTest(unittest.TestCase):
    def setUp(self):
        self.cache = tempfile.TemporaryDirectory()
        self.addCleanup(self.cache.cleanup)
        self.modules = dict(sys.modules)
        self.addCleanup(self.restore_modules)
        self.sources = {}
        requests = types.SimpleNamespace(get=self.get)
        self.request_patch = patch.dict(sys.modules, {"requests": requests})
        self.request_patch.start()
        self.addCleanup(self.request_patch.stop)
        spec = importlib.util.spec_from_file_location("bridge_under_test", APP_PATH)
        self.app = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.app)

    def restore_modules(self):
        for name in set(sys.modules) - set(self.modules):
            if name.startswith("_tv_spider_") or name == "spider":
                sys.modules.pop(name, None)
        for name, module in self.modules.items():
            if name.startswith("_tv_spider_") or name == "spider":
                sys.modules[name] = module

    def get(self, url, **kwargs):
        return types.SimpleNamespace(content=self.sources[url].encode(), headers={})

    def script(self, url, marker):
        self.sources[url] = SCRIPT.format(marker=marker)

    def test_same_filename_from_two_urls_keeps_each_instances_globals(self):
        first_url = "https://first.example/spider.py"
        second_url = "https://second.example/spider.py"
        self.script(first_url, "first")
        self.script(second_url, "second")
        first = self.app.spider(self.cache.name, first_url)
        first.init("first-config")
        second = self.app.spider(self.cache.name, second_url)
        second.init("second-config")
        self.assertEqual({"marker": "first", "extend": "first-config"}, first.homeContent(False))
        self.assertEqual({"marker": "second", "extend": "second-config"}, second.homeContent(False))

    def test_same_api_in_two_scoped_sites_has_independent_module_state(self):
        url = "https://same.example/spider.py"
        self.script(url, "shared")
        first = self.app.spider(self.cache.name, url, "1@source")
        first.init("first-config")
        second = self.app.spider(self.cache.name, url, "2@source")
        second.init("second-config")
        self.assertEqual("first-config", first.homeContent(False)["extend"])
        self.assertEqual("second-config", second.homeContent(False)["extend"])

    def test_reload_does_not_mutate_an_existing_instances_module(self):
        url = "https://same.example/spider.py"
        self.script(url, "first")
        first = self.app.spider(self.cache.name, url)
        first.init("old")
        self.script(url, "later")  # Same byte length; do not accept stale timestamp-based .pyc data.
        second = self.app.spider(self.cache.name, url)
        second.init("new")
        self.assertEqual({"marker": "first", "extend": "old"}, first.homeContent(False))
        self.assertEqual({"marker": "later", "extend": "new"}, second.homeContent(False))

    def test_failed_reload_restores_the_previous_module_registration(self):
        url = "https://same.example/spider.py"
        self.script(url, "working")
        first = self.app.spider(self.cache.name, url)
        first.init("old")
        name = first.__class__.__module__
        previous = sys.modules[name]
        self.sources[url] = "MARKER = 'broken'\nraise RuntimeError('bad script')\n"
        with self.assertRaises(RuntimeError):
            self.app.spider(self.cache.name, url)
        self.assertIs(previous, sys.modules[name])
        self.assertEqual({"marker": "working", "extend": "old"}, first.homeContent(False))

    def test_failed_first_load_does_not_leave_a_partial_module_registered(self):
        url = "https://same.example/spider.py"
        self.sources[url] = "raise RuntimeError('bad script')\n"
        before = {name for name in sys.modules if name.startswith("_tv_spider_")}
        with self.assertRaises(RuntimeError):
            self.app.spider(self.cache.name, url)
        self.assertEqual(before, {name for name in sys.modules if name.startswith("_tv_spider_")})

    def test_destroy_unregisters_only_the_owning_site_module(self):
        url = "https://same.example/spider.py"
        self.script(url, "shared")
        first = self.app.spider(self.cache.name, url, "1@source")
        second = self.app.spider(self.cache.name, url, "2@source")
        self.app.destroy(first)
        self.assertFalse(first.__class__.__module__ in sys.modules)
        self.assertTrue(second.__class__.__module__ in sys.modules)

    def test_destroy_of_an_old_instance_keeps_the_new_generation_registered(self):
        url = "https://same.example/spider.py"
        self.script(url, "shared")
        first = self.app.spider(self.cache.name, url)
        second = self.app.spider(self.cache.name, url)
        current = sys.modules[second.__class__.__module__]
        self.app.destroy(first)
        self.assertIs(current, sys.modules[second.__class__.__module__])

    def test_destroy_exception_still_unregisters_the_module(self):
        url = "https://same.example/spider.py"
        self.script(url, "shared")
        self.sources[url] += "\n    def destroy(self):\n        raise RuntimeError('destroy failed')\n"
        spider = self.app.spider(self.cache.name, url)
        with self.assertRaises(RuntimeError):
            self.app.destroy(spider)
        self.assertFalse(spider.__class__.__module__ in sys.modules)


if __name__ == "__main__":
    unittest.main()
