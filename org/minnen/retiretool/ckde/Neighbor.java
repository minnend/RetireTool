package org.minnen.retiretool.ckde;

import java.util.List;

import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

import smile.math.Math;

public class Neighbor implements Comparable<Neighbor>
{
  public double tr;
  public double distance;
  public double weight;
  public long   time;

  public Neighbor(double tr, double distance, double weight, long time)
  {
    this.tr = tr;
    this.distance = distance;
    this.weight = weight;
    this.time = time;
  }

  @Override
  public int compareTo(Neighbor x)
  {
    if (distance < x.distance) return -1;
    if (distance > x.distance) return 1;
    return 0;
  }

  @Override
  public String toString()
  {
    return String.format("[%.3f (%.3f) %.3f |%s]", distance, weight, tr, TimeLib.formatDate(time));
  }

  public static double[] extractReturns(List<Neighbor> neighbors)
  {
    double[] r = new double[neighbors.size()];
    for (int i = 0; i < r.length; ++i) {
      r[i] = neighbors.get(i).tr;
    }
    return r;
  }

  public static double[] extractDistances(List<Neighbor> neighbors)
  {
    double[] d = new double[neighbors.size()];
    for (int i = 0; i < d.length; ++i) {
      d[i] = neighbors.get(i).distance;
    }
    return d;
  }

  /** Estimate bandwidth for neighbors (assumes neighbors are sorted by increasing distance. */
  public static double estimateBandwidth(List<Neighbor> neighbors)
  {
    double[] x = extractDistances(neighbors);
    int n = x.length;
    double iqr = x[n * 3 / 4] - x[n / 4];
    assert iqr > 0.0;

    double sd = Library.stdev(x);
    return 1.06 * Math.min(sd, iqr / 1.34) / Math.pow(n, 0.2);
  }
}
