package com.example.armasreales.train;

import java.util.List;

/** Trazado poligonal que sigue el tren; se extrapola en línea recta antes del inicio y después del final. */
final class TrainPath {
    private final double[] x;
    private final double[] z;
    private final double[] cum;

    TrainPath(List<double[]> points) {
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
}
