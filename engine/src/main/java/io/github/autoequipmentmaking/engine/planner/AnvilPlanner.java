package io.github.autoequipmentmaking.engine.planner;

import java.util.*;

/** Exact ordered-tree search, including equal-level upgrades and resulting-level fees. */
public final class AnvilPlanner {
    public record Book(String enchantment, int level, int multiplier, int penalty) {
        public Book { if (level < 1 || multiplier < 1 || penalty < 0) throw new IllegalArgumentException("Invalid book"); }
    }
    public record Node(int leaf, Node left, Node right, int cost, int penalty, int total, int peak) {
        public boolean isLeaf() { return left == null; }
    }
    private record State(Node node,long levels) {}
    private final List<Book> books;
    private final long deadline;
    private final List<String> ids;
    private final int[] maxima,weights;
    private final long target;
    private final Map<Integer,List<State>> cache = new HashMap<>();
    public AnvilPlanner(List<Book> books,long deadline) {this(books,defaultTargets(books),deadline);}
    private static Map<String,Integer> defaultTargets(List<Book> books){
        var targets=new TreeMap<String,Integer>();books.forEach(b->targets.merge(b.enchantment(),b.level(),Math::max));return targets;
    }
    public AnvilPlanner(List<Book> books,Map<String,Integer> targets,long deadline) {
        this.books=List.copyOf(books);this.deadline=deadline;ids=new ArrayList<>(new TreeSet<>(targets.keySet()));
        if(books.size()>9||ids.size()>6||books.isEmpty())throw new IllegalArgumentException("Expected one to nine compatible single-enchantment books");
        maxima=new int[ids.size()];weights=new int[ids.size()];long encoded=0;
        for(int i=0;i<ids.size();i++){
            int index=i;maxima[i]=targets.get(ids.get(i));
            if(maxima[i]<1||maxima[i]>15)throw new IllegalArgumentException("Unsupported target level");
            weights[i]=books.stream().filter(b->b.enchantment().equals(ids.get(index))).findFirst().orElseThrow().multiplier();
            encoded|=(long)maxima[i]<<(i*4);
        }
        for(Book b:books){int i=ids.indexOf(b.enchantment());if(i<0||b.level()>maxima[i]||b.multiplier()!=weights[i])throw new IllegalArgumentException("Book outside target model");}
        target=encoded;
    }
    public Node solve(int equipmentPenalty) {
        if(equipmentPenalty<0)throw new IllegalArgumentException("Negative equipment penalty");
        cache.clear();cache.put(1,List.of(new State(new Node(0,null,null,0,equipmentPenalty,0,0),0)));
        for(int i=0;i<books.size();i++){Book b=books.get(i);cache.put(1<<(i+1),List.of(new State(new Node(i+1,null,null,0,b.penalty(),0,0),(long)b.level()<<(ids.indexOf(b.enchantment())*4))));}
        return states((1<<(books.size()+1))-1).stream().filter(s->s.levels==target).map(State::node)
                .min(Comparator.comparingInt(Node::total).thenComparingInt(Node::peak).thenComparingInt(Node::penalty))
                .orElseThrow(()->new IllegalStateException("PLAN_INFEASIBLE"));
    }
    private List<State> states(int mask) {
        if(System.nanoTime()>deadline)throw new IllegalStateException("PLAN_TIMEOUT");
        if(cache.containsKey(mask))return cache.get(mask);
        Map<Long,List<State>> byLevels=new HashMap<>();
        for(int left=(mask-1)&mask;left>0;left=(left-1)&mask){
            int right=mask^left;if(right==0||(right&1)!=0)continue;
            for(State a:states(left))for(State b:states(right)){
                long levels=a.levels;int transfer=0;
                for(int i=0;i<ids.size();i++){
                    int r=(int)((b.levels>>>(4*i))&15);if(r==0)continue;
                    int l=(int)((a.levels>>>(4*i))&15),out=Math.min(maxima[i],l==r?r+1:Math.max(l,r));
                    levels=(levels&~(15L<<(4*i)))|((long)out<<(4*i));transfer+=out*weights[i];
                }
                Node an=a.node,bn=b.node;long price=(long)an.penalty()+bn.penalty()+transfer;if(price>=40)continue;
                int penalty=(int)Math.min(Integer.MAX_VALUE,2L*Math.max(an.penalty(),bn.penalty())+1);
                Node n=new Node(-1,an,bn,(int)price,penalty,an.total()+bn.total()+(int)price,Math.max((int)price,Math.max(an.peak(),bn.peak())));
                // Different levels cannot dominate each other: their future upgrade costs differ.
                var out=byLevels.computeIfAbsent(levels,k->new ArrayList<>());
                if(out.stream().anyMatch(x->dominates(x.node,n)))continue;
                out.removeIf(x->dominates(n,x.node));out.add(new State(n,levels));
            }
        }
        var out=byLevels.values().stream().flatMap(Collection::stream).toList();cache.put(mask,out);return out;
    }
    private static boolean dominates(Node a,Node b){return a.total()<=b.total()&&a.penalty()<=b.penalty()&&a.peak()<=b.peak();}
}
