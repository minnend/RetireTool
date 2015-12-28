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

  public void set(int i, String name, double weight)
  {
    names[i] = name;
    weights[i] = weight;
  }

  public int size()
  {
    return weights.length;
  }

  public boolean isNormalized()
  {
    double sum = Library.sum(weights);
    return Math.abs(sum - 1.0) < 1e-6;
  }

  /** Ensure sum of distribution values is 1.0 (unless current sum is zero). */
  public void normalize()
  {
    double sum = Library.sum(weights);
    assert sum >= 0.0;
    if (sum > 0.0) {
      for (int i = 0; i < weights.length; ++i) {
        weights[i] /= sum;
      }
    }
  }

  /** Set all values to zero. */
  public void clear()
  {
    Arrays.fill(weights, 0.0);
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
    if (distribution.size() != size()) return false;
    for (int i = 0; i < distribution.size(); ++i) {
      if (Math.abs(distribution.weights[i] - weights[i]) > eps) return false;
    }
    return true;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(String.format("[%.1f", weights[0] * 100));
    for (int i = 1; i < weights.length; ++i) {
      sb.append(String.format(",%.1f", weights[i] * 100));
    }
    sb.append("]");
    return sb.toString();
  }
}
