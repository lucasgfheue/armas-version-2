package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
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
 * Tanque conducible: 5 asientos, sube escalones de 1 bloque, velocidad parecida a la de un cerdo.
 * W/S avanzan y retroceden, A/D giran, el cañón sigue la mirada del conductor y el clic izquierdo dispara.
 * Shift para bajarse.
 */
public class TankEntity extends Entity {
    public static final int MAX_SEATS = 5;

    /** Velocidad máxima en bloques por tick (0.2 = 4 bloques/s, como un cerdo con zanahoria). */
    private static final double MAX_SPEED = 0.2;
    private static final double REVERSE_FACTOR = 0.5;
    private static final double ACCELERATION = 0.015;
    private static final double BRAKING = 0.03;
    private static final float TURN_SPEED = 2.5f;
    private static final float MAX_HEALTH = 150.0f;
    private static final float RUN_OVER_DAMAGE = 10.0f;

    public static final int FIRE_COOLDOWN_TICKS = 40;
    /** Límites de elevación del cañón (pitch de la mirada: negativo = arriba). */
    public static final float MIN_PITCH = -45.0f;
    public static final float MAX_PITCH = 25.0f;

    /** Geometría del cañón (debe coincidir con {@link TankRenderer}), en bloques. */
    public static final double TURRET_FORWARD = 0.25;
    public static final double TURRET_HEIGHT = 1.0;
    public static final double BARREL_FORWARD = 0.8;
    public static final double BARREL_UP = 0.41;
    public static final double BARREL_LENGTH = 2.55;

    /** Asientos: {adelante, derecha} en bloques respecto al centro. El primero es el conductor. */
    private static final double[][] SEATS = {
            {1.55, -0.55}, {1.55, 0.55}, {-1.2, -0.55}, {-1.2, 0.55}, {-1.8, 0.0}
    };
    private static final double SEAT_HEIGHT = 0.9;

    private double speed = 0.0;
    private int fireCooldown = 0;
    private float health = MAX_HEALTH;
    private Vec3 lastPos = null;

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
        if (tag.contains("Vida")) {
            this.health = tag.getFloat("Vida");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Vida", this.health);
    }

    // ------------------------------------------------------------------ física

    @Override
    public void tick() {
        double moved = 0.0;
        if (this.lastPos != null) {
            moved = Math.hypot(this.getX() - this.lastPos.x, this.getZ() - this.lastPos.z);
        }
        super.tick();

        if (this.fireCooldown > 0) {
            this.fireCooldown--;
        }

        Entity driver = this.getControllingPassenger();
        boolean localControl = this.isControlledByLocalInstance();
        boolean physics = localControl || (!this.level().isClientSide && driver == null);

        if (this.level().isClientSide && !localControl) {
            this.tickLerp();
        } else {
            this.lerpSteps = 0;
        }

        if (physics) {
            float forward = 0.0f;
            float turn = 0.0f;
            if (this.level().isClientSide && driver instanceof Player player) {
                forward = player.zza;
                turn = player.xxa;
            }
            this.drive(forward, turn);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.horizontalCollision) {
                this.speed = 0.0;
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        if (!this.level().isClientSide && moved > 0.1) {
            this.runOver();
        }
        this.lastPos = this.position();
    }

    private void drive(float forward, float turn) {
        // A (xxa > 0) gira a la izquierda; marcha atrás invierte el giro, como un vehículo
        float steer = this.speed < -0.01 ? -turn : turn;
        this.setYRot(this.getYRot() - steer * TURN_SPEED);

        double target = forward > 0 ? MAX_SPEED : (forward < 0 ? -MAX_SPEED * REVERSE_FACTOR : 0.0);
        double rate = target == 0.0 ? BRAKING : ACCELERATION;
        if (this.speed < target) {
            this.speed = Math.min(target, this.speed + rate);
        } else if (this.speed > target) {
            this.speed = Math.max(target, this.speed - rate);
        }

        double yaw = Math.toRadians(this.getYRot());
        Vec3 current = this.getDeltaMovement();
        double dy = (current.y - 0.08) * 0.98;
        if (this.isInWater()) {
            dy = Math.min(current.y + 0.06, 0.12);
        }
        this.setDeltaMovement(-Math.sin(yaw) * this.speed, dy, Math.cos(yaw) * this.speed);
    }

    private void tickLerp() {
        if (this.lerpSteps > 0) {
            double nx = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
            double ny = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
            double nz = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
            float yaw = this.getYRot() + Mth.wrapDegrees(this.lerpYRot - this.getYRot()) / this.lerpSteps;
            this.lerpSteps--;
            this.setPos(nx, ny, nz);
            this.setRot(yaw, 0.0f);
        }
    }

    /** Interpolación de posición en los clientes que no conducen (sin @Override: depende de la versión). */
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = 10;
    }

    /** Sube escalones de un bloque (sin @Override: depende de la versión). */
    public float maxUpStep() {
        return 1.05f;
    }

    /** Arrolla a lo que se ponga delante cuando va rápido. */
    private void runOver() {
        AABB box = this.getBoundingBox().inflate(0.2, 0.0, 0.2);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.getVehicle() != this)) {
            target.hurt(this.damageSources().generic(), RUN_OVER_DAMAGE);
        }
    }

    // ------------------------------------------------------------------ cañón

    /** Disparo del cañón (lo llama el servidor cuando el conductor hace clic izquierdo). */
    public void fire(ServerPlayer player) {
        if (player != this.getControllingPassenger() || this.fireCooldown > 0) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();

        if (!player.getAbilities().instabuild) {
            ItemStack ammo = AmmoUtil.find(player, ModItems.OBUS.get());
            if (ammo.isEmpty()) {
                this.fireCooldown = 10;
                level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0f, 0.8f);
                player.displayClientMessage(Component.literal("Sin obuses"), true);
                return;
            }
            ammo.shrink(1);
        }
        this.fireCooldown = FIRE_COOLDOWN_TICKS;

        Vec3 dir = aimDirection(player);
        Vec3 muzzle = this.barrelPivot(dir).add(dir.scale(BARREL_LENGTH));

        TankShell shell = new TankShell(level, player);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.shoot(dir.x, dir.y, dir.z, 3.2f, 0.1f);
        level.addFreshEntity(shell);

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 4.0f, 0.5f);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DRAGON_FIREBALL_EXPLODE,
                SoundSource.PLAYERS, 2.0f, 1.3f);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 14, 0.25, 0.25, 0.25, 0.03);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.08);
    }

    /** Dirección del cañón: la mirada del conductor, con el cabeceo limitado. */
    public static Vec3 aimDirection(LivingEntity driver) {
        float pitch = Mth.clamp(driver.getXRot(), MIN_PITCH, MAX_PITCH);
        float yaw = driver.getYRot();
        float cp = Mth.cos(-pitch * ((float) Math.PI / 180F));
        float sp = Mth.sin(-pitch * ((float) Math.PI / 180F));
        float cy = Mth.cos(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
        float sy = Mth.sin(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
        return new Vec3(sy * -cp, sp, cy * -cp).normalize();
    }

    /** Punto donde pivota el cañón en el mundo. */
    private Vec3 barrelPivot(Vec3 dir) {
        double yaw = Math.toRadians(this.getYRot());
        Vec3 hullForward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 h = new Vec3(dir.x, 0.0, dir.z);
        h = h.lengthSqr() < 1.0E-6 ? hullForward : h.normalize();
        return this.position()
                .add(hullForward.scale(TURRET_FORWARD))
                .add(0.0, TURRET_HEIGHT + BARREL_UP, 0.0)
                .add(h.scale(BARREL_FORWARD));
    }

    // ------------------------------------------------------------------ pasajeros

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < MAX_SEATS;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        int idx = this.getPassengers().indexOf(passenger);
        if (idx < 0 || idx >= SEATS.length) {
            idx = 0;
        }
        double forward = SEATS[idx][0];
        double right = SEATS[idx][1];
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        callback.accept(passenger,
                this.getX() + fx * forward + rx * right,
                this.getY() + SEAT_HEIGHT,
                this.getZ() + fz * forward + rz * right);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            // Shift + clic derecho con el tanque vacío: lo recoges
            if (this.getPassengers().isEmpty()) {
                if (!this.level().isClientSide) {
                    if (!player.getAbilities().instabuild) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.TANQUE.get()));
                    }
                    this.discard();
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.startRiding(this)) {
            if (this.getControllingPassenger() == player) {
                player.displayClientMessage(
                        Component.literal("WASD: conducir · Clic izquierdo: disparar · Shift: bajar"), true);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ daño y colisión

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        if (this.level().isClientSide || this.isRemoved()) {
            return true;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker.getVehicle() == this) {
            return false;
        }
        if (attacker instanceof Player player && player.getAbilities().instabuild) {
            this.discard();
            return true;
        }
        this.health -= amount;
        if (this.health <= 0.0f) {
            this.destroy();
        }
        return true;
    }

    private void destroy() {
        this.ejectPassengers();
        this.level().explode(this, this.getX(), this.getY() + 1.0, this.getZ(), 2.0f,
                Level.ExplosionInteraction.NONE);
        this.spawnAtLocation(ModItems.TANQUE.get());
        this.discard();
    }

    /** Sin @Override: evita que los pasajeros sufran daño de caída. */
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(3.0, 1.0, 3.0);
    }
}
