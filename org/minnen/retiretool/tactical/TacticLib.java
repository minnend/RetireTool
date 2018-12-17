package org.minnen.retiretool.tactical;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
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

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, safeName);
  }

  public static CumulativeStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim,
      Comparator<CumulativeStats> comp)
  {
    return eval(config, name, nPerturb, sim, comp, null);
  }

  public static CumulativeStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim,
      Comparator<CumulativeStats> comp, List<CumulativeStats> allStats)
  {
    Predictor pred = config.build(null, TacticLib.assetNames);
    sim.run(pred, name);
    CumulativeStats worstStats = CumulativeStats.calc(sim.returnsDaily);
    worstStats.config = config;
    if (allStats != null) {
      allStats.clear();
      allStats.add(worstStats);
    }

    for (int i = 0; i < nPerturb; ++i) {
      PredictorConfig perturbedConfig = config.genPerturbed();
      pred = perturbedConfig.build(null, TacticLib.assetNames);
      sim.run(pred, name);
      CumulativeStats stats = CumulativeStats.calc(sim.returnsDaily);
      stats.config = perturbedConfig;
      if (allStats != null) allStats.add(stats);
      if (comp.compare(stats, worstStats) < 0) { // performance of strategy = worst over perturbed params
        worstStats = stats;
      }
    }

    return worstStats;
  }
}
