package com.fongmi.android.tv.ui.presenter;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodDetailPresenterTest {
    @Test public void onlyLatestSiteVideoAndTokenMayRender() {
        Harness h=new Harness(); VodDetailPresenter page=new VodDetailPresenter(h);
        page.request(); String old=page.token(); page.request(); String current=page.token();
        page.accept(result("site","video",old,true,""));
        page.accept(result("other","video",current,true,""));
        page.accept(result("site","other",current,true,""));
        assertEquals(0,h.renders); assertEquals(0,h.cleared);
        page.accept(result("site","video",current,true,"notice"));
        assertEquals(1,h.renders); assertEquals(1,h.cleared); assertEquals("notice",h.message);
    }
    @Test public void invalidationCancelsDependentHistoryAndRejectsOldDetail() {
        Harness h=new Harness(); VodDetailPresenter page=new VodDetailPresenter(h); page.request(); String token=page.token();
        page.invalidate(); page.accept(result("site","video",token,true,""));
        assertEquals(1,h.cancelled); assertEquals(0,h.renders);
    }
    @Test public void emptySourceSwitchContinuesSearchInsteadOfClosingScreen() {
        Harness h=new Harness(); h.switching=true; VodDetailPresenter page=new VodDetailPresenter(h); page.request();
        page.accept(result("site","video",page.token(),false,"failed"));
        assertEquals(1,h.resumed); assertEquals(1,h.continued); assertEquals(0,h.finished); assertNull(h.message);
    }
    @Test public void emptyResultPreservesCollectFailureAndFallbackProductPolicies() {
        for(boolean collect:new boolean[]{false,true}) for(boolean failed:new boolean[]{false,true}) for(boolean name:new boolean[]{false,true}) {
            Harness h=new Harness(); h.collect=collect; h.name=name;
            VodDetailPresenter page=new VodDetailPresenter(h); page.request();
            page.accept(result("site","video",page.token(),false,failed?"failed":""));
            assertEquals((collect||failed)&&!name?1:0,h.finished);
            assertEquals(!collect&&!failed&&!name?1:0,h.empty);
            assertEquals((collect||failed)&&name?1:0,h.offered);
            assertEquals(!collect&&!failed&&name?1:0,h.recovered);
        }
    }
    private static Result result(String key,String video,String token,boolean content,String message) {
        Result result=new Result() {
            @Override public String getKey(){return key;}
            @Override public String getRequestId(){return video;}
            @Override public String getRequestToken(){return token;}
            @Override public String getMsg(){return message;}
        };
        result.setList(content?Collections.singletonList(new Vod()):Collections.emptyList()); return result;
    }
    private static final class Harness implements VodDetailPresenter.Port {
        int sequence,renders,cleared,cancelled,resumed,continued,finished,empty,recovered,offered;
        boolean switching,collect,name; String message;
        @Override public String nextRequestToken(){return "request-"+(++sequence);}
        @Override public String siteKey(){return "site";}
        @Override public String videoId(){return "video";}
        @Override public void setPendingRequest(String token){}
        @Override public void cancelPendingRequest(){cancelled++;}
        @Override public void scheduleSourceTimeout(){}
        @Override public void requestDetail(String token){}
        @Override public boolean accepts(Result result){return true;}
        @Override public void clearSourceTimeout(){cleared++;}
        @Override public void prepareFor(Vod vod){}
        @Override public void render(Vod vod){renders++;}
        @Override public boolean isSourceSwitching(){return switching;}
        @Override public void resumeSourceSearch(){resumed++;}
        @Override public void continueSourceSwitch(){continued++;}
        @Override public boolean shouldFinishWhenEmpty(){return collect;}
        @Override public boolean hasFallbackName(){return name;}
        @Override public void finishScreen(){finished++;}
        @Override public void showEmptyState(){empty++;}
        @Override public void showFallbackName(){}
        @Override public void scheduleEmptyFallback(){}
        @Override public void recoverSource(){recovered++;}
        public void offerOtherSources(){offered++;}
        @Override public void notifyMessage(String value){message=value;}
    }
}
