package es.astralicastudio.xmaslights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

public final class LightString {
    final UUID id;
    final String world;
    final Vector a;
    final Vector b;
    final Vector normal;
    final BlockVector anchorA;
    final BlockVector anchorB;
    double sag;
    boolean straight;
    String type;
    AnimationMode mode;
    String group;
    UUID owner;
    boolean refundable;
    UUID chain;
    UUID decoDeployment;
    UUID decoDecoration;
    final Map<String, Integer> decoParts = new HashMap<>();
    boolean on = true;
    int count;
    int importBudget;
    long switchedAt = -1000000L;
    boolean spawned = true;
    boolean skipStart;
    int generation;
    String signature;
    boolean idle;
    boolean lastForcedOff;
    int rgbStride;
    double rgbClearance;
    final List<BlockVector> lightBlocks = new ArrayList<BlockVector>();
    final Map<Integer, BlockVector> bulbLights = new HashMap<Integer, BlockVector>();
    final Map<Integer, Display> bulbs = new HashMap<Integer, Display>();
    final Map<Integer, org.bukkit.entity.ItemDisplay> garlandBodies = new HashMap<>();
    final Set<Entity> wires = new HashSet<Entity>();
    final Map<Integer, Integer> shownVariant = new HashMap<Integer, Integer>();

    LightString(UUID id, String world, Vector a, Vector b, Vector normal, BlockVector anchorA, BlockVector anchorB, double sag, String type, AnimationMode mode) {
        this.id = id;
        this.world = world;
        this.a = a;
        this.b = b;
        this.normal = normal;
        this.anchorA = anchorA;
        this.anchorB = anchorB;
        this.sag = sag;
        this.type = type;
        this.mode = mode;
        this.chain = id;
    }

    public Vector point(double fraction) {
        Vector point = this.a.clone().add(this.b.clone().subtract(this.a).multiply(fraction));
        if (this.sag != 0.0) {
            point.setY(point.getY() - this.sag * 4.0 * fraction * (1.0 - fraction));
        }
        return point;
    }

    public double length() {
        return this.a.distance(this.b);
    }

    public boolean isAnchoredTo(Block block) {
        if (!block.getWorld().getName().equals(this.world)) {
            return false;
        }
        return LightString.same(this.anchorA, block) || LightString.same(this.anchorB, block);
    }

    private static boolean same(BlockVector position, Block block) {
        return position.getBlockX() == block.getX() && position.getBlockY() == block.getY() && position.getBlockZ() == block.getZ();
    }
}

