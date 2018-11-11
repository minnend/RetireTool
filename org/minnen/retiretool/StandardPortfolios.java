package org.minnen.retiretool;

import java.util.Arrays;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

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

  /**
   * Passive portfolio using Paul Merriman's aggressive asset allocation Source: https://paulmerriman.com/vanguard/
   */
  public Predictor merrimanAggressive()
  {
    // Admiral shares: VFIAX, VVIAX, VTMSX, VSIAX, VGSLX, VTMGX, VTRIX, VFSVX, VEMAX, VGRLX
    // Investor shares: "VFINX", "VIVAX", "VTMSX", "VISVX", "VGSIX", "VDVIX", "VTRIX", "VFSVX", "VEIEX", "VGXRX"

    // Final set mixes in some Fidelity funds that have more historical data.
    String[] assets = new String[] { "VFINX", "VIVAX", "VTMSX", "VISVX", "VGSIX", "FSIIX", "VTRIX", "FSCOX", "VEIEX",
        "FIREX" };
    double[] mix = new double[] { 11, 11, 11, 12, 5, 9, 18, 9, 9, 5 };
    return passive("Merriman Aggressive", assets, mix);
  }

  /** Standard dual-momentum strategy with 12-month look-back. */
  public Predictor dualMomentum(int nMaxKeep, String safeAsset, String... riskyAssets)
  {
    return dualMomentum(240, 220, nMaxKeep, safeAsset, riskyAssets);
  }

  public Predictor dualMomentum(int nBaseA, int nBaseB, int nMaxKeep, String safeAsset, String... riskyAssets)
  {
    FeatureExtractor feDualMom = new Momentum(20, 1, nBaseA, nBaseB, Momentum.ReturnOrMul.Return,
        Momentum.CompoundPeriod.Weekly, FinLib.Close);
    int dualMomAge = (nBaseA + nBaseB + 20) / 40;
    Stump stump = new Stump(0, 0.0, false, 5.0);
    String[] allAssets = mergeAssets(safeAsset, riskyAssets);
    Predictor predictor = new AdaptivePredictor(feDualMom, stump, nMaxKeep, safeAsset, sim.broker.accessObject,
        allAssets);
    predictor.name = String.format("Dual_Momentum[%d]", dualMomAge);
    return predictor;
  }

  public Predictor simpleSMA(int nMonths, String riskyAsset, String safeAsset)
  {
    int n = nMonths * 20;
    PredictorConfig config = new ConfigSMA(5, 0, n, n - 5, 10, FinLib.AdjClose, 2 * TimeLib.MS_IN_DAY);
    Predictor predictor = config.build(sim.broker.accessObject, new String[] { riskyAsset, safeAsset });
    predictor.name = String.format("SMA:%d", nMonths);
    return predictor;
  }

  public Sequence run(Predictor predictor, long timeSimStart, long timeSimEnd)
  {
    return run(predictor, timeSimStart, timeSimEnd, true);
  }

  public Sequence run(Predictor predictor, long timeSimStart, long timeSimEnd, boolean bVerbose)
  {
    Sequence returns = sim.run(predictor, timeSimStart, timeSimEnd, predictor.name);
    if (bVerbose) System.out.println(CumulativeStats.calc(returns));
    return returns;
  }

  public String[] mergeAssets(String last, String... assets)
  {
    String[] allAssets = new String[assets.length + 1];
    System.arraycopy(assets, 0, allAssets, 0, assets.length);
    allAssets[assets.length] = last;
    return allAssets;
  }
}
