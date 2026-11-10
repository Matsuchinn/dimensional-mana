package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Bee;

/**
 * 秽蚀蜂的嗡鸣：链锯的低频抖动叠上指甲刮黑板的高频毛刺，贴着蜂飞。
 *
 * <p>原版客户端给每只蜜蜂挂一个 {@code BeeFlyingSoundInstance}，播放的音事件写死成
 * {@code BEE_LOOP}。我们在 {@link ClientModEvents} 里用 {@code PlaySoundEvent}
 * 把它整个换成本实例——换成自家的音事件，跟随、循环、音量都自己管。</p>
 */
public final class CorruptedBeeHumInstance extends AbstractTickableSoundInstance {
    private final Bee bee;
    /** 每只蜂的嗡鸣有固定的细微音高差，避免蜂群完全同相变成一台大马达。 */
    private final float basePitch;

    public CorruptedBeeHumInstance(Bee bee) {
        super(GeneratedMod.CORRUPTED_BEE_HUM.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.bee = bee;
        this.looping = true;
        this.delay = 0;
        this.basePitch = 0.92F + this.random.nextFloat() * 0.16F;
        this.x = bee.getX();
        this.y = bee.getY();
        this.z = bee.getZ();
    }

    @Override
    public void tick() {
        if (this.bee.isRemoved()) {
            this.stop();
            return;
        }
        this.x = this.bee.getX();
        this.y = this.bee.getY();
        this.z = this.bee.getZ();
        // 飞行才嗡嗡作响，落在花上时只剩很轻的振翅；发怒时音调略抬——r91 整体调轻：
        // 原来的响度贴近原版蜂群「背景噪音」定位的两倍还多，一群蜂同屏就像电锯车间。
        this.volume = this.bee.isFlying() ? 0.32F : 0.10F;
        this.pitch = this.bee.isAngry() ? this.basePitch * 1.06F : this.basePitch;
    }
}
