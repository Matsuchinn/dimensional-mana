package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** 仅改变本地画面：手边前摇保持饱满，自己的近景光层给准星留一个开口。 */
final class ClientSpellView {
    final Vec3 camera, right, up, forward;
    private final boolean firstPerson;
    private final Entity viewer;
    private final double handSide;
    boolean ownEffect;

    ClientSpellView(Camera view) {
        camera = view.getPosition();
        var r = view.getLeftVector();
        var u = view.getUpVector();
        var f = view.getLookVector();
        right = new Vec3(-r.x(), -r.y(), -r.z());
        up = new Vec3(u.x(), u.y(), u.z());
        forward = new Vec3(f.x(), f.y(), f.z());
        var mc = Minecraft.getInstance();
        firstPerson = mc.options.getCameraType().isFirstPerson();
        viewer = mc.getCameraEntity();
        handSide = mc.player != null && mc.player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    }

    void scope(int owner, int anchor) {
        ownEffect = firstPerson && viewer != null && (viewer.getId() == owner || viewer.getId() == anchor);
    }

    Vec3 hand(Vec3 fallback) {
        if (!ownEffect) return fallback;
        return camera.add(forward.scale(1.05)).add(right.scale(handSide * 0.66)).add(up.scale(-0.48));
    }

    Vec3 chargeOrb(Vec3 fallback) {
        if (!ownEffect) return fallback;
        return camera.add(forward.scale(2.4)).add(right.scale(handSide * 1.45)).add(up.scale(-0.85));
    }

    /** 中央椭圆只裁自己6格内的装饰层，侧下方手部、远处命中、其他玩家不受影响。 */
    double visibility(double x, double y, double z) {
        if (!ownEffect) return 1;
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        double depth = dx * forward.x + dy * forward.y + dz * forward.z;
        if (depth >= 6) return 1;
        if (depth <= 0.10) return 0;
        double sx = (dx * right.x + dy * right.y + dz * right.z) / depth;
        double sy = (dx * up.x + dy * up.y + dz * up.z) / depth;
        double ellipse = Math.sqrt(sx * sx / (0.30 * 0.30) + sy * sy / (0.24 * 0.24));
        double aperture = smooth((ellipse - 0.80) / 0.55);
        return aperture + (1 - aperture) * smooth((depth - 3.5) / 2.5);
    }

    double visibility(Vec3 at) { return visibility(at.x, at.y, at.z); }
    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
