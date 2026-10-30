import importlib.util
import pathlib
import sys
import tempfile
import types
import threading
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
        return types.SimpleNamespace(content=self.sources[url].encode(), headers={}, status_code=200)

    def test_http_error_preserves_the_last_good_file(self):
        path = pathlib.Path(self.cache.name) / "cached.py"
        path.write_text("GOOD = True\n")
        with patch.object(self.app, "redirect", return_value=types.SimpleNamespace(
                status_code=503, content=b"<html>unavailable</html>")):
            with self.assertRaises(Exception):
                self.app.download(str(path), "https://source.example/spider.py")
        self.assertEqual("GOOD = True\n", path.read_text())

    def test_invalid_python_preserves_the_last_good_file(self):
        path = pathlib.Path(self.cache.name) / "cached.py"
        path.write_text("GOOD = True\n")
        self.sources["https://source.example/spider.py"] = "<html>error</html>"
        with self.assertRaises(SyntaxError):
            self.app.download(str(path), "https://source.example/spider.py")
        self.assertEqual("GOOD = True\n", path.read_text())

    def test_dependencies_are_isolated_between_sites_and_reload_generations(self):
        # Exercise the real base loader while stubbing only Android-only imports.
        shared = pathlib.Path(self.cache.name) / "py"
        shared.mkdir()
        platform = types.SimpleNamespace(getApplication=lambda: types.SimpleNamespace(
            getCacheDir=lambda: types.SimpleNamespace(getAbsolutePath=lambda: self.cache.name)))
        with patch.dict(sys.modules, {
            "com.chaquo.python": types.SimpleNamespace(Python=types.SimpleNamespace(getPlatform=lambda: platform)),
            "com.github.catvod": types.SimpleNamespace(Proxy=None),
            "lxml": types.SimpleNamespace(etree=None),
            "app": self.app,
        }):
            spec = importlib.util.spec_from_file_location("audit_base", APP_PATH.parent / "base/spider.py")
            base = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(base)
            with patch.dict(sys.modules, {"audit_base": base}):
                for host, marker in [("first", "first"), ("second", "second")]:
                    self.sources[f"https://{host}.example/spider.py"] = '''
from audit_base import Spider as Base
class Spider(Base):
    def getDependence(self): return ["helper"]
    def init(self, extend=""):
        self.helper = self.loadModule("helper")
    def value(self): return self.helper.MARKER
'''
                    self.sources[f"https://{host}.example/helper.py"] = f"MARKER = {marker!r}\n"
                first = self.app.spider(self.cache.name, "https://first.example/spider.py", "a")
                (shared / "helper.py").write_text("MARKER = 'first'\n")
                self.app.init(first, "")
                second = self.app.spider(self.cache.name, "https://second.example/spider.py", "b")
                (shared / "helper.py").write_text("MARKER = 'second'\n")
                self.app.init(second, "")
                self.sources["https://first.example/helper.py"] = "MARKER = 'later'\n"
                later = self.app.spider(self.cache.name, "https://first.example/spider.py", "a")
                (shared / "helper.py").write_text("MARKER = 'later'\n")
                self.app.init(later, "")
                self.assertEqual("first", first.value())
                self.assertEqual("second", second.value())
                self.assertEqual("later", later.value())
                dependency_name = first.helper.__name__
                dependency_directory = first._tv_dependency_context["directory"]
                self.app.destroy(first)
                self.assertNotIn(dependency_name, sys.modules)
                self.assertFalse(pathlib.Path(dependency_directory).exists())
                self.assertIn(later.helper.__name__, sys.modules)
                self.assertEqual("later", later.value())

    def test_download_failure_and_invalid_source_leave_no_partial_files(self):
        path = pathlib.Path(self.cache.name) / "cached.py"
        path.write_text("GOOD = True\n")
        with patch.object(self.app, "redirect", side_effect=OSError("offline")):
            with self.assertRaises(OSError):
                self.app.download(str(path), "https://source.example/spider.py")
        self.assertEqual("GOOD = True\n", path.read_text())
        self.assertEqual([path], list(pathlib.Path(self.cache.name).iterdir()))

    def test_dependency_path_cannot_escape_its_scope(self):
        url = "https://source.example/spider.py"
        self.script(url, "valid")
        spider = self.app.spider(self.cache.name, url, "site")
        with self.assertRaises(ValueError):
            self.app.load_dependency(spider, "../other-site/helper")

    def test_concurrent_dependency_load_never_returns_a_half_initialized_module(self):
        url = "https://source.example/spider.py"
        self.script(url, "valid")
        spider = self.app.spider(self.cache.name, url)
        entered, resume, second_done = threading.Event(), threading.Event(), threading.Event()
        errors, values = [], []
        barrier = types.SimpleNamespace(entered=entered, resume=resume)
        self.sources["https://source.example/helper.py"] = (
            "import dependency_barrier\ndependency_barrier.entered.set()\n"
            "dependency_barrier.resume.wait(3)\nMARKER = 'ready'\n")
        def load(done=None):
            try:
                values.append(self.app.load_dependency(spider, "helper").MARKER)
            except Exception as error:
                errors.append(error)
            finally:
                if done:
                    done.set()
        with patch.dict(sys.modules, {"dependency_barrier": barrier}):
            first = threading.Thread(target=load)
            second = threading.Thread(target=load, args=(second_done,))
            first.start()
            try:
                self.assertTrue(entered.wait(2))
                second.start()
                self.assertFalse(second_done.wait(0.1), "Returned an incomplete dependency")
            finally:
                resume.set()
                first.join(3)
                if second.ident:
                    second.join(3)
        self.assertEqual([], errors)
        self.assertEqual(["ready", "ready"], values)

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
