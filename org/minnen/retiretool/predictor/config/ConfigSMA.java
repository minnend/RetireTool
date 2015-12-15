package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.SMAPredictor;
import org.minnen.retiretool.util.Random;

public class ConfigSMA extends PredictorConfig
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

  public static ConfigSMA genRandom(int iPrice, long minTimeBetweenFlips)
  {
    while (true) {
      int nTriggerA = 5 * rng.nextInt(1, 12);
      int nTriggerB = 0;
      int nBaseA = 5 * rng.nextInt(1, 50);
      int nBaseB = Math.max(0, nBaseA - 10 * rng.nextInt(1, Math.max(1, nBaseA / 10)));
      double margin = rng.nextInt(1, 12) * 0.25;
      ConfigSMA config = new ConfigSMA(nTriggerA, nTriggerB, nBaseA, nBaseB, margin, iPrice, minTimeBetweenFlips);
      if (config.isValid()) return config;
    }
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new SMAPredictor(this, assetNames[0], assetNames[1], brokerAccess);
  }

  @Override
  public boolean isValid()
  {
    return nLookbackTriggerA >= 0 && nLookbackTriggerB >= 0 && nLookbackBaseA >= 0 && nLookbackBaseB >= 0
        && nLookbackTriggerA >= nLookbackTriggerB && nLookbackBaseA >= nLookbackBaseB && margin >= 0.0;
  }

  @Override
  public PredictorConfig genPerturbed()
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
    int halfWidth = Math.max(1, (int) Math.round(Math.abs(x) * 0.05));
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
    double halfWidth = Math.max(0.01, x * 0.1);
    double xmin = Math.max(x - halfWidth, 0.0);
    double xmax = x + halfWidth;
    assert xmin <= x && xmax >= x && xmax >= xmin;
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
