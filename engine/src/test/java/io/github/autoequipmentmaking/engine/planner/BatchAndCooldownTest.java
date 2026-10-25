package io.github.autoequipmentmaking.engine.planner;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchAndCooldownTest {
    @Test void keepsLargestOrderedPrefixAndTransactionalSpace(){
        assertEquals(2,BatchPacking.prefix(List.of(5,3,4),16,4));
        assertEquals(3,BatchPacking.prefix(List.of(5,3,4),19,4));
        assertEquals(1,BatchPacking.prefix(List.of(9,5),16,4));
        assertEquals(0,BatchPacking.prefix(List.of(9),13,4));
        assertEquals(2,BatchPacking.prefix(List.of(5,3,20,1),30,4));
    }
    @Test void waitsTwentySecondsWarnsOnceAndReportsRecovery(){
        var c=new TradeCooldown();assertTrue(c.due(100));assertTrue(c.exhausted(100));
        assertFalse(c.due(20099));assertTrue(c.due(20100));assertFalse(c.exhausted(20100));
        assertFalse(c.exhausted(40100));assertFalse(c.exhausted(60100));assertTrue(c.recovered());assertEquals(0,c.checks());
        assertTrue(c.due(60101));assertTrue(c.exhausted(60101));assertTrue(c.recovered());assertFalse(c.recovered());
    }
}
