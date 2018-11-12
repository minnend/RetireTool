package org.minnen.retiretool.tactical;

import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class GeneratorSMA extends ConfigGenerator
{
  public static final long gap = 2 * TimeLib.MS_IN_DAY;

  @Override
  public PredictorConfig genRandom()
  {
    return ConfigSMA.genRandom(FinLib.AdjClose, gap);
  }

  @Override
  public PredictorConfig genCandidate(PredictorConfig baseConfig)
  {
    ConfigSMA config = (ConfigSMA) baseConfig;
    final int N = 1000;
    for (int i = 0; i < N; ++i) {
      int nLookbackTriggerA = ConfigSMA.perturbLookback(config.nLookbackTriggerA);
      int nLookbackTriggerB = 0;
      int nLookbackBaseA = ConfigSMA.perturbLookback(config.nLookbackBaseA);
      int nLookbackBaseB = ConfigSMA.perturbLookback(config.nLookbackBaseB);
      int margin = ConfigSMA.perturbMargin(config.margin);
      ConfigSMA perturbed = new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin,
          config.iPrice, config.minTimeBetweenFlips);
      if (perturbed.isValid()) return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

}
