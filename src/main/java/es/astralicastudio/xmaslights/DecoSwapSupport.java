package es.astralicastudio.xmaslights;

import com.google.gson.Gson;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional protocol 1. Only Bukkit/JDK types cross the plugin class-loader boundary. */
public final class DecoSwapSupport {
    private static final Gson JSON = new Gson();
    private static final NamespacedKey DEPLOYMENT = key("decoswap:deployment_id"), DECORATION = key("decoswap:decoration_id"),
            SPAWNED = key("decoswap:spawned"), LOCAL = key("decoswap:local_entity_id");
    private static final NamespacedKey COPY = key("xmaslights:deco_copy");
    private final XmasLightsPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private record Pending(int parts, boolean modelPack) { }
    private record State(int protocol, UUID source, UUID chain, UUID owner, String type, String mode, String group,
                         boolean on, boolean straight, boolean skipStart, boolean modelPack, double sag, int count,
                         int parts, String role, double[] a, double[] b, double[] normal, double[] anchorA, double[] anchorB) { }

    DecoSwapSupport(XmasLightsPlugin plugin) { this.plugin = plugin; }
    public int protocolVersion() { return 1; }
    private LightManager manager() { return plugin.manager(); }
    private static NamespacedKey key(String id) { return Objects.requireNonNull(NamespacedKey.fromString(id)); }
    private void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bukkit primary thread required"); }
    private IllegalStateException error(String key) { return new IllegalStateException(plugin.messages().plain(key)); }
    private static List<Entity> parts(LightString string) {
        List<Entity> result = new ArrayList<>(string.wires); result.addAll(string.bulbs.values()); return result;
    }

    /** Selecting one visible part selects its entire segment, never an unrelated connected segment. */
    public Set<UUID> expandSelection(Collection<UUID> selection, Player player) {
        thread(); Set<UUID> result = new LinkedHashSet<>(selection); Set<UUID> visited = new HashSet<>(); boolean upgraded = false;
        for (UUID id : selection) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) continue;
            LightString string = manager().byEntity(entity);
            if (string == null) {
                if (entity.getPersistentDataContainer().has(key("xmaslights:string"))) throw error("decoswap.incomplete");
                continue;
            }
            if (!visited.add(string.id)) continue;
            if (player != null && !manager().canManage(player, string)) throw error("errors.not-owner");
            List<Entity> all = parts(string);
            LightType type = manager().getType(string.type);
            if (type == null) throw error("errors.type-missing");
            int expected = manager().importCost(string);
            if (!string.spawned || all.size() != expected || all.stream().anyMatch(e -> !e.isValid())) throw error("decoswap.incomplete");
            if (all.stream().anyMatch(e -> !e.getPersistentDataContainer().has(manager().keyPart))) {
                // Assign stable part roles to pre-2.7 geometry before DecoSwap builds its UUID list.
                String problem = manager().regenerate(string); if (problem != null) throw error(problem);
                all.forEach(e -> result.remove(e.getUniqueId())); all = parts(string); upgraded = true;
            }
            for (Entity part : all) result.add(part.getUniqueId());
        }
        if (upgraded) save();
        return result;
    }

    public Map<String, String> capture(Entity entity, Location anchor) {
        thread(); LightString string = manager().byEntity(entity);
        if (string == null || anchor.getWorld() != entity.getWorld()) throw error("decoswap.incomplete");
        String role = entity.getPersistentDataContainer().get(manager().keyPart, PersistentDataType.STRING);
        if (role == null) { // Upgrade existing 2.6 entities on first capture, without respawning them.
            role = "legacy:" + entity.getUniqueId();
            entity.getPersistentDataContainer().set(manager().keyPart, PersistentDataType.STRING, role);
        }
        State state = new State(1, string.id, string.chain, string.owner, string.type, string.mode.id(), string.group,
                string.on, string.straight, string.skipStart, plugin.usesModelPack(), string.sag, string.count,
                parts(string).size(), role, relative(string.a, anchor.toVector()), relative(string.b, anchor.toVector()),
                array(string.normal), relative(blockVector(string.anchorA), floor(anchor)), relative(blockVector(string.anchorB), floor(anchor)));
        var content = JSON.toJsonTree(state).getAsJsonObject();
        for (String field : List.of("source", "chain", "owner", "modelPack")) content.remove(field);
        return Map.of("state", JSON.toJson(state), "content", JSON.toJson(content), "role", role);
    }

    /** Validates every member before DecoSwap starts writing blocks/entities. */
    public void validateDeployment(List<byte[]> encoded, Location target, int rotation, UUID actor) {
        thread(); Map<UUID, State> states = new LinkedHashMap<>(); Map<UUID, Set<String>> roles = new HashMap<>();
        for (byte[] bytes : encoded) {
            State state = read(bytes);
            State previous = states.putIfAbsent(state.source, state);
            if (previous != null && !sameSegment(previous, state)) throw error("decoswap.invalid");
            if (!roles.computeIfAbsent(state.source, ignored -> new HashSet<>()).add(state.role)) throw error("decoswap.invalid");
        }
        List<LightString> imported = new ArrayList<>();
        for (State state : states.values()) {
            if (roles.get(state.source).size() != state.parts) throw error("decoswap.incomplete");
            LightString string = restored(state, target, rotation, UUID.randomUUID(), actor);
            String error = manager().importError(string);
            if (error != null) throw error(error);
            imported.add(string);
        }
        String limitError = manager().importLimitError(imported);
        if (limitError != null) throw error(limitError);
    }

    public void prepare(byte[] encoded, Location target, int rotation, UUID decoration, UUID deployment, UUID actor) {
        thread(); State state = read(encoded);
        UUID id = copyId(deployment, state.source);
        LightString string = manager().byId(id);
        if (string == null) {
            string = restored(state, target, rotation, deployment, actor);
            string.decoDeployment = deployment; string.decoDecoration = decoration;
            manager().insertImported(string); pending.put(id, new Pending(state.parts, state.modelPack));
        }
        if (!deployment.equals(string.decoDeployment)) throw error("decoswap.invalid");
    }
    public void adopt(Entity entity, byte[] encoded, Location target, int rotation, UUID decoration, UUID deployment, UUID actor, int localId) {
        thread(); State state = read(encoded);
        if (!(entity instanceof Display display)) throw error("decoswap.invalid");
        prepare(encoded, target, rotation, decoration, deployment, actor);
        LightString string = manager().byId(copyId(deployment, state.source));
        if (!deployment.equals(string.decoDeployment) || string.decoParts.putIfAbsent(state.role, localId) != null) throw error("decoswap.invalid");
        entity.getPersistentDataContainer().set(manager().keyPart, PersistentDataType.STRING, state.role);
        manager().adoptImported(display, string);
    }

    public void finishDeployment(UUID deployment) {
        thread();
        for (LightString string : manager().getStrings()) if (deployment.equals(string.decoDeployment)) {
            Pending imported = pending.get(string.id);
            if (imported == null) continue;
            if (parts(string).size() != imported.parts) throw error("decoswap.incomplete");
            if (imported.modelPack != plugin.usesModelPack() || manager().importCount(string) != string.count) {
                String problem = manager().regenerate(string); if (problem != null) throw error(problem);
            }
            manager().finishImported(string);
        }
        save();
        pending.keySet().removeIf(id -> { LightString string = manager().byId(id); return string == null || deployment.equals(string.decoDeployment); });
    }

    public void removeSelected(Collection<UUID> selection, Player player) {
        thread(); Set<LightString> selected = new LinkedHashSet<>();
        for (UUID id : expandSelection(selection, player)) { Entity entity = Bukkit.getEntity(id); if (entity != null) { LightString string = manager().byEntity(entity); if (string != null) selected.add(string); } }
        if (!selected.isEmpty() && !manager().removeAll(selected)) throw error("errors.save");
    }
    /** Restore/rollback also works when its original player is offline. No kits are refunded for copies. */
    public void releaseDeployment(UUID deployment) {
        thread(); List<LightString> selected = manager().getStrings().stream().filter(s -> deployment.equals(s.decoDeployment)).toList();
        if (!selected.isEmpty() && !manager().removeAll(selected)) throw error("errors.save");
        selected.forEach(s -> pending.remove(s.id));
    }
    public void detachDeployment(UUID deployment) {
        thread();
        for (LightString string : manager().getStrings()) if (deployment.equals(string.decoDeployment)) {
            string.decoDeployment = null; string.decoDecoration = null; string.decoParts.clear();
            for (Entity part : parts(string)) clearStamp(part);
        }
        save();
    }
    /** Own native LIGHT blocks are runtime effects, never permanent decoration blocks. */
    public String originalBlock(Block block) { thread(); Material original = manager().originalLight(block); return original == null ? null : original.createBlockData().getAsString(); }

    void stamp(Entity entity, LightString string) {
        if (string.decoDeployment == null) return;
        var data = entity.getPersistentDataContainer();
        data.set(COPY, PersistentDataType.BYTE, (byte)1);
        data.set(DEPLOYMENT, PersistentDataType.STRING, string.decoDeployment.toString());
        data.set(DECORATION, PersistentDataType.STRING, string.decoDecoration.toString()); data.set(SPAWNED, PersistentDataType.BYTE, (byte)1);
        Integer local = string.decoParts.get(data.get(manager().keyPart, PersistentDataType.STRING));
        if (local != null) data.set(LOCAL, PersistentDataType.INTEGER, local);
    }
    void loaded(Entity entity, LightString string) {
        var data = entity.getPersistentDataContainer();
        if (string.decoDeployment == null) { if (data.has(COPY)) clearStamp(entity); return; }
        String role = data.get(manager().keyPart, PersistentDataType.STRING); Integer local = data.get(LOCAL, PersistentDataType.INTEGER);
        if (role != null && local != null) string.decoParts.put(role, local);
        stamp(entity, string);
    }
    private static void clearStamp(Entity entity) {
        for (NamespacedKey key : List.of(DEPLOYMENT, DECORATION, SPAWNED, LOCAL, COPY)) entity.getPersistentDataContainer().remove(key);
    }
    void geometryChanged(LightString string) {
        if (string.decoDeployment == null) return;
        Set<Integer> managed = new HashSet<>(string.decoParts.values());
        for (LightString other : manager().getStrings()) if (string.decoDeployment.equals(other.decoDeployment)) managed.addAll(other.decoParts.values());
        Object result = deco("syncExternalDeployment", new Class<?>[]{UUID.class, Set.class}, string.decoDeployment, managed);
        if (Boolean.TRUE.equals(result)) {
            for (Entity entity : parts(string)) {
                var data = entity.getPersistentDataContainer();
                String role = data.get(manager().keyPart, PersistentDataType.STRING); Integer id = data.get(LOCAL, PersistentDataType.INTEGER);
                if (role != null && id != null) string.decoParts.put(role, id);
            }
        }
    }
    void reconcileOnStartup() {
        Map<UUID, Object> states = new HashMap<>();
        for (LightString string : manager().getStrings()) if (string.decoDeployment != null && !states.containsKey(string.decoDeployment))
            states.put(string.decoDeployment, deco("externalDeploymentState", new Class<?>[]{UUID.class}, string.decoDeployment));
        for (var entry : states.entrySet()) if ("RETAINED".equals(entry.getValue())) detachDeployment(entry.getKey());
        List<LightString> closed = manager().getStrings().stream().filter(s -> s.decoDeployment != null)
                .filter(s -> "REMOVED".equals(states.get(s.decoDeployment))).toList();
        if (!closed.isEmpty() && !manager().removeAll(closed)) throw error("errors.save");
    }
    private Object deco(String method, Class<?>[] parameters, Object... arguments) {
        var host = Bukkit.getPluginManager().getPlugin("DecoSwap");
        if (host == null || !host.isEnabled()) return null;
        try { return host.getClass().getMethod(method, parameters).invoke(host, arguments); }
        catch (NoSuchMethodException oldVersion) { return null; }
        catch (InvocationTargetException failure) { throw new IllegalStateException(plugin.messages().plain("decoswap.journal"), failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(plugin.messages().plain("decoswap.journal"), failure); }
    }
    private void save() { if (!manager().saveData()) throw error("errors.save"); }
    private State read(byte[] bytes) {
        try {
            if (bytes == null || bytes.length < 2 || bytes.length > 16_384) throw error("decoswap.invalid");
            State state = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), State.class);
            if (state == null || state.protocol != 1 || state.source == null || state.chain == null || state.type == null || state.type.length() > 80
                    || state.parts < 1 || state.parts > 1024 || state.count < 1 || state.count > 256 || state.role == null || state.role.length() > 80
                    || !state.role.matches("[a-zA-Z0-9:_-]+") || AnimationMode.parse(state.mode) == null || state.group != null && !LightManager.validGroup(state.group)
                    || !Double.isFinite(state.sag) || state.sag < 0 || state.sag > 256) throw error("decoswap.invalid");
            for (double[] vector : List.of(state.a, state.b, state.normal, state.anchorA, state.anchorB)) vec(vector);
            if (vec(state.normal).lengthSquared() > 1.001 || vec(state.a).distance(vec(state.b)) > 256) throw error("decoswap.invalid");
            if (manager().getType(state.type) == null) throw error("errors.type-missing");
            return state;
        } catch (RuntimeException failure) { throw error("decoswap.invalid"); }
    }
    private static boolean sameSegment(State a, State b) {
        var first = JSON.toJsonTree(a).getAsJsonObject(); var second = JSON.toJsonTree(b).getAsJsonObject();
        first.remove("role"); second.remove("role"); return first.equals(second);
    }
    private LightString restored(State state, Location target, int rotation, UUID deployment, UUID actor) {
        if (target.getWorld() == null || rotation % 90 != 0) throw error("decoswap.invalid");
        Vector base = target.toVector(), block = floor(target);
        LightString string = new LightString(copyId(deployment, state.source), target.getWorld().getName(), rotate(vec(state.a), rotation).add(base),
                rotate(vec(state.b), rotation).add(base), rotate(vec(state.normal), rotation), rotate(vec(state.anchorA), rotation).add(block).toBlockVector(),
                rotate(vec(state.anchorB), rotation).add(block).toBlockVector(), state.sag, state.type, AnimationMode.parse(state.mode));
        string.chain = copyId(deployment, state.chain); string.owner = actor == null ? state.owner : actor; string.refundable = false;
        string.group = state.group; string.on = state.on; string.straight = state.straight; string.skipStart = state.skipStart; string.count = state.count; string.importBudget = state.parts;
        return string;
    }
    private static UUID copyId(UUID deployment, UUID source) { return UUID.nameUUIDFromBytes((deployment + ":" + source).getBytes(StandardCharsets.UTF_8)); }
    private static double[] array(Vector vector) { return new double[]{vector.getX(), vector.getY(), vector.getZ()}; }
    private static Vector floor(Location location) { return new Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private static Vector blockVector(Vector vector) { return new Vector(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ()); }
    private static double[] relative(Vector vector, Vector anchor) { return array(vector.clone().subtract(anchor)); }
    private static Vector vec(double[] array) {
        if (array == null || array.length != 3) throw new IllegalArgumentException("vector");
        for (double value : array) if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new IllegalArgumentException("vector");
        return new Vector(array[0], array[1], array[2]);
    }
    private static Vector rotate(Vector vector, int rotation) {
        return switch (Math.floorMod(rotation, 360)) { case 0 -> vector; case 90 -> new Vector(-vector.getZ(), vector.getY(), vector.getX());
            case 180 -> new Vector(-vector.getX(), vector.getY(), -vector.getZ()); case 270 -> new Vector(vector.getZ(), vector.getY(), -vector.getX()); default -> throw new IllegalArgumentException("rotation"); };
    }
}
