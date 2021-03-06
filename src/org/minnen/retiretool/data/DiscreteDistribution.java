package org.minnen.retiretool.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

  public DiscreteDistribution(String[] names, double... weights)
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

  public boolean hasNames()
  {
    if (names == null || names.length != weights.length) return false;
    for (int i = 0; i < names.length; ++i) {
      if (names[i] == null || names[i].isEmpty()) return false;
    }
    return true;
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
    int ix = find(name);
    return ix < 0 ? 0.0 : weights[ix];
  }

  /** @return number of non-zero weights */
  public int getNumNonZero()
  {
    int n = 0;
    for (int i = 0; i < weights.length; ++i) {
      if (Math.abs(weights[i]) > 1e-7) ++n;
    }
    return n;
  }

  public int find(String name)
  {
    if (name == null) return -1;
    for (int i = 0; i < names.length; ++i) {
      if (names[i] != null && names[i].equals(name)) return i;
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
    return Math.abs(sum() - 1.0) < 1e-4;
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

  public void sortByName()
  {
    int[] ii = Library.sort(names, true);
    Library.reorder(weights, ii);
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
      long change = Math.min(nearestPct, Math.abs(diff));
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
        wlong[iBest] += change;
        diff += change;
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
        wlong[iBest] -= change;
        diff -= change;
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

  public static DiscreteDistribution uniform(String... names)
  {
    DiscreteDistribution distribution = new DiscreteDistribution(names);
    double w = 1.0 / names.length;
    for (int i = 0; i < names.length; ++i) {
      distribution.weights[i] = w;
    }
    return distribution;
  }

  public static Set<String> getAllNames(DiscreteDistribution... dists)
  {
    Set<String> names = new HashSet<>();
    for (DiscreteDistribution dist : dists) {
      for (String name : dist.names) {
        names.add(name);
      }
    }
    return names;
  }

  public boolean isSimilar(DiscreteDistribution distribution, double eps)
  {
    if (distribution == null) return false;

    Set<String> names = DiscreteDistribution.getAllNames(this, distribution);
    for (String name : names) {
      double w1 = this.get(name);
      double w2 = distribution.get(name);
      if (Math.abs(w1 - w2) > eps) return false;
    }
    return true;
  }

  public String toStringWithNames(int nSigDig)
  {
    StringBuilder sb = new StringBuilder("[");
    int nPrinted = 0;
    String format = nSigDig >= 0 ? String.format("%%s:%%.%df", nSigDig) : null;
    for (int i = 0; i < weights.length; ++i) {
      if (Math.abs(weights[i]) < 1e-4) continue;
      if (nPrinted > 0) sb.append(",");
      String s = nSigDig < 0 ? names[i] : String.format(format, names[i], weights[i] * 100);
      sb.append(s);
      ++nPrinted;
    }
    sb.append("]");
    return sb.toString();
  }

  public static DiscreteDistribution fromStringWithNames(String s)
  {
    s = s.trim();
    if (!s.startsWith("[") || !s.endsWith("]")) return null;
    s = s.substring(1, s.length() - 1);
    String[] toks = s.split(",");
    String[] names = new String[toks.length];
    double[] weights = new double[toks.length];
    for (int i = 0; i < toks.length; ++i) {
      String[] kv = toks[i].split(":");
      names[i] = kv[0].trim();
      weights[i] = Double.parseDouble(kv[1].trim()) / 100.0;
    }
    return new DiscreteDistribution(names, weights);
  }

  public String toString(String format)
  {
    StringBuilder sb = new StringBuilder(String.format("[" + format, weights[0] * 100));
    for (int i = 1; i < weights.length; ++i) {
      sb.append(String.format("," + format, weights[i] * 100));
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public String toString()
  {
    return toString("%4.1f");
  }

  /** @return a new distribution with zero-weight elements removed. */
  public DiscreteDistribution removeZeroWeights(double eps)
  {
    int numNonZero = getNumNonZero();
    if (numNonZero == size()) return this;

    String[] nonZeroNames = new String[numNonZero];
    double[] nonZeroWeights = new double[numNonZero];
    int iNonZero = 0;
    for (int i = 0; i < weights.length; ++i) {
      if (weights[i] < eps) continue;
      nonZeroNames[iNonZero] = names[i];
      nonZeroWeights[iNonZero] = weights[i];
      ++iNonZero;
    }
    assert iNonZero == numNonZero;
    return new DiscreteDistribution(nonZeroNames, nonZeroWeights);
  }

  /** @return distribution formed by averaging this with `other`. */
  public DiscreteDistribution blend(DiscreteDistribution other)
  {
    // Find union of all names.
    Set<String> nameSet = new HashSet<>(Arrays.asList(this.names));
    nameSet.addAll(Arrays.asList(other.names));
    nameSet.remove(null);
    String[] names = nameSet.toArray(new String[nameSet.size()]);

    // Find average weight of all names.
    double[] weights = new double[names.length];
    for (int i = 0; i < names.length; ++i) {
      weights[i] = (this.weight(names[i]) + other.weight(names[i])) / 2.0;
    }
    return new DiscreteDistribution(names, weights);
  }
}
