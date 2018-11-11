package org.minnen.retiretool.tactical;

import java.io.IOException;
import java.util.HashSet;
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
    return worstStats;
  }

  private static int prefer(CumulativeStats s1, CumulativeStats s2)
  {
    if (s1.dominates(s2)) return 1;
    if (s2.dominates(s1)) return -1;

    double score1 = s1.scoreSimple();
    double score2 = s2.scoreSimple();
    if (score1 > score2 + 0.01) return 1;
    if (score2 > score1 + 0.01) return -1;

    return s1.compareTo(s2);
  }

  private static ConfigSMA optimize(ConfigSMA baseConfig, CumulativeStats baseStats)
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

    return baseConfig;
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
        new ConfigSMA(5, 0, 184, 44, 353, FinLib.AdjClose, gap), };
    for (ConfigSMA config : goodConfigs) {
      System.out.printf("%s  (%s)\n", eval(config, "Good", 20), config);
    }

    Set<ConfigSMA> set = new HashSet<>();
    final int nMaxSeeds = 100;
    int nSeedsFound = 0;
    while (nSeedsFound < nMaxSeeds) {
      ConfigSMA config = ConfigSMA.genRandom(FinLib.AdjClose, gap);
      if (set.contains(config)) continue;
      set.add(config);
      ++nSeedsFound;

      CumulativeStats stats = eval(config, "random");
      System.out.printf("%d: %s\n", nSeedsFound, stats);
      optimize(config, stats);
    }
  }
}
