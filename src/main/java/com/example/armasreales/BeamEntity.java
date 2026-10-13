package com.example.armasreales;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entidad invisible que representa un rayo activo. El servidor sólo le dice su longitud;
 * el cliente (BeamRenderer) lo dibuja saliendo del arma del dueño hacia donde mira.
 * Se elimina sola cuando el jugador deja de usar el arma.
 */
public class BeamEntity extends Entity {
    public static final int STYLE_LASER = 0;
    public static final int STYLE_PLASMA = 1;

    private static final Map<UUID, BeamEntity> ACTIVE = new HashMap<>();

    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STYLE =
            SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.INT);

    public BeamEntity(EntityType<? extends BeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Crea o actualiza el rayo del jugador (llamar cada tick mientras dispara). */
    public static void update(ServerLevel level, Player player, int style, float length) {
        BeamEntity beam = ACTIVE.get(player.getUUID());
        if (beam == null || beam.isRemoved() || beam.level() != level) {
            beam = new BeamEntity(ModEntities.BEAM.get(), level);
            beam.setOwnerId(player.getId());
            beam.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(beam);
            ACTIVE.put(player.getUUID(), beam);
        }
        beam.setStyle(style);
        beam.setLength(length);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        Entity owner = this.level().getEntity(this.getOwnerId());
        boolean valid = owner instanceof net.minecraft.server.level.ServerPlayer player
                && GunControl.isActive(player);
        if (!valid) {
            ACTIVE.values().removeIf(b -> b == this);
            this.discard();
            return;
        }
        this.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(LENGTH, 0.0f);
        builder.define(STYLE, STYLE_LASER);
    }

    public int getOwnerId() { return this.entityData.get(OWNER_ID); }
    public void setOwnerId(int id) { this.entityData.set(OWNER_ID, id); }
    public float getLength() { return this.entityData.get(LENGTH); }
    public void setLength(float length) { this.entityData.set(LENGTH, length); }
    public int getStyle() { return this.entityData.get(STYLE); }
    public void setStyle(int style) { this.entityData.set(STYLE, style); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
