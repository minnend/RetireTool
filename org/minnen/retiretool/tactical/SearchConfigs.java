package org.minnen.retiretool.tactical;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class SearchConfigs
{
  public static final SequenceStore store                     = new SequenceStore();
  public static final String        riskyName                 = "stock";
  public static final String        safeName                  = "3-month-treasuries";
  public static final String[]      assetNames                = new String[] { riskyName, safeName };
  public static final int           nEvalPerturb              = 10;
  public static final int           nEvalPerturbKnown         = 20;
  public static final int           nMaxSeeds                 = 10000;
  public static final boolean       initializeSingleDefenders = false;

  private static Simulation         sim;

  private static void setupData() throws IOException
  {
    TiingoFund fund = TiingoFund.fromSymbol("VFINX", true);
    Sequence stock = fund.data;
    System.out.printf("%s: [%s] -> [%s]\n", stock.getName(), TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, riskyName);
    store.add(stock.getIntegralSeq()); // pre-compute integral sequence to speed up SMA calculations

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, safeName);
  }

  /** Convenience method to run eval with default number of eval perturbations. */
  private static CumulativeStats eval(PredictorConfig config, String name)
  {
    return eval(config, name, nEvalPerturb);
  }

  /** Eval stats are *worst* results for the given number of perturbations. */
  private static CumulativeStats eval(PredictorConfig config, String name, int nPerturb)
  {
    Predictor pred = config.build(null, assetNames);
    sim.run(pred, name);
    CumulativeStats worstStats = CumulativeStats.calc(sim.returnsMonthly);

    for (int i = 0; i < nPerturb; ++i) {
      PredictorConfig perturbedConfig = config.genPerturbed();
      pred = perturbedConfig.build(null, assetNames);
      sim.run(pred, name);
      CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
      if (stats.prefer(worstStats) < 0) { // performance of strategy = worst over perturbed params
        worstStats = stats;
      }
    }

    worstStats.config = config;
    return worstStats;
  }

  /** Simple hill-climbing optimizer based on testing random perturbations. */
  private static CumulativeStats optimize(PredictorConfig baseConfig, CumulativeStats baseStats,
      ConfigGenerator generator)
  {
    int nTries = 0;
    while (nTries < 10) {
      PredictorConfig config = generator.genCandidate(baseConfig);
      CumulativeStats stats = eval(config, "Improved");
      if (stats.prefer(baseStats) > 0) {
        // System.out.printf(" %s (%s)\n", stats, config);
        baseConfig = config;
        baseStats = stats;
        nTries = 0;
      } else {
        ++nTries;
      }
    }
    return baseStats;
  }

  public static void main(String[] args) throws IOException
  {
    setupData();

    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 470 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    sim = new Simulation(store, guideSeq);
    sim.setCheckBusinessDays(false); // assume data is correct wrt business days (faster but slightly dangerous)

    // Set up "defenders" based on known-good configs.
    List<CumulativeStats> dominators = new ArrayList<>();

    if (initializeSingleDefenders) {
      for (PredictorConfig config : GeneratorSMA.knownConfigs) {
        CumulativeStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s  (%s)\n", stats, config);
        dominators.add(stats);
      }
      CumulativeStats.filter(dominators);
      System.out.printf("Initial defenders: %d\n", dominators.size());
      for (CumulativeStats x : dominators) {
        System.out.printf("Defender: %s  (%s)\n", x, x.config);
      }
    }

    // ConfigGenerator generator = new GeneratorSMA();
    ConfigGenerator generator = new GeneratorTwoSMA();

    // Search for better configs.
    Set<PredictorConfig> set = new HashSet<>();
    int nSeedsFound = 0;
    while (nSeedsFound < nMaxSeeds) {
      PredictorConfig config = generator.genRandom();
      if (set.contains(config)) continue;
      set.add(config);
      ++nSeedsFound;

      CumulativeStats stats = eval(config, "random");
      System.out.printf("%d: %s  (%s)\n", nSeedsFound, stats, config);
      CumulativeStats optimized = optimize(config, stats, generator);
      if (!optimized.isDominated(dominators)) {
        System.out.printf("New dominator: %s (%s) *******\n", optimized, optimized.config);
        dominators.add(optimized);
        CumulativeStats.filter(dominators);
        for (CumulativeStats x : dominators) {
          System.out.printf("Defender: %s  (%s)\n", x, x.config);
        }
      } else if (optimized.config != config) {
        System.out.printf("    %s  (%s)\n", optimized, optimized.config);
      }
    }
  }
}
