package com.example.armasreales.train;

import com.example.armasreales.ModEntities;
import com.example.armasreales.rail.RailData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hace pasar un tren cada 10 minutos (12000 ticks) por la estación: llega por una de las cuatro
 * vías, se detiene 15 segundos y sigue hacia la aldea del otro lado. Solo aparece si hay un
 * jugador cerca de la estación, y desaparece cuando nadie lo tiene cerca.
 */
public final class TrainManager {
    private static final int INTERVAL_TICKS = 12000;
    private static final int FIRST_TICK = 1200;      // primer tren a 1 minuto de arrancar el mundo
    private static final int SPAWN_WINDOW = 1200;    // reintenta durante 1 minuto si nadie está cerca
    private static final int CARS = 5;               // locomotora + 4 vagones
    private static final double SPACING = 6.0;
    private static final double START_DIST = 80.0;   // a cuántos bloques de la estación aparece
    private static final double FAR = 1_000_000.0;

    private static final double MAX_SPEED = 0.85;    // bloques por tick (~17 bloques/s)
    private static final double ACCEL = 0.012;
    private static final double BRAKE_STEP = 0.02;
    private static final double BRAKE_RATE = 0.010;
    private static final int DWELL_TICKS = 300;
    private static final double HIT_DAMAGE = 10.0;

    private static final List<Train> TRAINS = new ArrayList<>();
    private static long lastCycle = -1;

    private TrainManager() {
    }

    public static void clear() {
        TRAINS.clear();
        lastCycle = -1;
    }

    public static void tick(ServerLevel level, RailData data) {
        long t = level.getGameTime();
        long cycle = t / INTERVAL_TICKS;
        long phase = t % INTERVAL_TICKS;
        if (cycle != lastCycle && phase >= FIRST_TICK && phase < FIRST_TICK + SPAWN_WINDOW && phase % 20 == 0) {
            if (trySpawn(level, data, cycle)) {
                lastCycle = cycle;
            }
        }
        Iterator<Train> it = TRAINS.iterator();
        while (it.hasNext()) {
            Train train = it.next();
            if (!train.update(level, data)) {
                train.discard();
                it.remove();
            }
        }
    }

    private static boolean trySpawn(ServerLevel level, RailData data, long cycle) {
        boolean someoneNear = false;
        for (ServerPlayer p : level.players()) {
            if (Math.hypot(p.getX() - data.sx, p.getZ() - data.sz) < 160) {
                someoneNear = true;
                break;
            }
        }
        if (!someoneNear) {
            return false;
        }

        int idx = (int) (cycle % 4);
        int trackZ = (idx % 2 == 0) ? data.sz - 2 : data.sz + 2;
        int arrive = (idx == 0 || idx == 3) ? 1 : -1;

        RailData.Route out = null;
        for (RailData.Route r : data.routes) {
            if (r.side() == -arrive && r.tz() == trackZ) {
                out = r;
            }
        }
        if (out == null) {
            return false;
        }

        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{data.sx + arrive * START_DIST, trackZ});
        pts.add(new double[]{data.sx, trackZ});
        if (out.hasLeg2()) {
            pts.add(new double[]{out.cx(), trackZ});
            pts.add(new double[]{out.cx(), trackZ + Math.signum(out.ez() - out.tz()) * FAR});
        } else {
            pts.add(new double[]{data.sx - arrive * FAR, trackZ});
        }
        TrainPath path = new TrainPath(pts);

        // todos los coches deben caer en chunks ya cargados
        for (int i = 0; i < CARS; i++) {
            double[] p = path.at(-i * SPACING);
            if (!level.hasChunkAt(BlockPos.containing(p[0], data.deckY, p[1]))) {
                return false;
            }
        }

        List<TrainCar> cars = new ArrayList<>();
        for (int i = 0; i < CARS; i++) {
            TrainCar car = new TrainCar(ModEntities.TRAIN_CAR.get(), level);
            car.setKind(i == 0 ? TrainCar.LOCOMOTIVE : TrainCar.WAGON);
            double[] p = path.at(-i * SPACING);
            car.setPos(p[0], data.deckY + 1.125, p[1]);
            level.addFreshEntity(car);
            cars.add(car);
        }
        Train train = new Train(cars, path, START_DIST + (CARS / 2) * SPACING);
        train.place(data);
        TRAINS.add(train);
        level.playSound(null, BlockPos.containing(data.sx, data.deckY, data.sz),
                SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------

    private enum Phase { APPROACH, DWELL, DEPART }

    private static final class Train {
        final List<TrainCar> cars;
        final TrainPath path;
        final double stopS;
        double s = 0.0;           // posición de la locomotora sobre el trazado
        double v = MAX_SPEED;
        Phase phase = Phase.APPROACH;
        int dwell;
        int lonely;               // ticks seguidos sin jugadores cerca
        int age;

        Train(List<TrainCar> cars, TrainPath path, double stopS) {
            this.cars = cars;
            this.path = path;
            this.stopS = stopS;
        }

        void discard() {
            for (TrainCar car : cars) {
                if (!car.isRemoved()) {
                    car.discard();
                }
            }
        }

        boolean update(ServerLevel level, RailData data) {
            for (TrainCar car : cars) {
                if (car.isRemoved()) {
                    return false;
                }
            }
            age++;

            switch (phase) {
                case APPROACH -> {
                    double dist = stopS - s;
                    double target = Math.min(MAX_SPEED, Math.sqrt(2.0 * BRAKE_RATE * Math.max(dist, 0.0)) + 0.02);
                    if (v > target) {
                        v = Math.max(target, v - BRAKE_STEP);
                    } else {
                        v = Math.min(target, v + ACCEL);
                    }
                    if (s + v >= stopS) {
                        s = stopS;
                        v = 0.0;
                        phase = Phase.DWELL;
                        dwell = 0;
                        TrainCar head = cars.get(0);
                        level.playSound(null, head.blockPosition(), SoundEvents.BELL_BLOCK,
                                SoundSource.NEUTRAL, 3.0f, 0.5f);
                    } else {
                        s += v;
                    }
                }
                case DWELL -> {
                    if (++dwell >= DWELL_TICKS) {
                        phase = Phase.DEPART;
                        TrainCar head = cars.get(0);
                        level.playSound(null, head.blockPosition(), SoundEvents.BELL_BLOCK,
                                SoundSource.NEUTRAL, 3.0f, 0.4f);
                    }
                }
                case DEPART -> {
                    v = Math.min(MAX_SPEED, v + ACCEL);
                    s += v;
                }
            }

            place(data);
            effects(level);

            // desaparece si nadie lo tiene cerca
            TrainCar head = cars.get(0);
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                if (Math.hypot(p.getX() - head.getX(), p.getZ() - head.getZ()) < 200) {
                    near = true;
                    break;
                }
            }
            lonely = near ? 0 : lonely + 1;
            return lonely < 100;
        }

        void place(RailData data) {
            for (int i = 0; i < cars.size(); i++) {
                double sc = s - i * SPACING;
                double[] front = path.at(sc + 2.7);
                double[] rear = path.at(sc - 2.7);
                double cx = (front[0] + rear[0]) / 2.0;
                double cz = (front[1] + rear[1]) / 2.0;
                float yaw = (float) Math.toDegrees(Math.atan2(-(front[0] - rear[0]), front[1] - rear[1]));
                TrainCar car = cars.get(i);
                car.setPos(cx, data.deckY + 1.125, cz);
                car.setYRot(yaw);
                car.setXRot(0.0f);
            }
        }

        void effects(ServerLevel level) {
            TrainCar head = cars.get(0);
            double yaw = Math.toRadians(head.getYRot());
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);

            if (v > 0.05) {
                if (age % 4 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            head.getX() + fx * 2.0, head.getY() + 3.0, head.getZ() + fz * 2.0,
                            1, 0.05, 0.05, 0.05, 0.01);
                }
                if (age % 10 == 0) {
                    level.playSound(null, head.getX(), head.getY(), head.getZ(),
                            SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.8f, 0.5f);
                }
            }

            // la locomotora arrolla a quien esté en la vía
            if (v > 0.15) {
                List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class,
                        head.getBoundingBox().inflate(0.3),
                        e -> !(e.getVehicle() instanceof TrainCar) && !e.isSpectator());
                for (LivingEntity e : hit) {
                    double px = e.getX() - head.getX();
                    double pz = e.getZ() - head.getZ();
                    double side = (fx * pz - fz * px) >= 0 ? 1.0 : -1.0;
                    e.hurt(level.damageSources().generic(), (float) HIT_DAMAGE);
                    e.setDeltaMovement(-fz * side * 0.9 + fx * 0.5, 0.5, fx * side * 0.9 + fz * 0.5);
                    e.hurtMarked = true;
                }
            }
        }
    }
}
