package org.minnen.retiretool.ckde;

/** Implements a 1D uniform distribution. */
public class Uniform extends Distribution
{
  public final double a, b;

  public Uniform(double a, double b)
  {
    assert a <= b;
    this.a = a;
    this.b = b;
  }

  @Override
  public double density(double x)
  {
    if (x < a || x > b) return 0.0;
    double diff = b - a;
    if (diff == 0.0) return Double.POSITIVE_INFINITY;
    else return 1.0 / diff;
  }

  @Override
  public double cdf(double x)
  {
    if (x < a) return 0.0;
    if (x >= b) return 1.0;
    return (x - a) / (b - a);
  }
}
