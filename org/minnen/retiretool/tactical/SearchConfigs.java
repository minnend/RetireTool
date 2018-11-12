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
  public static final SequenceStore store        = new SequenceStore();
  public static final String        riskyName    = "stock";
  public static final String        safeName     = "3-month-treasuries";
  public static final String[]      assetNames   = new String[] { riskyName, safeName };
  public static final long          gap          = 2 * TimeLib.MS_IN_DAY;
  public static final int           nEvalPerturb = 10;

  private static Simulation         sim;

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

  private static ConfigSMA genCandidate(ConfigSMA config)
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
      ConfigSMA perturbedConfig = (ConfigSMA) config.genPerturbed();
      pred = perturbedConfig.build(null, assetNames);
      sim.run(pred, name);
      CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
      if (stats.compareTo(worstStats) < 0) { // TODO improve comparison
        worstStats = stats;
      }
    }

    worstStats.config = config;
    return worstStats;
  }

  private static boolean isDominated(CumulativeStats challenger, List<CumulativeStats> defenders)
  {
    double bestCagr = 0.0;
    double bestDrawdown = 999.0;
    for (CumulativeStats defender : defenders) {
      if (defender.dominates(challenger) > 0) return true;
      bestCagr = Math.max(bestCagr, defender.cagr);
      bestDrawdown = Math.min(bestDrawdown, defender.drawdown);
    }

    // Nothing dominates directly, but we still reject challenger unless it improves best CAGR or drawdown.
    return (challenger.cagr < bestCagr + 0.008 && challenger.drawdown > bestDrawdown - 0.1);
  }

  private static int prefer(CumulativeStats s1, CumulativeStats s2)
  {
    double score1 = s1.scoreSimple();
    double score2 = s2.scoreSimple();
    if (score1 > score2 + 0.1) return 1;
    if (score2 > score1 + 0.1) return -1;

    if (s1.cagr > s2.cagr + 0.1) return 1;
    if (s2.cagr > s1.cagr + 0.1) return -1;

    if (s1.drawdown < s2.drawdown - 0.4) return 1;
    if (s2.drawdown < s1.drawdown - 0.4) return -1;

    if (s1.annualPercentiles[2] > s2.annualPercentiles[2] + 0.1) return 1;
    if (s2.annualPercentiles[2] > s1.annualPercentiles[2] + 0.1) return -1;

    return 0;
  }

  private static CumulativeStats optimize(ConfigSMA baseConfig, CumulativeStats baseStats)
  {
    int nTries = 0;
    while (nTries < 10) {
      ConfigSMA config = genCandidate(baseConfig);
      CumulativeStats stats = eval(config, "Improved");
      if (prefer(stats, baseStats) > 0) {
        System.out.printf(" %s  (%s)\n", stats, config);
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

    ConfigSMA[] goodConfigs = new ConfigSMA[] { // list of good configs
        new ConfigSMA(20, 0, 240, 150, 25, FinLib.AdjClose, gap),
        new ConfigSMA(15, 0, 259, 125, 21, FinLib.AdjClose, gap),
        new ConfigSMA(34, 0, 182, 82, 684, FinLib.AdjClose, gap),
        new ConfigSMA(5, 0, 184, 44, 353, FinLib.AdjClose, gap),
        new ConfigSMA(9, 0, 176, 167, 1243, FinLib.AdjClose, gap),
        new ConfigSMA(14, 0, 246, 100, 900, FinLib.AdjClose, gap),
        new ConfigSMA(21, 0, 212, 143, 213, FinLib.AdjClose, gap),
        new ConfigSMA(3, 0, 205, 145, 438, FinLib.AdjClose, gap),
        new ConfigSMA(15, 0, 155, 105, 997, FinLib.AdjClose, gap),
        new ConfigSMA(20, 0, 126, 54, 690, FinLib.AdjClose, gap), };

    List<CumulativeStats> dominators = new ArrayList<>();
    for (ConfigSMA config : goodConfigs) {
      CumulativeStats stats = eval(config, "Good", 100);
      System.out.printf("%s  (%s)\n", stats, config);
      dominators.add(stats);
    }
    CumulativeStats.filter(dominators);
    System.out.printf("Initial defenders: %d\n", dominators.size());
    for (CumulativeStats x : dominators) {
      System.out.printf("Defender: %s  (%s)\n", x, x.config);
    }

    Set<ConfigSMA> set = new HashSet<>();
    final int nMaxSeeds = 10000;
    int nSeedsFound = 0;
    while (nSeedsFound < nMaxSeeds) {
      ConfigSMA config = ConfigSMA.genRandom(FinLib.AdjClose, gap);
      if (set.contains(config)) continue;
      set.add(config);
      ++nSeedsFound;

      CumulativeStats stats = eval(config, "random");
      System.out.printf("%d: %s\n", nSeedsFound, stats);
      CumulativeStats optimized = optimize(config, stats);
      if (!isDominated(optimized, dominators)) {
        System.out.printf("New dominator: %s (%s) *****\n", optimized, optimized.config);
        dominators.add(optimized);
        CumulativeStats.filter(dominators);
        for (CumulativeStats x : dominators) {
          System.out.printf("Defender: %s  (%s)\n", x, x.config);
        }
      }
    }
  }
}
