package org.minnen.retiretool.predictor.optimize;

import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;

public class AdaptiveScanner extends ConfigScanner<ConfigAdaptive>
{
  IntScanner trigger = IntScanner.fromList(20, 30, 40, 60);
  // IntScanner trigger = IntScanner.fromRange(20, 5, 60);
  IntScanner baseA   = IntScanner.fromList(80, 100, 120);
  IntScanner baseB   = IntScanner.fromList(60, 80, 100);
  // IntScanner baseA = IntScanner.fromRange(60, 10, 120);
  // IntScanner baseB = IntScanner.fromRange(40, 10, 100);
  IntScanner maxKeep = IntScanner.fromList(4, 5);

  public AdaptiveScanner()
  {
    parameters.add(trigger);
    parameters.add(baseA);
    parameters.add(baseB);
    parameters.add(maxKeep);
  }

  public ConfigAdaptive get()
  {
    while (!isDone()) {
      ConfigAdaptive config = new ConfigAdaptive(-1, -1, Weighting.Equal, trigger.getValue(), baseA.getValue(),
          baseB.getValue(), 0.5, maxKeep.getValue(), 2, TradeFreq.Weekly, 0);
      advance();
      if (config.isValid()) return config;
    }
    return null;
  }
}
