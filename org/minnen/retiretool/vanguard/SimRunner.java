package org.minnen.retiretool.vanguard;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;

public class SimRunner implements PortfolioRunner
{
  private Simulation sim;
  private long       timeSimStart;
  private long       timeSimEnd;
  private int        durStatsMonths;

  public SimRunner(Simulation sim, long timeSimStart, long timeSimEnd, int durStatsMonths)
  {
    this.sim = sim;
    this.timeSimStart = timeSimStart;
    this.timeSimEnd = timeSimEnd;
    this.durStatsMonths = durStatsMonths;
  }

  @Override
  public FeatureVec run(DiscreteDistribution portfolio)
  {
    portfolio = portfolio.removeZeroWeights(1e-5);
    PredictorConfig config = new ConfigMixed(portfolio, ConfigConst.wrap(portfolio.names));
    Predictor pred = config.build(sim.broker.accessObject, portfolio.names);
    String name = portfolio.toStringWithNames(0);
    sim.run(pred, timeSimStart, timeSimEnd, name);
    return SummaryTools.calcStats(sim.returnsMonthly, durStatsMonths);
  }

}
