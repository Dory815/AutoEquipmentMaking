package io.github.autoequipmentmaking.engine.planner;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpgradePlannerTest {
    @Test void highLevelSelectionAndInsufficientPairs(){
        assertEquals(List.of(3),UpgradeBooks.select(20,20,1));
        assertEquals(List.of(2,2),UpgradeBooks.select(20,2,0));
        assertEquals(List.of(2,1,1),UpgradeBooks.select(4,1,0));
        assertEquals(List.of(1,1,1,1),UpgradeBooks.select(4,0,0));
        assertTrue(UpgradeBooks.select(1,1,0).isEmpty());
        assertTrue(UpgradeBooks.select(3,0,0).isEmpty());
        assertEquals(3,UpgradeBooks.capacity(6,1,1));
        assertFalse(UpgradeBooks.supported("minecraft:protection"));
    }
    record Piece(boolean equipment,int penalty,Map<String,Integer> levels) {}
    // Independent pair-reduction search; canonical whole-workbench states memoize equivalent reductions.
    int brute(List<Piece> pieces,Map<String,Integer> target,Map<String,Integer> weights,Map<String,Integer> memo){
        if(pieces.size()==1)return pieces.getFirst().levels.equals(target)?0:100000;
        String key=pieces.stream().map(p->p.equipment+":"+p.penalty+":"+new TreeMap<>(p.levels)).sorted().toList().toString();
        if(memo.containsKey(key))return memo.get(key);int best=100000;
        for(int i=0;i<pieces.size();i++)for(int j=0;j<pieces.size();j++){
            if(i==j||pieces.get(j).equipment)continue;
            Piece a=pieces.get(i),b=pieces.get(j);var levels=new TreeMap<>(a.levels);int cost=a.penalty+b.penalty;
            for(var e:b.levels.entrySet()){
                int old=levels.getOrDefault(e.getKey(),0),n=Math.min(target.get(e.getKey()),old==e.getValue()?old+1:Math.max(old,e.getValue()));
                levels.put(e.getKey(),n);cost+=n*weights.get(e.getKey());
            }
            if(cost>=40)continue;
            var rest=new ArrayList<>(pieces);rest.remove(Math.max(i,j));rest.remove(Math.min(i,j));rest.add(new Piece(a.equipment,2*Math.max(a.penalty,b.penalty)+1,levels));
            best=Math.min(best,cost+brute(rest,target,weights,memo));
        }
        memo.put(key,best);return best;
    }
    @Test void duplicateUpgradeTreesMatchIndependentSearch(){
        var random=new Random(3417);
        for(int[] levels:List.of(new int[]{2,2},new int[]{2,1,1},new int[]{1,1,1,1},new int[]{1,2},new int[]{1,1,1})){
            for(int sample=0;sample<4;sample++){
                var books=new ArrayList<AnvilPlanner.Book>();var pieces=new ArrayList<Piece>();int ep=sample%2;
                pieces.add(new Piece(true,ep,Map.of()));
                for(int level:levels){int p=random.nextInt(2);books.add(new AnvilPlanner.Book("special",level,4,p));pieces.add(new Piece(false,p,Map.of("special",level)));}
                books.add(new AnvilPlanner.Book("other",3,1,0));pieces.add(new Piece(false,0,Map.of("other",3)));
                var target=Map.of("special",3,"other",3);int expected=brute(pieces,target,Map.of("special",4,"other",1),new HashMap<>());
                if(expected>=100000)assertThrows(IllegalStateException.class,()->new AnvilPlanner(books,target,Long.MAX_VALUE).solve(ep));
                else assertEquals(expected,new AnvilPlanner(books,target,Long.MAX_VALUE).solve(ep).total());
            }
        }
    }
    @Test void bootsWithFourLevelOneBooksWithinPlanningBudget(){
        var books=new ArrayList<AnvilPlanner.Book>();var targets=new TreeMap<String,Integer>();
        String[] ids={"protection","depth_strider","soul_speed","feather_falling","unbreaking","mending"};
        int[] levels={4,3,3,4,3,1},weights={1,2,4,1,1,2};
        for(int i=0;i<ids.length;i++){targets.put(ids[i],levels[i]);for(int k=0;k<(i==2?4:1);k++)books.add(new AnvilPlanner.Book(ids[i],i==2?1:levels[i],weights[i],0));}
        var plan=new AnvilPlanner(books,targets,System.nanoTime()+2_000_000_000L).solve(0);
        assertTrue(plan.peak()<40);assertEquals(9,books.size());System.out.println("Four-I boots levels="+plan.total());
    }
}
