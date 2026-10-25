package io.github.autoequipmentmaking.engine.planner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlannerTest {
    @Test void finalRenameCountsOnceAndRespectsTooExpensive(){
        var books=List.of(new AnvilPlanner.Book("x",1,1,38));
        assertEquals(39,new AnvilPlanner(books,Long.MAX_VALUE).solve(0).total());
        assertThrows(IllegalStateException.class,()->new AnvilPlanner(books,Long.MAX_VALUE).solve(0,1));
        var normal=List.of(new AnvilPlanner.Book("x",1,1,0),new AnvilPlanner.Book("y",1,2,0));
        assertEquals(new AnvilPlanner(normal,Long.MAX_VALUE).solve(0).total()+1,new AnvilPlanner(normal,Long.MAX_VALUE).solve(0,1).total());
    }
    @Test void chestplate() {
        var p=new AnvilPlanner(List.of(new AnvilPlanner.Book("protection",4,1,0),new AnvilPlanner.Book("unbreaking",3,1,0),new AnvilPlanner.Book("mending",1,2,0)),Long.MAX_VALUE).solve(0);
        assertEquals(13,p.total()); assertEquals(5,p.peak()); assertEquals(7,p.penalty());
    }
    @Test void capAndTimeout() {
        assertThrows(IllegalStateException.class,()->new AnvilPlanner(List.of(new AnvilPlanner.Book("x",1,1,39)),Long.MAX_VALUE).solve(0));
        assertThrows(IllegalStateException.class,()->new AnvilPlanner(List.of(new AnvilPlanner.Book("x",1,1,0)),0).solve(0));
    }
    @Test void allFourteenEquipmentTargets() {
        int[][] weights={{6,4,2,3,2},{4,3,2},{4,12,3,2},{4,6,12,4,3,2},{5,6,6,3,2},{5,6,6,3,2},{4,5,3,2},{6,5,3,2},{5,3,2},{5,3,2},{5,6,3,2},{3,2},{3,2},{5,4,2,4,3}};
        for(int[] target:weights){var books=new ArrayList<AnvilPlanner.Book>();var states=new ArrayList<State>();states.add(new State(Set.of(0),0,0,true));for(int i=0;i<target.length;i++){books.add(new AnvilPlanner.Book("e"+i,1,target[i],0));states.add(new State(Set.of(i+1),0,target[i],false));}assertEquals(brute(states),new AnvilPlanner(books,Long.MAX_VALUE).solve(0).total());}
    }
    // Independent exhaustive reduction of all ordered pairs, rather than the planner's subset recurrence.
    record State(Set<Integer> leaves,int penalty,int transfer,boolean equipment) {}
    int brute(List<State> list) {
        if(list.size()==1)return 0;
        int best=100000;
        for(int i=0;i<list.size();i++) for(int j=0;j<list.size();j++) {
            if(i==j||list.get(j).equipment)continue;
            State a=list.get(i),b=list.get(j);int cost=a.penalty+b.penalty+b.transfer;if(cost>=40)continue;
            var next=new ArrayList<>(list);next.remove(Math.max(i,j));next.remove(Math.min(i,j));
            var leaves=new HashSet<>(a.leaves);leaves.addAll(b.leaves);
            next.add(new State(leaves,2*Math.max(a.penalty,b.penalty)+1,a.transfer+b.transfer,a.equipment));
            best=Math.min(best,cost+brute(next));
        }return best;
    }
    @Test void compareIndependentExhaustive() {
        var random=new Random(207);
        for(int sample=0;sample<24;sample++) {
            var books=new ArrayList<AnvilPlanner.Book>();var states=new ArrayList<State>();
            int ep=random.nextInt(3);states.add(new State(Set.of(0),ep,0,true));
            for(int i=0;i<4;i++){int w=1+random.nextInt(12),p=random.nextInt(4);books.add(new AnvilPlanner.Book("e"+i,1,w,p));states.add(new State(Set.of(i+1),p,w,false));}
            int expected=brute(states);
            if(expected>=100000)assertThrows(IllegalStateException.class,()->new AnvilPlanner(books,Long.MAX_VALUE).solve(ep));
            else assertEquals(expected,new AnvilPlanner(books,Long.MAX_VALUE).solve(ep).total());
        }
    }
}
