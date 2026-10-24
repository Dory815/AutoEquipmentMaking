package io.github.autoequipmentmaking.client;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Complete the kit's observed trade deficits before crafting any diamond equipment. */
final class Procurement implements Robot.Step {
    record Need(Predicate<ItemStack> predicate,int required,String label) {}
    record Buy(Need need,int count) {}
    record Target(Need need,int inventoryAmount) {}
    final class Payments {
        final List<Target> targets;
        boolean cancelled;
        Payments(List<Target> targets){this.targets=List.copyOf(targets);}
        boolean stillNeeded(Predicate<ItemStack> predicate){return !cancelled&&targets.stream().filter(t->t.need.predicate==predicate).anyMatch(t->r.available(predicate)<t.need.required);}
        boolean fitsFunding(Map<String,Integer> payments){
            int extra=payments.entrySet().stream().mapToInt(e->slots(e.getValue()-r.inventoryCount(r.base(e.getKey())))).sum();
            int products=targets.stream().mapToInt(t->Math.min(Math.max(0,t.inventoryAmount-r.inventoryCount(t.need.predicate)),Math.max(0,t.need.required-r.available(t.need.predicate)))).sum();
            return extra+products+4<=free();
        }
        Map<String,Integer> remaining(Trading.Quote current){
            int emeralds=0,books=0;
            for(var target:targets){
                int n=Math.min(Math.max(0,target.inventoryAmount-r.inventoryCount(target.need.predicate)),Math.max(0,target.need.required-r.available(target.need.predicate)));if(n==0)continue;
                var estimate=quote(target.need);int price=r.trading.emeralds(estimate.offer()),paper=books(estimate);
                if(target.need.predicate.test(current.offer().getResult())){price=Math.max(price,r.trading.emeralds(current.offer()));paper=Math.max(paper,books(current));}
                emeralds+=n*price;books+=n*paper;
            }
            if((long)r.trading.spent+emeralds>r.trading.limit("maxEmeraldsPerKit"))throw new Robot.Waiting("当前报价超过剩余集中采购预算");
            var result=new LinkedHashMap<String,Integer>();if(emeralds>0)result.put("minecraft:emerald",emeralds);if(books>0)result.put("minecraft:book",books);return result;
        }
    }
    final Robot r;final List<Need> needs=new ArrayList<>();
    Procurement(Robot r){
        this.r=r;
        var books=new LinkedHashMap<io.github.autoequipmentmaking.engine.planner.AnvilPlanner.Book,Integer>();
        for(var plan:r.plans)for(var book:plan.books())books.merge(book,1,Integer::sum);
        books.forEach((b,n)->needs.add(new Need(s->Stacks.book(s,b.enchantment(),b.level(),b.penalty()),n,Robot.label(b.enchantment())+b.level()+"级附魔书")));
        needs.add(new Need(r.base("minecraft:bow"),1,Robot.label("minecraft:bow")));
        if(r.kit.stream().anyMatch(t->t.key().equals("shield")))needs.add(new Need(r.base("minecraft:shield"),1,Robot.label("minecraft:shield")));
    }
    int free(){int n=0;for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i)&&r.mc.player.getInventory().getItem(i).isEmpty())n++;return n;}
    boolean product(ItemStack stack){return needs.stream().anyMatch(n->n.predicate.test(stack));}
    static int slots(int count){return (Math.max(0,count)+63)/64;}
    int books(Trading.Quote q){return (q.offer().getCostA().is(Items.BOOK)?q.offer().getCostA().getCount():0)+(q.offer().getCostB().is(Items.BOOK)?q.offer().getCostB().getCount():0);}
    Trading.Quote quote(Need need){
        var found=r.trading.quotes.stream().filter(q->r.trading.supported(q.offer())&&need.predicate.test(q.offer().getResult())).min(Comparator.comparing((Trading.Quote q)->q.offer().isOutOfStock()).thenComparingInt(q->r.trading.emeralds(q.offer())));
        if(found.isPresent())return found.get();
        r.close();r.queue.addFirst(r.trading.new Discover());r.queue.addFirst(r.new Scan());
        throw new Robot.Waiting("集中采购没有匹配报价："+need.label);
    }
    void waitForBudget(String message){
        r.close();r.queue.addFirst(r.trading.new Discover());r.queue.addFirst(r.new Scan());throw new Robot.Waiting(message);
    }
    public boolean tick(){
        r.state="PROCURING";
        var missing=needs.stream().filter(n->r.available(n.predicate)<n.required).toList();
        if(missing.isEmpty()){
            if(free()<16){r.prepend(List.of(new ProcurementStorage(this,16,true),this));return true;}
            r.record("PRETRADE COMPLETE all kit deficits filled before diamond crafting");return true;
        }
        long wholeBudget=r.trading.spent;
        for(var need:missing){var q=quote(need);int price=r.trading.emeralds(q.offer());if(price>r.trading.limit("maxEmeraldsPerTrade"))waitForBudget("集中采购超过单笔绿宝石预算："+need.label);wholeBudget+=(long)(need.required-r.available(need.predicate))*price;}
        if(wholeBudget>r.trading.limit("maxEmeraldsPerKit"))waitForBudget("集中采购超过整套绿宝石预算");
        int emeralds=0,books=0,products=0,haveEmeralds=r.inventoryCount(r.base("minecraft:emerald")),haveBooks=r.inventoryCount(r.base("minecraft:book"));
        var batch=new ArrayList<Buy>();
        for(var need:missing){
            var q=quote(need);int price=r.trading.emeralds(q.offer()),paper=books(q),count=0;
            if(price>r.trading.limit("maxEmeraldsPerTrade"))waitForBudget("集中采购超过单笔绿宝石预算："+need.label);
            int deficit=need.required-r.available(need.predicate);
            for(int i=0;i<deficit;i++){
                // Reserve input/output/crafting slots even while payments are being decomposed.
                int occupied=products+1+slots(emeralds+price-haveEmeralds)+slots(books+paper-haveBooks)+4;
                if(occupied>free())break;
                emeralds+=price;books+=paper;products++;count++;
            }
            if(count>0)batch.add(new Buy(need,count));
            if(count<deficit)break;
        }
        int remainingProducts=missing.stream().mapToInt(n->n.required-r.available(n.predicate)).sum();
        int carriedProducts=(int)java.util.stream.IntStream.range(0,36).filter(i->!r.protectedSlots.contains(i)&&product(r.mc.player.getInventory().getItem(i))).count();
        // Reclaim purchase slots before funding another partial batch. Otherwise the
        // last few free slots create two-item and one-item return trips for currency.
        if(products<remainingProducts&&carriedProducts>0){
            int targetFree=free()+carriedProducts;
            r.record("PRETRADE CONSOLIDATE held="+carriedProducts+" free="+free()+" targetFree="+targetFree);
            r.prepend(List.of(new ProcurementStorage(this,targetFree,false),this));return true;
        }
        if(batch.isEmpty()){
            if(java.util.stream.IntStream.range(0,36).anyMatch(i->!r.protectedSlots.contains(i)&&product(r.mc.player.getInventory().getItem(i)))){r.prepend(List.of(new ProcurementStorage(this,16,false),this));return true;}
            throw new Robot.Waiting("背包空间不足，无法携带集中交易的货币和产物；请腾出任务槽位");
        }
        if((long)r.trading.spent+emeralds>r.trading.limit("maxEmeraldsPerKit"))waitForBudget("集中采购超过整套绿宝石预算");
        r.say("集中采购 "+products+" 件；预备 "+emeralds+" 颗绿宝石、"+books+" 本普通书");
        r.record("PRETRADE PLAN products="+products+" emeralds="+emeralds+" books="+books);
        var actions=new ArrayList<Robot.Step>();
        if(emeralds>0)actions.add(r.trading.new Currency("minecraft:emerald",emeralds));
        if(books>0)actions.add(r.trading.new Currency("minecraft:book",books));
        actions.add(()->{
            // Funding refreshes stock. Recheck deficits before buying; never replace sufficient stock.
            var targets=new ArrayList<Target>();
            for(var buy:batch){int n=Math.min(buy.count,Math.max(0,buy.need.required-r.available(buy.need.predicate)));if(n>0)targets.add(new Target(buy.need,r.inventoryCount(buy.need.predicate)+n));}
            var payments=new Payments(targets);var purchases=new ArrayList<Robot.Step>();
            for(var target:targets)purchases.add(r.trading.new Acquire(target.need.predicate,target.inventoryAmount,payments));
            r.prepend(purchases);return true;
        });
        actions.add(this);r.prepend(actions);return true;
    }
}
