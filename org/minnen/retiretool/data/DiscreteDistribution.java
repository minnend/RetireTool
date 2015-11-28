package org.minnen.retiretool.data;

import java.util.Arrays;

import org.minnen.retiretool.Library;

/**
 * Represents a discrete distribution (i.e. a multinomial).
 *
 * Each element in the distribution has an associated name.
 */
public class DiscreteDistribution
{
  public final double[] weights;
  public final String[] names;

  public DiscreteDistribution(int n)
  {
    weights = new double[n];
    names = new String[n];
  }

  public DiscreteDistribution(double... weights)
  {
    this(weights.length);
    System.arraycopy(weights, 0, this.weights, 0, weights.length);
  }

  public DiscreteDistribution(String[] names, double[] weights)
  {
    this(names.length);
    assert names.length == weights.length;

    System.arraycopy(names, 0, this.names, 0, names.length);
    System.arraycopy(weights, 0, this.weights, 0, weights.length);
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
}
