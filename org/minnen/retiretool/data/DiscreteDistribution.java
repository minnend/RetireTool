package org.minnen.retiretool.data;

import java.util.Arrays;

import org.minnen.retiretool.util.Library;

/**
 * Represents a discrete distribution (i.e. a multinomial).
 *
 * Each element in the distribution has an associated name.
 */
public class DiscreteDistribution
{
  // TODO allow iterating over name/weight pairs.
  public final double[] weights;
  public final String[] names;

  public DiscreteDistribution(int n)
  {
    weights = new double[n];
    names = new String[n];
  }

  public DiscreteDistribution(String... names)
  {
    this(names.length);
    System.arraycopy(names, 0, this.names, 0, names.length);
  }

  public DiscreteDistribution(double... weights)
  {
    this(weights.length);
    System.arraycopy(weights, 0, this.weights, 0, weights.length);
  }

  public DiscreteDistribution(String name)
  {
    this(1);
    names[0] = name;
    weights[0] = 1.0;
  }

  public DiscreteDistribution(String[] names, double[] weights)
  {
    this(names.length);
    assert names.length == weights.length;

    System.arraycopy(names, 0, this.names, 0, names.length);
    System.arraycopy(weights, 0, this.weights, 0, weights.length);
  }

  public DiscreteDistribution(DiscreteDistribution distribution)
  {
    this(distribution.names, distribution.weights);
  }

  public DiscreteDistribution copyFrom(DiscreteDistribution distribution)
  {
    assert distribution.size() == size();
    System.arraycopy(distribution.names, 0, names, 0, names.length);
    System.arraycopy(distribution.weights, 0, weights, 0, weights.length);
    return this;
  }

  /** Update the name and weight of the i^th entry. */
  public void set(int i, String name, double weight)
  {
    names[i] = name;
    weights[i] = weight;
  }

  public void set(String name, double weight)
  {
    weights[find(name)] = weight;
  }

  public double get(String name)
  {
    return weights[find(name)];
  }

  public int find(String name)
  {
    for (int i = 0; i < names.length; ++i) {
      if (names[i].equals(name)) return i;
    }
    return -1;
  }

  public double weight(String name)
  {
    int index = find(name);
    return (index < 0 ? 0.0 : weights[index]);
  }

  public int size()
  {
    return weights.length;
  }

  public double sum()
  {
    return Library.sum(weights);
  }

  public boolean isNormalized()
  {
    return Math.abs(sum() - 1.0) < 1e-6;
  }

  /** Ensure sum of distribution values is 1.0 (unless current sum is zero). */
  public void normalize()
  {
    double sum = sum();
    assert sum >= 0.0;
    if (sum > 0.0) {
      for (int i = 0; i < weights.length; ++i) {
        weights[i] /= sum;
      }
    }
  }

  public void clean(int nearestPct)
  {
    normalize();

    final int n = weights.length;
    long[] wlong = new long[n];
    for (int i = 0; i < n; ++i) {
      double w = weights[i] * 100.0;
      wlong[i] = Math.round(w / nearestPct) * nearestPct;
    }

    long diff = Library.sum(wlong) - 100;
    while (diff != 0) {
      final double eps = 1e-6;
      if (diff < 0) {
        // Find farthest below.
        int iBest = -1;
        double bestGap = 0.0;
        for (int i = 0; i < n; ++i) {
          if (Math.abs(weights[i]) < eps) continue;
          double gap = weights[i] - wlong[i] / 100.0;
          if (gap > 0 && (iBest < 0 || gap > bestGap)) {
            iBest = i;
            bestGap = gap;
          }
        }
        wlong[iBest] += nearestPct;
        diff += nearestPct;
      } else {
        assert diff > 0;
        // Find farthest above.
        int iBest = -1;
        double bestGap = 0.0;
        for (int i = 0; i < n; ++i) {
          if (Math.abs(weights[i]) < eps) continue;
          double gap = wlong[i] / 100.0 - weights[i];
          if (gap > 0 && (iBest < 0 || gap > bestGap)) {
            iBest = i;
            bestGap = gap;
          }
        }
        wlong[iBest] -= nearestPct;
        diff -= nearestPct;
      }
    }

    for (int i = 0; i < weights.length; ++i) {
      weights[i] = wlong[i] / 100.0;
    }
  }

  /** Set all values to zero. */
  public void clear()
  {
    Arrays.fill(weights, 0.0);
  }

  /** Multiply all weights by the given value. */
  public void mul(double m)
  {
    for (int i = 0; i < weights.length; ++i) {
      weights[i] *= m;
    }
  }

  public static DiscreteDistribution uniform(int n)
  {
    DiscreteDistribution distribution = new DiscreteDistribution(n);
    double w = 1.0 / n;
    for (int i = 0; i < n; ++i) {
      distribution.weights[i] = w;
    }
    return distribution;
  }

  public static DiscreteDistribution uniform(String[] names)
  {
    DiscreteDistribution distribution = new DiscreteDistribution(names);
    double w = 1.0 / names.length;
    for (int i = 0; i < names.length; ++i) {
      distribution.weights[i] = w;
    }
    return distribution;
  }

  public boolean isSimilar(DiscreteDistribution distribution, double eps)
  {
    if (distribution == null || distribution.size() != size()) return false;
    for (int i = 0; i < distribution.size(); ++i) {
      if (Math.abs(distribution.weights[i] - weights[i]) > eps) return false;
    }
    return true;
  }

  public String toStringWithNames()
  {
    StringBuilder sb = new StringBuilder("[");
    int nPrinted = 0;
    for (int i = 0; i < weights.length; ++i) {
      if (Math.abs(weights[i]) < 1e-4) continue;
      if (nPrinted > 0) sb.append(",");
      sb.append(String.format("%s:%.2f", names[i], weights[i] * 100));
      ++nPrinted;
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(String.format("[%4.1f", weights[0] * 100));
    for (int i = 1; i < weights.length; ++i) {
      sb.append(String.format(",%4.1f", weights[i] * 100));
    }
    sb.append("]");
    return sb.toString();
  }
}
