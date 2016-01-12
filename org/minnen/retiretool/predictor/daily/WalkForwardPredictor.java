package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.optimize.ConfigScanner;
import org.minnen.retiretool.stats.CumulativeStats;

public class WalkForwardPredictor extends Predictor
{
  private final ConfigScanner  scanner;
  private Predictor            predictor;
  private DiscreteDistribution prevDistribution = null;
  private final Simulation     sim;

  public WalkForwardPredictor(Simulation sim, ConfigScanner scanner, BrokerInfoAccess brokerAccess,
      String[] assetChoices)
  {
    super("WalkForward", brokerAccess, assetChoices);
    this.sim = sim;
    this.scanner = scanner;
    this.predictorType = PredictorType.Distribution;
  }

  @Override
  protected void calcDistribution(DiscreteDistribution distribution)
  {
    if (prevDistribution != null && !brokerAccess.getTimeInfo().isLastDayOfWeek) {
      distribution.copyFrom(prevDistribution);
      return;
    }

    if (predictor == null || brokerAccess.getTimeInfo().isLastDayOfMonth) {
      scanner.reset();

      // List<CumulativeStats> cstats = new ArrayList<CumulativeStats>();
      // while (true) {
      // PredictorConfig config = scanner.get();
      // if (config == null) break;
      // // System.out.println(config);
      // predictor = config.build(sim.broker.accessObject, assetChoices);
      // Sequence ret = sim.run(predictor, config.toString());
      // CumulativeStats stats = CumulativeStats.calc(ret);
      // stats.config = config;
      // cstats.add(stats);
      // }
      // CumulativeStats.filter(cstats);
      // PredictorConfig config = cstats.get(0).config;

      PredictorConfig config = scanner.get();
      predictor = config.build(brokerAccess, assetChoices);
    }

    predictor.calcDistribution(distribution);
    if (prevDistribution == null) {
      prevDistribution = new DiscreteDistribution(distribution);
    } else {
      prevDistribution.copyFrom(distribution);
    }
  }

  @Override
  public void reset()
  {
    prevDistribution = null;
  }
}
