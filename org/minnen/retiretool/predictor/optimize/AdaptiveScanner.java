package org.minnen.retiretool.predictor.optimize;

import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;
import org.minnen.retiretool.util.FinLib;

public class AdaptiveScanner extends ConfigScanner<ConfigAdaptive>
{
  public final int   iPrice;

  // private IntScanner trigger = IntScanner.fromList(20, 30, 40, 60);
  // // private IntScanner trigger = IntScanner.fromRange(20, 5, 60);
  // private IntScanner baseA = IntScanner.fromList(80, 100, 120);
  // private IntScanner baseB = IntScanner.fromList(60, 80, 100);
  // // private IntScanner baseA = IntScanner.fromRange(60, 10, 120);
  // // private IntScanner baseB = IntScanner.fromRange(40, 10, 100);
  // private IntScanner maxKeep = IntScanner.fromList(4);

  private IntScanner trigger = IntScanner.fromList(20, 40);
  private IntScanner baseA   = IntScanner.fromList(60, 100, 120);
  private IntScanner baseB   = IntScanner.fromList(40, 60, 80);
  private IntScanner maxKeep = IntScanner.fromList(4);

  // private IntScanner trigger = IntScanner.fromList(20, 30, 40, 60);
  // private IntScanner baseA = IntScanner.fromList(80, 100, 120);
  // private IntScanner baseB = IntScanner.fromList(60, 80, 100);
  // private IntScanner maxKeep = IntScanner.fromList(4, 5);

  public AdaptiveScanner(int iPrice)
  {
    this.iPrice = iPrice;
    parameters.add(trigger);
    parameters.add(baseA);
    parameters.add(baseB);
    parameters.add(maxKeep);
  }

  public ConfigAdaptive get()
  {
    while (!isDone()) {
      ConfigAdaptive config = new ConfigAdaptive(-1, -1, Weighting.Equal, trigger.getValue(), baseA.getValue(),
          baseB.getValue(), 0.5, maxKeep.getValue(), 2, TradeFreq.Weekly, iPrice);
      advance();
      if (config.isValid()) return config;
    }
    return null;
  }
}
