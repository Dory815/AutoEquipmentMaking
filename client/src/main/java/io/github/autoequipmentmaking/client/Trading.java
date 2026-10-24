package io.github.autoequipmentmaking.client;

import io.github.autoequipmentmaking.engine.planner.TradeCooldown;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.phys.*;

/** Quotes are observations, never stock. Purchases validate actual merchant menus. */
final class Trading {
    record Quote(UUID villager,int index,MerchantOffer offer) {}
    final Robot r;final List<Quote> quotes=new ArrayList<>();boolean scanned;int spent;
    Trading(Robot r){this.r=r;}
    void reset(){quotes.clear();scanned=false;spent=0;}
    boolean enabled(){return r.allowTrading&&(!r.cfg.root.getAsJsonObject("trading").has("enabled")||r.cfg.root.getAsJsonObject("trading").get("enabled").getAsBoolean());}
    int limit(String key){var v=r.cfg.root.getAsJsonObject("trading").get(key);return v==null||v.isJsonNull()?Integer.MAX_VALUE:v.getAsInt();}
    boolean supported(MerchantOffer o){
        var out=o.getResult();boolean product=(out.is(Items.ENCHANTED_BOOK)&&Stacks.ench(out,true).size()==1)||Stacks.base(out,"minecraft:bow")||Stacks.base(out,"minecraft:shield");
        return product&&out.getCount()==1&&payment(o.getCostA())&&(o.getCostB().isEmpty()||payment(o.getCostB()));
    }
    boolean payment(ItemStack s){return Stacks.base(s,"minecraft:emerald")||Stacks.base(s,"minecraft:book");}
    int emeralds(MerchantOffer o){return (o.getCostA().is(Items.EMERALD)?o.getCostA().getCount():0)+(o.getCostB().is(Items.EMERALD)?o.getCostB().getCount():0);}
    boolean offered(Predicate<ItemStack> p){return enabled()&&quotes.stream().anyMatch(q->supported(q.offer)&&p.test(q.offer.getResult()));}
    int potential(Predicate<ItemStack> p,int needed){return offered(p)?needed:0;}
    void remember(UUID id,MerchantOffers offers){quotes.removeIf(q->q.villager.equals(id));for(int i=0;i<offers.size();i++)quotes.add(new Quote(id,i,offers.get(i).copy()));}
    void remember(UUID id,MerchantMenu menu){remember(id,menu.getOffers());}
    List<ItemStack> planningBooks(Map<String,Integer> needs){
        var result=new ArrayList<ItemStack>();var best=new TreeMap<String,Quote>();
        for(var q:quotes)if(enabled()&&supported(q.offer)&&q.offer.getResult().is(Items.ENCHANTED_BOOK)){
            var s=q.offer.getResult();var e=Stacks.ench(s,true).entrySet().iterator().next();String key=e.getKey()+"@"+e.getValue();
            if(needs.containsKey(key)||io.github.autoequipmentmaking.engine.planner.UpgradeBooks.supported(e.getKey()))best.merge(key,q,(a,b)->Stacks.penalty(a.offer.getResult())<Stacks.penalty(b.offer.getResult())?a:Stacks.penalty(a.offer.getResult())>Stacks.penalty(b.offer.getResult())?b:emeralds(a.offer)<=emeralds(b.offer)?a:b);
        }
        for(var entry:best.entrySet()){
            var stack=entry.getValue().offer.getResult();var e=Stacks.ench(stack,true).entrySet().iterator().next();String id=e.getKey();int level=e.getValue(),n=needs.getOrDefault(entry.getKey(),0);
            if(io.github.autoequipmentmaking.engine.planner.UpgradeBooks.supported(id)){
                n=needs.getOrDefault(id+"@3",0);int capacity=io.github.autoequipmentmaking.engine.planner.UpgradeBooks.capacity(physicalBooks(id,1),physicalBooks(id,2),physicalBooks(id,3));
                if(capacity>=n)continue;n*=1<<(3-level);
            }
            int deficit=Math.max(0,n-physicalBooks(id,level));for(int i=0;i<deficit;i++)result.add(stack.copy());
        }return result;
    }
    int physicalBooks(String id,int level){return r.available(s->s.is(Items.ENCHANTED_BOOK)&&Stacks.ench(s,true).equals(Map.of(id,level)));}
    final class Discover implements Robot.Step {
        List<UUID> villagers;int index,observed,opened;long requested;OpenMerchant open;
        public boolean tick(){
            r.state="SCANNING TRADES";
            if(villagers==null){quotes.clear();var box=new AABB(Vec3.atLowerCornerOf(r.cfg.min()),Vec3.atLowerCornerOf(r.cfg.max().offset(1,1,1)));villagers=r.mc.level.getEntitiesOfClass(Villager.class,box,v->!v.isBaby()&&!v.getVillagerData().profession().is(VillagerProfession.NONE)&&!v.getVillagerData().profession().is(VillagerProfession.NITWIT)).stream().sorted(Comparator.comparingDouble((Villager v)->v.distanceToSqr(r.mc.player))).map(Villager::getUUID).toList();r.require(villagers.size()<=256,"村民超过256个");}
            if(index==villagers.size()){scanned=true;r.record("TRADE SCAN villagers="+villagers.size()+" offers="+quotes.size()+" observed="+observed+" opened="+opened);return true;}
            if(open==null){
                var entity=r.mc.level.getEntity(villagers.get(index));if(!(entity instanceof Villager v)||!v.isAlive()){next();return false;}
                if(requested==0){r.observer.refreshOffers(v);requested=r.ticks;return false;}
                var offers=r.observer.cachedOffers(v);
                if(offers!=null&&!offers.isEmpty()){remember(v.getUUID(),offers);observed++;next();return false;}
                if(offers==null&&r.ticks-requested<120)return false;
                open=new OpenMerchant(villagers.get(index));opened++;
            }
            if(!open.tick())return false;
            if(!open.failed)remember(villagers.get(index),(MerchantMenu)r.mc.player.containerMenu);r.close();next();return false;
        }
        void next(){open=null;requested=0;index++;r.stepStart=r.ticks;}
    }
    final class OpenMerchant implements Robot.Step {
        final UUID id;Robot.Navigator nav;BlockPos target;boolean sent,failed;long start;
        OpenMerchant(UUID id){this.id=id;}
        public boolean tick(){
            var entity=r.mc.level.getEntity(id);
            if(!(entity instanceof Villager v)||!v.isAlive()){failed=true;return true;}
            if(!sent){r.close();if(!r.inside(v.blockPosition())){failed=true;return true;}
                if(!v.blockPosition().equals(target)){target=v.blockPosition().immutable();nav=r.new Navigator(target);}
                if(!nav.tick())return false;r.food.due=false;r.look(v.getEyePosition());r.mc.gameMode.interact(r.mc.player,v,new EntityHitResult(v,v.getEyePosition()),InteractionHand.MAIN_HAND);sent=true;start=r.ticks;return false;
            }
            if(r.mc.player.containerMenu instanceof MerchantMenu menu&&!menu.getOffers().isEmpty()&&r.ticks-start>=5)return true;
            r.require(r.mc.player.containerMenu==r.mc.player.inventoryMenu||r.mc.player.containerMenu instanceof MerchantMenu,"村民交互打开了错误菜单");
            if(r.ticks-start>=100){failed=true;r.record("TRADE OPEN UNAVAILABLE "+id);return true;}return false;
        }
    }
    final class Acquire implements Robot.Step {
        final Predicate<ItemStack> predicate;final int amount;final boolean purchaseOnly;final Procurement.Payments batchPayments;final TradeCooldown cooldown=new TradeCooldown();
        List<UUID> round;int index;OpenMerchant open;Quote chosen;boolean sawExhausted;
        Acquire(Predicate<ItemStack> p,int n){predicate=p;amount=n;purchaseOnly=false;batchPayments=null;}
        Acquire(Predicate<ItemStack> p,int n,Procurement.Payments payments){predicate=p;amount=n;purchaseOnly=true;batchPayments=payments;}
        public boolean tick(){
            if(r.inventoryCount(predicate)>=amount)return true;
            if(batchPayments!=null&&!batchPayments.stillNeeded(predicate))return true;
            if(!purchaseOnly&&r.available(predicate)>=amount){r.close();r.prepend(List.of(r.new Fetch(predicate,amount)));return true;}
            r.state="TRADING";r.stepStart=r.ticks; // Restock polling is intentionally unbounded, without replaying purchases.
            if(round==null){if(!cooldown.due(System.nanoTime()/1_000_000L))return false;
                round=quotes.stream().filter(q->supported(q.offer)&&predicate.test(q.offer.getResult())).sorted(Comparator.comparing((Quote q)->q.offer.isOutOfStock()).thenComparingInt(q->emeralds(q.offer))).map(Quote::villager).distinct().toList();index=0;sawExhausted=false;
                if(round.isEmpty()){round=null;open=null;r.queue.addFirst(new Discover());r.queue.addFirst(r.new Scan());throw new Robot.Waiting("没有匹配的村民报价或原料："+description());}
            }
            if(index>=round.size()){
                r.close();
                if(!sawExhausted){round=null;open=null;r.queue.addFirst(new Discover());r.queue.addFirst(r.new Scan());throw new Robot.Waiting("村民暂时不可交易或报价改变："+description());}
                if(cooldown.exhausted(System.nanoTime()/1_000_000L))r.announce("村民进入补货冷却："+description()+"；每20秒复查一次");
                r.record("TRADE COOLDOWN CHECK "+cooldown.checks()+" "+description());round=null;open=null;return false;
            }
            if(open==null)open=new OpenMerchant(round.get(index));if(!open.tick())return false;
            if(open.failed){open=null;index++;return false;}
            var menu=(MerchantMenu)r.mc.player.containerMenu;remember(round.get(index),menu);
            sawExhausted|=quotes.stream().anyMatch(q->q.villager.equals(round.get(index))&&supported(q.offer)&&predicate.test(q.offer.getResult())&&q.offer.isOutOfStock());
            chosen=quotes.stream().filter(q->q.villager.equals(round.get(index))&&supported(q.offer)&&predicate.test(q.offer.getResult())&&!q.offer.isOutOfStock()).min(Comparator.comparingInt(q->emeralds(q.offer))).orElse(null);
            if(chosen==null){r.close();open=null;index++;return false;}
            if(cooldown.recovered())r.announce("村民补货完成，可以继续交易："+description());
            var o=chosen.offer;int cost=emeralds(o);if(cost>limit("maxEmeraldsPerTrade")||spent+cost>limit("maxEmeraldsPerKit")){r.close();round=null;open=null;throw new Robot.Waiting("村民交易超过配置的绿宝石预算："+description());}
            var payments=new LinkedHashMap<String,Integer>();for(var s:List.of(o.getCostA(),o.getCostB()))if(!s.isEmpty())payments.merge(Stacks.id(s),s.getCount(),Integer::sum);
            if(batchPayments!=null){
                try{var batchNeed=batchPayments.remaining(chosen);batchNeed.forEach((id,n)->payments.merge(id,n,Math::max));}
                catch(Robot.Waiting waiting){r.close();round=null;open=null;throw waiting;}
            }
            boolean funded=payments.entrySet().stream().allMatch(e->r.inventoryCount(r.base(e.getKey()))>=e.getValue());
            if(!funded&&batchPayments!=null&&!batchPayments.fitsFunding(payments)){
                r.close();batchPayments.cancelled=true;r.record("PRETRADE REPLAN price funding exceeds backpack capacity");return true;
            }
            if(!funded){r.close();var steps=new ArrayList<Robot.Step>();payments.forEach((id,n)->steps.add(new Currency(id,n)));steps.add(this);r.prepend(steps);round=null;open=null;return true;}
            var trade=new Purchase(chosen,this);r.prepend(List.of(trade,this));round=null;open=null;return true;
        }
        String description(){return quotes.stream().filter(q->predicate.test(q.offer.getResult())).findFirst().map(q->{var s=q.offer.getResult();return s.is(Items.ENCHANTED_BOOK)?Stacks.ench(s,true).entrySet().stream().map(e->Robot.label(e.getKey())+e.getValue()+"级附魔书").findFirst().orElse("附魔书"):Robot.label(Stacks.id(s));}).orElse("所需物品");}
    }
    /** Currency shortages refresh observations after good; blocks are consumed one at a time. */
    final class Currency implements Robot.Step {
        final String id;final int amount;boolean refreshed;
        Currency(String id,int n){this.id=id;amount=n;}
        public boolean tick(){
            if(r.inventoryCount(r.base(id))>=amount)return true;
            if(!refreshed){refreshed=true;r.prepend(List.of(r.new Scan(),this));return true;}
            int have=r.inventoryCount(r.base(id))+r.count(r.base(id));
            if(have>=amount){r.prepend(List.of(r.new Fetch(r.base(id),amount)));return true;}
            if(id.equals("minecraft:emerald")&&r.inventoryCount(r.base("minecraft:emerald_block"))+r.count(r.base("minecraft:emerald_block"))>0){
                int blocks=Math.min(64,Math.min((amount-have+8)/9,r.available(r.base("minecraft:emerald_block"))));
                var steps=new ArrayList<Robot.Step>();steps.add(r.new Fetch(r.base("minecraft:emerald_block"),blocks));steps.add(r.new Open(r.cfg.station("crafting"),CraftingMenu.class));steps.add(r.new Put(r.base("minecraft:emerald_block"),1,blocks));
                for(int i=0;i<blocks;i++){steps.add(r.new TakeResult(0,s->Stacks.base(s,"minecraft:emerald")&&s.getCount()==9,-1));steps.add(()->{r.record("EMERALD BLOCK UNPACKED");return true;});}
                steps.add(()->{r.close();return true;});steps.add(new Currency(id,amount));r.prepend(steps);return true;
            }
            refreshed=false;throw new Robot.Waiting((id.equals("minecraft:emerald")?"绿宝石缺货，原料容器也没有可分解的绿宝石块":"普通书缺货")+"：需要 "+amount+"，现有 "+have);
        }
    }
    /** Keep vanilla's task-funded inputs. Restore only private items, then buy the bounded deficit. */
    final class Purchase implements Robot.Step {
        final Quote q;final Acquire owner;int phase,bought;long sent,revision;ItemStack[] before;MerchantMenu menu;boolean requote;
        Purchase(Quote q,Acquire owner){this.q=q;this.owner=owner;}
        void privateUnchanged(){for(int inv:r.protectedSlots)r.require(ItemStack.matches(before[inv],r.mc.player.getInventory().getItem(inv)),"交易改变了受保护物品");}
        boolean sameOffer(){
            if(q.index>=menu.getOffers().size())return false;var live=menu.getOffers().get(q.index);
            return ItemStack.matches(live.getResult(),q.offer.getResult())&&ItemStack.matches(live.getCostA(),q.offer.getCostA())&&ItemStack.matches(live.getCostB(),q.offer.getCostB());
        }
        boolean refreshQuote(){
            r.record("TRADE REQUOTE before result click index="+q.index);return finish();
        }
        boolean finish(){
            r.prepend(List.of(new ReturnPayment(0),new ReturnPayment(1),()->{
                r.require(r.mc.player.containerMenu==menu,"交易结束前菜单身份改变");privateUnchanged();r.require(menu.getCarried().isEmpty(),"交易结束时光标非空");
                r.record("TRADE SESSION COMPLETE purchases="+bought);r.close();return true;
            }));return true;
        }
        public boolean tick(){
            r.require(r.mc.player.containerMenu instanceof MerchantMenu,"交易菜单关闭，禁止重新购买");
            if(phase==0){menu=(MerchantMenu)r.mc.player.containerMenu;
                r.require(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getCarried().isEmpty(),"交易选择前输入槽非空");before=new ItemStack[36];for(int i=0;i<36;i++)before[i]=r.mc.player.getInventory().getItem(i).copy();
                if(!sameOffer()||menu.getOffers().get(q.index).isOutOfStock())return refreshQuote();
                menu.setSelectionHint(q.index);revision=Robot.revisions.getOrDefault(menu.containerId,0L);r.record("TRADE SELECT INTENT index="+q.index);r.mc.getConnection().send(new ServerboundSelectTradePacket(q.index));sent=r.ticks;phase=1;return false;
            }
            r.require(r.mc.player.containerMenu==menu,"交易菜单身份改变，禁止重新购买");
            if(phase==1){r.require(r.ticks-sent<120,"交易选择填料未确认，禁止重发");if(r.ticks-sent<2||Robot.revisions.getOrDefault(menu.containerId,0L)<=revision)return false;var steps=new ArrayList<Robot.Step>();
                var deficits=new int[36];for(int inv=0;inv<36;inv++){
                    var held=r.mc.player.getInventory().getItem(inv);deficits[inv]=before[inv].getCount()-held.getCount();
                    r.require(deficits[inv]>=0&&(held.isEmpty()||ItemStack.isSameItemSameComponents(before[inv],held)),"村民自动填料与库存快照不一致");
                }
                for(int pay=0;pay<2;pay++){var input=menu.getSlot(pay).getItem();int remaining=input.getCount();for(int inv=0;inv<36&&remaining>0;inv++){
                    if(!before[inv].isEmpty()&&ItemStack.isSameItemSameComponents(before[inv],input)){
                        int missing=deficits[inv];if(missing>0){int n=Math.min(missing,remaining);if(r.protectedSlots.contains(inv))steps.add(new PaymentMove(pay,r.menuSlot(inv),n,input));remaining-=n;deficits[inv]-=n;}
                    }
                }r.require(remaining==0,"村民自动填料与库存快照不一致");}
                r.require(Arrays.stream(deficits).allMatch(n->n==0),"村民自动填料存在未对账物品");
                phase=2;steps.add(this);r.prepend(steps);return true;
            }
            privateUnchanged();if(requote||!sameOffer())return refreshQuote();
            if(r.inventoryCount(owner.predicate)>=owner.amount||(owner.batchPayments!=null&&!owner.batchPayments.stillNeeded(owner.predicate))||q.offer.isOutOfStock()||menu.getOffers().get(q.index).isOutOfStock())return finish();
            int cost=emeralds(q.offer);if(cost>limit("maxEmeraldsPerTrade")||(long)spent+cost>limit("maxEmeraldsPerKit"))return finish();
            var payments=new HashMap<String,Integer>();for(var payment:List.of(q.offer.getCostA(),q.offer.getCostB()))if(!payment.isEmpty())payments.merge(Stacks.id(payment),payment.getCount(),Integer::sum);
            for(var payment:payments.entrySet()){
                int held=r.inventoryCount(r.base(payment.getKey()));for(int slot=0;slot<2;slot++)if(r.base(payment.getKey()).test(menu.getSlot(slot).getItem()))held+=menu.getSlot(slot).getItem().getCount();
                if(held<payment.getValue())return finish(); // Acquire funds the next trip or enters the normal good gate.
            }
            // Whole task stacks go into payment slots. Vanilla deducts only the live offer's price.
            if(!q.offer.getCostB().isEmpty()&&ItemStack.isSameItemSameComponents(q.offer.getCostA(),q.offer.getCostB())){
                // Unusual custom offers charge the same item twice. Keep that case exact to avoid starving slot B.
                r.prepend(List.of(new ReturnPayment(0),new ReturnPayment(1),r.new Put(r.base(Stacks.id(q.offer.getCostA())),0,q.offer.getCostA().getCount()),r.new Put(r.base(Stacks.id(q.offer.getCostB())),1,q.offer.getCostB().getCount()),new TradeResult(this),this));return true;
            }
            r.prepend(List.of(new FillPayment(0,q.offer.getCostA()),new FillPayment(1,q.offer.getCostB()),new TradeResult(this),this));return true;
        }
    }

    /** Empty payment slots explicitly: vanilla close may merge leftovers into private stacks. */
    final class ReturnPayment implements Robot.Step {
        final int slot;final AbstractContainerMenu original;
        ReturnPayment(int slot){this.slot=slot;original=r.mc.player.containerMenu;}
        public boolean tick(){requireMerchant(original);var stack=original.getSlot(slot).getItem();if(!stack.isEmpty())r.prepend(List.of(new PaymentMove(slot,r.transferTarget(stack,stack.getCount()),stack.getCount(),stack)));return true;}
    }
    final class FillPayment implements Robot.Step {
        final int slot;final ItemStack cost;final AbstractContainerMenu original;
        FillPayment(int slot,ItemStack cost){this.slot=slot;this.cost=cost.copy();original=r.mc.player.containerMenu;}
        public boolean tick(){
            requireMerchant(original);var input=original.getSlot(slot).getItem();
            if(cost.isEmpty()){r.require(input.isEmpty(),"交易出现额外付款");return true;}
            r.require(input.isEmpty()||ItemStack.isSameItemSameComponents(input,cost),"交易付款物品改变");
            if(input.getCount()>=cost.getCount())return true;
            int source=-1,largest=0;
            for(int inv=0;inv<36;inv++)if(!r.protectedSlots.contains(inv)){
                var held=r.mc.player.getInventory().getItem(inv);
                int take=Math.min(held.getCount(),cost.getMaxStackSize()-input.getCount());
                if(ItemStack.isSameItemSameComponents(held,cost)&&take>largest){source=inv;largest=take;}
            }
            r.require(source>=0,"任务交易货币不足，禁止使用受保护物品");
            var held=r.mc.player.getInventory().getItem(source);r.record("TRADE PAYMENT STACK slot="+slot+" count="+largest);
            r.prepend(List.of(new PaymentMove(r.menuSlot(source),slot,largest,held),this));return true;
        }
    }
    void requireMerchant(AbstractContainerMenu menu){r.require(menu instanceof MerchantMenu&&r.mc.player.containerMenu==menu,"交易付款前菜单身份改变，禁止继续搬运");}
    /** Use vanilla half-stack pickup or return the smaller remainder, instead of n right-clicks. */
    final class PaymentMove implements Robot.Step {
        final int source,target,amount;final ItemStack expected;final AbstractContainerMenu original;
        PaymentMove(int source,int target,int amount,ItemStack expected){this.source=source;this.target=target;this.amount=amount;this.expected=expected.copy();original=r.mc.player.containerMenu;}
        public boolean tick(){
            requireMerchant(original);var menu=original;var from=menu.getSlot(source).getItem().copy();var to=menu.getSlot(target).getItem().copy();int count=from.getCount(),held=to.getCount();
            r.require(menu.getCarried().isEmpty()&&ItemStack.isSameItemSameComponents(from,expected)&&amount>0&&count>=amount,"交易货币移动前状态改变");
            r.require((to.isEmpty()||ItemStack.isSameItemSameComponents(from,to))&&held+amount<=Math.min(from.getMaxStackSize(),menu.getSlot(target).getMaxStackSize(from)),"交易货币目标槽位不可用");
            var actions=new ArrayList<Robot.Step>();boolean half=amount<count&&amount==(count+1)/2;int picked=half?amount:count;
            actions.add(r.new Click(source,half?1:0,()->ItemStack.matches(menu.getSlot(source).getItem(),from.copyWithCount(count-picked))&&ItemStack.matches(menu.getCarried(),from.copyWithCount(picked))));
            if(amount==picked){
                actions.add(r.new Click(target,0,()->menu.getCarried().isEmpty()&&ItemStack.matches(menu.getSlot(target).getItem(),from.copyWithCount(held+amount))));
            }else if(amount<=count-amount){
                for(int n=1;n<=amount;n++){int placed=n;actions.add(r.new Click(target,1,()->ItemStack.matches(menu.getSlot(target).getItem(),from.copyWithCount(held+placed))&&ItemStack.matches(menu.getCarried(),from.copyWithCount(count-placed))));}
                actions.add(r.new Click(source,0,()->menu.getCarried().isEmpty()&&ItemStack.matches(menu.getSlot(source).getItem(),from.copyWithCount(count-amount))));
            }else{
                for(int n=1;n<=count-amount;n++){int returned=n;actions.add(r.new Click(source,1,()->ItemStack.matches(menu.getSlot(source).getItem(),from.copyWithCount(returned))&&ItemStack.matches(menu.getCarried(),from.copyWithCount(count-returned))));}
                actions.add(r.new Click(target,0,()->menu.getCarried().isEmpty()&&ItemStack.matches(menu.getSlot(target).getItem(),from.copyWithCount(held+amount))));
            }
            int clicks=actions.size();actions.add(()->{r.require(menu==r.mc.player.containerMenu&&menu.getCarried().isEmpty()&&ItemStack.matches(menu.getSlot(source).getItem(),from.copyWithCount(count-amount))&&ItemStack.matches(menu.getSlot(target).getItem(),from.copyWithCount(held+amount)),"交易货币移动后对账失败");r.record("TRADE PAYMENT MOVE COMMITTED amount="+amount+" clicks="+clicks);return true;});r.prepend(actions);return true;
        }
    }
    final class TradeResult implements Robot.Step {
        final Purchase purchase;final Quote q;Robot.Click click;ItemStack result,remainingA,remainingB;int free;long wait;MerchantMenu menu;
        TradeResult(Purchase purchase){this.purchase=purchase;this.q=purchase.q;}
        ItemStack remainder(ItemStack input,ItemStack cost){
            r.require(cost.isEmpty()?input.isEmpty():ItemStack.isSameItemSameComponents(input,cost)&&input.getCount()>=cost.getCount(),"交易付款与报价不一致");
            return input.getCount()==cost.getCount()?ItemStack.EMPTY:input.copyWithCount(input.getCount()-cost.getCount());
        }
        public boolean tick(){
            if(click==null){if(wait==0)wait=r.ticks;r.require(r.mc.player.containerMenu instanceof MerchantMenu,"交易菜单改变");menu=(MerchantMenu)r.mc.player.containerMenu;
                r.require(menu==purchase.menu,"交易菜单身份改变");purchase.privateUnchanged();
                // No result click has been sent: return payments and re-open for fresh batch pricing.
                if(!purchase.sameOffer()||menu.getOffers().get(q.index).isOutOfStock()){purchase.requote=true;return true;}
                var out=menu.getSlot(2).getItem();if(out.isEmpty()){r.require(r.ticks-wait<100,"村民没有返回交易产物");return false;}
                r.require(ItemStack.matches(out,q.offer.getResult()),"交易产物与报价不一致");
                remainingA=remainder(menu.getSlot(0).getItem(),q.offer.getCostA());remainingB=remainder(menu.getSlot(1).getItem(),q.offer.getCostB());
                free=r.empty();result=out.copy();r.record("TRADE BUY INTENT index="+q.index+" emeralds="+emeralds(q.offer));
                click=r.new Click(2,0,()->ItemStack.matches(menu.getCarried(),result)&&ItemStack.matches(menu.getSlot(0).getItem(),remainingA)&&ItemStack.matches(menu.getSlot(1).getItem(),remainingB));
            }
            if(!click.tick())return false;
            purchase.privateUnchanged();spent+=emeralds(q.offer);q.offer.increaseUses();purchase.bought++;
            r.prepend(List.of(r.new Click(r.menuSlot(free),0,()->menu.getCarried().isEmpty()&&ItemStack.matches(r.mc.player.getInventory().getItem(free),result))));r.record("TRADE BUY CONFIRMED index="+q.index);return true;
        }
    }
}
