package es.astralicastudio.xmaslights;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, cached block-surface probes. Display models are never ray targets. */
final class SurfaceLighting {
    record Hit(Location position, double distance, Vector normal) {
        Hit(Location position, double distance) { this(position, distance, new Vector(0, 1, 0)); }
    }
    private record Cached(Location emitter, Vector direction, Hit hit, long expires) { }
    private final Map<StrobeLightsBridge.Key, Cached> cache = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<StrobeLightsBridge.Key, Cached> entry) { return size() > 1024; }
    };
    private final double reach;
    private final int refreshTicks, probesPerUpdate;
    private long time;
    private int remaining;

    SurfaceLighting(double reach, int refreshTicks, int probesPerUpdate) {
        this.reach = reach; this.refreshTicks = refreshTicks; this.probesPerUpdate = probesPerUpdate;
    }
    void beginUpdate(int ticks) { time += ticks; remaining = probesPerUpdate; }
    Hit project(StrobeLightsBridge.Source source) {
        Cached previous = cache.get(source.key());
        boolean same = previous != null && previous.emitter.equals(source.location()) && previous.direction.equals(source.direction());
        if (same && time < previous.expires) return loaded(previous.hit) ? previous.hit : null;
        if (remaining-- <= 0) return same && loaded(previous.hit) ? previous.hit : null;
        Hit hit = trace(source.location(), source.direction(), reach);
        if (hit == null && source.direction().getY() > -.9) {
            Hit floor = trace(source.location(), new Vector(0, -1, 0), reach);
            if (floor != null) hit = floor;
        }
        int jitter = Math.floorMod(source.key().hashCode(), Math.max(1, refreshTicks));
        cache.put(source.key(), new Cached(source.location().clone(), source.direction().clone(), hit, time + refreshTicks + jitter));
        return hit;
    }
    private static boolean loaded(Hit hit) {
        return hit != null && hit.position.getWorld().isChunkLoaded(hit.position.getBlockX() >> 4, hit.position.getBlockZ() >> 4);
    }
    void clear() { cache.clear(); }

    /** Traverse only already-loaded voxels, then use the block's precise collision shape. */
    static Hit trace(Location emitter, Vector direction, double reach) {
        World world = emitter.getWorld();
        if (world == null || direction.lengthSquared() < 1e-9) return null;
        Vector ray = direction.clone().normalize();
        int x = emitter.getBlockX(), y = emitter.getBlockY(), z = emitter.getBlockZ();
        int sx = sign(ray.getX()), sy = sign(ray.getY()), sz = sign(ray.getZ());
        double dx = delta(ray.getX()), dy = delta(ray.getY()), dz = delta(ray.getZ());
        double tx = boundary(emitter.getX(), x, ray.getX()), ty = boundary(emitter.getY(), y, ray.getY()), tz = boundary(emitter.getZ(), z, ray.getZ());
        double travelled = 0;
        // At reach <= 16 a ray crosses at most 30 voxels; the cap also guards future changes.
        for (int visited = 0; visited < 64 && travelled <= reach; visited++) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return null;
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable() && !block.isLiquid()) {
                RayTraceResult collision = block.rayTrace(emitter, ray, reach, FluidCollisionMode.NEVER);
                if (collision != null && collision.getHitBlockFace() != null) {
                    double distance = collision.getHitPosition().distance(emitter.toVector());
                    if (distance < .06) return null;
                    Vector outside = collision.getHitBlockFace().getDirection().multiply(.04);
                    return new Hit(collision.getHitPosition().add(outside).toLocation(world), distance, collision.getHitBlockFace().getDirection());
                }
            }
            double next = Math.min(tx, Math.min(ty, tz));
            // Advance tied axes together; a corner is not a solid surface.
            if (tx <= next + 1e-9) { x += sx; tx += dx; }
            if (ty <= next + 1e-9) { y += sy; ty += dy; }
            if (tz <= next + 1e-9) { z += sz; tz += dz; }
            travelled = next;
        }
        return null;
    }
    private static int sign(double value) { return value > 0 ? 1 : value < 0 ? -1 : 0; }
    private static double delta(double value) { return value == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / value); }
    private static double boundary(double start, int cell, double direction) {
        return direction == 0 ? Double.POSITIVE_INFINITY : ((direction > 0 ? cell + 1 : cell) - start) / direction;
    }
}
