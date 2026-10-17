import json
import os
import inspect
import hashlib
import sys
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
        instance = module.Spider()
        module.__dict__["_tv_spider_owner"] = id(instance)
        return instance
    except BaseException:
        if sys.modules.get(name) is module:
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
        raise


def download(path, api):
    if api.startswith('http'):
        writeFile(path, redirect(api).content)
    else:
        writeFile(path, str.encode(api))


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False)
    if 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    return rsp


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
