package io.github.autoequipmentmaking.engine.planner;

import java.util.List;

/** Longest ordered prefix that fits, retaining slots for transactions and currency. */
public final class BatchPacking {
    private BatchPacking() {}
    public static int prefix(List<Integer> bookCounts,int freeSlots,int reserve) {
        if(freeSlots<0||reserve<0)throw new IllegalArgumentException();
        int used=reserve,n=0;
        for(int books:bookCounts){if(books<0)throw new IllegalArgumentException();if(used+1+books>freeSlots)break;used+=1+books;n++;}
        return n;
    }
}
