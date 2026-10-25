package io.github.autoequipmentmaking.client;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Regression for client placeholder caches, empty boxes, sparse slots and double chests. */
public final class RemoteInventoryGameTest implements FabricClientGameTest {
    public static final class Cache {
        final Map<BlockPos,CompoundTag> values=new HashMap<>();
        public void removeFromCache(BlockPos p){values.remove(p);}
        public CompoundTag getBlockEntityNbtFromCache(BlockPos p){return values.getOrDefault(p,new CompoundTag());}
        public Object getBlockEntityDataEntryFromCache(BlockPos p){return values.get(p);}
    }
    public static final class Tracker {
        final Set<BlockPos> pending=new HashSet<>();
        public void setPendingLocalBlockEntityRequest(BlockPos p,boolean value){if(value)pending.add(p);else pending.remove(p);}
        public boolean hasPendingLocalBlockEntity(BlockPos p){return pending.contains(p);}
    }
    public static final class Manager {
        final Cache cache=new Cache();final Tracker tracker=new Tracker();int requests;
        public Cache getCache(){return cache;}
        public Tracker getRequestTracker(){return tracker;}
        private void requestServuxBlockEntityData(BlockPos p){requests++;}
        void reply(BlockPos p,CompoundTag tag){cache.values.put(p,tag);tracker.pending.remove(p);}
    }
    static CompoundTag item(int slot,Item item,int count){
        return (CompoundTag)ItemStackWithSlot.CODEC.encodeStart(net.minecraft.client.Minecraft.getInstance().level.registryAccess().createSerializationContext(NbtOps.INSTANCE),new ItemStackWithSlot(slot,new ItemStack(item,count))).getOrThrow();
    }
    static CompoundTag inventory(CompoundTag... items){var root=new CompoundTag();var list=new ListTag();for(var item:items)list.add(item);root.put("Items",list);return root;}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    @Override public void runTest(ClientGameTestContext context){
        BlockPos box=new BlockPos(0,65,3),left=new BlockPos(0,65,0),right=new BlockPos(1,65,0);
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var level=server.overworld();for(var p:List.of(box,left,right)){level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(p.above(),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(box,Blocks.SHULKER_BOX.defaultBlockState());
                level.setBlockAndUpdate(left,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT));
                level.setBlockAndUpdate(right,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.RIGHT));
            });context.waitTicks(30);
            context.runOnClient(mc->{try{
                var m=new Manager();RemoteInventory.refresh(m,box);
                m.cache.values.put(box,new CompoundTag());
                check(RemoteInventory.read(m,box)==null,"Client placeholder was accepted before server acknowledgement");
                m.reply(box,new CompoundTag());var empty=RemoteInventory.read(m,box);
                check(empty!=null&&empty.getContainerSize()==27&&empty.isEmpty(),"Empty server shulker was not recognized");
                m.cache.removeFromCache(box);check(RemoteInventory.read(m,box)==null,"Expired cache must not become an empty shulker");
                int retries=m.requests;RemoteInventory.refreshMissing(m,box);RemoteInventory.refreshMissing(m,box);
                check(m.requests==retries+1&&m.tracker.hasPendingLocalBlockEntity(box),"Cache loss must issue one refresh while awaiting reply");
                m.reply(box,inventory(item(26,Items.DIAMOND,10)));var sparse=RemoteInventory.read(m,box);
                check(sparse.getContainerSize()==27&&sparse.getItem(26).is(Items.DIAMOND)&&sparse.getItem(26).getCount()==10&&sparse.getItem(0).isEmpty(),"Sparse server slots were truncated or renumbered");
                int requests=m.requests;RemoteInventory.refresh(m,left);check(m.requests==requests+2,"Both chest halves must be refreshed");
                m.reply(left,inventory(item(26,Items.DIAMOND,10)));m.cache.values.put(right,new CompoundTag());
                check(RemoteInventory.read(m,left)==null,"Double chest accepted one unconfirmed half");
                m.reply(right,inventory(item(0,Items.STICK,64)));var combined=RemoteInventory.read(m,left);
                check(combined.getContainerSize()==54&&combined.getItem(0).is(Items.STICK)&&combined.getItem(53).is(Items.DIAMOND),"Double chest order or capacity did not match the server menu");
                m.cache.removeFromCache(right);check(RemoteInventory.read(m,left)==null,"Expired half of a double chest accepted as empty");
                retries=m.requests;RemoteInventory.refreshMissing(m,left);RemoteInventory.refreshMissing(m,left);
                check(m.requests==retries+2,"Expired double chest must refresh both halves once");
                boolean rejected=false;try{RemoteInventory.decode(inventory(item(0,Items.DIAMOND,1),item(0,Items.STICK,1)));}catch(IllegalStateException expected){rejected=true;}
                check(rejected,"Duplicate server slots were silently overwritten");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        }
    }
}
