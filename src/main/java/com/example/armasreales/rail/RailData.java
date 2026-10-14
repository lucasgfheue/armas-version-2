package com.example.armasreales.rail;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Datos persistentes de la red ferroviaria: dónde está la estación, qué vías salen de ella
 * y qué trozos del mundo (chunks) ya tienen sus vías construidas.
 *
 * Geometría: la estación es un salón largo en el eje X con dos vías (z = sz-2 y z = sz+2).
 * Cada vía sale hacia el este y hacia el oeste. Cada ruta va en línea recta por X hasta la
 * coordenada X de su aldea y luego gira por Z hasta llegar a ella.
 */
public class RailData extends SavedData {
    public static final String NAME = "armasreales_rails";

    /** side = +1 (este) o -1 (oeste); tz = Z de la vía; (cx, ez) = destino. */
    public record Route(int tz, int side, int cx, int ez) {
        public boolean hasLeg2() {
            return ez != tz;
        }
    }

    public boolean initialized;
    public int sx, sz, deckY;
    public final List<Route> routes = new ArrayList<>();
    public final Set<Long> done = new HashSet<>();

    public static RailData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RailData::new, RailData::load, null), NAME);
    }

    public static RailData load(CompoundTag tag, HolderLookup.Provider registries) {
        RailData data = new RailData();
        data.initialized = tag.getBoolean("initialized");
        data.sx = tag.getInt("sx");
        data.sz = tag.getInt("sz");
        data.deckY = tag.getInt("deckY");
        ListTag list = tag.getList("routes", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            int[] r = list.getIntArray(i);
            if (r.length == 4) {
                data.routes.add(new Route(r[0], r[1], r[2], r[3]));
            }
        }
        for (long l : tag.getLongArray("done")) {
            data.done.add(l);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("initialized", initialized);
        tag.putInt("sx", sx);
        tag.putInt("sz", sz);
        tag.putInt("deckY", deckY);
        ListTag list = new ListTag();
        for (Route r : routes) {
            list.add(new net.minecraft.nbt.IntArrayTag(new int[]{r.tz(), r.side(), r.cx(), r.ez()}));
        }
        tag.put("routes", list);
        long[] arr = new long[done.size()];
        int i = 0;
        for (long l : done) {
            arr[i++] = l;
        }
        tag.putLongArray("done", arr);
        return tag;
    }

    // ------------------------------------------------------------------ geometría

    public boolean inStation(int x, int z) {
        return Math.abs(x - sx) <= 16 && Math.abs(z - sz) <= 7;
    }

    /** Rectángulos {x0, z0, x1, z1} donde puede haber algo construido. */
    public List<int[]> rects() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{sx - 16, sz - 7, sx + 16, sz + 7});
        for (Route r : routes) {
            out.add(new int[]{Math.min(sx, r.cx()), r.tz(), Math.max(sx, r.cx()), r.tz()});
            if (r.hasLeg2()) {
                out.add(new int[]{r.cx(), Math.min(r.tz(), r.ez()), r.cx(), Math.max(r.tz(), r.ez())});
            }
            int[] h = haltRect(r);
            out.add(h);
        }
        return out;
    }

    /** Andén de la parada final (incluye el tope). */
    public static int[] haltRect(Route r) {
        if (r.hasLeg2()) {
            int d = Integer.signum(r.ez() - r.tz());
            int a = r.ez() - 8 * d;
            int b = r.ez() + 2 * d;
            return new int[]{r.cx() - 2, Math.min(a, b), r.cx() + 2, Math.max(a, b)};
        }
        int a = r.cx() - 8 * r.side();
        int b = r.cx() + 2 * r.side();
        return new int[]{Math.min(a, b), r.tz() - 2, Math.max(a, b), r.tz() + 2};
    }

    public boolean chunkRelevant(int chunkX, int chunkZ) {
        int x0 = chunkX << 4, z0 = chunkZ << 4, x1 = x0 + 15, z1 = z0 + 15;
        for (int[] r : rects()) {
            if (r[0] <= x1 && r[2] >= x0 && r[1] <= z1 && r[3] >= z0) {
                return true;
            }
        }
        return false;
    }

    public enum Kind { RAIL, BUFFER, STATION, HALT }

    /** Qué hay que construir en una columna (x, z). */
    public static class Plan {
        public final Kind kind;
        public final net.minecraft.world.level.block.state.properties.RailShape shape;
        public final boolean powered;
        public final int a; // STATION: dx | HALT: lateral
        public final int b; // STATION: dz | HALT: distancia al final

        Plan(Kind kind, net.minecraft.world.level.block.state.properties.RailShape shape, boolean powered, int a, int b) {
            this.kind = kind;
            this.shape = shape;
            this.powered = powered;
            this.a = a;
            this.b = b;
        }
    }

    public Plan plan(int x, int z) {
        for (Route r : routes) {
            int minX = Math.min(sx, r.cx()), maxX = Math.max(sx, r.cx());
            boolean leg1 = z == r.tz() && x >= minX && x <= maxX;
            boolean leg2 = r.hasLeg2() && x == r.cx()
                    && z >= Math.min(r.tz(), r.ez()) && z <= Math.max(r.tz(), r.ez());
            if (!leg1 && !leg2) {
                continue;
            }
            boolean corner = r.hasLeg2() && x == r.cx() && z == r.tz();
            net.minecraft.world.level.block.state.properties.RailShape shape;
            if (corner) {
                boolean south = r.ez() > r.tz();
                boolean fromWest = r.side() > 0;
                if (fromWest) {
                    shape = south ? net.minecraft.world.level.block.state.properties.RailShape.SOUTH_WEST
                            : net.minecraft.world.level.block.state.properties.RailShape.NORTH_WEST;
                } else {
                    shape = south ? net.minecraft.world.level.block.state.properties.RailShape.SOUTH_EAST
                            : net.minecraft.world.level.block.state.properties.RailShape.NORTH_EAST;
                }
            } else if (leg2 && !leg1) {
                shape = net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH;
            } else {
                shape = net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST;
            }
            boolean powered = !corner && ((x + z) & 1) == 0;
            return new Plan(Kind.RAIL, shape, powered, 0, 0);
        }
        for (Route r : routes) {
            int bx, bz;
            if (r.hasLeg2()) {
                bx = r.cx();
                bz = r.ez() + Integer.signum(r.ez() - r.tz());
            } else {
                bx = r.cx() + r.side();
                bz = r.tz();
            }
            if (x == bx && z == bz) {
                return new Plan(Kind.BUFFER, null, false, 0, 0);
            }
        }
        if (inStation(x, z)) {
            return new Plan(Kind.STATION, null, false, x - sx, z - sz);
        }
        for (Route r : routes) {
            int[] h = haltRect(r);
            if (x >= h[0] && x <= h[2] && z >= h[1] && z <= h[3]) {
                int lat, dist;
                if (r.hasLeg2()) {
                    lat = x - r.cx();
                    dist = Math.abs(r.ez() - z);
                } else {
                    lat = z - r.tz();
                    dist = Math.abs(r.cx() - x);
                }
                return new Plan(Kind.HALT, null, false, lat, dist);
            }
        }
        return null;
    }
}
