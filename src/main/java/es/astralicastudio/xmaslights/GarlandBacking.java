package es.astralicastudio.xmaslights;

import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

/** Full opaque support is checked at centre and all four corners, never through unloaded chunks. */
final class GarlandBacking {
    private GarlandBacking() { }
    static int hiddenSide(World world, LightGeometry.Module module, float size) {
        if (covered(world, module, size, -1)) return -1;
        return covered(world, module, size, 1) ? 1 : 0;
    }
    private static boolean covered(World world, LightGeometry.Module module, float size, int side) {
        for (int sample = 0; sample < 5; sample++) {
            float x = sample == 0 ? 0 : (sample % 2 == 0 ? 1 : -1) * module.length() * .46f;
            float y = sample == 0 ? 0 : (sample <= 2 ? -1 : 1) * .29f * size;
            var local = module.rotation().transform(new Vector3f(x, y, side * (.32f * size + .025f)));
            Vector point = module.center().clone().add(new Vector(local.x, local.y, local.z));
            if (!world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)
                    || !world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()).getType().isOccluding()) return false;
        }
        return true;
    }
}
