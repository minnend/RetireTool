package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.util.Random;

public class ConfigSMA
{
  public static final Random rng = new Random();

  public final double        margin;
  public final int           nLookbackBaseA;
  public final int           nLookbackBaseB;
  public final int           nLookbackTriggerA;
  public final int           nLookbackTriggerB;
  public final int           iPrice;
  public final long          minTimeBetweenFlips;

  public ConfigSMA(int nLookbackTriggerA, int nLookbackTriggerB, int nLookbackBaseA, int nLookbackBaseB, double margin,
      int iPrice, long minTimeBetweenFlips)
  {
    this.nLookbackTriggerA = nLookbackTriggerA;
    this.nLookbackTriggerB = nLookbackTriggerB;
    this.nLookbackBaseA = nLookbackBaseA;
    this.nLookbackBaseB = nLookbackBaseB;
    this.margin = margin;
    this.iPrice = iPrice;
    this.minTimeBetweenFlips = minTimeBetweenFlips;
  }

  public boolean isValid()
  {
    return nLookbackTriggerA >= 0 && nLookbackTriggerB >= 0 && nLookbackBaseA >= 0 && nLookbackBaseB >= 0
        && nLookbackTriggerA >= nLookbackTriggerB && nLookbackBaseA >= nLookbackBaseB && margin >= 0.0;
  }

  /** @return a perturbed version of this configuration. */
  public ConfigSMA genPerturbed()
  {
    final int N = 1000;
    for (int i = 0; i < N; ++i) {
      int nLookbackTriggerA = perturbLookback(this.nLookbackTriggerA);
      int nLookbackTriggerB = perturbLookback(this.nLookbackTriggerB);
      int nLookbackBaseA = perturbLookback(this.nLookbackBaseA);
      int nLookbackBaseB = perturbLookback(this.nLookbackBaseB);
      double margin = perturbMargin(this.margin);
      ConfigSMA perturbed = new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin,
          iPrice, minTimeBetweenFlips);
      if (!perturbed.isValid()) continue;
      return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

  private static int perturbLookback(int x)
  {
    int halfWidth = Math.max(2, (int) Math.ceil(Math.abs(x) * 0.05));
    int xmin = Math.max(x - halfWidth, 0);
    assert xmin >= 0;
    int xmax = x + halfWidth;
    int range = xmax - xmin + 1;
    int px = rng.nextInt(range) + xmin;
    assert px >= xmin && px <= xmax;
    return px;
  }

  private static double perturbMargin(double x)
  {
    assert x >= 0.0;
    double halfWidth = Math.max(0.05, x * 0.1);
    double xmin = Math.max(x - halfWidth, 0.0);
    double xmax = x + halfWidth;
    assert xmin <= x && xmax > x && xmax > xmin;
    double range = xmax - xmin;
    double px = rng.nextDouble(true, true) * range + xmin;
    assert px >= xmin && px <= xmax;
    return px;
  }

  @Override
  public String toString()
  {
    return String.format("[%d,%d] / [%d,%d] m=%.2f%%", nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA,
        nLookbackBaseB, margin);
  }
}
