package org.minnen.retiretool.tactical;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.stats.AllStats;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class TacticScatter
{
  public static final SequenceStore     store                     = new SequenceStore();

  public static final boolean           initializeOldTactical     = false;
  public static final boolean           initializeSingleDefenders = false;
  public static final boolean           initializeDoubleDefenders = false;
  public static final boolean           initializeTripleDefenders = false;
  public static final boolean           initializeFavorites       = true;
  public static final int               nPerturb                  = 500;
  public static final double            radius                    = 2.5;

  public static Comparator<AllStats>    comp                      = AllStats
      .getCompare(CumulativeStats.getComparatorBasic());

  public static Comparator<AllStats>    compCumDom                = AllStats
      .getCompare(CumulativeStats.getComparatorDominates());

  private static Simulation             sim;

  public static final String[]          favoriteParams            = new String[] {
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [51,0] / [180,3] m=2539",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [5,0] / [178,50] m=145",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [43,0] / [55,4] m=2025",

      // High win rates (not stable):
      // "[29,0] / [22,5] m=256",
      // "[15,0] / [21,8] m=222",
      // "[14,0] / [11,2] m=156",
      // "[31,0] / [243,176] m=76",
      // "[14,0] / [190,172] m=332",
      // "[23,0] / [200,152] m=168",
      // "[16,0] / [272,124] m=23 | [23,0] / [18,9] m=32",
      "[22,0] / [190,147] m=170 | [67,0] / [34,21] m=9", "[64,0] / [34,21] m=8 | [21,0] / [201,150] m=184",
      "[66,0] / [36,21] m=9 | [23,0] / [224,60] m=221", "[65,0] / [36,22] m=10 | [21,0] / [239,27] m=199",
      "[63,0] / [24,15] m=115 | [59,0] / [119,53] m=61",
      // "[70,1] / [36,20] m=10 | [55,1] / [220,55] m=175",
      
      "[60,0] / [23,14] m=120 | [57,0] / [115,51] m=62 | [26,0] / [20,4] m=308",
      "[23,0] / [183,150] m=154 | [64,0] / [33,22] m=9 | [38,0] / [43,16] m=66",
      "[23,0] / [183,147] m=153 | [69,0] / [35,20] m=10 | [50,0] / [121,42] m=63",
      "[21,0] / [182,141] m=158 | [66,0] / [35,21] m=10 | [42,0] / [127,49] m=54",
      "[21,0] / [189,149] m=162 | [65,0] / [36,21] m=10 | [52,0] / [94,66] m=253",
      "[22,0] / [198,151] m=184 | [64,0] / [36,21] m=10 | [32,0] / [44,14] m=59",
      "[21,0] / [188,141] m=171 | [67,0] / [33,20] m=8 | [30,0] / [136,48] m=29",
      "[23,0] / [188,143] m=154 | [66,0] / [34,20] m=8 | [52,0] / [103,23] m=272",

      // "[15,0] / [259,125] m=21", // *
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145",                                                      // *
      // "[20,0] / [240,150] m=25 | [50,0] / [180,30] m=100 | [10,0] / [220,0] m=200",
      // "[20,0] / [240,150] m=25 | [25,0] / [155,125] m=75 | [5,0] / [165,5] m=50",
      // "[29,1] / [269,99] m=138 | [23,1] / [233,106] m=103 | [12,0] / [162,109] m=25",
      //
      // "[28,2] / [280,98] m=143 | [11,0] / [156,109] m=23 | [55,1] / [23,13] m=121", // *
      // "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [19,0] / [213,83] m=269", // *
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [63,0] / [23,14] m=105",                             // *
      // "[29,0] / [276,103] m=141 | [11,0] / [165,109] m=25 | [15,0] / [162,121] m=91",
      // "[14,0] / [247,129] m=19 | [6,1] / [172,49] m=149 | [33,1] / [127,89] m=266",

      // Old (2016) configs.
      // "[20,0] / [240,150] m=25 | [25,0] / [155,125] m=75 | [5,0] / [165,5] m=50",
      // "[20,0] / [240,150] m=25 | [50,0] / [180,30] m=100 | [10,0] / [220,0] m=200",
  };

  public static final PredictorConfig[] favoriteConfigs           = new PredictorConfig[favoriteParams.length];

  static {
    for (int i = 0; i < favoriteParams.length; ++i) {
      // TODO better top-level parsing function instead of split-and-test here.
      String[] fields = favoriteParams[i].split("\\s*\\|\\s*");
      if (fields.length == 1) {
        favoriteConfigs[i] = GeneratorSMA.parse(favoriteParams[i]);
      } else if (fields.length == 2) {
        favoriteConfigs[i] = GeneratorTwoSMA.parse(favoriteParams[i]);
      } else if (fields.length == 3) {
        favoriteConfigs[i] = GeneratorThreeSMA.parse(favoriteParams[i]);
      }
    }
  }

  public static void main(String[] args) throws IOException
  {
    TacticLib.setupData("VFINX", store);

    Sequence stock = store.get(TacticLib.riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 470 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    sim = new Simulation(store, guideSeq);
    sim.setCheckBusinessDays(false); // assume data is correct wrt business days (faster but slightly dangerous)
    System.out.printf("#perturb per eval: %d\n", nPerturb);

    ConfigConst baselineConfig = new ConfigConst(stock.getName());
    AllStats baselineStats = TacticLib.eval(baselineConfig, "Baseline", sim);
    Sequence baselineReturns = baselineStats.cumulative.monthlyReturns;
    System.out.printf("%s: %s\n", baselineReturns.getName(), baselineStats);

    // Set up "defenders" based on known-good configs.
    List<AllStats> dominators = new ArrayList<>();
    List<List<AllStats>> allStats = new ArrayList<>();

    if (initializeOldTactical) {
      ConfigMulti tacticalConfig = ConfigMulti.buildTactical(FinLib.AdjClose, 0, 1);
      List<AllStats> list = new ArrayList<>();
      AllStats tacticalStats = TacticLib.eval(tacticalConfig, "Tactical", nPerturb, sim, comp, baselineReturns, list);
      allStats.add(list);
      System.out.printf("%s (%s)\n", tacticalStats, tacticalConfig);
      dominators.add(tacticalStats);
    }

    if (initializeSingleDefenders) {
      for (PredictorConfig config : GeneratorSMA.knownConfigs) {
        List<AllStats> list = new ArrayList<>();
        AllStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, baselineReturns, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeDoubleDefenders) {
      for (PredictorConfig config : GeneratorTwoSMA.knownConfigs) {
        List<AllStats> list = new ArrayList<>();
        AllStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, baselineReturns, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeTripleDefenders) {
      for (PredictorConfig config : GeneratorThreeSMA.knownConfigs) {
        List<AllStats> list = new ArrayList<>();
        AllStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, baselineReturns, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeFavorites) {
      for (PredictorConfig config : favoriteConfigs) {
        List<AllStats> list = new ArrayList<>();
        AllStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, baselineReturns, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    System.out.printf("Strategies: %d\n", allStats.size());

    AllStats.filter(dominators, compCumDom);
    System.out.printf("Defenders: %d\n", dominators.size());
    for (AllStats x : dominators) {
      System.out.printf(" %s (%s)\n", x, x.config);
    }

    Sequence[] scatterData = new Sequence[allStats.size()];
    for (int i = 0; i < scatterData.length; ++i) {
      scatterData[i] = new Sequence();
      for (AllStats stats : allStats.get(i)) {
        if (scatterData[i].getName() == null) {
          scatterData[i].setName(stats.config.toString());
        }
        ComparisonStats comparison = stats.comparisons.get("Baseline");
        FeatureVec x = new FeatureVec(stats.config.toString(), 4, stats.cumulative.devAnnualReturn,
            stats.cumulative.meanAnnualReturn, comparison.durationToResults.get(120).winPercent2,
            comparison.durationToResults.get(240).winPercent2);
        scatterData[i].addData(x);
      }
      System.out.printf("%d  %s  n=%d\n", i, scatterData[i].getName(), scatterData[i].length());
    }

    // CAGR vs. standard deviation.
    Sequence[] seqs = new Sequence[scatterData.length];
    for (int i = 0; i < seqs.length; ++i) {
      seqs[i] = scatterData[i].extractDims(0, 1);
    }
    Chart.saveScatterPlot(new File(DataIO.getOutputPath(), "tactical-scatter-cagr-sdev.html"), "Tactical Scatter Plot",
        "100%", "900px", radius, new String[] { "Deviation", "Average Return" }, seqs);

    // Regrest: 10 year. vs 20 year.
    for (int i = 0; i < seqs.length; ++i) {
      seqs[i] = scatterData[i].extractDims(2, 3);
    }
    Chart.saveScatterPlot(new File(DataIO.getOutputPath(), "tactical-scatter-regret-10-20.html"), "Tactical Scatter Plot",
        "100%", "900px", radius, new String[] { "10-year Regret", "20-year Regret" }, seqs);

    // CAGR vs. 20-year regret.
    for (int i = 0; i < seqs.length; ++i) {
      seqs[i] = scatterData[i].extractDims(3, 1);
    }
    Chart.saveScatterPlot(new File(DataIO.getOutputPath(), "tactical-scatter-cagr-regret-20.html"), "Tactical Scatter Plot",
        "100%", "900px", radius, new String[] { "20-year Regret", "Average Return" }, seqs);
  }
}
