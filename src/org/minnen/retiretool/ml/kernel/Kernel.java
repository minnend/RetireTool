package org.minnen.retiretool.ml.kernel;

public abstract class Kernel
{
  public abstract double weight(double u);

  public double weight(double x, double x0, double kw)
  {
    return weight((x - x0) / kw);
  }
}
