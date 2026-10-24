package io.github.autoequipmentmaking.engine.planner;

import java.util.*;

/** User policy: consume the highest available levels first for one level-III target. */
public final class UpgradeBooks {
    private UpgradeBooks() {}
    public static boolean supported(String id){return id.equals("minecraft:soul_speed")||id.equals("minecraft:swift_sneak");}
    public static List<Integer> select(int ones,int twos,int threes){
        if(threes>0)return List.of(3);
        if(twos>=2)return List.of(2,2);
        if(twos>=1&&ones>=2)return List.of(2,1,1);
        if(ones>=4)return List.of(1,1,1,1);
        return List.of();
    }
    public static int capacity(int ones,int twos,int threes){return threes+(twos+ones/2)/2;}
}
