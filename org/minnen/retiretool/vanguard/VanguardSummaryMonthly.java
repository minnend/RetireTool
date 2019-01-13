package org.minnen.retiretool.vanguard;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;

public class VanguardSummaryMonthly
{
  public static final SequenceStore             store          = new SequenceStore();

  public static final Slippage                  slippage       = Slippage.None;
  public static final String[]                  fundSymbols    = VanguardFund.getOldFunds();
  public static final Map<String, VanguardFund> funds          = VanguardFund.fundMap;
  public static final String[]                  statNames      = new String[] { "CAGR", "MaxDrawdown", "Worst Period",
      "10th Percentile", "Median " };
  public static final int                       durStatsMonths = 5 * 12;
  public static final int                       momentumMonths = 10;

  public static MonthlyRunner                   runner;

  static {
    SummaryTools.fundSymbols = fundSymbols;
  }

  public static List<FeatureVec> genPortfolios(List<Sequence> seqs) throws IOException
  {
    // Search over all portfolios.
    File file = new File(DataIO.getFinancePath(), "vanguard-portfolios-monthly.txt");

    // scanDistributions(1, 10, 10, 100, 10, portfolios);
    // scanDistributions(1, 8, 10, 40, 10, portfolios);
    // scanDistributions(4, 6, 5, 30, 5, portfolios); // last one run
    // scanDistributions(3, 3, 20, 40, 10, portfolios);
    PortfolioSearchConfig config = new PortfolioSearchConfig(1, 99, 10, 40, 10);
    List<FeatureVec> stats = SummaryTools.savePortfolioStats(runner, config, file);

    boolean prunePortfolios = true;
    if (prunePortfolios) {
      // Save pruned stats.
      SummaryTools.prunePortfolios(stats);
      System.out.printf("Pruned Portfolios: %d\n", stats.size());
      file = new File(DataIO.getFinancePath(), "vanguard-portfolios-monthly-pruned.txt");
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
        for (FeatureVec v : stats) {
          writer.write(String.format("%-80s %s\n", v.getName(), v));
        }
      }
    }

    return stats;
  }

  public static void genScatterPlots(List<FeatureVec> stats, File outputDir) throws IOException
  {
    Sequence scatter = new Sequence().append(stats);
    int nStats = stats.get(0).getNumDims();
    for (int i = 0; i < nStats; ++i) {
      for (int j = i + 1; j < nStats; ++j) {
        String filename = String.format("vanguard-monthly-scatter-%d%d.html", i + 1, j + 1);
        ChartConfig chartConfig = new ChartConfig(new File(outputDir, filename)).setType(ChartConfig.Type.Scatter)
            .setSize(800, 600).setRadius(2).setData(scatter).showToolTips(true).setDimNames(statNames)
            .setAxisTitles(statNames[i], statNames[j]).setIndexXY(i, j);
        Chart.saveScatterPlot(chartConfig);
      }
    }
  }

  public static Sequence applyMomentumFilter(Sequence seq, int momentumMonths)
  {
    if (momentumMonths < 1) return seq;

    double[] baseReturns = seq.extractDim(0);
    for (int t = momentumMonths; t < seq.length(); ++t) {
      // Calculate N month momentum.
      double r = 1.0;
      for (int i = t - momentumMonths; i < t; ++i) {
        r *= baseReturns[i];
      }

      // Only allowed to hold assets that have positive momentum.
      // TODO could compare against cash or short-term treasuries.
      if (r <= 1.0) {
        // Setting return to 1.0 is equivalent to holding cash (with no interest).
        seq.get(t).set(0, 1.0);
      }
    }
    return seq;
  }

  public static void main(String[] args) throws IOException
  {
    // Load monthly return data.
    // Note: run GenMonthlyReturns to create a CSV file with monthly returns.
    File file = new File(DataIO.getFinancePath(), "vanguard-monthly.csv");
    List<Sequence> seqs = DataIO.loadSequenceCSV(file);
    System.out.printf("Funds: %d\n", seqs.size());

    long timeStart = seqs.get(0).getStartMS();
    long timeEnd = seqs.get(0).getEndMS();
    double nSimMonths = TimeLib.monthsBetween(timeStart, timeEnd);
    System.out.printf("Monthly Return Data: [%s] -> [%s]  (%.1f months total)\n", TimeLib.formatMonth(timeStart),
        TimeLib.formatMonth(timeEnd), nSimMonths);

    // Convert monthly returns to multipliers.
    System.out.printf("Momentum months: %d\n", momentumMonths);
    for (Sequence seq : seqs) {
      for (FeatureVec v : seq) {
        double r = v.get(0);
        v.set(0, FinLib.ret2mul(r));
      }
      if (momentumMonths > 0) {
        applyMomentumFilter(seq, momentumMonths);
      }
    }
    System.out.printf("Duration Statistics: %s\n", TimeLib.formatDurationMonths(durStatsMonths));
    runner = new MonthlyRunner(seqs, durStatsMonths);

    List<FeatureVec> stats = genPortfolios(seqs);

    // Load portfolios from disk.
    // file = new File(outputDir, "vanguard-portfolios-monthly-8x10x40-mom6-pruned.txt");
    // file = new File(DataIO.getFinancePath(), "vanguard-portfolios-monthly-1-6-10-pruned.txt");
    // file = new File(DataIO.getFinancePath(), "vanguard-portfolios-monthly-1-6-10-momentum6-pruned.txt");
    // List<FeatureVec> stats = SummaryTools.loadPortfolioStats(file, false);
    // System.out.printf("Portfolios: %d\n", stats.size());

    // Select a random subset of portfolios for visualization.
    // Collections.shuffle(stats);
    // stats = stats.subList(0, 250000);

    // Remove portfolios that don't meet minimum requirements.
    // for (int i = 0; i < stats.size(); ++i) {
    // FeatureVec v = stats.get(i);
    // if (v.get(SummaryTools.WORST) > 0.0 && v.get(SummaryTools.PERCENTILE10) > 3.0 && v.get(SummaryTools.MEDIAN) > 8.0
    // && v.get(SummaryTools.DRAWDOWN) > -40.0)
    // continue;
    // stats.set(i, null);
    // }
    Library.removeNulls(stats);
    System.out.printf("Pruned: %d\n", stats.size());

    // Load saved distributions.
    // file = new File(outputDir, "vanguard-favs.txt");
    // List<DiscreteDistribution> portfolios = SummaryTools.loadPortfolios(file);
    // for (DiscreteDistribution portfolio : portfolios) {
    // assert portfolio.isNormalized();
    // FeatureVec stats = runner.run(portfolio);
    // System.out.printf("%-80s %s\n", portfolio.toStringWithNames(0), stats);
    // }

    genScatterPlots(stats, DataIO.getOutputPath());
  }
}
