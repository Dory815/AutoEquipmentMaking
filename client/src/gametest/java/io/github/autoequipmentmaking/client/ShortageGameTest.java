package io.github.autoequipmentmaking.client;
/** Tests material deficits, book deficits, XP deficits, and chat-gated retries. */
public final class ShortageGameTest extends ProductionGameTest {
    @Override protected boolean shortageOnly(){return true;}
}
