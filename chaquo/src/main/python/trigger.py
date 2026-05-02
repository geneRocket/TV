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


class Trigger():
    @staticmethod
    def init(sp_obj, extend=""):
        sp_obj.init(extend)

    @staticmethod
    def homeContent(sp_obj, filter):
        return sp_obj.homeContent(filter)

    @staticmethod
    def homeVideoContent(sp_obj):
        return sp_obj.homeVideoContent()

    @staticmethod
    def categoryContent(sp_obj, tid, pg, filter, extend):
        return sp_obj.categoryContent(tid, pg, filter, extend)

    @staticmethod
    def detailContent(sp_obj, ids):
        return sp_obj.detailContent(ids)

    @staticmethod
    def searchContent(sp_obj, key, quick, pg="1"):
        if accepts_args(sp_obj.searchContent, 3):
            return sp_obj.searchContent(key, quick, pg)
        return sp_obj.searchContent(key, quick)

    @staticmethod
    def playerContent(sp_obj, flag, id, vipFlags):
        return sp_obj.playerContent(flag, id, vipFlags)

    @staticmethod
    def liveContent(sp_obj, url=""):
        if accepts_args(sp_obj.liveContent, 1):
            return sp_obj.liveContent(url)
        return sp_obj.liveContent()

    @staticmethod
    def isVideoFormat(sp_obj, url):
        return sp_obj.isVideoFormat(url)

    @staticmethod
    def manualVideoCheck(sp_obj):
        return sp_obj.manualVideoCheck()

    @staticmethod
    def action(sp_obj, action):
        return sp_obj.action(action)

    @staticmethod
    def destroy(sp_obj):
        sp_obj.destroy()
