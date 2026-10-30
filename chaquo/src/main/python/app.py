import json
import os
import inspect
import hashlib
import sys
import shutil
import tempfile
import threading
from urllib.parse import urljoin
from importlib.util import module_from_spec, spec_from_loader
from importlib.machinery import SourceFileLoader

import requests


def spider(cache, api, key=""):
    identity = hashlib.sha256(((key or "") + "\0" + api).encode("utf-8")).hexdigest()
    name = "_tv_spider_" + identity
    path = os.path.join(cache, identity + ".py")
    download(path, api)
    loader = SourceFileLoader(name, path)
    module = module_from_spec(spec_from_loader(name, loader))
    previous = sys.modules.get(name)
    sys.modules[name] = module
    try:
        # Always compile the downloaded bytes: a same-size refresh within one second
        # must not reuse a timestamp-based .pyc. A fresh module also isolates old instances.
        exec(loader.source_to_code(loader.get_data(path), path), module.__dict__)
        context = {"directory": tempfile.mkdtemp(prefix=identity + "-", dir=cache),
                   "api": api, "modules": {}, "lock": threading.RLock()}
        module.Spider._tv_dependency_context = context
        instance = module.Spider()
        module.__dict__["_tv_spider_owner"] = id(instance)
        return instance
    except BaseException:
        if "context" in locals():
            release_dependencies(context)
        if sys.modules.get(name) is module:
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
        raise


def download(path, api):
    if api.startswith('http'):
        response = redirect(api)
        try:
            if not 200 <= response.status_code < 300:
                raise IOError("Python script download failed: HTTP %s" % response.status_code)
            content = response.content
        finally:
            if hasattr(response, "close"):
                response.close()
    else:
        content = str.encode(api)
    compile(content, path, 'exec')
    writeFile(path, content)


def writeFile(path, content):
    fd, staged = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(path)), suffix='.tmp')
    try:
        with os.fdopen(fd, 'wb') as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(staged, path)
    finally:
        if os.path.exists(staged):
            os.unlink(staged)


def redirect(url):
    return requests.get(url, allow_redirects=True, verify=False, timeout=(5, 20))


def dependency_path(context, name):
    root = context["directory"]
    path = os.path.abspath(os.path.join(root, name + ".py"))
    if os.path.commonpath([root, path]) != root:
        raise ValueError("Dependency path escapes spider directory")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def load_dependency(spider, name):
    context = spider._tv_dependency_context
    with context["lock"]:
        return _load_dependency(context, name)


def _load_dependency(context, name):
    if name in context["modules"]:
        return context["modules"][name]
    path = dependency_path(context, name)
    if not os.path.exists(path):
        download(path, urljoin(context["api"], name + ".py"))
    module_name = "_tv_spider_dep_" + hashlib.sha256(path.encode()).hexdigest()
    loader = SourceFileLoader(module_name, path)
    module = module_from_spec(spec_from_loader(module_name, loader))
    sys.modules[module_name] = module
    context["modules"][name] = module
    try:
        exec(loader.source_to_code(loader.get_data(path), path), module.__dict__)
        # Dependencies which are spiders inherit the same isolated dependency graph.
        if hasattr(module, "Spider"):
            module.Spider._tv_dependency_context = context
        return module
    except BaseException:
        context["modules"].pop(name, None)
        if sys.modules.get(module_name) is module:
            sys.modules.pop(module_name, None)
        raise


def release_dependencies(context):
    with context["lock"]:
        for module in context["modules"].values():
            if sys.modules.get(module.__name__) is module:
                sys.modules.pop(module.__name__, None)
        context["modules"].clear()
        shutil.rmtree(context["directory"], ignore_errors=True)


def str2json(content):
    return json.loads(content)


def accepts_args(func, count):
    try:
        sig = inspect.signature(func)
        params = list(sig.parameters.values())
        positional = [p for p in params if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)]
        required = [p for p in positional if p.default is p.empty]
        has_varargs = any(p.kind == p.VAR_POSITIONAL for p in params)
        return count >= len(required) and (has_varargs or count <= len(positional))
    except Exception:
        return True


def getDependence(ru):
    return ru.getDependence()


def getName(ru):
    return ru.getName()


def init(ru, extend):
    context = ru._tv_dependency_context
    for name in ru.getDependence() or []:
        path = dependency_path(context, name)
        if not os.path.exists(path):
            download(path, urljoin(context["api"], name + ".py"))
    ru.init(extend)


def homeContent(ru, filter):
    return json.dumps(ru.homeContent(filter), ensure_ascii=False)


def homeVideoContent(ru):
    return json.dumps(ru.homeVideoContent(), ensure_ascii=False)


def categoryContent(ru, tid, pg, filter, extend):
    return json.dumps(ru.categoryContent(tid, pg, filter, str2json(extend)), ensure_ascii=False)


def detailContent(ru, array):
    return json.dumps(ru.detailContent(str2json(array)), ensure_ascii=False)


def searchContent(ru, key, quick, pg="1"):
    if accepts_args(ru.searchContent, 3):
        return json.dumps(ru.searchContent(key, quick, pg), ensure_ascii=False)
    return json.dumps(ru.searchContent(key, quick), ensure_ascii=False)


def playerContent(ru, flag, id, vipFlags):
    return json.dumps(ru.playerContent(flag, id, str2json(vipFlags)), ensure_ascii=False)


def liveContent(ru, url=""):
    if accepts_args(ru.liveContent, 1):
        return ru.liveContent(url)
    return ru.liveContent()


def localProxy(ru, param):
    return ru.localProxy(str2json(param))


def destroy(ru):
    try:
        ru.destroy()
    finally:
        if hasattr(ru, "_tv_dependency_context"):
            release_dependencies(ru._tv_dependency_context)
        for name, module in list(sys.modules.items()):
            if name.startswith("_tv_spider_") and module is not None:
                if module.__dict__.get("_tv_spider_owner") == id(ru):
                    sys.modules.pop(name, None)


def action(ru, action):
    return json.dumps(ru.action(action), ensure_ascii=False)


def run():
    pass


if __name__ == '__main__':
    run()
