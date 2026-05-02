import json
import os
import inspect
from importlib.machinery import SourceFileLoader

import requests


def spider(cache, api):
    name = os.path.basename(api)
    path = cache + '/' + name
    download(path, api)
    name = name.split('.')[0]
    return SourceFileLoader(name, path).load_module().Spider()


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
    ru.destroy()


def action(ru, action):
    return json.dumps(ru.action(action), ensure_ascii=False)


def run():
    pass


if __name__ == '__main__':
    run()
