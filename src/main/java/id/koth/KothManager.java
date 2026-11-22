package id.koth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menyimpan arena dan menjalankan logika game KOTH.
 *
 * Aturan capture:
 *  - Cuma 1 pemain di zona  -> timer capture jalan.
 *  - Lebih dari 1 pemain    -> contested, timer capture pause (kalau capturer masih di dalam).
 *  - Capturer keluar zona   -> timer reset.
 */
public final class KothManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KothPlugin plugin;
    private final File arenaFile;
    private final Map<String, KothArena> arenas = new LinkedHashMap<>();

    private KothArena active;
    private UUID capturer;
    private String capturerName = "";
    private int progress;
    private int elapsed;
    private BukkitTask task;

    public KothManager(KothPlugin plugin) {
        this.plugin = plugin;
        this.arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
    }

    // ---------------------------------------------------------------- arena storage

    public void load() {
        arenas.clear();
        if (!arenaFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(arenaFile);
        ConfigurationSection sec = yml.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(key);
            if (a == null) continue;
            arenas.put(key.toLowerCase(), new KothArena(key, a.getString("world", "world"),
                    a.getInt("min-x"), a.getInt("min-y"), a.getInt("min-z"),
                    a.getInt("max-x"), a.getInt("max-y"), a.getInt("max-z")));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (KothArena a : arenas.values()) {
            String p = "arenas." + a.name() + ".";
            yml.set(p + "world", a.world());
            yml.set(p + "min-x", a.minX());
            yml.set(p + "min-y", a.minY());
            yml.set(p + "min-z", a.minZ());
            yml.set(p + "max-x", a.maxX());
            yml.set(p + "max-y", a.maxY());
            yml.set(p + "max-z", a.maxZ());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(arenaFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal nyimpen arenas.yml: " + e.getMessage());
        }
    }

    public Map<String, KothArena> arenas() { return arenas; }

    public KothArena arena(String name) { return arenas.get(name.toLowerCase()); }

    public boolean addArena(KothArena arena) {
        if (arenas.containsKey(arena.name().toLowerCase())) return false;
        arenas.put(arena.name().toLowerCase(), arena);
        save();
        return true;
    }

    public boolean removeArena(String name) {
        if (active != null && active.name().equalsIgnoreCase(name)) stop(false);
        boolean removed = arenas.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    // ---------------------------------------------------------------- game state

    public boolean isRunning() { return active != null; }

    public KothArena activeArena() { return active; }

    public String statusLine() {
        if (active == null) return "Lagi gak ada event KOTH yang jalan.";
        int need = captureTime();
        if (capturer == null) {
            return "KOTH <white>" + active.name() + "</white> lagi jalan. Belum ada yang nge-capture.";
        }
        return "KOTH <white>" + active.name() + "</white> | capturer: <white>" + capturerName
                + "</white> | " + fmt(progress) + " / " + fmt(need);
    }

    public boolean start(KothArena arena) {
        if (active != null) return false;
        active = arena;
        capturer = null;
        capturerName = "";
        progress = 0;
        elapsed = 0;
        announce("Event KOTH <white>" + arena.name() + "</white> dimulai! Kuasai zona selama <white>"
                + fmt(captureTime()) + "</white> buat menang. Koordinat: <white>" + arena.world() + " "
                + arena.minX() + "," + arena.minY() + "," + arena.minZ() + " -> "
                + arena.maxX() + "," + arena.maxY() + "," + arena.maxZ() + "</white>");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stop(boolean announceStop) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (active != null && announceStop) {
            announce("Event KOTH <white>" + active.name() + "</white> dihentikan.");
        }
        active = null;
        capturer = null;
        capturerName = "";
        progress = 0;
        elapsed = 0;
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        if (active == null) return;
        elapsed++;

        List<Player> inside = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (active.contains(p.getLocation())) inside.add(p);
        }

        boolean contested = false;
        if (inside.isEmpty()) {
            if (capturer != null) {
                announce("<white>" + capturerName + "</white> keluar dari zona, capture di-reset.");
            }
            capturer = null;
            capturerName = "";
            progress = 0;
        } else if (inside.size() == 1) {
            Player p = inside.get(0);
            if (!p.getUniqueId().equals(capturer)) {
                capturer = p.getUniqueId();
                capturerName = p.getName();
                progress = 0;
                announce("<white>" + capturerName + "</white> mulai nge-capture KOTH <white>"
                        + active.name() + "</white>!");
            }
            progress++;
        } else {
            contested = true;
            boolean capturerInside = false;
            for (Player p : inside) {
                if (p.getUniqueId().equals(capturer)) {
                    capturerInside = true;
                    break;
                }
            }
            if (!capturerInside) {
                capturer = null;
                capturerName = "";
                progress = 0;
            }
        }

        int need = captureTime();

        // action bar buat yang ada di zona
        for (Player p : inside) {
            String text;
            if (contested) {
                text = "<red>CONTESTED</red> <gray>- timer di-pause (" + fmt(progress) + " / " + fmt(need) + ")";
            } else {
                text = "<green>Capturing</green> <gray>" + fmt(progress) + " / " + fmt(need);
            }
            p.sendActionBar(MM.deserialize(text));
        }

        // menang
        if (capturer != null && progress >= need) {
            win();
            return;
        }

        // broadcast progress berkala
        int interval = Math.max(5, plugin.getConfig().getInt("broadcast-interval-seconds", 30));
        if (capturer != null && !contested && progress > 0 && progress % interval == 0) {
            announce("<white>" + capturerName + "</white> lagi nge-capture <white>" + active.name()
                    + "</white>: <white>" + fmt(progress) + " / " + fmt(need) + "</white>");
        }

        // timeout
        int maxDur = plugin.getConfig().getInt("max-duration-seconds", 1800);
        if (maxDur > 0 && elapsed >= maxDur) {
            announce("Waktu habis! Gak ada yang menang di KOTH <white>" + active.name() + "</white>.");
            stop(false);
        }
    }

    private void win() {
        String arenaName = active.name();
        String winnerName = capturerName;
        Player winner = capturer == null ? null : Bukkit.getPlayer(capturer);

        announce("<white>" + winnerName + "</white> berhasil menguasai KOTH <white>" + arenaName
                + "</white> dan menang!");

        if (winner != null) {
            FileConfiguration cfg = plugin.getConfig();
            for (String cmd : cfg.getStringList("rewards")) {
                String run = cmd.replace("%player%", winner.getName()).replace("%arena%", arenaName);
                if (run.startsWith("/")) run = run.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
            }
        }
        stop(false);
    }

    // ---------------------------------------------------------------- util

    private int captureTime() {
        return Math.max(5, plugin.getConfig().getInt("capture-time-seconds", 300));
    }

    private static String fmt(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public void announce(String miniMessage) {
        Bukkit.broadcast(prefixed(miniMessage));
    }

    public Component prefixed(String miniMessage) {
        return MM.deserialize(plugin.getConfig().getString("prefix", "<gold><bold>[KOTH]</bold></gold> <gray>")
                + miniMessage);
    }
}
