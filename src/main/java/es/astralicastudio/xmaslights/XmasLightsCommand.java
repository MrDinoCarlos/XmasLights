package es.astralicastudio.xmaslights;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class XmasLightsCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("give", "remove", "spawn", "despawn", "regenerate", "on", "off", "mode", "group", "groups", "chain", "save", "types", "reload", "owner", "pack", "debug");
    private static final List<String> GROUP_SUBS = List.of("create", "edit", "add", "unassign", "info", "rename", "delete");
    private final XmasLightsPlugin plugin;
    private final LightManager manager;
    private final Messages messages;
    public XmasLightsCommand(XmasLightsPlugin plugin, LightManager manager) { this.plugin = plugin; this.manager = manager; messages = plugin.messages(); }

    private static String permission(String sub) {
        return switch (sub) {
            case "give", "reload", "save", "debug" -> "xmaslights.admin";
            case "group", "groups" -> "xmaslights.manage-groups";
            case "owner" -> "xmaslights.manage-all";
            default -> "xmaslights.use";
        };
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(permission(sub))) { messages.send(sender, "errors.no-permission"); return true; }
        try {
            switch (sub) {
                case "give" -> give(sender, args);
                case "remove" -> remove(sender, args);
                case "spawn", "despawn", "regenerate", "on", "off", "mode" -> modify(sender, sub, args);
                case "group" -> group(sender, args, label);
                case "groups" -> listGroups(sender);
                case "types" -> listTypes(sender);
                case "debug" -> {
                    Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null;
                    if (target == null) { messages.send(sender, "errors.player-missing"); break; }
                    for (String line : manager.rgbDiagnostic(target)) {
                        sender.sendMessage("[XmasLights] " + line);
                        if (sender instanceof Player) plugin.getLogger().info(line);
                    }
                }
                case "pack" -> { if (sender instanceof Player player) plugin.requestModelPack(player); else messages.send(sender, "errors.players-only"); }
                case "chain" -> {
                    if (sender instanceof Player player) messages.send(sender, manager.toggleChaining(player.getUniqueId()) ? "commands.chain-on" : "commands.chain-off");
                    else messages.send(sender, "errors.players-only");
                }
                case "save" -> messages.send(sender, manager.saveData() ? "commands.saved" : "errors.save");
                case "reload" -> {
                    try { plugin.reload(); messages.send(sender, "commands.reloaded"); }
                    catch (Exception failure) { messages.send(sender, "errors.reload", "error", plugin.diagnostic(failure)); }
                }
                case "owner" -> owner(sender, args);
                default -> help(sender, label, false);
            }
        } catch (RuntimeException failure) {
            messages.warn("log.command-failed", "error", failure.getMessage());
            messages.send(sender, "errors.command");
        }
        return true;
    }

    private List<LightString> targets(CommandSender sender, String target) {
        List<LightString> selected;
        if (target != null) {
            if (!sender.hasPermission("xmaslights.manage-all")) { messages.send(sender, "errors.no-permission"); return List.of(); }
            if (target.equalsIgnoreCase("all")) selected = new ArrayList<>(manager.getStrings());
            else if (manager.groupExists(target)) selected = manager.getGroup(target);
            else { messages.send(sender, "groups.missing", "group", target); return List.of(); }
        } else if (sender instanceof Player player) {
            LightString lookedAt = manager.findLookedAt(player, 8);
            if (lookedAt == null) { messages.send(sender, "errors.look-at"); return List.of(); }
            selected = manager.getChain(lookedAt.chain);
        } else { messages.send(sender, "errors.console-target"); return List.of(); }
        if (selected.isEmpty()) messages.send(sender, "errors.no-targets");
        else if (selected.stream().anyMatch(string -> !manager.canManage(sender, string))) { messages.send(sender, "errors.not-owner"); return List.of(); }
        return selected;
    }
    private void modify(CommandSender sender, String sub, String[] args) {
        AnimationMode mode = null;
        if (sub.equals("mode")) {
            mode = args.length >= 2 ? AnimationMode.parse(args[1]) : null;
            if (mode == null) { messages.send(sender, "usage.mode", "modes", String.join("|", modeIds())); return; }
        }
        if (sub.equals("spawn") && args.length < 2) { messages.send(sender, "usage.spawn"); return; }
        int offset = sub.equals("mode") ? 2 : 1;
        List<LightString> selected = targets(sender, args.length > offset ? args[offset] : null);
        if (selected.isEmpty()) return;
        int changed = 0;
        for (LightString string : selected) {
            String error = null;
            switch (sub) {
                case "spawn" -> { if (string.spawned) continue; error = manager.spawn(string); }
                case "despawn" -> { if (!manager.despawn(string)) continue; }
                case "regenerate" -> { if (!string.spawned) continue; error = manager.regenerate(string); }
                case "on", "off" -> manager.setOn(string, sub.equals("on"));
                case "mode" -> manager.setMode(string, mode);
                default -> throw new IllegalArgumentException(sub);
            }
            if (error != null) messages.send(sender, "errors.segment", "id", string.id, "reason", messages.text(sender, error));
            else changed++;
        }
        if (!manager.saveData()) { messages.send(sender, "errors.save"); return; }
        if (sub.equals("mode")) messages.send(sender, "commands.mode", "mode", messages.text(sender, "modes." + mode.id()), "count", changed);
        else messages.send(sender, "commands." + sub, "count", changed);
    }
    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) { messages.send(sender, "usage.give"); return; }
        LightType type = manager.getType(args[1]);
        if (type == null) { messages.send(sender, "errors.unknown-type", "types", String.join(", ", manager.getTypes().keySet())); return; }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException failure) { messages.send(sender, "errors.amount"); return; }
            if (amount < 1 || amount > 64) { messages.send(sender, "errors.amount"); return; }
        }
        Player recipient = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player player ? player : null;
        if (recipient == null) { messages.send(sender, args.length >= 4 ? "errors.player-missing" : "errors.console-player"); return; }
        manager.giveKits(recipient, type, amount);
        messages.send(sender, "commands.given", "count", amount, "type", typeName(sender, type), "player", recipient.getName());
    }
    private void remove(CommandSender sender, String[] args) {
        if (args.length > 1 && !args[1].equalsIgnoreCase("outline")) { messages.send(sender, "usage.remove"); return; }
        if (!(sender instanceof Player player)) { messages.send(sender, "errors.players-only"); return; }
        LightString lookedAt = manager.findLookedAt(player, 8);
        if (lookedAt == null) { messages.send(sender, "errors.look-at"); return; }
        List<LightString> removed = args.length >= 2 && args[1].equalsIgnoreCase("outline") ? manager.getChain(lookedAt.chain) : List.of(lookedAt);
        if (removed.stream().anyMatch(string -> !manager.canManage(sender, string))) { messages.send(sender, "errors.not-owner"); return; }
        if (!(removed.size() > 1 ? manager.removeChain(lookedAt.chain) > 0 : manager.remove(lookedAt))) { messages.send(sender, "errors.save"); return; }
        manager.refund(player, removed);
        messages.send(sender, "commands.removed", "count", removed.size());
    }

    private void group(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { help(sender, label, true); return; }
        String action = args[1].toLowerCase(Locale.ROOT), name = args.length >= 3 ? args[2] : null;
        if (action.equals("edit") && (name == null || name.equalsIgnoreCase("none"))) {
            if (sender instanceof Player player) { manager.setEditing(player.getUniqueId(), null); messages.send(sender, "groups.edit-stopped"); }
            else messages.send(sender, "errors.players-only");
            return;
        }
        if (!action.equals("unassign") && !LightManager.validGroup(name)) { messages.send(sender, "groups.invalid"); return; }
        switch (action) {
            case "create" -> messages.send(sender, manager.createGroup(name) ? "groups.created" : "groups.exists", "group", name);
            case "edit" -> {
                if (!(sender instanceof Player player)) { messages.send(sender, "errors.players-only"); return; }
                manager.setEditing(player.getUniqueId(), name); manager.saveData(); messages.send(sender, "groups.editing", "group", manager.getEditing(player.getUniqueId()));
            }
            case "add", "unassign" -> {
                List<LightString> selected = targets(sender, null); if (selected.isEmpty()) return;
                String group = action.equals("add") ? manager.canonicalGroup(name) : null;
                selected.forEach(string -> string.group = group); manager.saveData();
                messages.send(sender, action.equals("add") ? "groups.added" : "groups.unassigned", "count", selected.size());
            }
            case "info" -> {
                if (!manager.groupExists(name)) { messages.send(sender, "groups.missing", "group", name); return; }
                List<LightString> selected = manager.getGroup(name);
                messages.send(sender, "groups.info", "group", name, "count", selected.size(), "visible", selected.stream().filter(string -> string.spawned).count(),
                        "on", selected.stream().filter(string -> string.on).count(), "bulbs", selected.stream().mapToInt(string -> string.count).sum());
                String modes = String.join(", ", selected.stream().map(string -> messages.plain(sender, "modes." + string.mode.id())).distinct().toList());
                messages.send(sender, "groups.details", "types", String.join(", ", selected.stream().map(string -> string.type).distinct().toList()), "modes", modes,
                        "worlds", String.join(", ", selected.stream().map(string -> string.world).distinct().toList()));
            }
            case "rename" -> {
                if (!manager.groupExists(name)) { messages.send(sender, "groups.missing", "group", name); return; }
                if (args.length < 4 || !LightManager.validGroup(args[3])) { messages.send(sender, "usage.rename"); return; }
                messages.send(sender, manager.renameGroup(name, args[3]) ? "groups.renamed" : "groups.exists", "group", args[3]);
            }
            case "delete" -> {
                if (!sender.hasPermission("xmaslights.manage-all")) { messages.send(sender, "errors.no-permission"); return; }
                if (!manager.groupExists(name)) { messages.send(sender, "groups.missing", "group", name); return; }
                int count = manager.getGroup(name).size();
                if (count > 0 && (args.length < 4 || !args[3].equalsIgnoreCase("confirm"))) { messages.send(sender, "groups.confirm-delete", "group", name, "count", count); return; }
                if (manager.deleteGroup(name) < 0) messages.send(sender, "errors.save");
                else messages.send(sender, "groups.deleted", "group", name, "count", count);
            }
            default -> help(sender, label, true);
        }
    }
    private void owner(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "errors.players-only"); return; }
        if (args.length < 2) { messages.send(sender, "usage.owner"); return; }
        Player recipient = Bukkit.getPlayerExact(args[1]);
        if (recipient == null) { messages.send(sender, "errors.player-missing"); return; }
        LightString lookedAt = manager.findLookedAt(player, 8);
        if (lookedAt == null) { messages.send(sender, "errors.look-at"); return; }
        List<LightString> selected = manager.getChain(lookedAt.chain);
        selected.forEach(string -> string.owner = recipient.getUniqueId()); manager.saveData();
        messages.send(sender, "commands.owner", "count", selected.size(), "player", recipient.getName());
    }
    private void listGroups(CommandSender sender) {
        if (manager.getGroupNames().isEmpty()) { messages.send(sender, "groups.empty"); return; }
        messages.send(sender, "groups.title");
        for (String group : manager.getGroupNames()) {
            List<LightString> selected = manager.getGroup(group);
            messages.send(sender, "groups.entry", "group", group, "count", selected.size(), "visible", selected.stream().filter(string -> string.spawned).count());
        }
    }
    private net.kyori.adventure.text.Component typeName(CommandSender sender, LightType type) { return manager.typeName(sender, type); }
    private void listTypes(CommandSender sender) {
        messages.send(sender, "types.title");
        for (LightType type : manager.getTypes().values()) messages.send(sender, "types.entry", "id", type.id(), "style", messages.text(sender, type.wall() ? "labels.wall" : "labels.hanging"), "name", typeName(sender, type));
    }
    private void help(CommandSender sender, String label, boolean group) {
        messages.send(sender, group ? "groups.title" : "help.title");
        List<String> actions = group ? GROUP_SUBS : SUBS;
        for (String action : actions) if (group || sender.hasPermission(permission(action))) messages.send(sender, (group ? "help.group-" : "help.") + action, "label", label);
    }
    private static List<String> modeIds() { return Arrays.stream(AnimationMode.values()).map(AnimationMode::id).toList(); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return List.of();
        List<String> choices = new ArrayList<>();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) choices.addAll(SUBS.stream().filter(action -> sender.hasPermission(permission(action))).toList());
        else if (!sender.hasPermission(permission(sub))) return List.of();
        else if (args.length == 2) {
            switch (sub) {
                case "give" -> choices.addAll(manager.getTypes().keySet());
                case "mode" -> choices.addAll(modeIds());
                case "group" -> choices.addAll(GROUP_SUBS);
                case "remove" -> choices.add("outline");
                case "owner", "debug" -> Bukkit.getOnlinePlayers().forEach(player -> choices.add(player.getName()));
                case "spawn", "despawn", "regenerate", "on", "off" -> { if (sender.hasPermission("xmaslights.manage-all")) { choices.addAll(manager.getGroupNames()); choices.add("all"); } }
                default -> { }
            }
        } else if (args.length == 3) {
            switch (sub) {
                case "give" -> choices.addAll(List.of("1", "16", "64"));
                case "mode" -> { if (sender.hasPermission("xmaslights.manage-all")) { choices.addAll(manager.getGroupNames()); choices.add("all"); } }
                case "group" -> { choices.addAll(manager.getGroupNames()); if (args[1].equalsIgnoreCase("edit")) choices.add("none"); }
                default -> { }
            }
        } else if (args.length == 4) {
            if (sub.equals("give")) Bukkit.getOnlinePlayers().forEach(player -> choices.add(player.getName()));
            if (sub.equals("group") && args[1].equalsIgnoreCase("delete")) choices.add("confirm");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
