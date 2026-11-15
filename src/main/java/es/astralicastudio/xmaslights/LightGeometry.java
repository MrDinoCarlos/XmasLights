package es.astralicastudio.xmaslights;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/** Shared, deterministic placement for displays, native illumination, RGB and previews. */
final class LightGeometry {
    private LightGeometry() { }
    static int wireSegments(double sag, double precision) {
        return sag <= 1e-9 ? 1 : Math.max(2, Math.min(32, (int)Math.ceil(Math.sqrt(sag / precision))));
    }
    static Vector bulbPoint(LightString string, LightType type, int index) {
        Vector point = string.point(type.wall() && !type.garland() ? (double)index / Math.max(1, string.count - 1) : (index + .5) / string.count);
        if (type.wall()) point.add(string.normal.clone().multiply(type.size() * (type.garland() ? .30 : .5) + .01));
        return point;
    }
    static Vector lightPoint(LightString string, LightType type, int index) {
        Vector point = bulbCenter(string, type, index);
        if (type.garland()) return point;
        if (type.modelLamp()) {
            if (!(type.wall() && type.chain() == 0)) point.subtract(new Vector(0, type.size() * (type.model() == GarlandModel.BULB ? .0625 : .09375), 0));
        }
        else if (!type.bulbs().isEmpty() && type.bulbs().get(Math.floorMod(index, type.bulbs().size())) instanceof org.bukkit.block.data.type.Lantern lantern) {
            boolean hanging = type.wall() ? type.chain() > 0 || string.normal.getY() < -.5 : type.chain() > 0 || lantern.isHanging();
            point.subtract(new Vector(0, type.size() * (hanging ? .21875 : .28125), 0));
        }
        return type.wall() ? point.add(string.normal.clone().multiply(.05)) : point;
    }
    static Vector bulbCenter(LightString string, LightType type, int index) {
        Vector point = bulbPoint(string, type, index);
        if (type.garland()) return point;
        if (type.wall() && type.chain() == 0) {
            // Native lantern geometry sits below the block centre. Align its
            // luminous body with the cable, just like the centred pack bulbs.
            if (!type.bulbs().isEmpty() && type.bulbs().get(Math.floorMod(index, type.bulbs().size())) instanceof org.bukkit.block.data.type.Lantern)
                point.add(new Vector(0, type.size() * (string.normal.getY() < -.5 ? .21875 : .28125), 0));
            return point;
        }
        // Pack bulb sockets end at model Y=14 (0.375 above centre); cubes at Y=16.
        double top = type.model() == GarlandModel.BULB ? .375 : .5;
        return point.subtract(new Vector(0, type.chain() + type.size() * top, 0));
    }
    record Module(Vector center, Quaternionf rotation, float length) { }
    static Module module(LightString string, LightType type, int index) {
        double start = (double)index / string.count;
        double end = (index + 1.0) / string.count;
        Vector direction = string.point(end).subtract(string.point(start));
        var x = direction.toVector3f().normalize();
        var z = type.wall() && string.normal.lengthSquared() > 1e-6 ? string.normal.toVector3f() : null;
        Quaternionf rotation;
        if (z != null && Math.abs(z.dot(x)) < .99f) {
            z.sub(new org.joml.Vector3f(x).mul(z.dot(x))).normalize();
            var y = new org.joml.Vector3f(z).cross(x).normalize();
            rotation = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(x, y, z));
        } else {
            // Stable up direction for hanging garlands; reversing endpoints does not hide their front.
            if (Math.abs(x.x) >= Math.abs(x.z) ? x.x < 0 : x.z < 0) x.negate();
            var y = new org.joml.Vector3f(0, 1, 0).sub(new org.joml.Vector3f(x).mul(x.y));
            if (y.lengthSquared() < 1e-6) y.set(0, 0, 1);
            y.normalize(); z = new org.joml.Vector3f(x).cross(y).normalize();
            rotation = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(x, y, z));
        }
        return new Module(bulbPoint(string, type, index), rotation, (float)direction.length() + .035f);
    }
    static double curveLength(LightString string) {
        double length = 0; Vector previous = string.a;
        for (int i = 1; i <= 16; i++) { Vector next = string.point(i / 16.0); length += previous.distance(next); previous = next; }
        return length;
    }
    static org.joml.Vector3f vanillaLedOffset(int index, float size) {
        double phase = index * Math.PI / 2;
        return new org.joml.Vector3f(0, (float)Math.cos(phase) * .22f * size, (float)Math.sin(phase) * .22f * size);
    }
    static int sourceStride(double minimumSpacing, double spacing, int paletteSize) {
        int stride = Math.max(1, (int)Math.ceil(minimumSpacing / Math.max(.01, spacing)));
        // Do not permanently lose green/blue by sampling every second bulb in a four-color pattern.
        while (gcd(stride, Math.max(1, paletteSize)) != 1) stride++;
        return stride;
    }
    private static int gcd(int a, int b) { while (b != 0) { int next = a % b; a = b; b = next; } return a; }
}
