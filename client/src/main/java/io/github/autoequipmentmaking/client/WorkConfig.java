package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

final class WorkConfig {
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    static final Path DIR=FabricLoader.getInstance().getConfigDir().resolve("auto-equipment-making");
    JsonObject root;
    WorkConfig() {
        try { Files.createDirectories(DIR); Path file=DIR.resolve("workshop.json");
            if(Files.exists(file))root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            else {try(var in=getClass().getResourceAsStream("/examples/workshop.example.json")){root=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();} save();}
            if(root.get("schemaVersion").getAsInt()<2){root.addProperty("schemaVersion",2);root.getAsJsonObject("trading").addProperty("enabled",true);save();}
        }catch(Exception e){throw new IllegalStateException("配置读取失败",e);}
    }
    void save(){try{Path tmp=DIR.resolve("workshop.json.tmp");Files.writeString(tmp,GSON.toJson(root));Files.move(tmp,DIR.resolve("workshop.json"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalStateException(e);}}
    static JsonObject pos(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    static BlockPos read(JsonElement e){if(e==null||e.isJsonNull())throw new IllegalStateException("场地坐标未配置");var o=e.getAsJsonObject();return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());}
    BlockPos station(String type){var stations=root.getAsJsonObject("workstations");var a=stations==null?null:stations.getAsJsonArray(type);if(a==null||a.isEmpty())throw new IllegalStateException("工作站未配置: "+type+"；请使用 /aem mark 选点");return read(a.get(0));}
    BlockPos packing(){return read(root.getAsJsonObject("packing").get("placement"));}
    BlockPos output(){return read(root.getAsJsonObject("packing").get("outputContainer"));}
    boolean protectedSlot(int slot){for(var x:root.getAsJsonObject("inventory").getAsJsonArray("protectedSlots"))if(x.getAsInt()==slot)return true;return slot>=36;}
    BlockPos min(){return boundary("min");}
    BlockPos max(){return boundary("max");}
    BlockPos boundary(String side){var value=root.get("platformBounds");if(value==null||value.isJsonNull()||!value.getAsJsonObject().has(side))throw new IllegalStateException("平台边界未配置；请先 /aem mark corner1 和 /aem mark corner2");return read(value.getAsJsonObject().get(side));}
}
