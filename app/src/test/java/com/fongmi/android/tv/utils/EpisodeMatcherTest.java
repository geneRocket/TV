package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EpisodeMatcherTest {

    @Test
    public void matchesEquivalentEpisodeFormats() {
        List<String> names = Arrays.asList("第1集", "第2集", "第3集");
        assertEquals(1, EpisodeMatcher.find(names, "S01E02", true, -1));
        assertEquals(0, EpisodeMatcher.find(names, "2024 第01集", true, -1));
        assertEquals(2, EpisodeMatcher.find(Arrays.asList("01", "02", "03"), "EP03", true, -1));
        assertEquals(1, EpisodeMatcher.find(Arrays.asList("第十一集", "第十二集"), "12", true, -1));
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("０１", "０２"), "第1集", true, -1));
    }

    @Test
    public void doesNotTreatQualityOrYearAsEpisodeNumber() {
        List<String> names = Arrays.asList("第1集", "第2集", "第3集");
        assertEquals(-1, EpisodeMatcher.find(names, "1080P", true, -1));
        assertEquals(-1, EpisodeMatcher.find(names, "2024", true, -1));
        assertEquals(-1, EpisodeMatcher.find(Collections.singletonList("1-2"), "12", true, -1));
        assertEquals(-1, EpisodeMatcher.find(Collections.singletonList("1 - 2"), "12", true, -1));
    }

    @Test
    public void strictMatchNeverFallsBackToActivatedPosition() {
        List<String> names = Arrays.asList("第1集", "第2集");
        assertEquals(-1, EpisodeMatcher.find(names, "第9集", true, 1));
        assertEquals(1, EpisodeMatcher.find(names, "第9集", false, 1));
    }

    @Test
    public void fuzzyMatchMustBeUnique() {
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("高清正片", "预告"), "正片", true, -1));
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("正片上", "正片下"), "正片", true, -1));
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("上海篇", "北京篇"), "上", true, -1));
    }

    @Test
    public void matchesUnambiguousUpperAndLowerParts() {
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("上集", "下集"), "上", true, -1));
        assertEquals(1, EpisodeMatcher.find(Arrays.asList("前篇", "后篇"), "下部", true, -1));
    }

    @Test
    public void strictSingleEpisodeStillRequiresANameMatch() {
        assertEquals(-1, EpisodeMatcher.find(Collections.singletonList("预告"), "正片", true, 0));
        assertEquals(0, EpisodeMatcher.find(Collections.singletonList("预告"), "正片", false, 0));
    }

    @Test
    public void differentExplicitSeasonsDoNotMatch() {
        assertEquals(-1, EpisodeMatcher.find(Collections.singletonList("S02E03"), "S01E03", true, -1));
    }

    @Test
    public void missingEpisodeCannotMatchDigitsInsideAnotherEpisode() {
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("112", "113"), "12", true, -1));
    }

    @Test
    public void shorterCandidateCannotMatchDigitsInsideTargetEpisode() {
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("12", "13"), "112", true, -1));
    }

    @Test
    public void seasonEpisodePrefixCannotSelectADifferentEpisode() {
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("S01E030", "S01E040"), "S01E03", true, -1));
    }

    @Test
    public void fuzzyNumberMatchStillAcceptsNonNumericLabels() {
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("12（备用）", "13（备用）"), "12", true, -1));
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("12", "13"), "12（备用）", true, -1));
    }

    @Test
    public void ambiguousEpisodeAcrossSeasonsCannotBeChosenByZeroPadding() {
        assertEquals(-1, EpisodeMatcher.find(Arrays.asList("S01E3", "S02E03"), "03", true, -1));
    }

    @Test
    public void nonStrictAmbiguousEpisodeUsesActivatedPosition() {
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("S01E3", "S02E03"), "03", false, 0));
    }

    @Test
    public void explicitSeasonResolvesOtherwiseAmbiguousEpisode() {
        assertEquals(0, EpisodeMatcher.find(Arrays.asList("S01E3", "S02E03"), "S01E03", true, -1));
        assertEquals(1, EpisodeMatcher.find(Arrays.asList("S01E3", "S02E03"), "S02E03", true, -1));
    }
}
