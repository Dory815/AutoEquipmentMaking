package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.*;

/** Vanilla A* search with player-sized, client-side movement edges. No proxy mob is spawned. */
class PlayerNavigation implements Robot.Step {
    private final Robot r;
    private final BlockPos target;
    private final boolean pickup;
    private final Terrain terrain;
    private List<BlockPos> route;
    private int index, stalled, replans, age;
    private double capability = Double.NaN, bestDistance = Double.POSITIVE_INFINITY;
    private Vec3 progressOrigin;
    // Package-visible counters are also useful to the isolated movement regression suite.
    int plans, jumps, movingTicks, centeringTicks;

    PlayerNavigation(Robot r, BlockPos target, boolean pickup) {
        this.r = r;
        this.target = target.immutable();
        this.pickup = pickup;
        terrain = new Terrain(r);
    }

    @Override public boolean tick() {
        var player = r.mc.player;
        r.require(r.inside(target), "寻路目标超出工坊范围");
        age++;
        if (player.onGround() && reached(player.position(), 3.4)) {
            r.release();
            return true;
        }
        double currentCapability = terrain.jumpHeight(terrain.playerNode());
        if (player.onGround() && (route == null || Math.abs(currentCapability - capability) > .01)) {
            plan(currentCapability);
        }
        if (route == null) { r.release(); return false; }

        // Consume nodes in this tick, without the old one-tick key release at every block.
        boolean centering = false;
        while (index < route.size() && near(terrain.feet(route.get(index)), .32)) {
            // A player near a turn can still be on the wrong side of a thin panel.
            // Keep this anchor until the actual position can reach its successor.
            if (player.onGround() && index + 1 < route.size()
                && terrain.transition(route.get(index), route.get(index + 1))
                && !canApproach(route.get(index + 1))) {
                centering = true;
                break;
            }
            index++;
            bestDistance = Double.POSITIVE_INFINITY;
            stalled = 0;
        }
        if (index >= route.size()) {
            r.release();
            if (player.onGround()) replan("nodes exhausted");
            return false;
        }
        BlockPos next = route.get(index);
        Vec3 destination = terrain.feet(next);
        if (player.onGround()) {
            BlockPos from = terrain.playerNode();
            // Smoothed routes start at the actual position, not the center of its containing cell.
            boolean clearEdge = canApproach(next);
            if (!terrain.standable(next) || !clearEdge) {
                r.release();
                replan("edge changed from=" + from + " next=" + next);
                return false;
            }
            // Only skip nodes on a supported, collision-free level segment.
            int end = Math.min(route.size() - 1, index + 5);
            for (int i = end; i > index; i--) {
                Vec3 candidate = terrain.feet(route.get(i));
                boolean flat = Math.abs(candidate.y - player.getY()) < .05;
                for (int j = index; flat && j <= i; j++)
                    flat = Math.abs(terrain.feet(route.get(j)).y - candidate.y) < .05;
                if (flat && terrain.levelSegment(player.position(), candidate)) {
                    index = i; next = route.get(i); destination = candidate;
                    break;
                }
            }
        }

        Vec3 delta = destination.subtract(player.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float desiredYaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float turn = Mth.wrapDegrees(desiredYaw - player.getYRot());
        player.setYRot(player.getYRot() + Mth.clamp(turn, -20, 20));
        player.setXRot(player.getXRot() + Mth.clamp(10 - player.getXRot(), -4, 4));
        boolean aligned = Math.abs(Mth.wrapDegrees(desiredYaw - player.getYRot())) < 25;
        boolean forward = aligned && horizontal > (centering ? .02 : .17);
        r.navigating = true;
        // Use vanilla sprint input so hunger, item use and collisions retain their normal rules.
        r.sprintKey(forward);
        if (!forward) player.setSprinting(false);
        r.mc.options.keyUp.setDown(forward);
        // Jump only for a verified rising edge. Collision by itself never requests a jump.
        boolean jump = forward && player.onGround() && delta.y > player.maxUpStep() + .02
            && horizontal < 1.45;
        r.mc.options.keyJump.setDown(jump);
        if (jump) jumps++;
        if (forward) movingTicks++;
        if (centering) centeringTicks++;

        double distance = player.position().distanceTo(destination);
        if (distance + .05 < bestDistance) { bestDistance = distance; stalled = 0; }
        else stalled++;
        if (progressOrigin == null || player.position().distanceTo(progressOrigin) > 1.5) {
            progressOrigin = player.position(); replans = 0;
        }
        if (stalled >= 45 && player.onGround()) { r.release(); replan("no progress"); }
        r.require(age < 2200, "寻路超时，停止移动");
        return false;
    }

    private boolean near(Vec3 point, double radius) {
        return Math.abs(r.mc.player.getY() - point.y) < .15
            && r.mc.player.position().subtract(point).horizontalDistance() < radius;
    }

    private boolean canApproach(BlockPos point) {
        Vec3 destination = terrain.feet(point);
        return terrain.standable(point) && (Math.abs(destination.y - r.mc.player.getY()) < .05
            ? terrain.levelSegment(r.mc.player.position(), destination) : terrain.transition(terrain.playerNode(), point));
    }

    private boolean reached(Vec3 feet, double range) {
        if (pickup) return feet.distanceTo(terrain.feet(target)) < .5;
        AABB body = terrain.body(feet);
        if (body.maxX > target.getX() && body.minX < target.getX() + 1
            && body.maxZ > target.getZ() && body.minZ < target.getZ() + 1) return false;
        Vec3 eye = feet.add(0, r.mc.player.getEyeHeight(), 0);
        Vec3 aim = Vec3.atCenterOf(target);
        if (eye.distanceTo(aim) >= range) return false;
        var hit = r.mc.level.clip(new ClipContext(eye, aim, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, r.mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private void replan(String reason) {
        if (r.task != null && r.running) r.record("NAV REPLAN " + reason + " target=" + target
            + " position=" + r.mc.player.position() + " route=" + route);
        r.require(++replans <= 3, "移动受阻，重新寻路后仍不可达");
        route = null; stalled = 0; bestDistance = Double.POSITIVE_INFINITY;
    }

    private void plan(double jumpHeight) {
        BlockPos start = terrain.playerNode();
        if (!terrain.standable(start)) {
            Vec3 actual = r.mc.player.position();
            boolean clear = terrain.clear(actual), supported = terrain.supported(actual);
            if (r.task != null && r.running) r.record("NAV START CHECK position=" + actual + " node=" + start
                + " floor=" + terrain.feet(start) + " clear=" + clear + " supported=" + supported);
            r.require(clear && supported, "玩家不在安全站立面上；请移到平整空地，取消旧任务后重新开始");
            BlockPos anchor = null;
            double distance = Double.POSITIVE_INFINITY;
            for (BlockPos p : BlockPos.betweenClosed(start.offset(-1, -1, -1), start.offset(1, 1, 1))) {
                if (!terrain.standable(p) || !terrain.levelSegment(actual, terrain.feet(p))) continue;
                double nextDistance = actual.distanceToSqr(terrain.feet(p));
                if (nextDistance < distance) { anchor = p.immutable(); distance = nextDistance; }
            }
            r.require(anchor != null, "当前位置没有可通行的安全站立点；请移到平整空地后重新开始");
            start = anchor;
        }
        var goals = new LinkedHashSet<BlockPos>();
        if (pickup) { if (terrain.standable(target)) goals.add(target); }
        else for (BlockPos p : BlockPos.betweenClosed(target.offset(-4, -4, -4), target.offset(4, 3, 4))) {
            if (terrain.standable(p) && reached(terrain.feet(p), 3.0)) goals.add(p.immutable());
        }
        r.require(!goals.isEmpty(), "目标附近没有可交互的安全站立点: " + target);
        var evaluator = new PlayerNodes(terrain, start);
        var region = new PathNavigationRegion(r.mc.level, r.cfg.min().offset(-1, -1, -1), r.cfg.max().offset(1, 4, 1));
        // PathFinder only passes Mob to prepare(); our evaluator uses the actual LocalPlayer.
        var path = new PathFinder(evaluator, 30000).findPath(region, null, goals, 512, 0, 1);
        r.require(path != null && path.canReach(), "没有安全路线: " + target);
        route = new ArrayList<>();
        for (int i = 0; i < path.getNodeCount(); i++) route.add(path.getNodePos(i));
        index = 0; capability = jumpHeight; bestDistance = Double.POSITIVE_INFINITY; stalled = 0; plans++;
    }

    static final class PlayerNodes extends NodeEvaluator {
        private final Terrain terrain;
        private final BlockPos start;
        PlayerNodes(Terrain terrain, BlockPos start) { this.terrain = terrain; this.start = start; }
        @Override public void prepare(PathNavigationRegion region, Mob unused) { nodes.clear(); }
        @Override public void done() { nodes.clear(); }
        @Override public Node getStart() { return getNode(start); }
        @Override public Target getTarget(double x, double y, double z) { return new Target(Mth.floor(x), Mth.floor(y), Mth.floor(z)); }
        @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) { return getPathType(context, x, y, z); }
        @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) { return terrain.standable(new BlockPos(x, y, z)) ? PathType.WALKABLE : PathType.BLOCKED; }
        @Override public int getNeighbors(Node[] result, Node node) {
            int count = 0;
            BlockPos from = node.asBlockPos();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy : new int[]{0, 1, 2, -1}) {
                    BlockPos to = from.offset(dx, dy, dz);
                    if (terrain.standable(to) && terrain.transition(from, to)) {
                        Node next = getNode(to);
                        if (!next.closed) { next.type = PathType.WALKABLE; next.costMalus = dy > 0 ? .5f * dy : 0; result[count++] = next; }
                        break;
                    }
                }
            }
            return count;
        }
    }

    /** Collision-shape checks also account for slabs, stairs, player width and headroom. */
    static final class Terrain extends WalkNodeEvaluator {
        private final Robot r;
        Terrain(Robot r) { this.r = r; }
        BlockPos playerNode() { return BlockPos.containing(r.mc.player.getX(), Math.ceil(r.mc.player.getY() - .05), r.mc.player.getZ()); }
        Vec3 feet(BlockPos p) { return new Vec3(p.getX() + .5, getFloorLevel(r.mc.level, p), p.getZ() + .5); }
        AABB body(Vec3 p) {
            double half = r.mc.player.getBbWidth() / 2.0;
            return new AABB(p.x - half, p.y + .001, p.z - half, p.x + half, p.y + r.mc.player.getBbHeight(), p.z + half);
        }
        boolean standable(BlockPos p) {
            if (!r.inside(p) || !r.mc.level.hasChunkAt(p) || !r.mc.level.hasChunkAt(p.below())) return false;
            Vec3 feet = feet(p);
            // Use the same height-to-node rule as playerNode, including thin floors.
            if ((int)Math.ceil(feet.y - .05) != p.getY()) return false;
            return clear(feet) && supported(feet);
        }
        boolean supported(Vec3 feet) {
            return !r.mc.level.noCollision(r.mc.player, body(feet).move(0, -.06, 0));
        }
        boolean clear(Vec3 feet) {
            AABB box = body(feet);
            if (!r.mc.level.getWorldBorder().isWithinBounds(box) || !r.mc.level.noCollision(r.mc.player, box)) return false;
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - .1, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (!r.mc.level.hasChunkAt(p) || !r.mc.level.getFluidState(p).isEmpty()) return false;
                PathType type = getPathTypeFromState(r.mc.level, p);
                if (switch (type) {
                    case LAVA, WATER, FIRE, FIRE_IN_NEIGHBOR, DAMAGING, DAMAGING_IN_NEIGHBOR,
                        DAMAGE_CAUTIOUS, POWDER_SNOW, ON_TOP_OF_POWDER_SNOW, FENCE, STICKY_HONEY -> true;
                    default -> false;
                }) return false;
            }
            return true;
        }
        double jumpHeight(BlockPos from) { return jumpHeight(from, true); }
        private double jumpHeight(BlockPos from, boolean durable) {
            var player = r.mc.player;
            var effect = player.getEffect(MobEffects.JUMP_BOOST);
            // Do not commit a jump that depends on an effect about to expire.
            double boost = effect != null && (!durable || effect.isInfiniteDuration() || effect.getDuration() > 30) ? player.getJumpBoostPower() : 0;
            double factor = r.mc.level.getBlockState(from.below()).getBlock().getJumpFactor();
            double velocity = player.getAttributeValue(Attributes.JUMP_STRENGTH) * factor + boost;
            double height = 0;
            double gravity = player.getGravity();
            if (gravity <= 0) return 0; // Unsupported custom physics: fail closed.
            for (int i = 0; i < 80 && velocity > 0; i++) { height += velocity; velocity = (velocity - gravity) * .98; }
            return velocity > 0 ? 0 : height;
        }
        boolean sweep(Vec3 a, Vec3 b, boolean support) {
            int steps = Math.max(1, (int)Math.ceil(a.distanceTo(b) / .2));
            for (int i = 0; i <= steps; i++) {
                Vec3 p = a.lerp(b, i / (double)steps);
                if (!r.inside(BlockPos.containing(p.x, Math.ceil(p.y - .05), p.z)) || !clear(p) || (support && !supported(p))) return false;
            }
            return true;
        }
        boolean levelSegment(Vec3 a, Vec3 b) { return Math.abs(a.y - b.y) < .05 && sweep(a, b, true); }
        boolean transition(BlockPos from, BlockPos to) {
            Vec3 a = feet(from), b = feet(to);
            double rise = b.y - a.y;
            if (Math.abs(rise) < .05) return levelSegment(a, b);
            if (Math.abs(from.getX() - to.getX()) + Math.abs(from.getZ() - to.getZ()) != 1) return false;
            if (rise > 0) {
                boolean step = rise <= r.mc.player.maxUpStep();
                if (!step && (rise + .03 > jumpHeight(from) || rise > 2.01)) return false;
                // Clearance uses the full current arc, even if its effect expires soon.
                double apex = step ? rise : jumpHeight(from, false) + .02;
                Vec3 high = a.add(0, apex, 0), across = new Vec3(b.x, high.y, b.z);
                return sweep(a, high, false) && sweep(high, across, false) && sweep(across, b, false);
            }
            if (rise < -1.01) return false;
            Vec3 over = new Vec3(b.x, a.y, b.z);
            return sweep(a, over, false) && sweep(over, b, false);
        }
    }
}
