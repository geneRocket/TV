package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import com.github.catvod.utils.Trans;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class SubtitleExpansionTest {
    @Test public void hongKongQueriesUseBothScriptsWithoutRemovingSequelNumbers() {
        List<String> aliases=new SubtitleQueries(Trans::toSimplified,Trans::toTraditional).variants("無間道2（粵語）[2160p]");
        assertTrue(aliases.contains("無間道2")); assertTrue(aliases.contains("无间道2"));
        assertFalse(aliases.contains("无间道")); assertTrue(aliases.size()<=4);
        assertEquals("英雄本色",SubtitleQueries.clean("英雄本色（国语）"));
        assertEquals("2046",SubtitleQueries.clean("2046"));
    }

    @Test public void cantoneseSortingCannotPromoteADifferentMovieAboveTheExactTitle() {
        Subtitle exact=item("exact","English");exact.setMovieTitle("無間道");
        Subtitle cantonese=item("粤语修复版","粵語");cantonese.setMovieTitle("無間道2");
        List<Subtitle> sorted=SubtitleRanking.order(Arrays.asList(cantonese,exact),Arrays.asList("无间道","無間道"),SubtitlePreference.CANTONESE);
        assertSame(exact,sorted.get(0));
    }

    @Test public void preferencesRankSameMovieWithoutDroppingOtherVersions() {
        Subtitle english=item("English release","English"),traditional=item("繁体版","繁体"),
                cantonese=item("粤英字幕","粵語 英语 双语");
        List<Subtitle> input=Arrays.asList(english,traditional,cantonese);
        for(Subtitle item:input)item.setMovieTitle("无间道");
        assertSame(cantonese,SubtitleRanking.order(input,Collections.singletonList("无间道"),SubtitlePreference.CANTONESE).get(0));
        assertSame(traditional,SubtitleRanking.order(input,Collections.singletonList("无间道"),SubtitlePreference.TRADITIONAL).get(0));
        assertSame(cantonese,SubtitleRanking.order(input,Collections.singletonList("无间道"),SubtitlePreference.BILINGUAL).get(0));
        assertSame(english,SubtitleRanking.order(input,Collections.singletonList("无间道"),SubtitlePreference.ALL).get(0));
        assertEquals(3,SubtitleRanking.order(input,Collections.singletonList("无间道"),SubtitlePreference.CHINESE).size());
    }

    @Test public void subhdParsesReleaseLanguagePagingAndWebsiteAction() {
        String card="<div class='clearfix'><a class='align-middle' href='/a/abc123'>無間道</a>"
                +"<div class='view-text'><a href='/a/abc123'>Infernal Affairs 2002 4K</a></div>"
                +"<div class='f11'><span class='fw-bold'>繁体</span><span class='fw-bold'>粤语</span></div></div>";
        SubtitleGateway.Page result=SubhdSubtitleParser.search(card+card+"<div class='pagination'><a href='/search/title/2'>2</a></div>","無間道",1);
        assertEquals(1,result.items.size());Subtitle item=result.items.get(0);
        assertEquals("無間道",item.getMovieTitle()); assertEquals("繁体 粤语",item.getLanguage());
        assertEquals("https://subhd.me/a/abc123",item.getUrl());assertTrue(item.isExternal());
        assertEquals("SubHD",item.getSource());assertFalse(item.getIsZip()); assertTrue(result.hasNext);
    }

    @Test public void subhdChallengesAreErrorsRatherThanEmptySearches() {
        try {SubhdSubtitleParser.search("<title>Just a moment...</title><form id='challenge-form'/>","無間道",1);fail();}
        catch(IllegalArgumentException expected){}
        assertTrue(SubhdSubtitleParser.search("<input id='sn'>没有结果","test",1).items.isEmpty());
    }

    @Test public void r3AndSubhdUrlsEncodeTitlesAndStripOnlyKnownVersionLabels() {
        assertEquals("https://subhd.me/search/%E6%97%A0%E9%97%B4%E9%81%93",SubtitleWebsites.subhd("无间道（粤语）"));
        assertEquals("無間道2",okhttp3.HttpUrl.parse(SubtitleWebsites.r3("無間道2（粵語）")).queryParameter("s"));
    }

    @Test public void oneUnavailableSourceCannotEraseAnotherSourcesResultsAndCancelOwnsEveryRequest() {
        Map<SubtitleSource,SubtitleGateway.Callback<SubtitleGateway.Page>> pending=new EnumMap<>(SubtitleSource.class);
        Set<SubtitleSource> cancelled=EnumSet.noneOf(SubtitleSource.class);
        List<SubtitleGateway.Page> results=new ArrayList<>();List<Throwable> errors=new ArrayList<>();
        SubtitleGateway.Request request=SubtitleFanOut.start(1,(source,callback)->{pending.put(source,callback);return ()->cancelled.add(source);},probe(results,errors));
        pending.get(SubtitleSource.ASSRT).failure(new IOException());
        pending.get(SubtitleSource.SUBHD).success(page(item("粤语","粤语"),true,4));
        pending.get(SubtitleSource.WEBSITE).success(page(item("English","English"),false,-1));
        assertTrue(errors.isEmpty());assertEquals(1,results.size());assertEquals(2,results.get(0).items.size());
        assertEquals(Collections.singletonList("ASSRT"),results.get(0).unavailable);assertTrue(results.get(0).hasNext);
        request.cancel();assertEquals(3,cancelled.size());
    }

    @Test public void cancelledFanOutDiscardsLateResponses() {
        Map<SubtitleSource,SubtitleGateway.Callback<SubtitleGateway.Page>> pending=new EnumMap<>(SubtitleSource.class);
        List<SubtitleGateway.Page> results=new ArrayList<>();List<Throwable> errors=new ArrayList<>();
        SubtitleGateway.Request request=SubtitleFanOut.start(1,(source,callback)->{pending.put(source,callback);return ()->{};},probe(results,errors));
        request.cancel();for(SubtitleGateway.Callback<SubtitleGateway.Page> callback:pending.values())callback.success(page(item("late",""),false,-1));
        assertTrue(results.isEmpty());assertTrue(errors.isEmpty());
    }

    @Test public void failingLaterPagesWithNoNewResultsReportFailureToPreserveAcceptedPage() {
        List<SubtitleGateway.Page> results=new ArrayList<>();List<Throwable> errors=new ArrayList<>();
        SubtitleFanOut.start(2,(source,callback)->{
            if(source==SubtitleSource.ASSRT)callback.failure(new IOException());
            else callback.success(new SubtitleGateway.Page(Collections.emptyList(),false,false,-1));
            return ()->{};
        },probe(results,errors));
        assertEquals(1,errors.size());assertTrue(results.isEmpty());
    }

    @Test public void duplicateProviderCallbacksCannotFinishEarlyAndUnknownPaginationRemainsOpen() {
        Map<SubtitleSource,SubtitleGateway.Callback<SubtitleGateway.Page>> pending=new EnumMap<>(SubtitleSource.class);
        List<SubtitleGateway.Page> results=new ArrayList<>();List<Throwable> errors=new ArrayList<>();
        SubtitleFanOut.start(1,(source,callback)->{pending.put(source,callback);return ()->{};},probe(results,errors));
        pending.get(SubtitleSource.ASSRT).success(page(item("a",""),true,-1));
        pending.get(SubtitleSource.ASSRT).failure(new IOException());assertTrue(results.isEmpty());
        pending.get(SubtitleSource.SUBHD).success(page(item("b",""),false,1));
        pending.get(SubtitleSource.WEBSITE).success(page(item("c",""),false,-1));
        assertEquals(1,results.size());assertTrue(results.get(0).hasNext);assertEquals(-1,results.get(0).total);
        assertTrue(errors.isEmpty());assertTrue(results.get(0).unavailable.isEmpty());
    }

    private static Subtitle item(String name,String language){Subtitle value=new Subtitle(name,"https://fixture.test/"+name,false);value.setLanguage(language);return value;}
    private static SubtitleGateway.Page page(Subtitle value,boolean next,int total){return new SubtitleGateway.Page(Collections.singletonList(value),false,next,total);}
    private static SubtitleGateway.Callback<SubtitleGateway.Page> probe(List<SubtitleGateway.Page> results,List<Throwable> errors){
        return new SubtitleGateway.Callback<SubtitleGateway.Page>(){public void success(SubtitleGateway.Page page){results.add(page);}public void failure(Throwable error){errors.add(error);}};
    }
}
