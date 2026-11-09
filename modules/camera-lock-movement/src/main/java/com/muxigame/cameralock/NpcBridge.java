package com.muxigame.cameralock;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** No linkage to NPC classes: absence of CustomNPCs is an ordinary supported configuration. */
public final class NpcBridge {
    public static int priority(LivingEntity entity, Player player) {
        if (entity.getType().toString().contains("customnpcs")) {
            try {
                Object faction = entity.getClass().getMethod("getFaction").invoke(entity);
                if ((boolean)faction.getClass().getMethod("isFriendlyToPlayer",Player.class).invoke(faction,player)) return 3;
                if ((boolean)faction.getClass().getMethod("isNeutralToPlayer",Player.class).invoke(faction,player)) return 2;
                return 0; // faction hostility also remains recognizable in creative mode
            } catch (ReflectiveOperationException | RuntimeException ex) {
                // Unknown NPC API: retain safe neutral classification, with current aggression honored.
                return entity instanceof Mob mob && mob.getTarget()==player ? 0 : 2;
            }
        }
        if (entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget()==player) return 1;
        return -1;
    }
}
