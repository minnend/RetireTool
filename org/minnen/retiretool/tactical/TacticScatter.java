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
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class TacticScatter
{
  public static final SequenceStore         store                     = new SequenceStore();

  public static final boolean               initializeOldTactical     = false;
  public static final boolean               initializeSingleDefenders = false;
  public static final boolean               initializeDoubleDefenders = false;
  public static final boolean               initializeTripleDefenders = false;
  public static final boolean               initializeFavorites       = true;
  public static final int                   nPerturb                  = 200;
  public static final double                radius                    = 2.5;
  public static Comparator<CumulativeStats> comp                      = CumulativeStats.getComparatorBasic();

  private static Simulation                 sim;

  public static final String[]              favoriteParams            = new String[] {
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [51,0] / [180,3] m=2539",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [5,0] / [178,50] m=145",
      // "[15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [43,0] / [55,4] m=2025",

      "[15,0] / [259,125] m=21",                                                                                   // *
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145",                                                          // *
      // "[20,0] / [240,150] m=25 | [50,0] / [180,30] m=100 | [10,0] / [220,0] m=200",
      // "[20,0] / [240,150] m=25 | [25,0] / [155,125] m=75 | [5,0] / [165,5] m=50",
      // "[29,1] / [269,99] m=138 | [23,1] / [233,106] m=103 | [12,0] / [162,109] m=25",
      //
      "[28,2] / [280,98] m=143 | [11,0] / [156,109] m=23 | [55,1] / [23,13] m=121",                                // *
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [19,0] / [213,83] m=269",                                // *
      "[15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [63,0] / [23,14] m=105",                                 // *
      // "[29,0] / [276,103] m=141 | [11,0] / [165,109] m=25 | [15,0] / [162,121] m=91",
      // "[14,0] / [247,129] m=19 | [6,1] / [172,49] m=149 | [33,1] / [127,89] m=266",

      // Old (2016) configs.
      "[20,0] / [240,150] m=25 | [25,0] / [155,125] m=75 | [5,0] / [165,5] m=50",
      "[20,0] / [240,150] m=25 | [50,0] / [180,30] m=100 | [10,0] / [220,0] m=200",

  };

  public static final PredictorConfig[]     favoriteConfigs           = new PredictorConfig[favoriteParams.length];

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

    // Set up "defenders" based on known-good configs.
    List<CumulativeStats> dominators = new ArrayList<>();
    List<List<CumulativeStats>> allStats = new ArrayList<>();

    if (initializeOldTactical) {
      ConfigMulti tacticalConfig = ConfigMulti.buildTactical(FinLib.AdjClose, 0, 1);
      List<CumulativeStats> list = new ArrayList<>();
      CumulativeStats tacticalStats = TacticLib.eval(tacticalConfig, "Tactical", nPerturb, sim, comp, list);
      allStats.add(list);
      System.out.printf("%s (%s)\n", tacticalStats, tacticalConfig);
      dominators.add(tacticalStats);
    }

    if (initializeSingleDefenders) {
      for (PredictorConfig config : GeneratorSMA.knownConfigs) {
        List<CumulativeStats> list = new ArrayList<>();
        CumulativeStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeDoubleDefenders) {
      for (PredictorConfig config : GeneratorTwoSMA.knownConfigs) {
        List<CumulativeStats> list = new ArrayList<>();
        CumulativeStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeTripleDefenders) {
      for (PredictorConfig config : GeneratorThreeSMA.knownConfigs) {
        List<CumulativeStats> list = new ArrayList<>();
        CumulativeStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    if (initializeFavorites) {
      for (PredictorConfig config : favoriteConfigs) {
        List<CumulativeStats> list = new ArrayList<>();
        CumulativeStats stats = TacticLib.eval(config, "Known", nPerturb, sim, comp, list);
        allStats.add(list);
        System.out.printf("%s (%s)\n", stats, config);
        dominators.add(stats);
      }
    }
    System.out.printf("Strategies: %d\n", allStats.size());

    CumulativeStats.filter(dominators);
    System.out.printf("Defenders: %d\n", dominators.size());
    for (CumulativeStats x : dominators) {
      System.out.printf(" %s (%s)\n", x, x.config);
    }

    Sequence[] scatterData = new Sequence[allStats.size()];
    for (int i = 0; i < scatterData.length; ++i) {
      scatterData[i] = new Sequence();
      for (CumulativeStats stats : allStats.get(i)) {
        if (scatterData[i].getName() == null) {
          scatterData[i].setName(stats.config.toString());
        }
        FeatureVec x = new FeatureVec(stats.config.toString(), 2, stats.devAnnualReturn, stats.meanAnnualReturn);
        scatterData[i].addData(x);
      }
      System.out.printf("%d  %s  n=%d\n", i, scatterData[i].getName(), scatterData[i].length());
    }
    Chart.saveScatterPlot(new File(DataIO.outputPath, "tactical-scatter.html"), "Tactical Scatter Plot", "100%",
        "900px", radius, new String[] { "Deviation", "Average Return" }, scatterData);
  }
}
