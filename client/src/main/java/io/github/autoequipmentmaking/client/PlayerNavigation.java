package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.*;

/** Shape-based A*: half-block horizontal lattice, exact collision-surface heights.
 * Only ordinary player input is emitted. Never moves entities or changes blocks. */
final class PlayerNavigation implements Robot.Step {
    final Robot r;
    final BlockPos target;
    final boolean pickup;
    final UUID merchant;
    final Terrain terrain;
    List<Vec3> route;
    int index, age, stalled, replans, plans, jumps, movingTicks, centeringTicks, settling;
    double best=Double.POSITIVE_INFINITY;
    boolean jumping;
    Vec3 launch;
    final Set<Key> rejectedGoals=new HashSet<>();
    record Key(int x,int y,int z) {
        Vec3 point(){return new Vec3(x/8.0,y/16.0,z/8.0);}
        static Key of(Vec3 p){return new Key((int)Math.round(p.x*8),(int)Math.round(p.y*16),(int)Math.round(p.z*8));}
    }
    record Visit(Key key,double cost,double score) {}
    PlayerNavigation(Robot r,BlockPos target,boolean pickup){this(r,target,pickup,null);}
    PlayerNavigation(Robot r,net.minecraft.world.entity.npc.villager.Villager merchant){this(r,merchant.blockPosition(),false,merchant.getUUID());}
    private PlayerNavigation(Robot r,BlockPos target,boolean pickup,UUID merchant){this.r=r;this.target=target.immutable();this.pickup=pickup;this.merchant=merchant;terrain=new Terrain(r);}

    @Override public boolean tick(){
        var p=r.mc.player;age++;
        r.require(r.inside(target),"寻路目标超出工坊范围: "+target);
        if(p.onGround()&&reached(p.position(),3.2)){r.release();return true;}
        if(route==null){
            r.release();if(!p.onGround())return false;
            // A closing lid or an edge step can leave onGround true for one update.
            // Release input and let ordinary physics settle before rejecting the start.
            if(!terrain.clear(p.position())||!terrain.supported(p.position())){
                if(++settling<=20)return false;
            }else settling=0;
            plan();
        }
        if(index>=route.size()){replan("route end");return false;}
        Vec3 dest=route.get(index),actual=p.position();
        // The final anchor is never consumed by proximity alone: actual line of sight
        // must satisfy reached(), otherwise a thin panel can hide the container.
        if(p.onGround()&&index+1<route.size()&&Math.abs(actual.y-dest.y)<.08&&actual.subtract(dest).horizontalDistance()<.045
            &&terrain.edge(actual,route.get(index+1))){
            index++;stalled=0;best=Double.POSITIVE_INFINITY;jumping=false;
            if(index>=route.size()){r.release();return false;}
            dest=route.get(index);
        }
        // Smooth only supported, fully swept level segments. Never cut a rising corner.
        if(p.onGround()&&!jumping){
            for(int n=Math.min(index+10,route.size()-1);n>index;n--){
                boolean flat=true;for(int k=index;k<=n;k++)if(Math.abs(route.get(k).y-actual.y)>.02){flat=false;break;}
                if(flat&&terrain.levelSegment(actual,route.get(n))){index=n;dest=route.get(n);break;}
            }
        }
        Vec3 delta=dest.subtract(actual);double horizontal=delta.horizontalDistance();
        boolean rise=delta.y>p.maxUpStep()+.02;
        if(p.onGround()&&!jumping){
            if(!terrain.edge(actual,dest)){
                replan("edge changed actual="+actual+" next="+dest);return false;
            }
            if(rise){jumping=true;launch=actual;jumps++;}
        }
        // Rise vertically before crossing a ledge; this also handles narrow trapdoor tops.
        boolean hold=jumping&&actual.y<dest.y+.015&&p.getDeltaMovement().y>=0;
        if(jumping&&p.onGround()&&actual.distanceTo(launch)>.05&&actual.y<dest.y-.1){
            jumping=false;replan("jump landed below ledge");return false;
        }
        float yaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90);
        float turn=Mth.wrapDegrees(yaw-p.getYRot());
        p.setYRot(p.getYRot()+Mth.clamp(turn,-30,30));p.setXRot(10);
        boolean aligned=Math.abs(Mth.wrapDegrees(yaw-p.getYRot()))<4;
        boolean sprint=p.onGround()&&!jumping&&aligned&&horizontal>1.4&&Math.abs(delta.y)<.02
            &&terrain.wideSegment(actual,actual.lerp(dest,Math.min(1,2/horizontal)));
        double along=horizontal<1e-5?0:p.getDeltaMovement().dot(new Vec3(delta.x/horizontal,0,delta.z/horizontal));
        double desired=Math.min(p.onGround()?(sprint?.36:.215):.17,horizontal*.55);
        // Account for momentum instead of repeatedly overshooting narrow turn anchors.
        float input=hold||!aligned?0:(float)Mth.clamp((desired-along)/(p.onGround()?.1:.02),-1,1);
        if(horizontal<.001)input=0;
        r.navigating=true;r.navigationScale=input;
        r.mc.options.keyUp.setDown(Math.abs(input)>.001);
        r.mc.options.keyJump.setDown(jumping&&p.onGround()&&actual.distanceTo(launch)<.05);
        r.sprintKey(sprint&&input>.8);if(!sprint)p.setSprinting(false);
        if(Math.abs(input)>.01)movingTicks++;if(horizontal<.5)centeringTicks++;
        double distance=actual.distanceTo(dest);
        if(distance+.025<best){best=distance;stalled=0;}else stalled++;
        if(stalled>80&&p.onGround()){
            if(index==route.size()-1)rejectedGoals.add(Key.of(dest));
            replan("no progress actual="+actual+" next="+dest);
        }
        r.require(age<6000,"寻路超时: "+target);
        return false;
    }
    void replan(String reason){
        r.release();if(r.running)r.record("NAV REPLAN "+reason+" target="+target);
        r.require(++replans<=8,"移动受阻，重新寻路后仍不可达: "+target);
        route=null;jumping=false;stalled=0;best=Double.POSITIVE_INFINITY;
    }
    boolean reached(Vec3 feet,double range){
        if(pickup)return feet.distanceTo(Vec3.atBottomCenterOf(target))<.65;
        if(merchant!=null){
            var entity=r.mc.level.getEntity(merchant);
            if(!(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager)||!villager.isAlive()||terrain.body(feet).intersects(villager.getBoundingBox()))return false;
            return merchantAim(r,villager,feet,range)!=null;
        }
        var body=terrain.body(feet);
        if(body.maxX>target.getX()&&body.minX<target.getX()+1&&body.maxZ>target.getZ()&&body.minZ<target.getZ()+1)return false;
        Vec3 eye=feet.add(0,r.mc.player.getEyeHeight(),0),aim=Vec3.atCenterOf(target);
        if(eye.distanceTo(aim)>=range)return false;
        var hit=r.mc.level.clip(new ClipContext(eye,aim,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,r.mc.player));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(target);
    }
    /** Aim inside the living entity's box: a counter can hide either its feet or its eyes. */
    static Vec3 merchantAim(Robot r,net.minecraft.world.entity.npc.villager.Villager villager,Vec3 feet,double range){
        Vec3 eye=feet.add(0,r.mc.player.getEyeHeight(),0);var box=villager.getBoundingBox();var center=box.getCenter();
        double height=box.maxY-box.minY,dx=(box.maxX-box.minX)*.25,dz=(box.maxZ-box.minZ)*.25;
        if(eye.distanceTo(center)>range+height)return null;
        for(var aim:List.of(villager.getEyePosition(),center,center.add(0,-height*.25,0),center.add(0,height*.25,0),center.add(dx,0,0),center.add(-dx,0,0),center.add(0,0,dz),center.add(0,0,-dz))){
            if(eye.distanceTo(aim)>=range)continue;
            // Entity visibility must ignore the selectable outline of signs;
            // physical counter/wall collisions still block this interaction ray.
            if(r.mc.level.clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,r.mc.player)).getType()==HitResult.Type.MISS)return aim;
        }
        return null;
    }
    double heuristic(Vec3 p){
        if(merchant!=null&&r.mc.level.getEntity(merchant) instanceof net.minecraft.world.entity.npc.villager.Villager villager)return Math.max(0,p.add(0,r.mc.player.getEyeHeight(),0).distanceTo(villager.getEyePosition())-2.8);
        return Math.max(0,p.distanceTo(Vec3.atCenterOf(target))-(pickup?.4:2.8));
    }
    void plan(){
        terrain.columns.clear();Vec3 actual=r.mc.player.position();
        r.require(terrain.clear(actual)&&terrain.supported(actual),"玩家当前位置没有安全支撑: "+actual+" clear="+terrain.clear(actual)+" supported="+terrain.supported(actual));
        var open=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::score));
        var costs=new HashMap<Key,Double>();var parents=new HashMap<Key,Key>();
        int sx=(int)Math.round(actual.x*2),sz=(int)Math.round(actual.z*2);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(Vec3 v:terrain.column(sx+dx,sz+dz)){
            if(!terrain.edge(actual,v))continue;
            var key=Key.of(v);double cost=actual.distanceTo(v);costs.put(key,cost);parents.put(key,null);open.add(new Visit(key,cost,cost+heuristic(v)));
        }
        Key goal=null;int visited=0;
        while(!open.isEmpty()&&visited++<60000){
            Visit visit=open.remove();if(visit.cost()>costs.getOrDefault(visit.key(),Double.POSITIVE_INFINITY)+1e-7)continue;
            Key key=visit.key();Vec3 from=key.point();
            if(!rejectedGoals.contains(key)&&goalVisible(from)){goal=key;break;}
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                if(dx==0&&dz==0||Math.abs(dx)+Math.abs(dz)>2)continue;
                for(Vec3 to:terrain.column((int)Math.round(from.x*2)+dx,(int)Math.round(from.z*2)+dz)){
                    double rise=to.y-from.y;
                    if(rise>terrain.jumpHeight()+.001||rise< -3.0)continue;
                    if(Math.abs(dx)+Math.abs(dz)==2&&Math.abs(dx)==2&&Math.abs(rise)<.02)continue;
                    if(!terrain.edge(from,to))continue;
                    Key next=Key.of(to);double cost=visit.cost()+from.distanceTo(to)+(rise>.6?.6:0);
                    if(cost+1e-6>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                    costs.put(next,cost);parents.put(next,key);open.add(new Visit(next,cost,cost+heuristic(to)));
                }
            }
        }
        if(goal==null&&r.running){
            var nearby=new ArrayList<String>();
            for(int x=(target.getX()-3)*2;x<=(target.getX()+3)*2;x++)for(int z=(target.getZ()-4)*2;z<=(target.getZ()+4)*2;z++)
                for(Vec3 v:terrain.column(x,z))if(v.y>=target.getY()-5)nearby.add(v+" cost="+costs.get(Key.of(v))+" goal="+reached(v,2.95));
            r.record("NAV NO ROUTE surfaces="+nearby);
        }
        r.require(goal!=null,"没有安全路线: "+target+" 起点="+actual+" 已搜索="+visited);
        var points=new LinkedList<Vec3>();for(Key k=goal;k!=null;k=parents.get(k))points.addFirst(k.point());
        route=points;index=0;plans++;stalled=0;best=Double.POSITIVE_INFINITY;
        if(r.running)r.record("NAV PLAN target="+target+" start="+actual+" nodes="+route.size()+" searched="+visited+" route="+route);
    }
    boolean goalVisible(Vec3 point){
        if(!reached(point,3.1))return false;
        if(pickup)return true;
        // Reject sight lines that only graze a panel edge at an idealized coordinate.
        for(Vec3 d:List.of(new Vec3(.04,0,0),new Vec3(-.04,0,0),new Vec3(0,0,.04),new Vec3(0,0,-.04)))
            if(!reached(point.add(d),3.15))return false;
        return true;
    }

    static final class Terrain extends WalkNodeEvaluator {
        final Robot r;
        final Map<Long,List<Vec3>> columns=new HashMap<>();
        Terrain(Robot r){this.r=r;}
        AABB body(Vec3 p){double half=r.mc.player.getBbWidth()/2.0;return new AABB(p.x-half,p.y+.002,p.z-half,p.x+half,p.y+r.mc.player.getBbHeight()-.002,p.z+half);}
        boolean collision(AABB box){for(var shape:r.mc.level.getBlockCollisions(r.mc.player,box))if(!shape.isEmpty())return true;return false;}
        boolean supported(Vec3 p){double half=r.mc.player.getBbWidth()/2.0-1e-7;return collision(new AABB(p.x-half,p.y-.04,p.z-half,p.x+half,p.y+.001,p.z+half));}
        boolean clear(Vec3 p){
            AABB box=body(p);
            if(!r.inside(BlockPos.containing(p))||!r.mc.level.getWorldBorder().isWithinBounds(box)||collision(box))return false;
            for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(box.minX+1e-7,box.minY-.05,box.minZ+1e-7),BlockPos.containing(box.maxX-1e-7,box.maxY-1e-7,box.maxZ-1e-7))){
                if(!r.mc.level.hasChunkAt(b))return false;
                var fluid=r.mc.level.getFluidState(b);
                // Standing beside a bubble column (or above a waterlogged support)
                // does not immerse the player. Test the actual fluid volume.
                if(!fluid.isEmpty())for(AABB volume:fluid.getShape(r.mc.level,b).toAabbs())if(volume.move(b).intersects(box))return false;
                if(switch(getPathTypeFromState(r.mc.level,b)){
                    case FIRE,FIRE_IN_NEIGHBOR,DAMAGING,DAMAGING_IN_NEIGHBOR,DAMAGE_CAUTIOUS,POWDER_SNOW,ON_TOP_OF_POWDER_SNOW,STICKY_HONEY->true;default->false;})return false;
            }
            return true;
        }
        List<Vec3> column(int x,int z){
            long key=((long)x<<32)^(z&0xffffffffL);return columns.computeIfAbsent(key,k->{
                var result=new ArrayList<Vec3>(samples(x*.5,z*.5));
                var regularHeights=new HashSet<Double>();for(Vec3 p:result)regularHeights.add(p.y);
                // Keep normal terrain sparse; offset samples expose narrow ledges.
                for(double dx:new double[]{-.125,0,.125})for(double dz:new double[]{-.125,0,.125}){
                    if(dx==0&&dz==0)continue;
                    for(Vec3 p:samples(x*.5+dx,z*.5+dz))if(!regularHeights.contains(p.y))result.add(p);
                }
                return result;
            });
        }
        List<Vec3> samples(double px,double pz){
            var heights=new TreeSet<Double>();
            for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(px-.299,r.cfg.min().getY()-1,pz-.299),BlockPos.containing(px+.299,r.cfg.max().getY(),pz+.299))){
                if(!r.mc.level.hasChunkAt(b))continue;
                for(AABB shape:r.mc.level.getBlockState(b).getCollisionShape(r.mc.level,b).toAabbs()){
                    AABB a=shape.move(b);if(a.maxX>px-.299&&a.minX<px+.299&&a.maxZ>pz-.299&&a.minZ<pz+.299)heights.add(a.maxY);
                }
            }
            var result=new ArrayList<Vec3>();for(double y:heights){Vec3 p=new Vec3(px,y,pz);if(clear(p)&&supported(p))result.add(p);}return result;
        }

        double jumpHeight(){
            var p=r.mc.player;var effect=p.getEffect(MobEffects.JUMP_BOOST);
            double boost=effect!=null&&(effect.isInfiniteDuration()||effect.getDuration()>30)?p.getJumpBoostPower():0;
            double v=p.getAttributeValue(Attributes.JUMP_STRENGTH)+boost,h=0;
            for(int i=0;i<80&&v>0;i++){h+=v;v=(v-p.getGravity())*.98;}return h-.025;
        }
        boolean sweep(Vec3 a,Vec3 b,boolean support){
            int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.1));
            for(int i=0;i<=n;i++){Vec3 p=a.lerp(b,i/(double)n);if(!clear(p)||support&&!supported(p))return false;}return true;
        }
        boolean levelSegment(Vec3 a,Vec3 b){return Math.abs(a.y-b.y)<.02&&sweep(a,b,true);}
        boolean wideSegment(Vec3 a,Vec3 b){
            int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.2));
            for(int i=0;i<=n;i++){Vec3 p=a.lerp(b,i/(double)n);if(collision(body(p).inflate(.15,0,.15))||!supported(p))return false;}return true;
        }
        boolean edge(Vec3 a,Vec3 b){
            double rise=b.y-a.y;if(Math.abs(rise)<.02)return levelSegment(a,b);
            if(a.subtract(b).horizontalDistance()>1.05||rise>jumpHeight()||rise< -3.0)return false;
            if(rise>0){
                // A low ceiling may truncate a vanilla jump without preventing landing.
                // Require clearance above the destination, not the unobstructed full apex.
                double apex=rise<=r.mc.player.maxUpStep()?rise:rise+.05;
                Vec3 high=a.add(0,apex,0),over=new Vec3(b.x,high.y,b.z);
                return sweep(a,high,false)&&sweep(high,over,false)&&sweep(over,b,false);
            }
            Vec3 over=new Vec3(b.x,a.y,b.z);return sweep(a,over,false)&&sweep(over,b,false);
        }
    }
}
