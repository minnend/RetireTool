package org.minnen.retiretool.ml.kernel;

public class EpanechnikovKernel extends Kernel
{

  @Override
  public double weight(double u)
  {
    double u2 = u * u;
    if (u2 >= 1.0) return 0.0;
    return 0.75 * (1.0 - u2);
  }

}
