package org.minnen.retiretool.util;

/** Implements y = a*x + b */
public class LinearFunc
{
  public static final LinearFunc Identity = new LinearFunc(1.0, 0.0);
  public static final LinearFunc Zero     = new LinearFunc(0.0, 0.0);

  public final double            mul, add;

  public LinearFunc(double mul, double add)
  {
    this.mul = mul;
    this.add = add;
  }

  public double calc(double x)
  {
    return mul * x + add;
  }

  @Override
  public String toString()
  {
    return String.format("[LinearFunc: %f, %f]", mul, add);
  }
}
