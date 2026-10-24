package io.github.autoequipmentmaking.engine.planner;

/** Real-time polling; the first failed retry announces once, later retries stay quiet. */
public final class TradeCooldown {
    public static final long INTERVAL_MS=20_000;
    private long next;
    private int checks;
    public boolean due(long now){return checks==0||now>=next;}
    public boolean exhausted(long now){checks++;next=now+INTERVAL_MS;return checks==2;}
    public boolean recovered(){boolean report=checks>0;checks=0;next=0;return report;}
    public int checks(){return checks;}
}
