package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import java.util.*;

public final class XmasLightsListener implements Listener {
    private final XmasLightsPlugin plugin;
    private final LightManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private record Pending(String world, Vector point, BlockVector anchor, Vector normal, UUID chain, boolean continued, String type, long expires) { }

    public XmasLightsListener(XmasLightsPlugin plugin, LightManager manager) { this.plugin = plugin; this.manager = manager; }
    public void clearPending() { pending.clear(); lastClick.clear(); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { manager.refreshLegacyKits(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = manager.getKitType(event.getItem());
        if (id == null) return;
        // Air clicks are often cancelled by vanilla. For block clicks respect protection plugins' DENY.
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && event.useInteractedBlock() == Event.Result.DENY) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("xmaslights.use")) { plugin.messages().send(player, "errors.no-permission"); return; }
        long now = System.nanoTime();
        Long previous = lastClick.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 100_000_000L) return;
        Pending old = pending.get(player.getUniqueId());
        if (old != null && now > old.expires) { pending.remove(player.getUniqueId()); old = null; }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (old != null) { pending.remove(player.getUniqueId()); plugin.messages().send(player, old.continued ? "placement.finished" : "placement.cancelled"); }
            else removeLookedAt(player);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (player.isSneaking() && old != null) { pending.remove(player.getUniqueId()); plugin.messages().send(player, old.continued ? "placement.finished" : "placement.cancelled"); }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        LightType type = manager.getType(id);
        if (type == null) { pending.remove(player.getUniqueId()); plugin.messages().send(player, "errors.type-missing"); return; }
        if (type.model() != null && plugin.usesModelPack() && !plugin.modelPackReady(player)) { plugin.messages().send(player, "pack.models-required"); return; }
        Block block = event.getClickedBlock();
        Vector point = type.wall() ? wallPoint(block, event.getBlockFace(), event.getInteractionPoint()) : anchorPoint(block, event.getBlockFace());
        Vector normal = event.getBlockFace().getDirection().normalize();
        BlockVector anchor = block.getLocation().toVector().toBlockVector();
        if (old == null || !old.world.equals(block.getWorld().getName()) || !old.type.equals(id)) { start(player, point, anchor, normal, type, now); return; }
        double distance = old.point.distance(point);
        if (distance < .5) { plugin.messages().send(player, "errors.points-close"); return; }
        if (distance > manager.getMaxLength()) {
            plugin.messages().send(player, "placement.too-far", "distance", (int)Math.ceil(distance), "max", manager.getMaxLength());
            start(player, point, anchor, normal, type, now); return;
        }
        Vector averaged = old.normal.clone().add(normal);
        if (averaged.lengthSquared() < 1e-6) averaged = old.normal.clone();
        averaged.normalize();
        LightString string;
        try {
            string = manager.create(player, old.point, point, old.anchor, anchor, type, averaged, old.chain, old.continued,
                    player.hasPermission("xmaslights.manage-groups") ? manager.getEditing(player.getUniqueId()) : null);
        } catch (OperationFailure failure) {
            plugin.messages().send(player, failure.getMessage()); return;
        } catch (RuntimeException failure) {
            plugin.messages().warn("log.render-failed", "id", player.getUniqueId(), "error", failure.getMessage());
            plugin.messages().send(player, "errors.render"); return;
        }
        if (string.refundable && event.getItem() != null) event.getItem().setAmount(event.getItem().getAmount() - 1);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1.4f);
        if (manager.isChaining(player.getUniqueId()) && event.getItem() != null && event.getItem().getAmount() > 0) {
            pending.put(player.getUniqueId(), new Pending(string.world, point, anchor, normal, string.chain, true, id, now + 300_000_000_000L));
            plugin.messages().send(player, "placement.continue", "group", string.group == null ? plugin.messages().text(player, "labels.none") : string.group);
        } else {
            pending.remove(player.getUniqueId());
            plugin.messages().send(player, "placement.placed", "group", string.group == null ? plugin.messages().text(player, "labels.none") : string.group);
        }
    }

    private void start(Player player, Vector point, BlockVector anchor, Vector normal, LightType type, long now) {
        pending.put(player.getUniqueId(), new Pending(player.getWorld().getName(), point, anchor, normal, null, false, type.id(), now + 300_000_000_000L));
        plugin.messages().send(player, "placement.first");
        player.spawnParticle(Particle.END_ROD, point.toLocation(player.getWorld()), 6, .05, .05, .05, .01);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1, 1.2f);
    }
    private void removeLookedAt(Player player) {
        LightString lookedAt = manager.findLookedAt(player, 8);
        if (lookedAt == null) { plugin.messages().send(player, "errors.look-at"); return; }
        List<LightString> removed = player.isSneaking() ? manager.getChain(lookedAt.chain) : List.of(lookedAt);
        if (removed.stream().anyMatch(string -> !manager.canManage(player, string))) { plugin.messages().send(player, "errors.not-owner"); return; }
        if (!(player.isSneaking() ? manager.removeChain(lookedAt.chain) > 0 : manager.remove(lookedAt))) { plugin.messages().send(player, "errors.save"); return; }
        manager.refund(player, removed);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1, 1.2f);
        plugin.messages().send(player, "commands.removed", "count", removed.size());
    }
    private static Vector anchorPoint(Block block, BlockFace face) {
        Vector center = block.getLocation().toVector().add(new Vector(.5, .5, .5));
        return center.add(Tag.FENCES.isTagged(block.getType()) || Tag.WALLS.isTagged(block.getType()) ? new Vector(0, .35, 0) : face.getDirection().multiply(.52));
    }
    private Vector wallPoint(Block block, BlockFace face, Location hit) {
        Vector base = block.getLocation().toVector();
        Vector local = hit == null ? new Vector(.5, .5, .5).add(face.getDirection().multiply(.5)) : hit.toVector().subtract(base);
        double x = snap(local.getX(), manager.getWallGrid()), y = snap(local.getY(), manager.getWallGrid()), z = snap(local.getZ(), manager.getWallGrid());
        switch (face) {
            case EAST -> x = 1; case WEST -> x = 0; case UP -> y = 1; case DOWN -> y = 0; case SOUTH -> z = 1; case NORTH -> z = 0;
            default -> { }
        }
        return base.add(new Vector(x, y, z));
    }
    private static double snap(double value, double grid) { return Math.clamp(grid <= 0 ? value : Math.round(value / grid) * grid, 0, 1); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { removeAnchored(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE); manager.backingChanged(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { manager.backingChanged(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> { event.getBlocks().forEach(manager::backingChanged); event.getBlocks().forEach(block -> manager.backingChanged(block.getRelative(event.getDirection()))); }, 3); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> { event.getBlocks().forEach(manager::backingChanged); event.getBlocks().forEach(block -> manager.backingChanged(block.getRelative(event.getDirection()))); }, 3); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakWithKit(BlockBreakEvent event) { if (manager.getKitType(event.getPlayer().getInventory().getItemInMainHand()) != null) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(block -> { removeAnchored(block, true); manager.backingChanged(block); }); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().forEach(block -> { removeAnchored(block, true); manager.backingChanged(block); }); }
    private void removeAnchored(Block block, boolean drops) {
        for (LightString string : manager.findByAnchor(block)) {
            LightType type = manager.getType(string.type);
            if (!manager.remove(string)) continue;
            if (drops && manager.isConsumeItem() && string.refundable && type != null) {
                string.refundable = false;
                block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), manager.createKit(type, 1, null));
            }
        }
    }
    @EventHandler public void onEntitiesLoad(EntitiesLoadEvent event) { event.getEntities().forEach(manager::register); }
    @EventHandler public void onEntitiesUnload(EntitiesUnloadEvent event) { event.getEntities().forEach(manager::unregister); }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, manager::onChunkLoad);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) { manager.onWorldUnload(event.getWorld()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); lastClick.remove(event.getPlayer().getUniqueId()); manager.forgetPlayer(event.getPlayer().getUniqueId()); }
}
