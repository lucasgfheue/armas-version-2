package com.example.armasreales.train;

import java.util.List;

/** Trazado poligonal que sigue el tren; se extrapola en línea recta antes del inicio y después del final. */
final class TrainPath {
    private final double[] x;
    private final double[] z;
    private final double[] cum;
    private final List<double[]> points;
    /** Posición del centro de la estación sobre este trazado. */
    final double stationS;
    private final double stationX;
    private final double stationZ;

    TrainPath(List<double[]> points, double stationX, double stationZ) {
        this.points = points;
        this.stationX = stationX;
        this.stationZ = stationZ;
        int n = points.size();
        x = new double[n];
        z = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = points.get(i)[0];
            z[i] = points.get(i)[1];
            if (i > 0) {
                cum[i] = cum[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
        }
        this.stationS = project(stationX, stationZ)[0];
    }

    /** El mismo trazado recorrido al revés. */
    TrainPath reversed() {
        List<double[]> rev = new java.util.ArrayList<>(points);
        java.util.Collections.reverse(rev);
        return new TrainPath(rev, stationX, stationZ);
    }


    /** Posición (x, z) a distancia s del inicio. */
    double[] at(double s) {
        int n = x.length;
        int seg;
        if (s <= 0) {
            seg = 0;
        } else if (s >= cum[n - 1]) {
            seg = n - 2;
        } else {
            seg = 0;
            while (seg < n - 2 && cum[seg + 1] <= s) {
                seg++;
            }
        }
        double len = Math.hypot(x[seg + 1] - x[seg], z[seg + 1] - z[seg]);
        double dx = (x[seg + 1] - x[seg]) / len;
        double dz = (z[seg + 1] - z[seg]) / len;
        double along = s - cum[seg];
        return new double[]{x[seg] + dx * along, z[seg] + dz * along};
    }

    double length() {
        return cum[cum.length - 1];
    }

    /** Punto del trazado más cercano a (px, pz): {s, distancia}. */
    double[] project(double px, double pz) {
        double bestS = 0, bestD = Double.MAX_VALUE;
        for (int i = 0; i < x.length - 1; i++) {
            double len = cum[i + 1] - cum[i];
            double dx = (x[i + 1] - x[i]) / len;
            double dz = (z[i + 1] - z[i]) / len;
            double t = (px - x[i]) * dx + (pz - z[i]) * dz;
            t = Math.max(0, Math.min(len, t));
            double d = Math.hypot(px - (x[i] + dx * t), pz - (z[i] + dz * t));
            if (d < bestD) {
                bestD = d;
                bestS = cum[i] + t;
            }
        }
        return new double[]{bestS, bestD};
    }
}
