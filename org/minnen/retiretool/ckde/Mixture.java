package org.minnen.retiretool.ckde;

import java.util.ArrayList;
import java.util.List;

/** Implements a 1D mixture density. */
public class Mixture extends Distribution
{
  private final List<Distribution> distributions = new ArrayList<>();
  private final List<Double>       weights       = new ArrayList<>();
  private double                   wsum          = 0.0;

  public void add(Distribution distribution, double weight)
  {
    distributions.add(distribution);
    weights.add(weight);
    wsum += weight;
  }

  public int size()
  {
    assert distributions.size() == weights.size();
    return distributions.size();
  }

  public void clear()
  {
    distributions.clear();
    weights.clear();
    wsum = 0.0;
  }

  @Override
  public double density(double x)
  {
    int n = size();
    assert n > 0;
    assert wsum > 0.0;

    double sum = 0.0;
    for (int i = 0; i < n; ++i) {
      sum += weights.get(i) * distributions.get(i).density(x);
    }
    return sum / wsum;
  }

  @Override
  public double cdf(double x)
  {
    int n = size();
    assert n > 0;
    assert wsum > 0.0;

    double sum = 0.0;
    for (int i = 0; i < n; ++i) {
      sum += weights.get(i) * distributions.get(i).cdf(x);
    }
    return sum / wsum;
  }

}
