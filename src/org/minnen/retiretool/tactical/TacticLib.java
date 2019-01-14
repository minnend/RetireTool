package org.minnen.retiretool.tactical;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.AllStats;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class TacticLib
{
  public static final String   riskyName  = "stock";
  public static final String   safeName   = "3-month-treasuries";
  public static final String[] assetNames = new String[] { riskyName, safeName };

  public static void setupData(String symbol, SequenceStore store) throws IOException
  {
    Sequence seq = DataIO.loadSymbol(symbol);
    System.out.printf("%s (Daily): [%s] -> [%s]\n", symbol, TimeLib.formatDate(seq.getStartMS()),
        TimeLib.formatDate(seq.getEndMS()));
    store.add(seq, riskyName);

    Sequence tbills = FinLib.inferAssetFrom3MonthTreasuries();

    // Extend t-bills since data may not be as recent as other symbols.
    FeatureVec lastTBill = tbills.getLast();
    int i = seq.getClosestIndex(tbills.getEndMS());
    assert seq.getTimeMS(i) == tbills.getEndMS();
    for (int j = i + 1; j < seq.size(); ++j) {
      tbills.addData(lastTBill.dup(), seq.getTimeMS(j));
    }

    store.add(tbills, safeName);
  }

  public static AllStats eval(PredictorConfig config, String name, Simulation sim)
  {
    return eval(config, name, 0, sim, null, null, null);
  }

  public static AllStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim,
      Comparator<AllStats> comp, Sequence baselineMonthlyReturns)
  {
    return eval(config, name, nPerturb, sim, comp, baselineMonthlyReturns, null);
  }

  public static AllStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim,
      Comparator<AllStats> comp, Sequence baselineMonthlyReturns, List<AllStats> statsList)
  {
    Predictor pred = config.build(null, TacticLib.assetNames);
    sim.run(pred, name);
    AllStats worstStats = new AllStats();
    worstStats.cumulative = CumulativeStats.calc(sim.returnsDaily, sim.returnsMonthly, true);
    if (baselineMonthlyReturns != null) {
      assert baselineMonthlyReturns.matches(sim.returnsMonthly);
      ComparisonStats comparison = ComparisonStats.calc(sim.returnsMonthly, baselineMonthlyReturns, 0.25);
      worstStats.comparisons.put(baselineMonthlyReturns.getName(), comparison);
    }
    worstStats.config = config;
    if (statsList != null) {
      statsList.clear();
      statsList.add(worstStats);
    }

    Set<PredictorConfig> tested = new HashSet<>();
    for (int i = 0; i < nPerturb; ++i) {
      PredictorConfig perturbedConfig = config.genPerturbed();
      if (tested.contains(perturbedConfig)) {
        System.out.println("DUP!");
        continue;
      }
      tested.add(perturbedConfig);

      pred = perturbedConfig.build(null, TacticLib.assetNames);
      sim.run(pred, name);
      AllStats stats = new AllStats();
      stats.cumulative = CumulativeStats.calc(sim.returnsDaily, sim.returnsMonthly, true);
      if (baselineMonthlyReturns != null) {
        assert baselineMonthlyReturns.matches(sim.returnsMonthly);
        ComparisonStats comparison = ComparisonStats.calc(sim.returnsMonthly, baselineMonthlyReturns, 0.25);
        stats.comparisons.put(baselineMonthlyReturns.getName(), comparison);
      }
      stats.config = perturbedConfig;
      if (statsList != null) statsList.add(stats);
      if (comp.compare(stats, worstStats) < 0) { // performance of strategy = worst over perturbed params
        worstStats = stats;
      }
    }

    return worstStats;
  }
}
