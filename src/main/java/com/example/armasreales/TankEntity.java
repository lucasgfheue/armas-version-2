package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque: 5 plazas (el primero en subir conduce y apunta el cañón), sube escalones de 1 bloque
 * y va a velocidad de cerdo. W/S avanzan, A/D giran el casco; el cañón sigue hacia donde mira el
 * conductor. Clic izquierdo = disparar (gasta un obús). Se recoge pegándole sin pasajeros.
 */
public class TankEntity extends Entity {
    /** Velocidad máxima en bloques/tick (0,2 = 4 bloques/s, como un cerdo con zanahoria). */
    private static final double MAX_SPEED = 0.20;
    private static final float TURN_SPEED = 3.0f;
    private static final int FIRE_COOLDOWN_TICKS = 40;
    public static final int SEATS = 5;
    /** Asientos: {lateral, adelante}. El primero es el del conductor. */
    private static final double[][] SEAT_OFFSETS = {
            {0.0, 1.05}, {-0.45, -0.45}, {0.45, -0.45}, {-0.45, -1.0}, {0.45, -1.0}
    };

    private double speed = 0.0;
    private int fireCooldown = 0;
    /** Último ángulo del cañón (para dejarlo quieto cuando nadie lo maneja). Solo cliente. */
    public float restTurretYaw = Float.NaN;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Sube escalones de un bloque de alto. */
    @Override
    public float maxUpStep() {
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fireCooldown > 0) {
            this.fireCooldown--;
        }

        Entity driver = this.getControllingPassenger();
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            if (driver instanceof LivingEntity livingDriver) {
                this.control(livingDriver);
            } else {
                this.speed *= 0.8;
            }
            this.applyMovement();
        } else if (this.level().isClientSide) {
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

    private void control(LivingEntity driver) {
        // A/D: giran el casco (xxa > 0 = izquierda).
        this.setYRot(this.getYRot() - driver.xxa * TURN_SPEED);
        float forward = driver.zza;
        double target = forward > 0 ? MAX_SPEED : (forward < 0 ? -MAX_SPEED * 0.5 : 0.0);
        double accel = target == 0.0 ? 0.12 : 0.07;
        this.speed += (target - this.speed) * accel;
        if (Math.abs(this.speed) < 0.002) {
            this.speed = 0.0;
        }
    }

    private void applyMovement() {
        double yaw = Math.toRadians(this.getYRot());
        double vx = -Math.sin(yaw) * this.speed;
        double vz = Math.cos(yaw) * this.speed;
        double vy = this.onGround() ? -0.08 : (this.getDeltaMovement().y - 0.08) * 0.98;
        if (this.isInWater()) {
            vy = Math.max(vy, -0.05);
        }
        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.horizontalCollision) {
            this.speed *= 0.6;
        }
    }

    /** Disparo del cañón (lo llama el servidor cuando el conductor hace clic). */
    public void fire(ServerPlayer player) {
        if (this.fireCooldown > 0 || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, ModItems.OBUS_TANQUE.get());
        if (!creative && ammo.isEmpty()) {
            level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.NEUTRAL, 1.0f, 1.0f);
            this.fireCooldown = 10;
            return;
        }

        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(this.getYRot());
        Vec3 muzzle = this.position()
                .add(0.0, 1.15, 0.0)
                .add(-Math.sin(yaw) * 0.35, 0.0, Math.cos(yaw) * 0.35)
                .add(look.scale(1.9));

        TankShell shell = new TankShell(level, player);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.shoot(look.x, look.y, look.z, 3.0f, 0.1f);
        level.addFreshEntity(shell);

        if (!creative) {
            ammo.shrink(1);
        }
        this.fireCooldown = FIRE_COOLDOWN_TICKS;

        level.playSound(null, this.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.NEUTRAL, 3.0f, 0.6f);
        level.playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 2.0f, 0.5f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.03);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.05);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
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
        return this.getPassengers().size() < SEATS;
    }

    /** El primer pasajero (si es jugador) conduce. */
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        int idx = Math.max(0, Math.min(SEATS - 1, this.getPassengers().indexOf(passenger)));
        double side = SEAT_OFFSETS[idx][0];
        double fwd = SEAT_OFFSETS[idx][1];
        double yaw = Math.toRadians(this.getYRot());
        double x = -Math.sin(yaw) * fwd + Math.cos(yaw) * side;
        double z = Math.cos(yaw) * fwd + Math.sin(yaw) * side;
        callback.accept(passenger, this.getX() + x, this.getY() + 0.8, this.getZ() + z);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(1.8, 1.0, 1.8);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !(source.getEntity() instanceof Player);
    }

    /** Pegarle (sin pasajeros) lo recoge como ítem. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.level().isClientSide) {
            return true;
        }
        if (source.getEntity() instanceof Player player && !this.isVehicle()) {
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(new ItemStack(ModItems.TANQUE.get()));
            }
            this.discard();
        }
        return true;
    }
}
