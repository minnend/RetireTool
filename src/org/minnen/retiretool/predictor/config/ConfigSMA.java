package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.SMAPredictor;
import org.minnen.retiretool.util.Random;

public class ConfigSMA extends PredictorConfig
{
  public static final Random rng = new Random();

  public final int           margin;             // basis points, i.e. 100 = 0.01%
  public final int           nLookbackBaseA;
  public final int           nLookbackBaseB;
  public final int           nLookbackTriggerA;
  public final int           nLookbackTriggerB;
  public final int           iPrice;
  public final long          minTimeBetweenFlips;

  public ConfigSMA(int nLookbackTriggerA, int nLookbackTriggerB, int nLookbackBaseA, int nLookbackBaseB, int margin,
      int iPrice, long minTimeBetweenFlips)
  {
    this(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin, iPrice, minTimeBetweenFlips, 0,
        1);
  }

  public ConfigSMA(int nLookbackTriggerA, int nLookbackTriggerB, int nLookbackBaseA, int nLookbackBaseB, int margin,
      int iPrice, long minTimeBetweenFlips, int iPredictIn, int iPredictOut)
  {
    super(iPredictIn, iPredictOut);
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
      int nTriggerA = rng.nextInt(5, 60);
      int nTriggerB = 0;
      int nBaseA = rng.nextInt(5, 250);
      int nBaseB = Math.max(0, nBaseA - 10 * rng.nextInt(1, Math.max(1, nBaseA / 10)));
      int margin = rng.nextInt(1, 30) * 10;
      ConfigSMA config = new ConfigSMA(nTriggerA, nTriggerB, nBaseA, nBaseB, margin, iPrice, minTimeBetweenFlips);
      if (config.isValid()) return config;
    }
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new SMAPredictor(this, assetNames[iPredictIn], assetNames[iPredictOut], brokerAccess);
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
      int margin = perturbMargin(this.margin);
      ConfigSMA perturbed = new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin,
          iPrice, minTimeBetweenFlips);
      if (perturbed.isValid()) return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

  public static int perturbLookback(int x)
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

  public static int perturbMargin(int x)
  {
    assert x >= 0;
    int halfWidth = Math.max(1, (x + 5) / 10);
    int xmin = Math.max(x - halfWidth, 0);
    int xmax = x + halfWidth;
    assert xmin <= x && xmax >= x && xmax >= xmin;
    int px = rng.nextInt(xmin, xmax);
    assert px >= xmin && px <= xmax;
    return px;
  }

  @Override
  public String toString()
  {
    return String.format("[%d,%d] / [%d,%d] m=%d", nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB,
        margin);
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + margin;
    result = prime * result + nLookbackBaseA;
    result = prime * result + nLookbackBaseB;
    result = prime * result + nLookbackTriggerA;
    result = prime * result + nLookbackTriggerB;
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ConfigSMA other = (ConfigSMA) obj;
    if (margin != other.margin) return false;
    if (nLookbackBaseA != other.nLookbackBaseA) return false;
    if (nLookbackBaseB != other.nLookbackBaseB) return false;
    if (nLookbackTriggerA != other.nLookbackTriggerA) return false;
    if (nLookbackTriggerB != other.nLookbackTriggerB) return false;
    return true;
  }
}
