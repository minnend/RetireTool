package org.minnen.retiretool.predictor.config;

public class ConfigSMA
{
  public final double margin;
  public final int    nLookbackBaseA;
  public final int    nLookbackBaseB;
  public final int    nLookbackTriggerA;
  public final int    nLookbackTriggerB;
  public final int    iPrice;
  public final long   minTimeBetweenFlips;

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

  @Override
  public String toString()
  {
    return String.format("[%d,%d] / [%d,%d] m=%.2f%%", nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA,
        nLookbackBaseB, margin);
  }
}
