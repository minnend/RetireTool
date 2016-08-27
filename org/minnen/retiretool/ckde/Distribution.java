package org.minnen.retiretool.ckde;

public abstract class Distribution
{
  /** Returns probability density at x. */
  public abstract double density(double x);

  /** Returns probability mass in [-inf, b] (i.e. the cumulative distribution function). */
  public abstract double cdf(double x);

  /** Returns probability mass in [a, b]. */
  public double integral(double a, double b)
  {
    return cdf(b) - cdf(a);
  }
}
