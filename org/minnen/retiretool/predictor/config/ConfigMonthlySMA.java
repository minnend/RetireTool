package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.MonthlySMAPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.util.Random;

public class ConfigMonthlySMA extends PredictorConfig
{
  public static final Random rng = new Random();

  public final int           nLookback;
  public final int           iPrice;
  public final boolean       invert;            // if true, go in when below SMA
  public final String        analysisName;

  public ConfigMonthlySMA(int nLookback, boolean invert, String analysisName, int iPrice)
  {
    this(nLookback, invert, analysisName, iPrice, 0, 1);
  }

  public ConfigMonthlySMA(int nLookback, boolean invert, String analysisName, int iPrice, int iPredictIn,
      int iPredictOut)
  {
    super(iPredictIn, iPredictOut);
    this.nLookback = nLookback;
    this.iPrice = iPrice;
    this.invert = invert;
    this.analysisName = analysisName;
  }

  public static ConfigMonthlySMA genRandom(boolean invert, String analysisName, int iPrice)
  {
    while (true) {
      int nLookback = rng.nextInt(1, 18);
      ConfigMonthlySMA config = new ConfigMonthlySMA(nLookback, invert, analysisName, iPrice);
      if (config.isValid()) return config;
    }
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new MonthlySMAPredictor(this, assetNames[iPredictIn], assetNames[iPredictOut],
        analysisName == null ? assetNames[iPredictIn] : analysisName, brokerAccess);
  }

  @Override
  public boolean isValid()
  {
    return nLookback > 0;
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    final int N = 1000;
    for (int i = 0; i < N; ++i) {
      int nLookback = perturbLookback(this.nLookback);
      ConfigMonthlySMA perturbed = new ConfigMonthlySMA(nLookback, invert, analysisName, iPrice);
      if (!perturbed.isValid()) continue;
      return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

  private static int perturbLookback(int x)
  {
    if (x == 1) {
      x += rng.nextInt(0, 1);
    } else {
      x += rng.nextInt(-1, 1);
    }
    return x;
  }

  @Override
  public String toString()
  {
    return String.format("[%d]", nLookback);
  }
}
