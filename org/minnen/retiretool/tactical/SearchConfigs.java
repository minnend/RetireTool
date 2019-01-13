package org.minnen.retiretool.tactical;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.stats.AllStats;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.tactical.ConfigGenerator.Mode;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class SearchConfigs
{
  public static final SequenceStore        store                     = new SequenceStore();
  public static final int                  nEvalPerturb              = 20;
  public static final int                  nEvalPerturbKnown         = 20;
  public static final int                  nMaxSeeds                 = 10000;
  public static final boolean              initializeSingleDefenders = false;
  public static final boolean              initializeDoubleDefenders = false;
  public static final boolean              initializeTripleDefenders = false;
  public static final Mode                 searchMode                = Mode.EXTEND;
  public static final String               targetNum                 = "three";
  public static final String               saveFilename              = String.format("%s-sma-winners-%s.txt", targetNum,
      TimeLib.formatTimeSig(TimeLib.getTime()));

  // public static final Comparator<AllStats> comp = AllStats
  // .getCompare(CumulativeStats.getComparatorBasic());
  // Comparator<AllStats> compCumDom = AllStats
  // .getCompare(CumulativeStats.getComparatorDominates());

  public static final Comparator<AllStats> comp                      = AllStats
      .getCompare(ComparisonStats.getComparatorBasic(), "Baseline");

  public static final Comparator<AllStats> compCumDom                = AllStats
      .getCompare(ComparisonStats.getComparatorDominates(), "Baseline");

  private static Simulation                sim;
  private static Sequence                  baselineMonthlyReturns, baselineDailyReturns;

  /** Convenience method to run eval with default number of eval perturbations. */
  private static AllStats eval(PredictorConfig config, String name)
  {
    return eval(config, name, nEvalPerturb);
  }

  /** Eval stats are *worst* results for the given number of perturbations. */
  private static AllStats eval(PredictorConfig config, String name, int nPerturb)
  {
    return TacticLib.eval(config, name, nPerturb, sim, comp, baselineMonthlyReturns);
  }

  /** Simple hill-climbing optimizer based on testing random perturbations. */
  private static AllStats optimize(PredictorConfig baseConfig, AllStats baseStats, ConfigGenerator generator)
  {
    int nTries = 0;
    while (nTries < 10) {
      PredictorConfig config = generator.genCandidate(baseConfig);
      AllStats stats = eval(config, "Improved");
      if (comp.compare(stats, baseStats) > 0) {
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
    TacticLib.setupData("VFINX", store);

    Sequence stock = store.get(TacticLib.riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 470 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    sim = new Simulation(store, guideSeq);
    sim.setCheckBusinessDays(false); // assume data is correct wrt business days (faster but slightly dangerous)

    ConfigConst baselineConfig = new ConfigConst(stock.getName());
    AllStats baselineStats = TacticLib.eval(baselineConfig, "Baseline", sim);
    baselineDailyReturns = baselineStats.cumulative.dailyReturns;
    baselineMonthlyReturns = baselineStats.cumulative.monthlyReturns;
    System.out.printf("%s: %s\n", baselineDailyReturns.getName(), baselineStats);

    // Set up "defenders" based on known-good configs.
    List<AllStats> dominators = new ArrayList<>();

    if (initializeSingleDefenders) {
      for (PredictorConfig config : GeneratorSMA.knownConfigs) {
        AllStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeDoubleDefenders) {
      for (PredictorConfig config : GeneratorTwoSMA.knownConfigs) {
        AllStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeTripleDefenders) {
      for (PredictorConfig config : GeneratorThreeSMA.knownConfigs) {
        AllStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }

    AllStats.filter(dominators, compCumDom);
    System.out.printf("Initial defenders: %d\n", dominators.size());
    for (AllStats x : dominators) {
      System.out.printf("Defender: %s (%s)\n", x, x.config);
    }

    ConfigGenerator generator;
    if (targetNum.equals("one")) {
      generator = new GeneratorSMA();
    } else if (targetNum.equals("two")) {
      generator = new GeneratorTwoSMA(searchMode);
    } else if (targetNum.equals("three")) {
      generator = new GeneratorThreeSMA(searchMode);
    } else {
      throw new RuntimeException("Invalid target: " + targetNum);
    }

    // Search for better configs.
    Set<PredictorConfig> set = new HashSet<>();
    int nSeedsFound = 0;

    System.out.printf("Save file: %s\n", saveFilename);
    try (Writer writer = new Writer(new File(DataIO.getOutputPath(), saveFilename))) {
      while (nSeedsFound < nMaxSeeds) {
        PredictorConfig config = generator.genRandom();
        if (set.contains(config)) continue;
        set.add(config);
        ++nSeedsFound;

        AllStats stats = eval(config, "random");
        System.out.printf("%d: %s (%s)\n", nSeedsFound, stats, config);
        AllStats optimized = optimize(config, stats, generator);
        // System.out.printf(" Optimized: %s\n", optimized);
        if (!optimized.isBeaten(dominators, compCumDom)) {
          System.out.printf("New dominator: %s (%s) *******\n", optimized, optimized.config);
          writer.write("New: %s  %s\n", optimized, optimized.config);
          dominators.add(optimized);
          AllStats.filter(dominators, compCumDom);
          for (AllStats x : dominators) {
            System.out.printf(" Defender: %s (%s)\n", x, x.config);
            writer.write(" Defender: %s  %s\n", x, x.config);
          }
          writer.flush();
        } else if (optimized.config != config) {
          System.out.printf(" %s (%s)\n", optimized, optimized.config);
        }
      }
    }
  }
}
