package org.minnen.retiretool.predictor.optimize;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;

public class Optimizer
{
  /**
   * Select optimal config via a grid search.
   * 
   * Run each config using the given simulator and time period. Select the best result as the optimal config. The best
   * result is calculated as a simple combination of CAGR and Drawdown.
   * 
   * @param scanner generates valid configs.
   * @param sim simulator for testing configs
   * @param timeStart start simulations at this time.
   * @param timeEnd end simulations at this time.
   * @param assetNames array of all asset names available to the predictor.
   * @return best predictor config.
   */
  public static PredictorConfig grid(ConfigScanner<? extends PredictorConfig> scanner, Simulation sim, long timeStart,
      long timeEnd, String... assetNames)
  {
    PredictorConfig bestConfig = null;
    double bestScore = 0.0;
    while (true) {
      PredictorConfig config = scanner.get();
      if (config == null) break;
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
