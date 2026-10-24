package io.github.autoequipmentmaking.client;

import java.util.List;

/** Full survival production: Soul Speed I x4 and Swift Sneak II + I x2, no level-III sources. */
public final class BookUpgradeGameTest extends ProductionGameTest {
    @Override protected List<Integer> bookLevels(String id,int level){
        return switch(id){case "minecraft:soul_speed"->List.of(1,1,1,1);case "minecraft:swift_sneak"->List.of(2,1,1);default->List.of(level);};
    }
    @Override protected int expectedBooks(){return 60;}
    @Override protected int expectedLevels(){return 375;}
}
