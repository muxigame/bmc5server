package noppes.npcs.mixin;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.MarkData;
import noppes.npcs.entity.data.IEntityMarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntity.class)
public abstract class EntityMarkDataMixin implements IEntityMarkData {
    @Unique private volatile MarkData cnpc$markData;
    @Override public MarkData cnpc$getMarkData() { return cnpc$markData; }
    @Override public void cnpc$setMarkData(MarkData data) { cnpc$markData = data; }
}
