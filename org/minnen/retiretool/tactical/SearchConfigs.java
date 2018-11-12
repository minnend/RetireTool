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
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class SearchConfigs
{
  public static final SequenceStore     store             = new SequenceStore();
  public static final String            riskyName         = "stock";
  public static final String            safeName          = "3-month-treasuries";
  public static final String[]          assetNames        = new String[] { riskyName, safeName };
  public static final long              gap               = 2 * TimeLib.MS_IN_DAY;
  public static final int               nEvalPerturb      = 20;
  public static final int               nEvalPerturbKnown = 100;
  public static final int               nMaxSeeds         = 10000;

  private static Simulation             sim;

  // List of good parameters for single SMA.
  public static final int[][]           knownParams       = new int[][] { new int[] { 20, 240, 150, 25 },
      new int[] { 15, 259, 125, 21 }, new int[] { 34, 182, 82, 684 }, new int[] { 5, 184, 44, 353 },
      new int[] { 9, 176, 167, 1243 }, new int[] { 14, 246, 100, 900 }, new int[] { 21, 212, 143, 213 },
      new int[] { 3, 205, 145, 438 }, new int[] { 15, 155, 105, 997 }, new int[] { 20, 126, 54, 690 },
      new int[] { 32, 116, 94, 938 }, new int[] { 22, 124, 74, 904 }, new int[] { 19, 201, 143, 207 },
      new int[] { 13, 186, 177, 1127 }, new int[] { 19, 147, 92, 885 }, new int[] { 18, 213, 79, 723 }, };

  public static final PredictorConfig[] knownConfigs      = new PredictorConfig[knownParams.length];

  static {
    for (int i = 0; i < knownParams.length; ++i) {
      int[] p = knownParams[i];
      knownConfigs[i] = new ConfigSMA(p[0], 0, p[1], p[2], p[3], FinLib.AdjClose, gap);
    }
  }

  private static void setupData() throws IOException
  {
    TiingoFund fund = TiingoFund.fromSymbol("VFINX", true);
    Sequence stock = fund.data;
    System.out.printf("%s: [%s] -> [%s]\n", stock.getName(), TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, riskyName);
    store.add(stock.getIntegralSeq());

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, safeName);
  }

  private static PredictorConfig genRandom()
  {
    return ConfigSMA.genRandom(FinLib.AdjClose, gap);
  }

  private static PredictorConfig genCandidate(PredictorConfig config)
  {
    if (config instanceof ConfigSMA) {
      return genCandidateSMA((ConfigSMA) config);
    }
    throw new IllegalArgumentException("Unsupported type: " + config.getClass().getName());
  }

  private static ConfigSMA genCandidateSMA(ConfigSMA config)
  {
    final int N = 1000;
    for (int i = 0; i < N; ++i) {
      int nLookbackTriggerA = ConfigSMA.perturbLookback(config.nLookbackTriggerA);
      int nLookbackTriggerB = 0;
      int nLookbackBaseA = ConfigSMA.perturbLookback(config.nLookbackBaseA);
      int nLookbackBaseB = ConfigSMA.perturbLookback(config.nLookbackBaseB);
      int margin = ConfigSMA.perturbMargin(config.margin);
      ConfigSMA perturbed = new ConfigSMA(nLookbackTriggerA, nLookbackTriggerB, nLookbackBaseA, nLookbackBaseB, margin,
          config.iPrice, config.minTimeBetweenFlips);
      if (perturbed.isValid()) return perturbed;
    }
    throw new RuntimeException(String.format("Failed to generate a valid perturbed config after %d tries.", N));
  }

  private static CumulativeStats eval(PredictorConfig config, String name)
  {
    return eval(config, name, nEvalPerturb);
  }

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

  private static CumulativeStats optimize(PredictorConfig baseConfig, CumulativeStats baseStats)
  {
    int nTries = 0;
    while (nTries < 10) {
      PredictorConfig config = genCandidate(baseConfig);
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

    List<CumulativeStats> dominators = new ArrayList<>();
    for (PredictorConfig config : knownConfigs) {
      CumulativeStats stats = eval(config, "Known", nEvalPerturbKnown);
      System.out.printf("%s  (%s)\n", stats, config);
      dominators.add(stats);
    }
    CumulativeStats.filter(dominators);
    System.out.printf("Initial defenders: %d\n", dominators.size());
    for (CumulativeStats x : dominators) {
      System.out.printf("Defender: %s  (%s)\n", x, x.config);
    }

    Set<PredictorConfig> set = new HashSet<>();
    int nSeedsFound = 0;
    while (nSeedsFound < nMaxSeeds) {
      PredictorConfig config = genRandom();
      if (set.contains(config)) continue;
      set.add(config);
      ++nSeedsFound;

      CumulativeStats stats = eval(config, "random");
      System.out.printf("%d: %s  (%s)\n", nSeedsFound, stats, config);
      CumulativeStats optimized = optimize(config, stats);
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
