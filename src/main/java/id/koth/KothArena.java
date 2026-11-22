package id.koth;

import org.bukkit.Location;

/**
 * Area KOTH berbentuk kotak (cuboid) yang disimpan sebagai koordinat blok min/max.
 */
public record KothArena(String name, String world,
                        int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ) {

    public static KothArena of(String name, Location a, Location b) {
        return new KothArena(name, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
