import inspect


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


class Runner():
    def __init__(self, spider):
        self.spider = spider

    def getDependence(self):
        return self.spider.getDependence()

    def getName(self):
        return self.spider.getName()

    def init(self, extend=""):
        self.spider.init(extend)

    def homeContent(self, filter):
        return self.spider.homeContent(filter)

    def homeVideoContent(self):
        return self.spider.homeVideoContent()

    def categoryContent(self, tid, pg, filter, extend):
        return self.spider.categoryContent(tid, pg, filter, extend)

    def detailContent(self, ids):
        return self.spider.detailContent(ids)

    def searchContent(self, key, quick, pg="1"):
        if accepts_args(self.spider.searchContent, 3):
            return self.spider.searchContent(key, quick, pg)
        return self.spider.searchContent(key, quick)

    def playerContent(self, flag, id, vipFlags):
        return self.spider.playerContent(flag, id, vipFlags)

    def liveContent(self, url=""):
        if accepts_args(self.spider.liveContent, 1):
            return self.spider.liveContent(url)
        return self.spider.liveContent()

    def localProxy(self, param):
        return self.spider.localProxy(param)

    def isVideoFormat(self, url):
        return self.spider.isVideoFormat(url)

    def manualVideoCheck(self):
        return self.spider.manualVideoCheck()

    def action(self, action):
        return self.spider.action(action)

    def destroy(self):
        self.spider.destroy()
