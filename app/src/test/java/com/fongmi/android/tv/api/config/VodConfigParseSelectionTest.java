package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Parse;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class VodConfigParseSelectionTest {
    @Test public void delayedHigherPrioritySourceReplacesTheDuplicateDefinitionInOrder() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Shared"));
        Parse cached = parser("Shared"), cachedOnly = parser("Cached only");
        Parse priority = parser("Shared"), priorityOnly = parser("Priority only");
        Parse god = parser("Intelligent");
        owner.setParses(List.of(cached, cachedOnly), god);
        owner.setParses(List.of(priority, priorityOnly, cached, cachedOnly), god);
        assertEquals(List.of(god, priority, priorityOnly, cachedOnly), owner.getParses());
        assertSame(priority, owner.getParses().get(1));
        assertSame(priority, owner.getParse());
        assertTrue(priority.isActivated());
    }

    @Test public void selectedDuplicateMustBeTheSameObjectAsTheFirstListedDefinition() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Shared"));
        Parse first = parser("Shared"), duplicate = parser("Shared");
        owner.setParses(List.of(first, duplicate), parser("Intelligent"));
        assertSame(first, owner.getParses().get(1));
        assertSame(first, owner.getParse());
    }

    @Test public void userSelectionSurvivesBackfillAndPointsAtTheRefreshedDefinition() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Cached"));
        Parse cached = parser("Cached"), god = parser("Intelligent");
        cached.setType(0);
        owner.setParses(List.of(cached), god);
        owner.setParse(cached);
        Parse updatedCached = parser("Cached");
        updatedCached.setType(0);
        owner.setParses(List.of(parser("Priority"), updatedCached), god);
        assertSame(updatedCached, owner.getParse());
        assertTrue(updatedCached.isActivated());
        assertFalse(owner.getParses().get(1).isActivated());
    }

    @Test public void intelligentSelectionSurvivesBackfill() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Intelligent"));
        Parse firstGod = parser("Intelligent");
        owner.setParses(List.of(parser("Cached")), firstGod);
        owner.setParse(firstGod);
        Parse updatedGod = parser("Intelligent");
        owner.setParses(List.of(parser("Priority"), parser("Cached")), updatedGod);
        assertSame(updatedGod, owner.getParses().get(0));
        assertSame(updatedGod, owner.getParse());
        assertTrue(updatedGod.isActivated());
    }

    @Test public void startupKeepsLegacySelectionRulesForAWebParser() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Web"));
        Parse web = parser("Web"), god = parser("Intelligent");
        web.setType(0);
        owner.setParses(List.of(web), god);
        assertSame(god, owner.getParse());
    }

    @Test public void configuredIntelligentParserTakesPrecedenceOverTheSyntheticEntry() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Intelligent"));
        Parse configured = parser("Intelligent");
        owner.setParses(List.of(configured), parser("Intelligent"));
        assertEquals(1, owner.getParses().size());
        assertSame(configured, owner.getParse());
        assertSame(configured, owner.getParses().get(0));
    }

    @Test public void emptyParserConfigurationDoesNotCreateAnIntelligentEntry() {
        VodConfig owner = new VodConfig().config(new MemoryConfig(""));
        owner.setParses(List.of(), parser("Intelligent"));
        assertTrue(owner.getParses().isEmpty());
    }

    @Test public void aSelectionFromAnOldUiSnapshotUsesTheCurrentDefinition() {
        VodConfig owner = new VodConfig().config(new MemoryConfig("Shared"));
        Parse old = parser("Shared"), current = parser("Shared");
        owner.setParses(List.of(old), parser("Intelligent"));
        owner.setParses(List.of(current), parser("Intelligent"));
        owner.setParse(old);
        assertSame(current, owner.getParse());
        assertTrue(current.isActivated());
    }

    private static Parse parser(String name) {
        return new PureParse(name);
    }

    private static class PureParse extends Parse {
        private final String name;
        PureParse(String name) {
            this.name = name;
            setType(2);
        }
        @Override public String getName() { return name; }
    }

    private static class MemoryConfig extends Config {
        private String selected;
        MemoryConfig(String selected) { this.selected = selected; }
        @Override public String getParse() { return selected; }
        @Override public void saveParse(String selected) { this.selected = selected; }
    }
}
