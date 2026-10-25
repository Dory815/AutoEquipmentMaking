package io.github.autoequipmentmaking.client;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.GameType;

/** Copies the real capture into an independent world, verifies it, and exports the save. */
public final class TradingHallCopyGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        Path[] save={null};var result=new LinkedHashMap<String,Object>();
        try(var world=context.worldBuilder().create()){
            save[0]=world.getWorldSave().getSaveDirectory();
            world.getServer().runOnServer(server->{
                server.tickRateManager().setFrozen(true);
                var snapshot=TradingHallFixture.read();var level=TradingHallFixture.install(server,snapshot);
                var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level,snapshot.getDoubleOr("playerX",1068.5),snapshot.getDoubleOr("playerY",57),snapshot.getDoubleOr("playerZ",21.5),Set.of(),0,0,true);
                server.saveEverything(false,true,true);
            });
            context.waitFor(mc->mc.level!=null&&mc.level.dimension()==net.minecraft.world.level.Level.END,300);
            context.waitTicks(40);
            // Forced chunks become entity-accessible after the section manager ticks.
            // Frozen simulation still permits chunk loading and tracking updates.
            world.getServer().runOnServer(server->result.putAll(TradingHallFixture.verify(server.getLevel(net.minecraft.world.level.Level.END),TradingHallFixture.read())));
            context.takeScreenshot("201-trading-hall-copy");
            world.getServer().runOnServer(server->{
                result.put("secondVerification",TradingHallFixture.verify(server.getLevel(net.minecraft.world.level.Level.END),TradingHallFixture.read()));
                // Deliver an editable working save. The user will arrange materials before baseline capture.
                server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE);
                server.tickRateManager().setFrozen(false);server.saveEverything(false,true,true);
            });
        }catch(Exception e){throw new IllegalStateException("201 fixture copy verification failed",e);}
        try{
            Path export=Path.of("201-trading-hall-working-save-v2").toAbsolutePath();
            if(Files.exists(export))throw new IllegalStateException("Refuse to overwrite existing editable world "+export);
            try(var files=Files.walk(save[0])){
                for(Path source:files.toList()){
                    if(source.getFileName().toString().equals("session.lock"))continue;
                    Path target=export.resolve(save[0].relativize(source));
                    if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target);
                }
            }
            var levelData=net.minecraft.nbt.NbtIo.readCompressed(export.resolve("level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var data=levelData.getCompoundOrEmpty("Data");
            data.putString("LevelName","201交易所-待整理-20261025");
            data.putInt("GameType",1);data.putBoolean("allowCommands",true);
            var spawn=data.getCompoundOrEmpty("spawn");spawn.putString("dimension","minecraft:the_end");
            spawn.putIntArray("pos",new int[]{1068,57,21});data.put("spawn",spawn);
            net.minecraft.nbt.NbtIo.writeCompressed(levelData,export.resolve("level.dat"));
            result.put("saveDirectory",save[0].toAbsolutePath().toString());result.put("snapshot",TradingHallFixture.SNAPSHOT.toString());
            result.put("exportDirectory",export.toString());
            result.put("complete",true);result.put("testServer","independent singleplayer");result.put("formalServerConnected",false);result.put("worldFrozen",false);
            result.put("deliveryMode","editable creative working copy; final baseline awaits user arrangement");
            result.put("defaultGameMode","creative");result.put("commandsEnabled",true);result.put("newPlayerSpawnDimension","minecraft:the_end");
            Files.writeString(Path.of("201-fixture-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
