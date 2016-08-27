package org.minnen.retiretool.ckde;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.util.Library;

import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;

/** Implements a 1D kernel density estimate. */
public class KDE extends Distribution
{
  private final List<Neighbor> neighbors;
  private final double[]       values;
  private GaussianDistribution gauss;

  public final static double[] cdflut;

  static {
    GaussianDistribution stdNormal = new GaussianDistribution(0.0, 1.0);
    cdflut = new double[100000];
    for (int i = 0; i < cdflut.length; ++i) {
      double x = -5.0 + 10.0 * i / (cdflut.length - 1);
      cdflut[i] = stdNormal.cdf(x);
    }
  }

  public KDE(List<Neighbor> neighbors)
  {
    this(neighbors, Double.NaN);
  }

  public KDE(List<Neighbor> neighbors, double bandwidth)
  {
    // Keep a local copy of the neighbors.
    this.neighbors = new ArrayList<Neighbor>(neighbors);

    // Sort neighbors by total return.
    Collections.sort(this.neighbors, new Comparator<Neighbor>()
    {
      @Override
      public int compare(Neighbor a, Neighbor b)
      {
        if (a == b) return 0;
        if (a.tr < b.tr) return -1;
        if (a.tr > b.tr) return 1;
        return 0;
      }
    });
    values = Neighbor.extractReturns(this.neighbors);

    if (Double.isNaN(bandwidth)) {
      // Estimate bandwidth since none was given.
      int n = values.length;
      double iqr = values[n * 3 / 4] - values[n / 4];
      assert iqr > 0.0;
      double sdev = Library.stdev(values);
      assert sdev > 0.0;
      bandwidth = 1.06 * Math.min(sdev, iqr / 1.34) / Math.pow(n, 0.2);
    }
    gauss = new GaussianDistribution(0.0, bandwidth);

    // Ensure weights sum to 1.0.
    double wsum = 0.0;
    for (Neighbor neighbor : this.neighbors) {
      wsum += neighbor.weight;
    }
    assert wsum > 0.0;
    for (Neighbor neighbor : this.neighbors) {
      neighbor.weight /= wsum;
    }
  }

  public double bandwidth()
  {
    return gauss.sd();
  }

  /** Returns probability density at x. */
  @Override
  public double density(double x)
  {
    int start = Arrays.binarySearch(values, x - 5 * gauss.sd());
    if (start < 0) {
      start = -start - 1;
    }

    int end = Arrays.binarySearch(values, x + 5 * gauss.sd());
    if (end < 0) {
      end = -end - 1;
    }

    double p = 0.0;
    for (int i = start; i < end; ++i) {
      Neighbor neighbor = neighbors.get(i);
      p += gauss.p(x - neighbor.tr) * neighbor.weight;
    }
    return p;
  }

  /** Returns probability mass in [-inf, b] (i.e. the cumulative distribution function). */
  @Override
  public double cdf(double x)
  {
    int start = Arrays.binarySearch(values, x - 5 * gauss.sd());
    if (start < 0) {
      start = -start - 1;
    }

    double cdf = 0.0;
    for (int i = 0; i < start; ++i) {
      cdf += neighbors.get(i).weight;
    }

    int end = Arrays.binarySearch(values, x + 5 * gauss.sd());
    if (end < 0) {
      end = -end - 1;
    }

    for (int i = start; i < end; ++i) {
      Neighbor neighbor = neighbors.get(i);
      double diff = x - neighbor.tr;
      // double c = gauss.cdf(diff);
      double u = diff / gauss.sd();
      double c = 0.0;
      if (u >= 5.0) {
        c = 1.0;
      } else if (u > -5.0) {
        int j = (int) Math.round((u + 5.0) / 10.0 * cdflut.length);
        c = j < cdflut.length ? cdflut[j] : 1.0;
        // double cc = gauss.cdf(diff);
        // System.out.printf("%f, %f (%f)\n", cc, c, Math.abs(c - cc));
      }
      cdf += c * neighbor.weight;
    }
    return cdf;
  }

  /** Returns the value for the given percentile (e.g. 50 => median). */
  public double percentile(double pct)
  {
    assert pct >= 0.0 && pct <= 100.0;
    pct /= 100.0;

    // Binary search for the requested percentile.
    int n = values.length;
    double range = values[n - 1] - values[0];
    double a = values[0] - range;
    double b = values[n - 1] + range;
    assert b > a;

    double va = cdf(a);
    double vb = cdf(b);

    while (b - a > 0.0005) {
      // System.out.printf("[%.3f -> %.3f] = [%.3f, %.3f]\n", a, b, va, vb);
      assert va <= pct;
      assert vb >= pct;

      double m = (a + b) / 2.0;
      double vm = cdf(m);
      if (vm < pct) {
        a = m;
        va = vm;
      } else if (vm > pct) {
        b = m;
        vb = vm;
      }
    }

    return (a + b) / 2.0;
  }
}
