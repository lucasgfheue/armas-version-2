package com.example.armasreales.train;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Un coche del tren (locomotora o vagón). No usa física: el {@link TrainManager} lo coloca cada
 * tick siguiendo el trazado de las vías. Se pueden subir hasta 4 pasajeros con clic derecho.
 */
public class TrainCar extends Entity {
    public static final int LOCOMOTIVE = 0;
    public static final int WAGON = 1;

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(TrainCar.class, EntityDataSerializers.INT);

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    public TrainCar(EntityType<? extends TrainCar> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public void setKind(int kind) {
        this.entityData.set(KIND, kind);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, WAGON);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                double nx = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
                double ny = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
                double nz = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
                float yaw = this.getYRot() + Mth.wrapDegrees(this.lerpYRot - this.getYRot()) / this.lerpSteps;
                this.lerpSteps--;
                this.setPos(nx, ny, nz);
                this.setRot(yaw, 0.0f);
            } else {
                this.reapplyPosition();
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = steps + 2;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 4;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        int idx = Math.max(0, this.getPassengers().indexOf(passenger));
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double offset = (idx - 1.0) * 1.3;
        callback.accept(passenger, this.getX() + fx * offset, this.getY() + 0.6, this.getZ() + fz * offset);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(3.2, 0.4, 3.2);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }
}
