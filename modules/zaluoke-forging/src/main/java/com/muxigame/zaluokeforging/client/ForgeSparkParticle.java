package com.muxigame.zaluokeforging.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;

/** Tiny, short-lived golden flecks; independent of vanilla wax particle lifetime. */
public final class ForgeSparkParticle extends TextureSheetParticle {
    public ForgeSparkParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites) {
        super(level,x,y,z,0,0,0);
        xd=dx; yd=dy+.025; zd=dz;
        lifetime=5+random.nextInt(3); quadSize=.012F+random.nextFloat()*.008F;
        gravity=.35F; friction=.8F; setColor(1F,.78F,.25F); pickSprite(sprites);
    }
    @Override public void tick() { super.tick(); alpha=Math.max(0,1F-(float)age/lifetime); }
    @Override public int getLightColor(float partial) { return 15728880; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
