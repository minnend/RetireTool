package org.minnen.retiretool.predictor.optimize;

import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.TimeLib;

public class Optimizer
{
  public static PredictorConfig grid(ConfigScanner<? extends PredictorConfig> scanner, Simulation sim,
      String... assetNames)
  {
    return grid(scanner, sim, TimeLib.TIME_BEGIN, TimeLib.TIME_END, assetNames);
  }

  public static PredictorConfig grid(ConfigScanner<? extends PredictorConfig> scanner, Simulation sim, long timeStart,
      long timeEnd, String... assetNames)
  {
    List<PredictorConfig> configs = scanner.getAll();
    PredictorConfig bestConfig = null;
    double bestScore = 0.0;
    for (PredictorConfig config : configs) {
      Predictor predictor = config.build(sim.broker.accessObject, assetNames);
      sim.run(predictor, timeStart, timeEnd, "GridOpt: " + config);
      CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
      double score = stats.scoreSimple();
      if (bestConfig == null || score > bestScore) {
        bestConfig = config;
        bestScore = score;
      }
    }
    return bestConfig;
  }
}
