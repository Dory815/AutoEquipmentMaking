package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import io.github.autoequipmentmaking.engine.planner.AnvilPlanner;
import io.github.autoequipmentmaking.engine.planner.UpgradeBooks;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class Robot {
    final Minecraft mc=Minecraft.getInstance();
    final WorkConfig cfg=new WorkConfig();
    final Trading trading=new Trading(this);
    final FoodControl food=new FoodControl(this);
    final ServuxObserver observer=new ServuxObserver();
    interface Step { boolean tick(); }
    static final class Waiting extends RuntimeException {Waiting(String message){super(message);}}
    record Source(BlockPos pos,int slot,ItemStack stack) {}
    record Target(String key,String item,int slot,Map<String,Integer> ench) {}
    record Planned(Target target,List<AnvilPlanner.Book> books,AnvilPlanner.Node node) {}
    final Deque<Step> queue=new ArrayDeque<>();
    final List<Source> stock=new ArrayList<>();
    final List<Planned> plans=new ArrayList<>();
    final List<Target> kit=new ArrayList<>();
    final List<Target> kitTemplate=new ArrayList<>();
    Set<Block> taskBoxBlocks=Set.of(Blocks.DYED_SHULKER_BOX.lightBlue());
    int scanLength=21,scanWidth=21,scanHeight=5,requestedKits=-1;
    boolean allowTrading=true,allowSpareAnvils=true,awaitingDelivery;
    static final Map<Integer,Long> revisions=new HashMap<>();
    static final Map<Integer,Long> playerSlotRevisions=new HashMap<>();
    static long foodRevision;
    public static void confirmPlayerSlot(int slot){playerSlotRevisions.merge(slot,1L,Long::sum);}
    public static void confirmFood(){foodRevision++;}
    static final Map<BlockPos,Long> blockRevisions=new HashMap<>();
    public static void confirm(int menu){revisions.merge(menu,1L,Long::sum);}
    public static void confirmBlock(BlockPos p){var robot=AutoEquipmentClient.robot;if(robot!=null){if(robot.running&&p.equals(robot.activePacking))blockRevisions.merge(p.immutable(),1L,Long::sum);AnvilSupply.confirmBlock(robot,p);}}
    final List<BlockPos> batchBoxes=new ArrayList<>();
    BlockPos batchCenter,activePacking;
    int deliveredKits;
    BlockPos packing(){return activePacking==null?cfg.packing():activePacking;}
    boolean running,uncertain,mining,awaitingGood,manualPaused,navigating;
    public boolean ownsNavigation(){return running&&navigating;}
    java.util.concurrent.CompletableFuture<List<Planned>> planning;
    public boolean isRunning(){return running;}
    public boolean ownsMining(){return running&&mining;}
    public boolean ownsEating(){return running&&food.active!=null;}
    String state="IDLE",server,dimension,task;
    long ticks,stepStart,lastChat;
    String lastNotice="";
    int completed,restockAcks;
    String waitingReason="";
    final Set<Integer> protectedSlots=new HashSet<>();
    final Path journal=WorkConfig.DIR.resolve("task.jsonl");
    public Robot(){
        try(var in=getClass().getResourceAsStream("/kits/newcomer-equipment-v1.json")){
            var json=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var row:json.getAsJsonArray("items")){var o=row.getAsJsonObject();var ench=new TreeMap<String,Integer>();o.getAsJsonObject("enchantments").entrySet().forEach(e->ench.put(e.getKey(),e.getValue().getAsInt()));kitTemplate.add(new Target(o.get("key").getAsString(),o.get("item").getAsString(),o.get("slot").getAsInt(),Map.copyOf(ench)));}
            kit.addAll(kitTemplate);
        }catch(Exception e){throw new IllegalStateException("装备清单无效",e);}
    }
    void say(String msg){if(mc.player!=null)mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[新人装备] "+msg));}
    void notice(String message){say(message);long now=System.currentTimeMillis();if(!message.equals(lastNotice)&&now-lastChat>=60000&&mc.getConnection()!=null&&cfg.root.getAsJsonObject("notifications").get("chatEnabled").getAsBoolean()){String chat=message.length()>230?message.substring(0,230)+"；详见本地状态":message;mc.getConnection().sendChat("[新人装备] "+chat);lastNotice=message;lastChat=now;}}
    void announce(String message){say(message);record("NOTICE "+message);if(mc.getConnection()!=null&&cfg.root.getAsJsonObject("notifications").get("chatEnabled").getAsBoolean())mc.getConnection().sendChat("[新人装备] "+message);}
    void record(String event){
        try{var o=new JsonObject();o.addProperty("time",System.currentTimeMillis());o.addProperty("task",task);o.addProperty("server",server);o.addProperty("dimension",dimension);o.addProperty("state",state);o.addProperty("completed",completed);o.addProperty("deliveredKits",deliveredKits);o.addProperty("batchSize",batchBoxes.size());if(activePacking!=null)o.add("taskBox",WorkConfig.pos(activePacking));o.addProperty("event",event);
            if(mc.player!=null&&mc.level!=null){o.add("playerPosition",WorkConfig.pos(mc.player.blockPosition()));o.addProperty("levels",mc.player.experienceLevel);var inv=new JsonArray();for(int i=0;i<36;i++)if(!mc.player.getInventory().getItem(i).isEmpty()){var item=new JsonObject();item.addProperty("slot",i);item.add("stack",stackJson(mc.player.getInventory().getItem(i)));inv.add(item);}o.add("inventory",inv);o.add("cursor",stackJson(mc.player.containerMenu.getCarried()));var menu=new JsonArray();for(var slot:mc.player.containerMenu.slots)menu.add(stackJson(slot.getItem()));o.add("menu",menu);}
            Files.writeString(journal,o.toString()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Exception e){running=false;throw new IllegalStateException("任务日志写入失败",e);}
    }
    JsonElement stackJson(ItemStack stack){return stack.isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow();}
    String server(){return mc.getCurrentServer()==null?"singleplayer":mc.getCurrentServer().ip;}
    String dimension(){return mc.level.dimension().identifier().toString();}
    public void bind(){if(mc.player==null)return;if(!queue.isEmpty()){say("请先取消任务再修改场地");return;}cfg.root.addProperty("serverKey",server());cfg.root.addProperty("dimension",dimension());cfg.save();say("服务器绑定完成");}
    public void mark(String type){
        if(!queue.isEmpty()){say("请先取消任务再修改场地");return;}
        if(!(mc.hitResult instanceof BlockHitResult hit)){say("请瞄准目标方块");return;}BlockPos p=hit.getBlockPos();
        switch(type){
            case "corner1","corner2"->{var bounds=cfg.root.get("platformBounds");JsonObject o=bounds==null||bounds.isJsonNull()?new JsonObject():bounds.getAsJsonObject();o.add(type.equals("corner1")?"min":"max",WorkConfig.pos(p));cfg.root.add("platformBounds",o);}
            case "craft","smith","anvil"->{String key=type.equals("craft")?"crafting":type.equals("smith")?"smithing":"anvils";var a=new JsonArray();a.add(WorkConfig.pos(p));cfg.root.getAsJsonObject("workstations").add(key,a);}
            case "packing"->cfg.root.getAsJsonObject("packing").add("placement",WorkConfig.pos(p.relative(hit.getDirection())));
            case "output"->cfg.root.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(p));
        }cfg.save();say(type+" = "+p.toShortString());
    }
    public void status(){say(state+"；交付 "+deliveredKits+"/"+batchBoxes.size()+" 盒；完成 "+completed+" 件；队列 "+queue.size()+"；日志 "+journal);}
    public void toggle(){if(running)manualPause("用户暂停");else if(!queue.isEmpty())resume();else start();}
    public void requestStart(){
        if(mc.player==null||mc.level==null){say("请先进入世界");return;}
        if(!queue.isEmpty()){say("已有任务，请恢复或取消后重新开始");return;}
        awaitingDelivery=true;say("请指向交付箱，按绑定交付箱快捷键；也可让任意玩家发送 skip 自动选择箱子");
    }
    public void bindDelivery(){
        if(!awaitingDelivery){say("请先开始工作，再绑定交付箱");return;}
        if(!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof ChestBlock)){say("指向的方块不是箱子，请重新选择");return;}
        selectDelivery(hit.getBlockPos());
    }
    void selectDelivery(BlockPos p){
        if(!inside(p)){say("交付箱不在工坊范围内，请重新选择");return;}
        cfg.root.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(p));cfg.save();awaitingDelivery=false;say("交付箱已绑定："+p.toShortString());start();
    }
    void skipDelivery(){
        if(!awaitingDelivery)return;
        try{
            BlockPos old=cfg.output();
            if(inside(old)&&mc.level.getBlockState(old).getBlock() instanceof ChestBlock){selectDelivery(old);return;}
        }catch(RuntimeException ignored){}
        BlockPos best=null;double distance=Double.POSITIVE_INFINITY;
        for(BlockPos pos:BlockPos.betweenClosed(cfg.min(),cfg.max()))if(mc.level.hasChunkAt(pos)&&mc.level.getBlockState(pos).getBlock() instanceof ChestBlock){double d=pos.distSqr(mc.player.blockPosition());if(d<distance){best=pos.immutable();distance=d;}}
        if(best==null){say("未找到交付箱，请指向箱子后按绑定快捷键");return;}
        selectDelivery(best);
    }
    public void emergencyStop(){if(awaitingDelivery){awaitingDelivery=false;say("已取消交付箱绑定");}if(!queue.isEmpty())manualPause("急停");}
    public void manualPause(String why){manualPaused=true;pause(why);}
    public void pause(String why){running=false;release();if(mc.player!=null&&mc.gameMode!=null)food.pause();if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();say(why+"；状态 "+state);if(task!=null)try{record("PAUSED: "+why);}catch(RuntimeException failure){uncertain=true;say("任务日志失败，禁止继续消耗物资");}}
    public void resume(){if(awaitingGood){say("等待任意玩家发送 good 确认补货");return;}if(uncertain){say("存在未确认操作。必须核对任务日志、菜单和物资，不能重发消耗操作。");return;}if(queue.isEmpty()){say("没有可继续任务");return;}manualPaused=false;running=true;stepStart=ticks;}
    public void playerChat(String player,String text){if(awaitingDelivery&&text.strip().equalsIgnoreCase("skip")){skipDelivery();return;}if(text.strip().equalsIgnoreCase("pause")){if(!queue.isEmpty())manualPause(player+" 发送 pause");return;}if(text.strip().equalsIgnoreCase("continue")){resume();return;}if(awaitingGood&&text.strip().equalsIgnoreCase("good")){awaitingGood=false;restockAcks++;lastNotice="";lastChat=0;record("RESTOCK ACK player="+player);say(player+" 确认补货，重新检查库存");if(manualPaused)say("补货确认完成；任务仍处于手动暂停");else resume();}}
    public void unsignedPlayerChat(String text){
        var matcher=java.util.regex.Pattern.compile("^<([^>]+)>\\s+((?i:good|pause|continue|skip))\\s*$").matcher(text);
        if(matcher.matches()&&mc.getConnection()!=null&&mc.getConnection().getOnlinePlayers().stream().anyMatch(p->p.getProfile().name().equals(matcher.group(1))))playerChat(matcher.group(1),matcher.group(2));
    }
    public void cancel(){awaitingDelivery=false;pause("取消：保留现场物资");queue.clear();food.reset();awaitingGood=false;state="CANCELLED";uncertain=false;record("CANCELLED");}
    void sprintKey(boolean down){var key=mc.options.keySprint;if(mc.options.toggleSprint().get()){if(key.isDown()!=down)key.setDown(true);}else key.setDown(down);}
    void release(){navigating=false;mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);sprintKey(false);mc.options.keyAttack.setDown(false);if(mc.player!=null)mc.player.setSprinting(false);}
    public void externalInput(){if(running){uncertain=true;pause("外部库存操作，必须对账");}}
    void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public void start(){
        try{
            var settings=RobotSettings.INSTANCE;
            taskBoxBlocks=settings.outputBlocks();scanLength=settings.scanLength.getIntegerValue();scanWidth=settings.scanWidth.getIntegerValue();scanHeight=settings.scanHeight.getIntegerValue();requestedKits=settings.manual()?settings.quantity.getIntegerValue():-1;
            allowTrading=settings.trading.getBooleanValue();allowSpareAnvils=settings.spareAnvils.getBooleanValue();
            kit.clear();for(var target:kitTemplate){if(!settings.elytra.getBooleanValue()&&target.key().equals("elytra"))continue;if(!settings.shield.getBooleanValue()&&target.key().equals("shield"))continue;var ench=new TreeMap<>(target.ench());if(!settings.swiftSneak.getBooleanValue())ench.remove("minecraft:swift_sneak");if(!settings.soulSpeed.getBooleanValue())ench.remove("minecraft:soul_speed");kit.add(new Target(target.key(),target.item(),target.slot(),Map.copyOf(ench)));}
            require(mc.player!=null&&mc.level!=null,"请进入世界");require(queue.isEmpty(),"已有任务，请继续或取消");
            require(!mc.player.isCreative()&&!mc.player.isSpectator(),"只支持生存模式");
            require(cfg.root.get("serverKey")!=null&&!cfg.root.get("serverKey").isJsonNull()&&cfg.root.get("serverKey").getAsString().equals(server()),"请 /aem bind 绑定服务器");
            require(cfg.root.get("dimension").getAsString().equals(dimension()),"维度与配置不一致");
            BlockPos a=cfg.min(),b=cfg.max();require(a.getX()<=b.getX()&&a.getY()<=b.getY()&&a.getZ()<=b.getZ(),"corner1 必须是最小坐标；corner2 必须是最大坐标");
            require(b.getX()-a.getX()<128&&b.getZ()-a.getZ()<128&&b.getY()-a.getY()<32,"平台超过 128×128×32");
            cfg.station("crafting");cfg.station("smithing");cfg.station("anvils");cfg.output();
            require(mc.player.containerMenu==mc.player.inventoryMenu&&mc.player.containerMenu.getCarried().isEmpty(),"请关闭菜单并清空光标");
            protectedSlots.clear();for(int i=0;i<36;i++)if(cfg.protectedSlot(i)||!mc.player.getInventory().getItem(i).isEmpty())protectedSlots.add(i);
            require(mc.level.getBlockState(cfg.output()).getBlock() instanceof ChestBlock,"交付容器必须是箱子");
            require(36-protectedSlots.size()>=16,"至少需要 16 个空背包槽；启动时已有物品全部保护");
            require(java.util.stream.IntStream.range(0,9).anyMatch(i->!protectedSlots.contains(i)),"需要一个空快捷栏槽回收任务盒");
            observer.connect();task=UUID.randomUUID().toString();server=server();dimension=dimension();completed=0;uncertain=false;mining=false;awaitingGood=false;manualPaused=false;restockAcks=0;planning=null;
            batchCenter=mc.player.blockPosition().immutable();batchBoxes.clear();activePacking=null;AnvilSupply.reset(this);trading.reset();food.reset();deliveredKits=0;
            record("START");queue.add(new DiscoverBoxes());running=true;stepStart=ticks;state="SCANNING";
        }catch(Exception e){say(e.getMessage());}
    }
    public void tick(){
        if(!running)return;ticks++;
        try{
            if(mc.player==null||mc.level==null||mc.player.isDeadOrDying()||!server.equals(server())||!dimension.equals(dimension())){uncertain=true;pause("连接、生命或维度改变：必须对账");return;}
            for(int key:new int[]{87,65,83,68,32})if(com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(),key)){manualPause("玩家移动输入");return;}
            if(mc.gui.screen()!=null&&!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)){manualPause("非任务界面打开");return;}
            if(queue.isEmpty()){running=false;release();state="COMPLETED";record("COMPLETED");say("交付完成："+deliveredKits+" 盒，"+completed+" 件装备");return;}
            if(ticks-stepStart>2400){uncertain=true;pause("步骤超时，保留现场并核对日志");return;}
            food.tick();
            if(queue.peek().tick()){queue.remove();stepStart=ticks;}
        }catch(Waiting e){state="WAITING_MATERIAL_OR_XP";waitingReason=e.getMessage();record("WAITING "+waitingReason);awaitingGood=true;notice(e.getMessage()+"；补充后请任意玩家发送 good");pause("等待玩家发送 good");}
        catch(Exception e){uncertain=true;state="RECOVERY_REQUIRED";pause(e.getMessage()==null?e.toString():e.getMessage());}
    }
    // The discovery origin and the resulting batch are fixed for this start, including restock waits.
    final class DiscoverBoxes implements Step {
        List<BlockPos> candidates;int index;long sent;
        public boolean tick(){
            if(candidates==null){candidates=new ArrayList<>();for(int x=-scanLength/2;x<scanLength-scanLength/2;x++)for(int z=-scanWidth/2;z<scanWidth-scanWidth/2;z++)for(int y=-scanHeight/2;y<scanHeight-scanHeight/2;y++){
                BlockPos p=batchCenter.offset(x,y,z);require(mc.level.hasChunkAt(p),"产物盒扫描区域未加载");
                if(taskBoxBlocks.contains(mc.level.getBlockState(p).getBlock()))candidates.add(p);
            }candidates.sort(Comparator.comparingDouble((BlockPos p)->p.distSqr(batchCenter)).thenComparing(BlockPos::toShortString));}
            if(index<candidates.size()){BlockPos p=candidates.get(index);if(sent==0){observer.refresh(p);sent=ticks;return false;}
                Container c=observer.cached(p);if(c==null){require(ticks-sent<120,"淡蓝色潜影盒库存未确认: "+p);return false;}
                if(c.isEmpty()){require(inside(p),"空产物盒在平台边界外: "+p);batchBoxes.add(p);}index++;sent=0;return false;
            }
            if(requestedKits>batchBoxes.size()){candidates=null;index=0;sent=0;batchBoxes.clear();throw new Waiting("产物盒不足：手动指定 "+requestedKits+" 套，请补充空产物盒");}
            if(requestedKits>0)batchBoxes.subList(requestedKits,batchBoxes.size()).clear();
            say(scanLength+"×"+scanWidth+"×"+scanHeight+" 内有 "+batchBoxes.size()+" 个可用产物盒；本次制作 "+batchBoxes.size()+" 套");record("BATCH boxes="+batchBoxes.size()+" center="+batchCenter);
            if(!batchBoxes.isEmpty()){activePacking=batchBoxes.getFirst();queue.add(new Scan());queue.add(Robot.this::prepare);}return true;
        }
    }
    boolean rawContainer(BlockPos p){var b=mc.level.getBlockState(p).getBlock();return b==Blocks.SHULKER_BOX||b==Blocks.CHEST;}
    final class Scan implements Step {
        List<BlockPos> positions;int index;long sent;
        public boolean tick(){
            if(positions==null){stock.clear();var list=new ArrayList<BlockPos>();BlockPos a=cfg.min(),b=cfg.max();
                for(int x=a.getX()>>4;x<=b.getX()>>4;x++)for(int z=a.getZ()>>4;z<=b.getZ()>>4;z++){
                    var chunk=mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);require(chunk!=null,"平台区块未加载，请靠近平台后重试");
                    for(var e:chunk.getBlockEntities().entrySet()){BlockPos p=e.getKey();if(!inside(p)||p.equals(cfg.output())||!rawContainer(p))continue;
                        if(e.getValue() instanceof ChestBlockEntity && mc.level.getBlockState(p).getValue(ChestBlock.TYPE)==ChestType.RIGHT)continue;
                        list.add(p.immutable());}
                }require(list.size()<=512,"容器超过512个");list.sort(Comparator.comparing(BlockPos::toShortString));positions=list;record("SCAN containers="+positions.size());
            }
            if(index==positions.size()){record("SCAN stacks="+stock.size());return true;}
            BlockPos p=positions.get(index);
            if(sent==0){observer.refresh(p);sent=ticks;return false;}
            Container c=observer.cached(p);
            if(c!=null){for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty())stock.add(new Source(p,i,c.getItem(i).copy()));index++;sent=0;}
            else require(ticks-sent<120,"库存未确认: "+p.toShortString());
            return false;
        }
    }
    boolean inside(BlockPos p){BlockPos a=cfg.min(),b=cfg.max();return p.getX()>=a.getX()&&p.getX()<=b.getX()&&p.getY()>=a.getY()&&p.getY()<=b.getY()&&p.getZ()>=a.getZ()&&p.getZ()<=b.getZ();}
    Predicate<ItemStack> base(String id){return s->Stacks.base(s,id);}
    int count(Predicate<ItemStack> p){return stock.stream().filter(s->p.test(s.stack)).mapToInt(s->s.stack.getCount()).sum();}
    static String label(String id){return switch(id){
        case "minecraft:diamond"->"钻石";case "minecraft:stick"->"木棍";case "minecraft:netherite_ingot"->"下界合金锭";case "minecraft:netherite_upgrade_smithing_template"->"下界合金升级模板";case "minecraft:elytra"->"鞘翅";case "minecraft:bow"->"弓";case "minecraft:shield"->"盾牌";
        case "minecraft:unbreaking"->"耐久";case "minecraft:mending"->"经验修补";case "minecraft:protection"->"保护";case "minecraft:respiration"->"水下呼吸";case "minecraft:aqua_affinity"->"水下速掘";case "minecraft:swift_sneak"->"迅捷潜行";case "minecraft:depth_strider"->"深海探索者";case "minecraft:soul_speed"->"灵魂疾行";case "minecraft:feather_falling"->"摔落缓冲";case "minecraft:sharpness"->"锋利";case "minecraft:smite"->"亡灵杀手";case "minecraft:sweeping_edge"->"横扫之刃";case "minecraft:looting"->"抢夺";case "minecraft:silk_touch"->"精准采集";case "minecraft:efficiency"->"效率";case "minecraft:fortune"->"时运";case "minecraft:power"->"力量";case "minecraft:punch"->"冲击";case "minecraft:flame"->"火矢";case "minecraft:infinity"->"无限";default->id;};}
    int inventoryCount(Predicate<ItemStack> predicate){int n=0;for(int i=0;i<36;i++)if(!protectedSlots.contains(i)&&predicate.test(mc.player.getInventory().getItem(i)))n+=mc.player.getInventory().getItem(i).getCount();return n;}
    int available(Predicate<ItemStack> p){return count(p)+inventoryCount(p);}
    boolean prepare(){
        state="PLANNING";plans.clear();var missing=new ArrayList<String>();
        Map<String,Integer> budget=new LinkedHashMap<>(Map.of("minecraft:diamond",40,"minecraft:stick",12,"minecraft:netherite_ingot",11,"minecraft:netherite_upgrade_smithing_template",11,"minecraft:bow",1));
        if(kit.stream().anyMatch(t->t.key().equals("elytra")))budget.put("minecraft:elytra",1);
        if(kit.stream().anyMatch(t->t.key().equals("shield")))budget.put("minecraft:shield",1);
        budget.forEach((id,n)->{int deficit=n-available(base(id));if(deficit>0&&!trading.offered(base(id)))missing.add(label(id)+" 缺 "+deficit);});
        var needs=new TreeMap<String,Integer>();for(var t:kit)t.ench.forEach((e,l)->needs.merge(e+"@"+l,1,Integer::sum));
        for(var n:needs.entrySet()){String[] parts=n.getKey().split("@");String id=parts[0];int have;
            if(UpgradeBooks.supported(id)){int a=bookCount(id,1),b=bookCount(id,2),c=bookCount(id,3);have=UpgradeBooks.capacity(a,b,c);if(have<n.getValue())missing.add(label(id)+"III 附魔书缺 "+(n.getValue()-have)+"（I="+a+"、II="+b+"、III="+c+"，可合成III="+have+"）");}
            else {have=bookCount(id,Integer.parseInt(parts[1]));if(have<n.getValue())missing.add(label(id)+parts[1]+"级附魔书：需要 "+n.getValue()+"，库存 "+have+"，缺少 "+(n.getValue()-have));}}
        if(!missing.isEmpty()&&trading.enabled()&&!trading.scanned){prepend(List.of(trading.new Discover(),Robot.this::prepare));return true;}
        if(AnvilSupply.available(this)==null&&(!allowSpareAnvils||AnvilSupply.carried(this)==null&&count(AnvilSupply::spare)==0))missing.add("铁砧缺货：没有可用工作站"+(allowSpareAnvils?"，原料箱内也没有备用铁砧":"，设置禁止取出备用铁砧"));
        if(!missing.isEmpty()){
            // Rebuild the observations when resumed, before trying the same planning step.
            trading.scanned=false;queue.addFirst(new Scan());throw new Waiting("缺货："+String.join("；",missing));
        }
        // Full-kit allocation is exhaustively searched over penalty classes, not individual identical books.
        Map<String,TreeMap<Integer,Integer>> pool=new TreeMap<>();
        var bookStacks=new ArrayList<ItemStack>();for(Source source:stock)bookStacks.add(source.stack);
        for(int i=0;i<36;i++)if(!protectedSlots.contains(i))bookStacks.add(mc.player.getInventory().getItem(i));
        bookStacks.addAll(trading.planningBooks(needs));
        for(ItemStack stack:bookStacks)if(stack.is(Items.ENCHANTED_BOOK)&&Stacks.ench(stack,true).size()==1){var e=Stacks.ench(stack,true).entrySet().iterator().next();String key=e.getKey()+"@"+e.getValue();if(needs.containsKey(key)||(UpgradeBooks.supported(e.getKey())&&e.getValue()>=1&&e.getValue()<=3))pool.computeIfAbsent(key,k->new TreeMap<>()).merge(Stacks.penalty(stack),stack.getCount(),Integer::sum);}
        if(planning==null){var weights=new HashMap<String,Integer>();for(var t:kit)for(String id:t.ench.keySet())weights.put(id,Stacks.multiplier(id,mc));planning=java.util.concurrent.CompletableFuture.supplyAsync(()->{Allocation search=new Allocation(pool,weights,System.nanoTime()+5_000_000_000L);search.items(0,new ArrayList<>(),0);if(search.best==null)throw new IllegalStateException("PLAN_INFEASIBLE");return search.best;});return false;}if(!planning.isDone())return false;plans.addAll(planning.join());
        int total=plans.stream().mapToInt(p->p.node.total()).sum();if(mc.player.experienceLevel<total)throw new Waiting("经验不足：当前 "+mc.player.experienceLevel+"，整盒需要 "+total+" 级");say("最优整盒费用 "+total+" 级；开始制作");record("PLAN levels="+total);
        List<Step> actions=new ArrayList<>();
        actions.add(new Open(packing(),ShulkerBoxMenu.class));
        actions.add(()->{require(taskBoxBlocks.contains(mc.level.getBlockState(packing()).getBlock()),"任务盒类型不在设置的产物盒列表中");for(int i=0;i<27;i++)require(mc.player.containerMenu.getSlot(i).getItem().isEmpty(),"任务盒在启动后出现杂物，停止制作");close();return true;});
        // Steps schedule their own substeps at the front after they are removed.
        actions.add(new Procurement(this));
        actions.add(()->{new ProductionBatches(this).schedule();return true;});
        actions.add(()->{scheduleDelivery();return true;});
        queue.addAll(actions);return true;
    }
    int bookCount(String id,int level){Predicate<ItemStack> p=s->s.is(Items.ENCHANTED_BOOK)&&Stacks.ench(s,true).equals(Map.of(id,level));return available(p)+trading.potential(p,14);}
    final class Allocation {
        final Map<String,TreeMap<Integer,Integer>> pool;final Map<String,Integer> weights;final long deadline;List<Planned> best;int bestCost=Integer.MAX_VALUE;
        Allocation(Map<String,TreeMap<Integer,Integer>> p,Map<String,Integer> weights,long d){pool=p;this.weights=Map.copyOf(weights);deadline=d;}
        void items(int i,List<Planned> chosen,int cost){if(System.nanoTime()>deadline)throw new IllegalStateException("PLAN_TIMEOUT");if(cost>=bestCost)return;if(i==kit.size()){best=List.copyOf(chosen);bestCost=cost;return;}var entries=new ArrayList<>(kit.get(i).ench.entrySet());entries.sort(Map.Entry.comparingByKey());books(i,entries,0,new ArrayList<>(),chosen,cost);}
        void books(int item,List<Map.Entry<String,Integer>> entries,int k,List<AnvilPlanner.Book> books,List<Planned> chosen,int cost){
            if(k==entries.size()){AnvilPlanner.Node node;try{node=new AnvilPlanner(books,kit.get(item).ench,deadline).solve(0);}catch(IllegalStateException failure){if("PLAN_INFEASIBLE".equals(failure.getMessage()))return;throw failure;}chosen.add(new Planned(kit.get(item),List.copyOf(books),node));items(item+1,chosen,cost+node.total());chosen.removeLast();return;}
            String id=entries.get(k).getKey();
            if(UpgradeBooks.supported(id)){
                var levels=UpgradeBooks.select(available(id,1),available(id,2),available(id,3));if(levels.isEmpty())return;
                // Each special enchantment occurs on exactly one kit item, so lower penalties dominate.
                var selected=new ArrayList<AnvilPlanner.Book>();for(int level:levels){var options=pool.get(id+"@"+level);int penalty=options.entrySet().stream().filter(e->e.getValue()>0).findFirst().orElseThrow().getKey();options.put(penalty,options.get(penalty)-1);var b=new AnvilPlanner.Book(id,level,weights.get(id),penalty);books.add(b);selected.add(b);}
                books(item,entries,k+1,books,chosen,cost);for(var b:selected){books.removeLast();var options=pool.get(id+"@"+b.level());options.put(b.penalty(),options.get(b.penalty())+1);}return;
            }
            var e=entries.get(k);var options=pool.get(e.getKey()+"@"+e.getValue());for(int penalty:new ArrayList<>(options.keySet()))if(options.get(penalty)>0){options.put(penalty,options.get(penalty)-1);books.add(new AnvilPlanner.Book(e.getKey(),e.getValue(),weights.get(e.getKey()),penalty));books(item,entries,k+1,books,chosen,cost);books.removeLast();options.put(penalty,options.get(penalty)+1);}
        }
        int available(String id,int level){return pool.getOrDefault(id+"@"+level,new TreeMap<>()).values().stream().mapToInt(Integer::intValue).sum();}
    }
    // Insert after the currently executing scheduling step.
    void prepend(List<Step> steps){Step current=queue.removeFirst();for(int i=steps.size()-1;i>=0;i--)queue.addFirst(steps.get(i));queue.addFirst(current);}
    int inventorySlot(Predicate<ItemStack> predicate){for(int i=0;i<36;i++)if(!protectedSlots.contains(i)&&predicate.test(mc.player.getInventory().getItem(i)))return i;throw new IllegalStateException("任务背包缺少目标物品");}
    int empty(){return inventorySlot(ItemStack::isEmpty);}
    int menuSlot(int inv){for(int i=0;i<mc.player.containerMenu.slots.size();i++){var s=mc.player.containerMenu.getSlot(i);if(s.container==mc.player.getInventory()&&s.getContainerSlot()==inv)return i;}throw new IllegalStateException("玩家槽位映射失败");}
    void close(){require(mc.player.containerMenu.getCarried().isEmpty(),"光标非空，不能关闭菜单");if(mc.player.containerMenu!=mc.player.inventoryMenu){mc.player.closeContainer();food.due=true;}}
    void scheduleEnchantment(Planned p){var list=new ArrayList<Step>();
        Map<AnvilPlanner.Node,ItemStack> outputs=new IdentityHashMap<>();
        list.add(()->{outputs.put(findLeaf(p.node,0),mc.player.getInventory().getItem(inventorySlot(base(p.target.item))).copy());var used=new HashSet<Integer>();for(int i=0;i<p.books.size();i++){var b=p.books.get(i);int slot=-1;for(int j=0;j<36;j++)if(!protectedSlots.contains(j)&&!used.contains(j)&&Stacks.book(mc.player.getInventory().getItem(j),b.enchantment(),b.level(),b.penalty())){slot=j;break;}require(slot>=0,"任务背包缺少独立附魔书: "+label(b.enchantment()));used.add(slot);outputs.put(findLeaf(p.node,i+1),mc.player.getInventory().getItem(slot).copyWithCount(1));}return true;});
        enchantSteps(p.node,outputs,list);prepend(list);
    }
    static String[] pattern(String type){return switch(type){case "helmet"->new String[]{"DDD","D D","   "};case "chestplate"->new String[]{"D D","DDD","DDD"};case "leggings"->new String[]{"DDD","D D","D D"};case "boots"->new String[]{"D D","D D","   "};case "sword"->new String[]{" D "," D "," S "};case "pickaxe"->new String[]{"DDD"," S "," S "};case "shovel"->new String[]{" D "," S "," S "};case "axe"->new String[]{"DD ","DS "," S "};case "hoe"->new String[]{"DD "," S "," S "};default->throw new IllegalArgumentException(type);};}
    AnvilPlanner.Node findLeaf(AnvilPlanner.Node n,int leaf){if(n.isLeaf())return n.leaf()==leaf?n:null;var a=findLeaf(n.left(),leaf);return a==null?findLeaf(n.right(),leaf):a;}
    boolean matches(ItemStack s,Target t){return !s.isEmpty()&&Stacks.id(s).equals(t.item)&&s.getCount()==1&&s.getDamageValue()==0&&Stacks.ench(s,false).equals(t.ench);}
    void enchantSteps(AnvilPlanner.Node n,Map<AnvilPlanner.Node,ItemStack> outputs,List<Step> list){if(n.isLeaf())return;enchantSteps(n.left(),outputs,list);enchantSteps(n.right(),outputs,list);
        list.add(new AnvilSupply(this));
        list.add(new Put(s->ItemStack.isSameItemSameComponents(s,outputs.get(n.left())),0,1));list.add(new Put(s->ItemStack.isSameItemSameComponents(s,outputs.get(n.right())),1,1));
        list.add(new TakeResult(2,s->{ItemStack left=outputs.get(n.left()),right=outputs.get(n.right());var ench=new TreeMap<>(Stacks.ench(left,left.is(Items.ENCHANTED_BOOK)));Stacks.ench(right,true).forEach((id,level)->ench.merge(id,level,(a,b)->Math.min(UpgradeBooks.supported(id)?3:Math.max(a,b),a.equals(b)?a+1:Math.max(a,b))));return Stacks.id(s).equals(Stacks.id(left))&&Stacks.ench(s,s.is(Items.ENCHANTED_BOOK)).equals(ench)&&Stacks.penalty(s)==n.penalty();},n.cost()){
            @Override void captured(ItemStack s){outputs.put(n,s.copy());}
        });
    }
    final class Fetch implements Step {
        final Predicate<ItemStack> predicate;final int amount;boolean scheduled;
        Fetch(Predicate<ItemStack> p,int n){predicate=p;amount=n;}
        public boolean tick(){if(scheduled)return true;int have=0;for(int i=0;i<36;i++)if(!protectedSlots.contains(i)&&predicate.test(mc.player.getInventory().getItem(i)))have+=mc.player.getInventory().getItem(i).getCount();int remaining=amount-have;if(remaining<=0)return true;
            var steps=new ArrayList<Step>();for(Source src:stock)if(predicate.test(src.stack)&&remaining>0){int take=Math.min(remaining,src.stack.getCount());ItemStack expected=src.stack.copy();steps.add(new SourceFetch(Robot.this,predicate,amount,src,take,expected));src.stack.shrink(take);remaining-=take;}
            if(remaining>0){if(trading.offered(predicate))steps.add(trading.new Acquire(predicate,amount));else {queue.addFirst(new Scan());throw new Waiting("物资缺货或外部库存改变，请核对任务所需原料");}}scheduled=true;prepend(steps);return false;
        }
    }
    int externalSlot(Source src,ItemStack expected){var menu=mc.player.containerMenu;for(int i=0;i<menu.slots.size();i++)if(menu.getSlot(i).container!=mc.player.getInventory()&&ItemStack.isSameItemSameComponents(menu.getSlot(i).getItem(),expected)&&menu.getSlot(i).getItem().getCount()>=expected.getCount())return i;throw new IllegalStateException("取料快照改变: "+src.pos);}
    final class Put implements Step {
        final Predicate<ItemStack> p;final int target,amount;
        Put(Predicate<ItemStack> p,int t,int n){this.p=p;target=t;amount=n;}
        public boolean tick(){require(mc.player.containerMenu.getSlot(target).getItem().isEmpty(),"目标槽位非空");var steps=new ArrayList<Step>();int remaining=amount;ItemStack identity=null;
            for(int i=0;i<36&&remaining>0;i++)if(!protectedSlots.contains(i)){var stack=mc.player.getInventory().getItem(i);if(p.test(stack)&&(identity==null||ItemStack.isSameItemSameComponents(stack,identity))){int n=Math.min(remaining,stack.getCount());steps.add(new Move(menuSlot(i),target,n,stack));remaining-=n;identity=stack;}}
            require(remaining==0,"任务背包缺少足量同类输入");prepend(steps);return true;}

    }
    int transferTarget(ItemStack s,int amount){for(int i=0;i<36;i++)if(!protectedSlots.contains(i)){var v=mc.player.getInventory().getItem(i);if(!v.isEmpty()&&ItemStack.isSameItemSameComponents(v,s)&&v.getCount()+amount<=s.getMaxStackSize())return menuSlot(i);}return menuSlot(empty());}
    final class Move implements Step {
        final int source,target,amount;final ItemStack expected;int phase,from,to,targetCount;ItemStack beforeSource,beforeTarget;Click click;int placed;
        Move(int source,int target,int amount,ItemStack expected){this.source=source;this.target=target;this.amount=amount;this.expected=expected.copy();}
        public boolean tick(){
            if(phase==0){require(mc.player.containerMenu.getCarried().isEmpty(),"光标非空");from=source;to=target<0?transferTarget(expected,amount):target;beforeSource=mc.player.containerMenu.getSlot(from).getItem().copy();beforeTarget=mc.player.containerMenu.getSlot(to).getItem().copy();require(ItemStack.isSameItemSameComponents(beforeSource,expected)&&beforeSource.getCount()>=amount,"输入物品改变");require(beforeTarget.isEmpty()||ItemStack.isSameItemSameComponents(beforeTarget,beforeSource),"目标槽位占用");targetCount=beforeTarget.getCount();require(targetCount+amount<=beforeSource.getMaxStackSize(),"合并超过堆叠上限");click=new Click(from,0,()->mc.player.containerMenu.getSlot(from).getItem().isEmpty()&&ItemStack.matches(mc.player.containerMenu.getCarried(),beforeSource));phase=1;}
            if(phase==1){if(!click.tick())return false;phase=2;click=null;}
            if(phase==2&&amount==beforeSource.getCount()){if(click==null)click=new Click(to,0,()->mc.player.containerMenu.getCarried().isEmpty()&&mc.player.containerMenu.getSlot(to).getItem().getCount()==targetCount+amount);if(!click.tick())return false;placed=amount;click=null;phase=3;}
            if(phase==2){if(placed<amount){if(click==null){int next=placed+1;click=new Click(to,1,()->mc.player.containerMenu.getSlot(to).getItem().getCount()==targetCount+next&&mc.player.containerMenu.getCarried().getCount()==beforeSource.getCount()-next);}if(!click.tick())return false;placed++;click=null;return false;}phase=3;}
            if(phase==3){if(!mc.player.containerMenu.getCarried().isEmpty()){if(click==null)click=new Click(from,0,()->mc.player.containerMenu.getCarried().isEmpty()&&mc.player.containerMenu.getSlot(from).getItem().getCount()==beforeSource.getCount()-amount);if(!click.tick())return false;}phase=4;record("MOVE COMMITTED "+amount);}
            return true;
        }
    }
    final class Click implements Step {
        final int slot,button;final BooleanSupplier done;boolean sent;long start;AbstractContainerMenu original;
        Click(int slot,int button,BooleanSupplier done){this.slot=slot;this.button=button;this.done=done;}
        public boolean tick(){if(!sent){var m=mc.player.containerMenu;original=m;record("CLICK INTENT menu="+m.containerId+" slot="+slot+" button="+button);mc.getConnection().send(new ServerboundContainerClickPacket(m.containerId,m.getStateId(),(short)slot,(byte)button,ContainerInput.PICKUP,new Int2ObjectOpenHashMap<>(),HashedStack.create(m.getCarried(),mc.getConnection().decoratedHashOpsGenenerator())));sent=true;start=ticks;return false;}
            require(mc.player.containerMenu==original,"菜单身份改变");require(ticks-start<120,"ACTION_UNCERTAIN：服务器未确认点击");return ticks>start+1&&done.getAsBoolean();}
    }
    class TakeResult implements Step {
        final int slot,cost;final Predicate<ItemStack> predicate;Click click;ItemStack result;int free,beforeLevels,storedCount;long wait;
        TakeResult(int s,Predicate<ItemStack> p,int cost){slot=s;predicate=p;this.cost=cost;}
        void captured(ItemStack s){}
        public boolean tick(){if(click==null){if(wait==0)wait=ticks;var menu=mc.player.containerMenu;ItemStack s=menu.getSlot(slot).getItem();if(s.isEmpty()){require(ticks-wait<100,"工作站没有产物");return false;}require(predicate.test(s),"工作站产物与计划不一致");if(cost>=0){require(menu instanceof AnvilMenu a&&a.getCost()==cost,"COST_MODEL_MISMATCH");if(mc.player.experienceLevel<cost)throw new Waiting("经验不足：下一步需要 "+cost+" 级");}result=s.copy();free=-1;for(int i=0;i<36;i++)if(!protectedSlots.contains(i)){var held=mc.player.getInventory().getItem(i);if(!held.isEmpty()&&ItemStack.isSameItemSameComponents(held,result)&&held.getCount()+result.getCount()<=result.getMaxStackSize()){free=i;break;}}if(free<0)free=empty();storedCount=mc.player.getInventory().getItem(free).getCount()+result.getCount();beforeLevels=mc.player.experienceLevel;click=new Click(slot,0,()->ItemStack.matches(mc.player.containerMenu.getCarried(),result));}
            if(click.sent&&mc.player.containerMenu!=click.original){
                require(cost>=0&&click.original instanceof AnvilMenu,"工作站结果点击后菜单关闭，必须对账，禁止重发");
                // An anvil may break onTake; vanilla closes the menu and returns its cursor to inventory.
                boolean found=false;for(int i=0;i<36;i++)if(!protectedSlots.contains(i)&&ItemStack.matches(mc.player.getInventory().getItem(i),result))found=true;
                if(found&&mc.player.containerMenu.getCarried().isEmpty()&&(cost<0||mc.player.experienceLevel==beforeLevels-cost)){captured(result);record("RESULT RECONCILED AFTER MENU CLOSE cost="+cost);return true;}
                require(ticks-click.start<120,"铁砧损坏后的成品未确认，禁止重发");return false;
            }
            if(!click.tick())return false;captured(result);prepend(List.of(new Click(menuSlot(free),0,()->mc.player.containerMenu.getCarried().isEmpty()&&ItemStack.matches(mc.player.getInventory().getItem(free),result.copyWithCount(storedCount)))));record("RESULT CONFIRMED cost="+cost);return true;}
    }
    final class Open implements Step {
        BlockPos pos;final Class<?> type;Navigator nav;boolean sent;long start;
        Open(BlockPos p,Class<?> t){pos=p;type=t;}
        public boolean tick(){if(!sent){close();if(type==AnvilMenu.class){BlockPos usable=AnvilSupply.available(Robot.this);if(usable==null){prepend(List.of(new AnvilSupply(Robot.this)));return true;}if(!usable.equals(pos)){pos=usable;nav=null;}}if(nav==null)nav=new Navigator(pos);if(!nav.tick())return false;BlockHitResult hit=type==AnvilMenu.class?AnvilOpenRetry.hit(Robot.this,pos):new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);look(hit.getLocation());if(type==AnvilMenu.class)AnvilOpenRetry.sent(Robot.this,this);food.due=false;record("OPEN "+type.getSimpleName()+" "+pos);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);sent=true;start=ticks;return false;}
            if(mc.player.containerMenu!=mc.player.inventoryMenu){require(type.isInstance(mc.player.containerMenu),"菜单类型错误");if(ticks-start>3){if(type==AnvilMenu.class)AnvilOpenRetry.complete(this);return true;}return false;}if(ticks-start>=100&&type==AnvilMenu.class&&AnvilOpenRetry.retry(Robot.this,this))return false;require(ticks-start<100,"工作站或容器打开失败: "+pos.toShortString());return false;}
    }
    void look(Vec3 p){Vec3 d=p.subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    boolean walkable(BlockPos p){return new PlayerNavigation.Terrain(this).standable(p);}
    final class Navigator extends PlayerNavigation {
        Navigator(BlockPos p){this(p,false);}
        Navigator(BlockPos p,boolean pickup){super(Robot.this,p,pickup);}
    }
    void scheduleDelivery(){state="DELIVERING";var actions=new ArrayList<Step>();actions.add(new Open(packing(),ShulkerBoxMenu.class));actions.add(()->{verifyBox();close();return true;});actions.add(new RecoverBox());actions.add(new Open(cfg.output(),AbstractContainerMenu.class));actions.add(()->{int inv=inventorySlot(this::validBox);int to=-1;for(int i=0;i<mc.player.containerMenu.slots.size();i++)if(mc.player.containerMenu.getSlot(i).container!=mc.player.getInventory()&&mc.player.containerMenu.getSlot(i).getItem().isEmpty()){to=i;break;}require(to>=0,"交付箱空间不足");int dest=to;prepend(List.of(new Move(menuSlot(inv),dest,1,mc.player.getInventory().getItem(inv)),()->{require(validBox(mc.player.containerMenu.getSlot(dest).getItem()),"交付盒内容核验失败");close();deliveredKits++;record("DELIVERED");if(deliveredKits<batchBoxes.size()){activePacking=batchBoxes.get(deliveredKits);planning=null;trading.reset();queue.add(new Scan());queue.add(Robot.this::prepare);}return true;}));return true;});prepend(actions);}
    void verifyBox(){for(int i=0;i<27;i++){int slot=i;var target=kit.stream().filter(t->t.slot()==slot).findFirst();if(target.isPresent())require(matches(mc.player.containerMenu.getSlot(i).getItem(),target.get()),"盒内装备不符: "+target.get().key());else require(mc.player.containerMenu.getSlot(i).getItem().isEmpty(),"盒内混入余料");}}
    boolean validBox(ItemStack box){if(!(box.getItem() instanceof BlockItem item)||!taskBoxBlocks.contains(item.getBlock()))return false;var contents=box.getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).allItemsCopyStream().toList();for(int i=0;i<27;i++){ItemStack stack=i<contents.size()?contents.get(i):ItemStack.EMPTY;int slot=i;var target=kit.stream().filter(t->t.slot()==slot).findFirst();if(target.isPresent()){if(!matches(stack,target.get()))return false;}else if(!stack.isEmpty())return false;}return true;}
    final class RecoverBox implements Step {
        Navigator nav=new Navigator(packing()),pickupNav;BlockPos pickupTarget;boolean started;long start;
        public boolean tick(){if(!started&&!nav.tick())return false;if(!started){require(empty()>=0,"背包满");int hand=java.util.stream.IntStream.range(0,9).filter(i->!protectedSlots.contains(i)&&mc.player.getInventory().getItem(i).isEmpty()).findFirst().orElseThrow(()->new IllegalStateException("需空快捷栏以徒手回收任务盒"));mc.player.getInventory().setSelectedSlot(hand);record("BREAK TASK BOX INTENT "+packing());started=true;start=ticks;}
            if(mc.level.getBlockState(packing()).getBlock() instanceof ShulkerBoxBlock){mining=true;look(Vec3.atCenterOf(packing()));mc.gameMode.continueDestroyBlock(packing(),Direction.UP);require(ticks-start<1000,"任务盒挖取超时");return false;}
            mining=false;mc.gameMode.stopDestroyBlock();for(int i=0;i<36;i++)if(!protectedSlots.contains(i)&&validBox(mc.player.getInventory().getItem(i))){release();return true;}require(ticks-start<1200,"任务盒拾取未确认，不能重复制作");
            var drops=mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(packing()).inflate(4),e->validBox(e.getItem()));
            require(drops.size()<=1,"多个相同成品盒掉落，必须核对");if(drops.isEmpty()){release();return false;}
            var drop=drops.getFirst();BlockPos destination=BlockPos.containing(drop.getX(),Math.ceil(drop.getY()-.05),drop.getZ());if(!walkable(destination)){release();return false;}
            if(!destination.equals(pickupTarget)){pickupTarget=destination.immutable();pickupNav=new Navigator(pickupTarget,true);record("PICKUP ROUTE "+pickupTarget);}pickupNav.tick();return false;}
    }
}
