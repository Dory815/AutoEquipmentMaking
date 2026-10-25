import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Read-only capture of already loaded blocks and authoritative Servux NBT. */
public final class CaptureWorker {
    static final Minecraft MC=Minecraft.getInstance();
    static final Path GAME=Path.of("D:/MC/AutoEquipmentMaking/runtime/formal-client/.minecraft/versions/26.2-Fabric-AEM-Formal");
    static final Path PROJECT=Path.of("D:/MC/AutoEquipmentMaking");
    static <T> T client(Callable<T> action) throws Exception {
        var f=new CompletableFuture<T>();MC.execute(()->{try{f.complete(action.call());}catch(Throwable e){f.completeExceptionally(e);}});
        return f.get(30,TimeUnit.SECONDS);
    }
    static Object invoke(Object target,String method,Class<?>[] types,Object...args)throws Exception {
        Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);
    }
    static void checkWorld() throws Exception {
        var fabric=Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
        Path game=(Path)fabric.getClass().getMethod("getGameDir").invoke(fabric);
        if(!game.toAbsolutePath().normalize().equals(GAME.toAbsolutePath().normalize()))throw new IllegalStateException("Wrong game directory");
        if(MC.player==null||MC.level==null||!MC.player.getName().getString().equals("bot_lighte")||MC.getCurrentServer()==null||!MC.getCurrentServer().ip.equals("101.gpa.ac.cn"))throw new IllegalStateException("Wrong account or server");
        if(!MC.level.dimension().identifier().toString().equals("minecraft:the_end"))throw new IllegalStateException("Expected End workshop");
        Object robot=Class.forName("io.github.autoequipmentmaking.client.AutoEquipmentClient").getField("robot").get(null);
        var field=robot.getClass().getDeclaredField("running");field.setAccessible(true);
        if(field.getBoolean(robot))throw new IllegalStateException("Production task must be idle for read-only capture");
    }
    public static void start(String arguments) {
        var args=arguments.split("\\|",2);Path output=Path.of(args[1]).toAbsolutePath().normalize();
        if(!output.startsWith(PROJECT))throw new IllegalArgumentException("Output outside project");
        Thread worker=new Thread(()->{
            var report=new LinkedHashMap<String,Object>();
            try {
                client(()->{checkWorld();return null;});
                report.put("account","bot_lighte");report.put("server","101.gpa.ac.cn");
                report.put("dimension","minecraft:the_end");report.put("readOnly",true);
                if(args[0].equals("survey"))survey(report);
                else if(args[0].equals("capture"))capture(output,report);
                else throw new IllegalArgumentException("Unknown action");
                report.put("complete",true);
            }catch(Throwable e){report.put("complete",false);report.put("error",e.toString());var lines=new ArrayList<String>();for(var t=e;t!=null;t=t.getCause())lines.add(t.toString());report.put("causes",lines);}
            try{Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
        },"aem-readonly-world-capture");worker.setDaemon(true);worker.start();
    }
    static void survey(Map<String,Object> report)throws Exception {
        client(()->{
            report.put("position",MC.player.position().toString());
            var chunks=new ArrayList<Object>();
            for(int cx=62;cx<=71;cx++)for(int cz=-4;cz<=5;cz++){
                var chunk=MC.level.getChunkSource().getChunk(cx,cz,ChunkStatus.FULL,false);
                if(chunk==null)continue;
                var entities=new ArrayList<Object>();
                for(var e:chunk.getBlockEntities().entrySet())entities.add(Map.of("pos",e.getKey().toShortString(),"type",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(e.getValue().getType()).toString()));
                chunks.add(Map.of("x",cx,"z",cz,"blockEntities",entities));
            }
            report.put("chunks",chunks);
            var villagers=new ArrayList<Object>();
            for(var e:MC.level.entitiesForRendering())if(e instanceof Villager)villagers.add(Map.of("uuid",e.getStringUUID(),"pos",e.position().toString()));
            report.put("villagers",villagers);return null;
        });
    }
    static CompoundTag authoritative(Object manager,Object key)throws Exception {
        boolean entity=key instanceof Integer;Class<?> keyType=entity?int.class:BlockPos.class;
        Object cache=manager.getClass().getMethod("getCache").invoke(manager),tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        String kind=entity?"Entity":"BlockEntity";
        client(()->{checkWorld();cache.getClass().getMethod("removeFromCache",keyType).invoke(cache,key);tracker.getClass().getMethod("setPendingLocal"+kind+"Request",keyType,boolean.class).invoke(tracker,key,true);invoke(manager,"requestServux"+kind+"Data",new Class<?>[]{keyType},key);return null;});
        for(int attempt=0;attempt<100;attempt++){
            Thread.sleep(100);
            CompoundTag tag=client(()->{
                checkWorld();if((boolean)tracker.getClass().getMethod("hasPendingLocal"+kind,keyType).invoke(tracker,key))return null;
                if(cache.getClass().getMethod("get"+kind+"DataEntryFromCache",keyType).invoke(cache,key)==null)return null;
                CompoundTag value=(CompoundTag)cache.getClass().getMethod("get"+kind+"NbtFromCache",keyType).invoke(cache,key);
                return value==null||value.isEmpty()?null:value.copy();
            });
            if(tag!=null)return tag;
        }
        throw new IllegalStateException("Authoritative Servux NBT timed out: "+key);
    }
    static void capture(Path output,Map<String,Object> report)throws Exception {
        Path snapshot=output.resolveSibling("201-trading-hall.nbt");
        if(Files.exists(snapshot))throw new IllegalStateException("Refuse to overwrite existing baseline");
        int minX=1008,minY=0,minZ=-48,sx=128,sy=256,sz=128;
        var root=new CompoundTag();root.putString("format","aem-world-fixture-v1");root.putString("dimension","minecraft:the_end");
        root.putIntArray("min",new int[]{minX,minY,minZ});root.putIntArray("size",new int[]{sx,sy,sz});
        root.putString("sourceServer","101.gpa.ac.cn");root.putLong("captureTime",System.currentTimeMillis());
        var states=new int[sx*sy*sz];var palette=new LinkedHashMap<BlockState,Integer>();
        var positions=new ArrayList<BlockPos>();
        for(int cx=minX>>4;cx<(minX+sx)>>4;cx++)for(int cz=minZ>>4;cz<(minZ+sz)>>4;cz++){
            final int xChunk=cx,zChunk=cz;
            client(()->{checkWorld();var c=MC.level.getChunkSource().getChunk(xChunk,zChunk,ChunkStatus.FULL,false);if(c==null)throw new IllegalStateException("Required chunk not loaded: "+xChunk+","+zChunk);positions.addAll(c.getBlockEntities().keySet());return null;});
        }
        for(int y=0;y<sy;y++){
            final int yy=y;
            client(()->{checkWorld();var pos=new BlockPos.MutableBlockPos();
                for(int z=0;z<sz;z++)for(int x=0;x<sx;x++){
                    var state=MC.level.getBlockState(pos.set(minX+x,minY+yy,minZ+z));
                    states[(yy*sz+z)*sx+x]=palette.computeIfAbsent(state,k->palette.size());
                }return null;
            });
        }
        var paletteTags=new ListTag();for(var state:palette.keySet())paletteTags.add(NbtUtils.writeBlockState(state));
        root.put("palette",paletteTags);root.putIntArray("states",states);
        Object manager=client(()->{var type=Class.forName("fi.dy.masa.minihud.data.EntityDataManager");Object m=type.getMethod("getInstance").invoke(null);if(!(boolean)type.getMethod("isEnabled").invoke(m)||!(boolean)type.getMethod("hasServuxServer").invoke(m))throw new IllegalStateException("Servux unavailable");return m;});
        var blockEntities=new ListTag();
        for(var pos:positions){
            if(pos.getY()<minY||pos.getY()>=minY+sy)continue;
            var tag=authoritative(manager,pos);
            if(tag.getIntOr("x",Integer.MIN_VALUE)!=pos.getX()||tag.getIntOr("y",Integer.MIN_VALUE)!=pos.getY()||tag.getIntOr("z",Integer.MIN_VALUE)!=pos.getZ())throw new IllegalStateException("Wrong block entity coordinates "+pos);
            blockEntities.add(tag);Thread.sleep(50);
        }
        root.put("blockEntities",blockEntities);
        List<Entity> entities=client(()->{var list=new ArrayList<Entity>();for(var e:MC.level.entitiesForRendering())if(!(e instanceof Player)&&e.getX()>=minX&&e.getX()<minX+sx&&e.getZ()>=minZ&&e.getZ()<minZ+sz&&e.getY()>=minY&&e.getY()<minY+sy)list.add(e);return list;});
        var entityTags=new ListTag();int villagers=0;var villagersWithoutOffers=new ArrayList<Object>();
        for(var e:entities){
            CompoundTag tag=authoritative(manager,e.getId());
            UUID uuid=client(e::getUUID);
            if(!tag.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElseThrow().equals(uuid))throw new IllegalStateException("Wrong entity UUID");
            tag.putString("id",BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            if(e instanceof Villager){
                if(!tag.contains("Offers")){
                    String profession=tag.getCompoundOrEmpty("VillagerData").getStringOr("profession","");
                    if(!profession.equals("minecraft:none")&&!profession.equals("minecraft:nitwit")){
                        Files.writeString(output.resolveSibling("villager-without-offers.snbt"),tag.toString());
                        throw new IllegalStateException("Professional villager missing offers "+uuid+" profession="+profession);
                    }
                    villagersWithoutOffers.add(Map.of("uuid",uuid.toString(),"profession",profession));
                }
                villagers++;
            }
            entityTags.add(tag);Thread.sleep(50);
        }
        root.put("entities",entityTags);
        // Snapshot exact source player location for reproducing approach and cornering.
        client(()->{root.putDouble("playerX",MC.player.getX());root.putDouble("playerY",MC.player.getY());root.putDouble("playerZ",MC.player.getZ());return null;});
        NbtIo.writeCompressed(root,snapshot);
        report.put("snapshot",snapshot.toString());report.put("min",new int[]{minX,minY,minZ});report.put("size",new int[]{sx,sy,sz});
        report.put("blockCount",states.length);report.put("paletteSize",palette.size());report.put("blockEntities",blockEntities.size());report.put("entities",entityTags.size());report.put("villagers",villagers);
        report.put("villagersWithoutOffers",villagersWithoutOffers);
        report.put("menusOpened",0);report.put("tradesExecuted",0);report.put("blocksChangedOnServer",0);report.put("movementRequested",false);
    }
}
