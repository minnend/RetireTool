package org.minnen.retiretool.ml.kernel;

public class ConstantKernel extends Kernel
{
  public final double constant;

  public ConstantKernel()
  {
    this(1.0);
  }

  public ConstantKernel(double constant)
  {
    this.constant = constant;
  }

  @Override
  public double weight(double u)
  {
    return constant;
  }

}
