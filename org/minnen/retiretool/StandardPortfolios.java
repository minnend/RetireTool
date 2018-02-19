package org.minnen.retiretool;

import java.util.Arrays;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;

public class StandardPortfolios
{
  private final Simulation sim;

  public StandardPortfolios(Simulation sim)
  {
    this.sim = sim;
  }

  /** Passive portfolio with a single fund. */
  public Predictor passive(String asset)
  {
    return passive(asset, asset);
  }

  /** Passive portfolio with a single fund. */
  public Predictor passive(String name, String asset)
  {
    PredictorConfig config = new ConfigConst(asset);
    Predictor predictor = config.build(sim.broker.accessObject, asset);
    predictor.name = name;
    return predictor;
  }

  /** Passive portfolio with fixed allocations to a set of funds. */
  public Predictor passive(String name, String[] assets, double... mix)
  {
    assert assets.length > 0 && assets.length == mix.length;
    PredictorConfig config = new ConfigMixed(new DiscreteDistribution(assets, mix), ConfigConst.wrap(assets));
    Predictor predictor = config.build(sim.broker.accessObject, assets);
    predictor.name = name;
    return predictor;
  }

  /** Passive portfolio with equal allocations to each fund. */
  public Predictor passiveEqualWeight(String name, String... assets)
  {
    double[] mix = new double[assets.length];
    Arrays.fill(mix, 1.0 / assets.length);
    return passive(name, assets, mix);
  }

  /** Standard dual-momentum strategy with 12-month look-back. */
  public Predictor dualMomentum(String safeAsset, String... riskyAssets)
  {
    return dualMomentum(240, 220, safeAsset, riskyAssets);
  }

  public Predictor dualMomentum(int nBaseA, int nBaseB, String safeAsset, String... riskyAssets)
  {
    FeatureExtractor feDualMom = new Momentum(20, 1, nBaseA, nBaseB, Momentum.ReturnOrMul.Return,
        Momentum.CompoundPeriod.Weekly, FinLib.Close);
    int dualMomAge = (nBaseA + nBaseB + 20) / 40;
    Stump stump = new Stump(0, 0.0, false, 5.0);
    String[] allAssets = new String[riskyAssets.length + 1];
    for (int i = 0; i < riskyAssets.length; ++i) {
      allAssets[i] = riskyAssets[i];
    }
    allAssets[riskyAssets.length] = safeAsset;
    Predictor predictor = new AdaptivePredictor(feDualMom, stump, 1, safeAsset, sim.broker.accessObject, allAssets);
    predictor.name = String.format("Dual_Momentum[%d]", dualMomAge);
    return predictor;
  }

  public Sequence run(Predictor predictor, long timeSimStart, long timeSimEnd)
  {
    Sequence returns = sim.run(predictor, timeSimStart, timeSimEnd, predictor.name);
    System.out.println(CumulativeStats.calc(returns));
    return returns;
  }
}
