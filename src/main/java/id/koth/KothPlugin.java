package id.koth;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class KothPlugin extends JavaPlugin implements TabExecutor {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final List<String> SUBS =
            List.of("pos1", "pos2", "create", "delete", "list", "start", "stop", "status", "reload");

    private KothManager manager;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new KothManager(this);
        manager.load();
        var cmd = getCommand("koth");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("SimpleKoth aktif. Arena ke-load: " + manager.arenas().size());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stop(false);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("status")) {
            sender.sendMessage(manager.prefixed(manager.statusLine()));
            return true;
        }
        if (!sender.hasPermission("koth.admin")) {
            sender.sendMessage(manager.prefixed("<red>Lu gak punya izin buat command ini."));
            return true;
        }

        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.prefixed("<red>Command ini cuma bisa dipake player."));
                    return true;
                }
                Location l = p.getLocation().getBlock().getLocation();
                (sub.equals("pos1") ? pos1 : pos2).put(p.getUniqueId(), l);
                sender.sendMessage(manager.prefixed("Posisi <white>" + sub + "</white> di-set ke <white>"
                        + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + "</white>."));
            }
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.prefixed("<red>Command ini cuma bisa dipake player."));
                    return true;
                }
                if (args.length < 2 || !NAME.matcher(args[1]).matches()) {
                    sender.sendMessage(manager.prefixed("<red>Pake: /" + label
                            + " create <nama> (huruf/angka/_/-, maks 32 karakter)"));
                    return true;
                }
                Location a = pos1.get(p.getUniqueId());
                Location b = pos2.get(p.getUniqueId());
                if (a == null || b == null) {
                    sender.sendMessage(manager.prefixed("<red>Set dulu /" + label + " pos1 dan /" + label + " pos2."));
                    return true;
                }
                if (!a.getWorld().equals(b.getWorld())) {
                    sender.sendMessage(manager.prefixed("<red>pos1 dan pos2 harus di world yang sama."));
                    return true;
                }
                if (manager.addArena(KothArena.of(args[1], a, b))) {
                    sender.sendMessage(manager.prefixed("Arena <white>" + args[1] + "</white> dibuat."));
                } else {
                    sender.sendMessage(manager.prefixed("<red>Arena dengan nama itu udah ada."));
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(manager.prefixed("<red>Pake: /" + label + " delete <nama>"));
                    return true;
                }
                sender.sendMessage(manager.prefixed(manager.removeArena(args[1])
                        ? "Arena <white>" + args[1] + "</white> dihapus."
                        : "<red>Arena gak ketemu."));
            }
            case "list" -> {
                if (manager.arenas().isEmpty()) {
                    sender.sendMessage(manager.prefixed("Belum ada arena."));
                } else {
                    sender.sendMessage(manager.prefixed("Arena: <white>"
                            + String.join(", ", manager.arenas().values().stream().map(KothArena::name).toList())));
                }
            }
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(manager.prefixed("<red>Pake: /" + label + " start <nama>"));
                    return true;
                }
                KothArena arena = manager.arena(args[1]);
                if (arena == null) {
                    sender.sendMessage(manager.prefixed("<red>Arena gak ketemu."));
                } else if (!manager.start(arena)) {
                    sender.sendMessage(manager.prefixed("<red>Masih ada event KOTH yang jalan. /" + label + " stop dulu."));
                }
            }
            case "stop" -> {
                if (!manager.isRunning()) {
                    sender.sendMessage(manager.prefixed("<red>Gak ada event yang jalan."));
                } else {
                    manager.stop(true);
                }
            }
            case "reload" -> {
                reloadConfig();
                manager.load();
                sender.sendMessage(manager.prefixed("Config dan arena di-reload."));
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(manager.prefixed("Command: <white>/" + label
                + " pos1|pos2|create <nama>|delete <nama>|list|start <nama>|stop|status|reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))
                        && (s.equals("status") || sender.hasPermission("koth.admin"))) out.add(s);
            }
        } else if (args.length == 2 && sender.hasPermission("koth.admin")
                && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("delete"))) {
            for (KothArena a : manager.arenas().values()) {
                if (a.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(a.name());
            }
        }
        return out;
    }
}
