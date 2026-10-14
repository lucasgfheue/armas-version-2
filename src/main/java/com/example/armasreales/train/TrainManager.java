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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hace pasar un tren (cada 10 minutos por jugador) por cualquier punto de las vías donde haya
 * alguien caminando: aparece a unos 50-110 bloques, viene hacia el jugador y lo pasa de largo.
 * Si la estación queda por delante, se detiene ahí 15 segundos. Desaparece cuando nadie lo tiene cerca.
 */
public final class TrainManager {
    private static final int INTERVAL_TICKS = 12000;   // un tren por jugador cada 10 minutos
    private static final int FIRST_DELAY = 600;        // 30 s después de pisar las vías por primera vez
    private static final int CARS = 5;                 // locomotora + 4 vagones
    private static final double SPACING = 6.0;
    private static final double FAR = 1_000_000.0;
    private static final double NEAR_RAIL = 8.0;       // a cuántos bloques de la vía cuenta "estar en las vías"
    private static final double[] START_DISTANCES = {110, 90, 70, 50};

    private static final double MAX_SPEED = 0.85;    // bloques por tick (~17 bloques/s)
    private static final double ACCEL = 0.012;
    private static final double BRAKE_STEP = 0.02;
    private static final double BRAKE_RATE = 0.010;
    private static final int DWELL_TICKS = 300;
    private static final double HIT_DAMAGE = 10.0;

    private static final List<Train> TRAINS = new ArrayList<>();
    private static final Map<UUID, Long> NEXT_TRAIN = new HashMap<>();

    private TrainManager() {
    }

    public static void clear() {
        TRAINS.clear();
        NEXT_TRAIN.clear();
    }

    public static void tick(ServerLevel level, RailData data) {
        long t = level.getGameTime();
        if (t % 20 == 0) {
            List<TrainPath> lines = null;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.getVehicle() != null
                        || Math.abs(p.getY() - (data.deckY + 1)) > 10) {
                    continue;
                }
                if (lines == null) {
                    lines = buildLines(data);
                }
                trySpawnFor(level, data, p, lines, t);
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

    /** Las 4 líneas completas (de un extremo infinito al otro, pasando por la estación). */
    private static List<TrainPath> buildLines(RailData data) {
        List<TrainPath> lines = new ArrayList<>();
        for (int tz : new int[]{data.sz - 2, data.sz + 2}) {
            RailData.Route west = null, east = null;
            for (RailData.Route r : data.routes) {
                if (r.tz() == tz) {
                    if (r.side() < 0) west = r; else east = r;
                }
            }
            if (west == null || east == null) {
                continue;
            }
            List<double[]> pts = new ArrayList<>();
            if (west.hasLeg2()) {
                pts.add(new double[]{west.cx(), tz + Math.signum(west.ez() - tz) * FAR});
                pts.add(new double[]{west.cx(), tz});
            } else {
                pts.add(new double[]{data.sx - FAR, tz});
            }
            pts.add(new double[]{data.sx, tz});
            if (east.hasLeg2()) {
                pts.add(new double[]{east.cx(), tz});
                pts.add(new double[]{east.cx(), tz + Math.signum(east.ez() - tz) * FAR});
            } else {
                pts.add(new double[]{data.sx + FAR, tz});
            }
            lines.add(new TrainPath(pts, data.sx, tz));
        }
        return lines;
    }

    private static void trySpawnFor(ServerLevel level, RailData data, ServerPlayer player,
                                    List<TrainPath> lines, long now) {
        // ¿está el jugador sobre (o junto a) alguna vía?
        TrainPath line = null;
        double s0 = 0;
        double best = NEAR_RAIL;
        for (TrainPath candidate : lines) {
            double[] pr = candidate.project(player.getX(), player.getZ());
            if (pr[1] <= best) {
                best = pr[1];
                line = candidate;
                s0 = pr[0];
            }
        }
        if (line == null) {
            return;
        }

        Long next = NEXT_TRAIN.get(player.getUUID());
        if (next == null) {
            NEXT_TRAIN.put(player.getUUID(), now + FIRST_DELAY);
            return;
        }
        if (now < next) {
            return;
        }
        // ya hay un tren cerca de este jugador
        for (Train train : TRAINS) {
            TrainCar head = train.cars.get(0);
            if (Math.hypot(head.getX() - player.getX(), head.getZ() - player.getZ()) < 300) {
                return;
            }
        }

        boolean forward = level.random.nextBoolean();
        TrainPath path = forward ? line : line.reversed();
        double sPlayer = forward ? s0 : path.length() - s0;
        double stationS = path.stationS;

        for (double dist : START_DISTANCES) {
            double sHead = sPlayer - dist;
            boolean loaded = true;
            for (int i = 0; i < CARS && loaded; i++) {
                double[] p = path.at(sHead - i * SPACING);
                loaded = level.hasChunkAt(BlockPos.containing(p[0], data.deckY, p[1]));
            }
            if (!loaded) {
                continue;
            }
            // no aparecer encima del jugador
            double[] hp = path.at(sHead);
            if (Math.hypot(hp[0] - player.getX(), hp[1] - player.getZ()) < 30) {
                continue;
            }

            List<TrainCar> cars = new ArrayList<>();
            for (int i = 0; i < CARS; i++) {
                TrainCar car = new TrainCar(ModEntities.TRAIN_CAR.get(), level);
                car.setKind(i == 0 ? TrainCar.LOCOMOTIVE : TrainCar.WAGON);
                double[] p = path.at(sHead - i * SPACING);
                car.setPos(p[0], data.deckY + 1.125, p[1]);
                level.addFreshEntity(car);
                cars.add(car);
            }
            double stopS = stationS + (CARS / 2) * SPACING;
            boolean stationAhead = stopS > sHead + 20;
            Train train = new Train(cars, path, sHead, stationAhead ? stopS : Double.MAX_VALUE,
                    stationAhead ? Phase.APPROACH : Phase.DEPART);
            train.place(data);
            TRAINS.add(train);
            level.playSound(null, BlockPos.containing(hp[0], data.deckY, hp[1]),
                    SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0f, 0.5f);
            NEXT_TRAIN.put(player.getUUID(), now + INTERVAL_TICKS);
            return;
        }
    }

    // ------------------------------------------------------------------

    private enum Phase { APPROACH, DWELL, DEPART }

    private static final class Train {
        final List<TrainCar> cars;
        final TrainPath path;
        final double stopS;
        double s;                 // posición de la locomotora sobre el trazado
        double v = MAX_SPEED;
        Phase phase;
        int dwell;
        int lonely;               // ticks seguidos sin jugadores cerca
        int age;

        Train(List<TrainCar> cars, TrainPath path, double startS, double stopS, Phase phase) {
            this.cars = cars;
            this.path = path;
            this.s = startS;
            this.stopS = stopS;
            this.phase = phase;
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
