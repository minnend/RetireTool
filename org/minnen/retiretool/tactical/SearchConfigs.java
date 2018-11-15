package org.minnen.retiretool.tactical;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.tactical.ConfigGenerator.Mode;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class SearchConfigs
{
  public static final SequenceStore store                     = new SequenceStore();
  public static final int           nEvalPerturb              = 10;
  public static final int           nEvalPerturbKnown         = 50;
  public static final int           nMaxSeeds                 = 10000;
  public static final boolean       initializeSingleDefenders = false;
  public static final boolean       initializeDoubleDefenders = true;
  public static final boolean       initializeTripleDefenders = false;
  public static final Mode          searchMode                = Mode.EXTEND;
  public static final String        saveFilename              = String.format("three-sma-winners-%s.txt",
      TimeLib.formatTimeSig(TimeLib.getTime()));

  private static Simulation         sim;

  /** Convenience method to run eval with default number of eval perturbations. */
  private static CumulativeStats eval(PredictorConfig config, String name)
  {
    return eval(config, name, nEvalPerturb);
  }

  /** Eval stats are *worst* results for the given number of perturbations. */
  private static CumulativeStats eval(PredictorConfig config, String name, int nPerturb)
  {
    return TacticLib.eval(config, name, nPerturb, sim);
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
    TacticLib.setupData("VFINX", store);

    Sequence stock = store.get(TacticLib.riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 470 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    sim = new Simulation(store, guideSeq);
    sim.setCheckBusinessDays(false); // assume data is correct wrt business days (faster but slightly dangerous)

    // Set up "defenders" based on known-good configs.
    List<CumulativeStats> dominators = new ArrayList<>();

    // TODO this vs. what's in the dashboard?
    ConfigMulti tacticalConfig = ConfigMulti.buildTactical(FinLib.AdjClose, 0, 1);
    CumulativeStats tacticalStats = eval(tacticalConfig, "Tactical", nEvalPerturbKnown);
    System.out.println(tacticalConfig);
    dominators.add(tacticalStats);

    if (initializeSingleDefenders) {
      for (PredictorConfig config : GeneratorSMA.knownConfigs) {
        CumulativeStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeDoubleDefenders) {
      for (PredictorConfig config : GeneratorTwoSMA.knownConfigs) {
        CumulativeStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeTripleDefenders) {
      for (PredictorConfig config : GeneratorThreeSMA.knownConfigs) {
        CumulativeStats stats = eval(config, "Known", nEvalPerturbKnown);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    CumulativeStats.filter(dominators);
    System.out.printf("Initial defenders: %d\n", dominators.size());
    for (CumulativeStats x : dominators) {
      System.out.printf("Defender: %s (%s)\n", x, x.config);
    }

    // ConfigGenerator generator = new GeneratorSMA();
    // ConfigGenerator generator = new GeneratorTwoSMA(searchMode);
    ConfigGenerator generator = new GeneratorThreeSMA(searchMode);

    // Search for better configs.
    Set<PredictorConfig> set = new HashSet<>();
    int nSeedsFound = 0;

    System.out.printf("Save file: %s\n", saveFilename);
    try (Writer writer = new Writer(new File(DataIO.outputPath, saveFilename))) {
      while (nSeedsFound < nMaxSeeds) {
        PredictorConfig config = generator.genRandom();
        if (set.contains(config)) continue;
        set.add(config);
        ++nSeedsFound;

        CumulativeStats stats = eval(config, "random");
        System.out.printf("%d: %s (%s)\n", nSeedsFound, stats, config);
        CumulativeStats optimized = optimize(config, stats, generator);
        if (!optimized.isDominated(dominators)) {
          System.out.printf("New dominator: %s (%s) *******\n", optimized, optimized.config);
          writer.write("New: %s  %s\n", optimized, optimized.config);
          dominators.add(optimized);
          CumulativeStats.filter(dominators);
          for (CumulativeStats x : dominators) {
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
