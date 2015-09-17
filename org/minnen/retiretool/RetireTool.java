package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.minnen.retiretool.Chart.ChartType;
import org.minnen.retiretool.Strategy.Disposition;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.MomentumPredictor;
import org.minnen.retiretool.predictor.SMAPredictor;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.stats.WinStats;

public class RetireTool
{
  public static final int           GRAPH_WIDTH  = 710;
  public static final int           GRAPH_HEIGHT = 450;

  public final static SequenceStore store        = new SequenceStore();

  public static void buildCumulativeReturnsStore(Sequence shiller, Sequence tbills)
  {
    assert tbills == null || shiller.length() == tbills.length();

    final int iStartData = 0;// shiller.getIndexForDate(1934, 12);
    final int iEndData = -1;// shiller.getIndexForDate(2009, 12);

    final int rebalanceMonths = 12;
    final double rebalanceBand = 0.0;
    final int iStartSimulation = 12;

    // Extract stock and bond data from shiller sequence.
    Sequence stockData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);
    assert stockData.length() == bondData.length();
    System.out.printf("Build Store: [%s] -> [%s]\n", Library.formatMonth(stockData.getStartMS()),
        Library.formatMonth(stockData.getEndMS()));
    store.addMisc(stockData, "StockData");
    store.addMisc(bondData, "BondData");

    // Calculate cumulative returns for full stock, bond, and t-bill data.
    Sequence stockAll = FinLib.calcSnpReturns(stockData, 0, -1, FinLib.DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    assert stockAll.length() == bondsAll.length();
    store.addMisc(stockAll, "Stock-All");
    store.addMisc(bondsAll, "Bonds-All");
    Sequence billsAll = null;
    if (tbills != null) {
      billsAll = Bond.calcReturnsRebuy(BondFactory.bill3Month, tbills, iStartData, iEndData);
      assert billsAll.length() == stockAll.length();
      store.addMisc(billsAll, "Bills-All");
    }

    // Extract requested subsequences from cumulative returns for stock, bonds, and t-bills.
    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    stock._div(stock.getFirst(0));
    bonds._div(bonds.getFirst(0));
    assert stock.length() == bonds.length();
    store.add(stock, "Stock");
    store.add(bonds, "Bonds");

    Sequence bills = null;
    if (billsAll != null) {
      bills = billsAll.subseq(iStartSimulation);
      bills._div(bills.getFirst(0));
      store.add(bills, "Bills");
    }

    Sequence stockQuarterlyDiv = FinLib
        .calcSnpReturns(stockData, iStartSimulation, -1, FinLib.DividendMethod.QUARTERLY);
    store.add(stockQuarterlyDiv, "Stock [QuarterlyDiv]");
    Sequence stockNoDiv = FinLib.calcSnpReturns(stockData, iStartSimulation, -1, FinLib.DividendMethod.NO_REINVEST);
    store.add(stockNoDiv, "Stock [NoDiv]");
    Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, iStartSimulation, -1);
    store.add(bondsHold, "Bonds [Hold]");

    // Stock / Bond mixes.
    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    for (int i = 0; i < percentStock.length; ++i) {
      int percentBonds = 100 - percentStock[i];
      Sequence mix = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock[i],
          percentBonds }, rebalanceMonths, rebalanceBand);
      mix.setName(String.format("Stock/Bonds [%d/%d]", percentStock[i], percentBonds));
      store.alias(String.format("%d/%d", percentStock[i], percentBonds), mix.getName());
      store.add(mix);
    }

    // Setup risky and safe assets for use with dynamic asset allocation strategies.
    Sequence risky = stockAll;
    Sequence safe = bondsAll;

    // Momentum sweep.
    final int[] momentumMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12 };
    for (int i = 0; i < momentumMonths.length; ++i) {
      WinStats winStats = new WinStats();
      Sequence momOrig = Strategy.calcMomentumReturns(momentumMonths[i], iStartSimulation, winStats, risky, safe);
      MomentumPredictor predictor = new MomentumPredictor(momentumMonths[i], store);
      Sequence mom = Strategy.calcReturns(predictor, iStartSimulation, winStats, risky, safe);

      assert mom.length() == momOrig.length(); // TODO
      for (int ii = 0; ii < mom.length(); ++ii) {
        double a = mom.get(ii, 0);
        double b = momOrig.get(ii, 0);
        assert Math.abs(a - b) < 1e-6;
      }

      store.add(mom);
      // if (i == 0) {
      // System.out.printf("Correct: %.2f%% / %.2f%%  (%d / %d)\n", winStats.percentCorrect(0),
      // winStats.percentCorrect(1), winStats.nCorrect[0], winStats.nCorrect[1]);
      // }
      // System.out.printf("Momentum-%d: %.2f%% Correct (%d vs. %d / %d)\n", momentumMonths[i],
      // winStats.percentCorrect(),
      // winStats.nPredCorrect, winStats.nPredWrong, winStats.total());
      // System.out.printf(" %.2f%% / %.2f%%  (%d / %d)\n", winStats.percentSelected(0), winStats.percentSelected(1),
      // winStats.nSelected[0], winStats.nSelected[1]);
    }

    // SMA sweep.
    int[] smaMonths = new int[] { 1, 2, 3, 4, 5, 6, 9, 10, 12 };
    for (int i = 0; i < smaMonths.length; ++i) {
      WinStats winStats = new WinStats();
      Sequence smaOrig = Strategy.calcSMAReturns(smaMonths[i], iStartSimulation, stockData, risky, safe);
      SMAPredictor predictor = new SMAPredictor(smaMonths[i], "StockData", store);
      Sequence sma = Strategy.calcReturns(predictor, iStartSimulation, winStats, risky, safe);

      assert sma.length() == smaOrig.length(); // TODO
      for (int ii = 0; ii < sma.length(); ++ii) {
        double a = sma.get(ii, 0);
        double b = smaOrig.get(ii, 0);
        assert Math.abs(a - b) < 1e-6;
      }

      store.add(sma);
    }

    // RAA = 50/50 sma-10 & mom-12
    Sequence raa = Strategy.calcMixedReturns(new Sequence[] { store.get("sma-10"), store.get("momentum-12") },
        new double[] { 50, 50 }, rebalanceMonths, rebalanceBand);
    store.add(raa, "RAA");

    // Multi-scale Momentum and SMA methods.
    for (Disposition disposition : Disposition.values()) {
      Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, risky, safe, disposition);
      Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, stockData, risky, safe, disposition);
      store.add(mom);
      store.add(sma);

      System.out.println(store.getCumulativeStats(mom.getName()));
      System.out.println(store.getCumulativeStats(sma.getName()));
    }

    // Full multi-momentum/sma sweep.
    for (int assetMap = 255, i = 0; i < 8; ++i) {
      Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, risky, safe, assetMap);
      Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, stockData, risky, safe, assetMap);

      System.out.println();
      String name = mom.getName();
      store.add(mom);
      CumulativeStats cstats = store.getCumulativeStats(name);
      // if (cstats.cagr >= 9.0 && cstats.drawdown < 20.0) {
      System.out.println(cstats);
      // }

      name = sma.getName();
      store.add(sma);
      cstats = store.getCumulativeStats(name);
      System.out.println(cstats);

      assetMap &= ~(1 << i);
    }

    // Strategy.calcMultiMomentumStats(risky, safe);

    // DAA = 50/50 SMA-risky & Momentum-safe.
    Sequence daa = Strategy.calcMixedReturns(
        new Sequence[] { store.get("multisma-risky"), store.get("multimom-moderate") }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    store.add(daa, "DAA");
    System.out.println(store.getCumulativeStats("DAA"));

    // Perfect = impossible strategy that always picks the best asset.
    Sequence perfect = Strategy.calcPerfectReturns(iStartSimulation, risky, safe);
    store.add(perfect);
  }

  public static void printReturnLikelihoods(Sequence cumulativeReturns, boolean bInvert)
  {
    Sequence seqLik = FinLib.calcReturnLikelihoods(cumulativeReturns, bInvert);
    for (FeatureVec fv : seqLik) {
      System.out.printf("%.3f", fv.get(0));
      for (int i = 0; i < seqLik.getNumDims() - 1; i++)
        System.out.printf(" %f", fv.get(i + 1));
      System.out.println();
    }
  }

  public static void printWithdrawalLikelihoods(Sequence cumulativeReturns, Sequence cpi, int numYears,
      double expenseRatio)
  {
    System.out.printf("Withdrawal Likelihoods over %d years:\n", numYears);
    double[] wrates = new double[] { 2.0, 2.5, 3.0, 3.5, 3.75, 4.0, 4.25, 4.5, 5.0 };
    int months = numYears * 12;
    for (double wrate : wrates) {
      // System.out.printf("Processing Withdrawal Rate: %.2f%%\n", wrate);
      double principal = 1000000.0;
      double salary = principal * wrate / 100.0;
      int nData = cumulativeReturns.length();
      int n = 0;
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + months >= nData)
          break; // not enough data
        double endBalance = FinLib.calcEndBalance(cumulativeReturns, cpi, principal, salary, expenseRatio, true, 0, 0,
            0.0, i, months);
        if (endBalance > 0.0)
          ++nOK;
        ++n;
      }
      System.out.printf("Withdrawal Rate: %.2f  %d / %d = %.2f%%\n", wrate, nOK, n, 100.0 * nOK / n);
    }
  }

  public static void compareRebalancingMethods(Sequence shiller, File dir) throws IOException
  {
    int iStartData = 0;
    int iEndData = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int percentStock = 60;
    int percentBonds = 40;

    Sequence snpData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStartData, -1);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(snpData, iStartData, -1, FinLib.DividendMethod.MONTHLY);
    stock.setName("Stock");

    Sequence mixedNone = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 0.0);
    // mixedNone.setName(String.format("Stock/Bonds-%d/%d (No Rebalance)", percentStock, percentBonds));
    mixedNone.setName("No Rebalance");

    Sequence mixedM6 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 6, 0.0);
    // mixedM6.setName(String.format("Stock/Bonds-%d/%d (Rebalance M6)", percentStock, percentBonds));
    mixedM6.setName("6 Months");

    Sequence mixedM12 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 12, 0.0);
    // mixedM12.setName(String.format("Stock/Bonds-%d/%d (Rebalance M12)", percentStock, percentBonds));
    mixedM12.setName("12 Months");

    Sequence mixedB5 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 5.0);
    // mixedB5.setName(String.format("Stock/Bonds-%d/%d (Rebalance B5)", percentStock, percentBonds));
    mixedB5.setName("5% Band");

    Sequence mixedB10 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 10.0);
    // mixedB10.setName(String.format("Stock/Bonds-%d/%d (Rebalance B10)", percentStock, percentBonds));
    mixedB10.setName("10% Band");

    Sequence[] all = new Sequence[] { stock, mixedM6, mixedM12, mixedB5, mixedB10 }; // bonds, mixedNone,
    CumulativeStats[] cumulativeStats = new CumulativeStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == all[0].length();
      cumulativeStats[i] = CumulativeStats.calc(all[i]);
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), cumulativeStats[i].cagr));
      System.out.printf("%s\n", cumulativeStats[i]);
    }

    Chart.saveLineChart(new File(dir, "rebalance-cumulative.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, all);
    Chart.saveStatsTable(new File(dir, "rebalance-table.html"), GRAPH_WIDTH, false, cumulativeStats);

    int duration = 10 * 12;
    DurationalStats[] stats = new DurationalStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = new DurationalStats(all[i], duration);
    }
    Chart.saveBoxPlots(new File(dir, "rebalance-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genReturnChart(Sequence shiller, Sequence tbills, File dir) throws IOException
  {
    String[] names = new String[] { "stock", "momentum-1", "momentum-3", "momentum-12", "multimom-safe",
        "multimom-cautious", "multimom-moderate", "multimom-risky" };
    // , "multisma-safe",
    // "multisma-cautious", "multisma-moderate", "multisma-risky", "multisma-daredevil", "raa", "daa" };
    // { "stock", "bonds", "momentum-1", "momentum-3", "momentum-12", "multimom-risky" };

    List<Sequence> all = store.getReturns(names);

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    List<DurationalStats> rstats = store.getDurationalStats(names);
    double[] scores = new double[names.length];

    Sequence scatter = new Sequence("Returns vs. Volatility");
    int duration = 10 * 12;
    for (int i = 0; i < names.length; ++i) {
      scores[i] = cstats.get(i).calcScore();
      scatter.addData(new FeatureVec(all.get(i).getName(), 2, rstats.get(i).mean, rstats.get(i).sdev));
    }

    // Chart.saveLineChart(fileChart, "Cumulative Market Returns", GRAPH_WIDTH, GRAPH_HEIGHT, true, multiSmaRisky, daa,
    // multiMomSafe, sma, raa, momentum, stock, mixed, bonds, bondsHold);
    Chart.saveLineChart(new File(dir, "cumulative-returns.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, all);

    int[] ii = Library.sort(scores, false);
    for (int i = 0; i < names.length; ++i) {
      System.out.printf("%d [%.1f]: %s\n", i + 1, scores[i], cstats.get(ii[i]));
    }
    Chart.saveStatsTable(new File(dir, "strategy-report.html"), GRAPH_WIDTH, true, cstats);

    Chart.saveBoxPlots(new File(dir, "strategy-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        rstats);

    Chart.saveScatterPlot(new File(dir, "strategy-scatter.html"),
        String.format("Momentum: Returns vs. Volatility (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH,
        GRAPH_HEIGHT, 5, scatter);

    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 1; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(all.get(i), all.get(0)));
    }
    Chart.saveComparisonTable(new File(dir, "strategy-comparisons.html"), GRAPH_WIDTH, comparisons);
  }

  public static void genReturnViz(Sequence shiller, File dir) throws IOException
  {
    final int nMonths = 10 * 12;

    Sequence stock = store.get("stock");
    Sequence bonds = store.get("bonds");

    // Chart.printDecadeTable(stockAll);
    Sequence returnLiks = FinLib.calcReturnLikelihoods(stock, true);
    returnLiks = returnLiks.subseq(60, 180);
    Sequence[] liks = new Sequence[returnLiks.getNumDims() - 1];
    int[] years = new int[] { 1, 2, 5, 10, 20, 30, 40 };
    for (int i = 0; i < liks.length; ++i) {
      liks[i] = returnLiks.extractDims(i + 1);
      liks[i].setName(String.format("%d year%s", years[i], years[i] == 1 ? "" : "s"));
      Chart.saveHighChart(new File(String.format("g:/web/return-likelihoods-%d-years.html", years[i])),
          Chart.ChartType.Area, "Return Likelihoods", FinLib.getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH,
          GRAPH_HEIGHT, 0.0, 1.0, Double.NaN, false, 0, liks[i]);
    }
    Chart.saveHighChart(new File(dir, "return-likelihoods.html"), Chart.ChartType.Line, "Return Likelihoods",
        FinLib.getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 1.0, Double.NaN, false, 0,
        liks);

    // Sequence[] assets = new Sequence[] { multiSmaRisky, daa, multiMomSafe, momentum, sma, raa, stock, bonds, mixed };
    Sequence[] assets = new Sequence[] { bonds };
    Sequence[] returns = new Sequence[assets.length];
    double vmin = 0.0, vmax = 0.0;
    for (int i = 0; i < assets.length; ++i) {
      DurationalStats.printDurationTable(assets[i]);
      returns[i] = FinLib.calcReturnsForDuration(assets[i], nMonths);

      // for (int j = 0; j < returns[i].length(); ++j) {
      // FeatureVec v = returns[i].get(j);
      // if (v.get(0) > 62.0 || v.get(0) < -42.0) {
      // System.out.printf("%d: %.2f  [%s]\n", j, v.get(0), Library.formatMonth(v.getTime()));
      // }
      // }
      returns[i].setName(assets[i].getName());
      double[] a = returns[i].extractDim(0);
      Arrays.sort(a);
      if (i == 0 || a[0] < vmin) {
        vmin = a[0];
      }
      if (i == 0 || a[a.length - 1] > vmax) {
        vmax = a[a.length - 1];
      }
    }

    Sequence[] histograms = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      histograms[i] = FinLib.computeHistogram(returns[i], vmin, vmax, 0.5, 0.0);
      histograms[i].setName(assets[i].getName());
    }

    String title = "Histogram of Returns - " + Library.formatDurationMonths(nMonths);
    String[] labels = FinLib.getLabelsFromHistogram(histograms[0]);
    Chart.saveHighChart(new File(dir, "histogram-returns.html"), Chart.ChartType.Bar, title, labels, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, Double.NaN, false, 1, histograms);

    // Generate histogram showing future returns.
    title = String.format("Future CAGR: %s (%s)", returns[0].getName(), Library.formatDurationMonths(nMonths));
    Chart.saveHighChart(new File(dir, "future-returns.html"), Chart.ChartType.Area, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, Double.NaN, false, 0, returns[0]);
  }

  public static void genDuelViz(Sequence shiller, Sequence tbills, File dir) throws IOException
  {
    assert dir.isDirectory();

    int duration = 10 * 12;
    store.calcDurationalStats(duration);

    String name1 = "multimom-cautious";
    String name2 = "stock";

    Sequence player1 = store.get(name1);
    Sequence player2 = store.get(name2);

    ComparisonStats comparison = ComparisonStats.calc(player1, player2);
    Chart.saveComparisonTable(new File(dir, "duel-comparison.html"), GRAPH_WIDTH, comparison);
    Chart.saveStatsTable(new File(dir, "duel-chart.html"), GRAPH_WIDTH, false, store.getCumulativeStats(name1, name2));

    Chart.saveHighChart(new File(dir, "duel-cumulative.html"), ChartType.Line, "Cumulative Market Returns", null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, 1, Double.NaN, 1.0, true, 0, player2, player1);

    DurationalStats dstatsA = store.getDurationalStats(name1);
    DurationalStats dstatsB = store.getDurationalStats(name2);

    // ReturnStats.printDurationTable(player1);
    // ReturnStats.printDurationTable(player2);

    // Generate scatter plot comparing results.
    String title = String.format("%s vs. %s (%s)", name1, name2, Library.formatDurationMonths(duration));
    Chart.saveHighChartScatter(new File(dir, "duel-scatter.html"), title, 730, GRAPH_HEIGHT, 0,
        dstatsB.durationReturns, dstatsA.durationReturns);

    // Generate histogram summarizing excess returns of B over A.
    title = String.format("Excess Returns: %s vs. %s (%s)", name1, name2, Library.formatDurationMonths(duration));
    Sequence excessReturns = dstatsA.durationReturns.sub(dstatsB.durationReturns);
    Sequence histogramExcess = FinLib.computeHistogram(excessReturns, 0.5, 0.0);
    histogramExcess.setName(String.format("%s vs. %s", name1, name2));
    String[] colors = new String[excessReturns.length()];
    for (int i = 0; i < colors.length; ++i) {
      double x = excessReturns.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }

    Chart.saveHighChart(new File(dir, "duel-returns.html"), Chart.ChartType.Line, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, false, 0, dstatsB.durationReturns, dstatsA.durationReturns);
    Chart.saveHighChart(new File(dir, "duel-excess-histogram.html"), Chart.ChartType.PosNegArea, title, null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN, Double.NaN, 1.0, false, 0, excessReturns);

    String[] labels = FinLib.getLabelsFromHistogram(histogramExcess);
    colors = new String[labels.length];
    for (int i = 0; i < colors.length; ++i) {
      double x = histogramExcess.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }
    Chart.saveHighChart(new File(dir, "duel-histogram.html"), Chart.ChartType.Bar, title, labels, colors, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 32, false, 1, histogramExcess);

    // double[] a = excessReturns.extractDim(0);
    // int[] ii = Library.sort(a, true);
    // for (int i = 0; i < excessReturns.length(); ++i) {
    // System.out.printf("[%s]  %.3f\n", Library.formatMonth(excessReturns.getTimeMS(ii[i])), a[i]);
    // }
  }

  public static void genStockBondMixSweepChart(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence snp = FinLib.calcSnpReturns(snpData, iStart, -1, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, -1);

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    boolean useLeverage = true;

    Sequence[] all = new Sequence[percentStock.length];
    for (int i = 0; i < percentStock.length; ++i) {
      all[i] = Strategy.calcMixedReturns(new Sequence[] { snp, bonds }, new double[] { percentStock[i],
          100 - percentStock[i] }, rebalanceMonths, rebalanceBand);
      all[i].setName(String.format("%d / %d", percentStock[i], 100 - percentStock[i]));
    }
    CumulativeStats[] cumulativeStats = CumulativeStats.calc(all);

    if (useLeverage) {
      for (int i = 0; i < all.length; ++i) {
        double leverage = FinLib.calcEqualizingLeverage(all[i], cumulativeStats[0].cagr);
        all[i] = FinLib.calcLeveragedReturns(all[i], leverage);
        cumulativeStats[i] = CumulativeStats.calc(all[i]);
        cumulativeStats[i].leverage = leverage;
      }
    }

    Chart.saveHighChart(new File(dir, "stock-bond-sweep.html"), ChartType.Line,
        "Cumulative Market Returns: Stock/Bond Mix", null, null, GRAPH_WIDTH, GRAPH_HEIGHT, 1.0, 262144.0, 1.0, true,
        0, all);
    Chart.saveStatsTable(new File(dir, "chart-stock-bond-sweep.html"), GRAPH_WIDTH, false, cumulativeStats);

    // all[0].setName("Stock");
    // all[all.length - 1].setName("Bonds");
    // Chart.printDecadeTable(all[0], all[all.length - 1]);

    int duration = 1 * 12;
    DurationalStats[] stats = new DurationalStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = new DurationalStats(all[i], duration);
      all[i].setName(String.format("%d/%d (%.2f%%)", percentStock[i], 100 - percentStock[i], stats[i].mean));
    }
    Chart.saveBoxPlots(new File(dir, "stock-bond-sweep-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genEfficientFrontier(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    Sequence stockData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stock = FinLib.calcSnpReturns(stockData, iStart, -1, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, -1);
    assert stock.length() == bonds.length();

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 12;
    double rebalanceBand = 10.0;
    int[] durations = new int[] { 1, 5, 10, 15, 20, 30 };

    // Generate curve for each decade.
    Calendar cal = Library.now();
    List<Sequence> decades = new ArrayList<Sequence>();
    int iDecadeStart = Library.FindStartofFirstDecade(stockData);
    for (int i = iDecadeStart; i + 120 < stockData.length(); i += 120) {
      cal.setTimeInMillis(stockData.getTimeMS(i));
      Sequence decadeStock = FinLib.calcSnpReturns(stockData, i, 120, FinLib.DividendMethod.MONTHLY);
      Sequence decadeBonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, i, i + 120);
      assert decadeStock.length() == decadeBonds.length();

      Sequence decade = new Sequence(String.format("%ss", cal.get(Calendar.YEAR)));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { decadeStock, decadeBonds }, new double[] {
            pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        CumulativeStats stats = CumulativeStats.calc(mixed);
        decade.addData(new FeatureVec(2, stats.cagr, stats.devAnnualReturn));
        // System.out.printf("%ss: %d / %d => %.2f (%.2f), %.2f\n", cal.get(Calendar.YEAR), pctStock, pctBonds,
        // stats.cagr, stats.meanAnnualReturn, stats.devAnnualReturn);
      }
      decades.add(decade);
    }
    Chart.saveHighChartSplines(new File(dir, "stock-bond-mix-decade-curves.html"), "Stock/Bond Decade Curves",
        GRAPH_WIDTH, GRAPH_HEIGHT, decades.toArray(new Sequence[decades.size()]));

    // Generate curve for requested duration.
    Sequence[] frontiers = new Sequence[durations.length];
    for (int i = 0; i < durations.length; ++i) {
      int duration = durations[i] * 12;
      Sequence frontier = new Sequence(Library.formatDurationMonths(duration));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds },
            new double[] { pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        DurationalStats stats = new DurationalStats(mixed, duration);
        String name = null;// String.format("%d", pctStock);
        // if (pctStock == 100) {
        // name = "100% Stock";
        // } else if (pctStock == 50) {
        // name = "50 / 50";
        // } else if (pctStock == 0) {
        // name = "100% Bonds";
        // }
        frontier.addData(new FeatureVec(name, 2, stats.mean, stats.sdev));
        System.out.printf("%d / %d: %.2f, %.2f\n", pctStock, pctBonds, stats.mean, stats.sdev);
      }
      frontiers[i] = frontier;
    }
    Chart.saveHighChartSplines(new File(dir, "stock-bond-mix-duration-curve.html"), "Stock / Bond Frontier",
        GRAPH_WIDTH, GRAPH_HEIGHT, frontiers);
  }

  public static void genSMASweepChart(Sequence shiller, File dir) throws IOException
  {
    final int[] months = new int[] { 1, 2, 3, 4, 5, 6, 9, 10, 12 };
    final int duration = 10 * 12;
    store.calcDurationalStats(duration);

    // Build list of names of assets/strategies.
    List<String> nameList = new ArrayList<>();
    nameList.add("stock");
    for (int i = 0; i < months.length; ++i) {
      nameList.add("sma-" + months[i]);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "sma-report.html"), GRAPH_WIDTH, true, store.getCumulativeStats(names));
    Chart.printDecadeTable(store.get("sma-10"), store.get("stock"));

    Chart.saveBoxPlots(new File(dir, "sma-box-plots.html"),
        String.format("Momentum Returns (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));
  }

  public static void genMomentumSweepChart(Sequence shiller, File dir) throws IOException
  {
    final int[] momentumMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12 };
    final int duration = 10 * 12;
    store.calcDurationalStats(duration);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("stock");
    for (int i = 0; i < momentumMonths.length; ++i) {
      nameList.add("momentum-" + momentumMonths[i]);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "momentum-report.html"), GRAPH_WIDTH, true, store.getCumulativeStats(names));
    // Chart.printDecadeTable(store.get("momentum-12"), store.get("stock"));

    // Create scatterplot of returns vs. volatility.
    List<DurationalStats> dstats = store.getDurationalStats(names);
    Sequence scatter = new Sequence("Momentum Results");
    for (int i = 0; i < dstats.size(); ++i) {
      DurationalStats stats = dstats.get(i);
      String label = "Stock";
      if (i < momentumMonths.length) {
        label = "" + momentumMonths[i];
      }
      scatter.addData(new FeatureVec(label, 2, stats.mean, stats.sdev));
    }
    Chart.saveScatterPlot(new File(dir, "momentum-scatter.html"),
        String.format("Momentum: Returns vs. Volatility (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH,
        GRAPH_HEIGHT, 5, scatter);

    names = new String[] { "stock", "bonds", "stock/bonds [60/40]", "momentum-12" };
    Chart.saveLineChart(new File(dir, "momentum-cumulative.html"), "Cumulative Market Returns: Momentum Strategy",
        GRAPH_WIDTH, GRAPH_HEIGHT, true, store.getReturns(names));

    Chart.saveBoxPlots(new File(dir, "momentum-box-plots.html"),
        String.format("Momentum Returns (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    Chart.saveStatsTable(new File(dir, "momentum-chart.html"), GRAPH_WIDTH, true, cstats);
  }

  public static void genReturnComparison(Sequence shiller, int numMonths, File file) throws IOException
  {
    int iStart = 0;// getIndexForDate(1881, 1);
    int iEnd = shiller.length() - 1;

    int percentStock = 60;
    int percentBonds = 40;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence cumulativeSNP = FinLib.calcSnpReturns(snpData, iStart, -1, FinLib.DividendMethod.MONTHLY);
    Sequence cumulativeBonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, -1);
    Sequence cumulativeMixed = Strategy.calcMixedReturns(new Sequence[] { cumulativeSNP, cumulativeBonds },
        new double[] { percentStock, percentBonds }, 6, 0.0);
    assert cumulativeSNP.length() == cumulativeBonds.length();

    Sequence snp = new Sequence("S&P");
    Sequence bonds = new Sequence("Bonds");
    Sequence mixed = new Sequence(String.format("Mixed (%d/%d)", percentStock, percentBonds));
    Sequence snpPremium = new Sequence("S&P Premium");
    int numBondWins = 0;
    int numStockWins = 0;
    for (int i = 0; i + numMonths < cumulativeSNP.size(); ++i) {
      int j = i + numMonths;
      double snpCAGR = FinLib.getAnnualReturn(cumulativeSNP.get(j, 0) / cumulativeSNP.get(i, 0), numMonths);
      double bondCAGR = FinLib.getAnnualReturn(cumulativeBonds.get(j, 0) / cumulativeBonds.get(i, 0), numMonths);
      double mixedCAGR = FinLib.getAnnualReturn(cumulativeMixed.get(j, 0) / cumulativeMixed.get(i, 0), numMonths);
      if (bondCAGR >= snpCAGR) {
        ++numBondWins;
      } else {
        ++numStockWins;
      }
      long timestamp = cumulativeBonds.getTimeMS(i);
      bonds.addData(bondCAGR, timestamp);
      snp.addData(snpCAGR, timestamp);
      mixed.addData(mixedCAGR, timestamp);
      snpPremium.addData(snpCAGR - bondCAGR, timestamp);
    }
    int N = numBondWins + numStockWins;
    double percentBondWins = 100.0 * numBondWins / N;
    double percentStockWins = 100.0 * numStockWins / N;
    System.out.printf("Future %d-months (Bonds vs. Stocks): %d vs. %d (%.1f%% vs. %.1f%%)\n", numMonths, numBondWins,
        numStockWins, percentBondWins, percentStockWins);

    snp.setName(String.format("S&P (%.1f%%)", percentStockWins));
    bonds.setName(String.format("Bonds (%.1f%%)", percentBondWins));

    String title = numMonths % 12 == 0 ? String.format("%d-Year Future Market Returns", numMonths / 12) : String
        .format("%d-Month Future Market Returns", numMonths);
    Chart.saveLineChart(file, title, GRAPH_WIDTH, GRAPH_HEIGHT, false, snp, bonds);
  }

  public static void genInterestRateGraph(Sequence shiller, Sequence tbills, File file) throws IOException
  {
    Sequence bonds = Shiller.getBondData(shiller);
    Chart.saveHighChart(file, ChartType.Line, "Interest Rates", null, null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 16.0, 1.0,
        false, 0, bonds, tbills);
  }

  public static void genCorrelationGraph(Sequence shiller, File dir) throws IOException
  {
    int iStartData = 0; // shiller.getIndexForDate(1999, 1);
    int iEndData = shiller.length() - 1;

    Sequence stockData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(stockData, 0, -1, FinLib.DividendMethod.MONTHLY);
    stock.setName("Stock");

    Sequence corr = FinLib.calcCorrelation(stock, bonds, 3 * 12);
    Chart.saveHighChart(new File(dir, "stock-bond-correlation.html"), ChartType.Area, corr.getName(), null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, -1.0, 1.0, 0.25, false, 0, corr);
  }

  public static Sequence calcEndBalances(Sequence cumulativeReturns, int nMonths)
  {
    Sequence balances = new Sequence("End Balances - " + cumulativeReturns.getName());
    for (int iStart = 0; iStart + nMonths < cumulativeReturns.size(); ++iStart) {
      double balance = FinLib.calcEndSavings(cumulativeReturns, 0.0, 5500.0, 2000.0, 0.1, iStart, nMonths);
      balances.addData(balance / 1e6, cumulativeReturns.getTimeMS(iStart));
    }
    return balances;
  }

  public static void genEndBalanceCharts(Sequence shiller, File dir) throws IOException
  {
    int nMonths = 20 * 12;

    int iStartData = 0; // shiller.getIndexForDate(1999, 1);
    int iEndData = shiller.length() - 1;
    Sequence stockData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence stock = FinLib.calcSnpReturns(stockData, 0, -1, FinLib.DividendMethod.MONTHLY);
    stock.setName("Stock");
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    bonds.setName("Bonds");

    int percentStock = 60;
    int percentBonds = 40;
    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 12, 0);

    Sequence ebStocks = calcEndBalances(stock, nMonths);
    Sequence ebBonds = calcEndBalances(bonds, nMonths);
    Sequence ebMixed = calcEndBalances(mixed, nMonths);

    Chart.saveHighChart(new File(dir, "end-balance.html"), ChartType.Line,
        String.format("End Balances (%s)", Library.formatDurationMonths(nMonths)), null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 0.5, false, 0, ebStocks, ebBonds, ebMixed);
  }

  public static void main(String[] args) throws IOException
  {
    if (args.length != 2) {
      System.err.println("Usage: java ~.ShillerSnp <shiller-data-file> <t-bill-file>");
      System.exit(1);
    }

    Sequence shiller = DataIO.loadShillerData(args[0]);
    Sequence tbills = DataIO.loadDateValueCSV(args[1]);
    tbills.setName("3-Month Treasury Bills");

    // long commonStart = Library.calcCommonStart(shiller, tbills);
    // long commonEnd = Library.calcCommonEnd(shiller, tbills);
    // System.out.printf("Shiller: [%s] -> [%s]\n", Library.formatDate(shiller.getStartMS()),
    // Library.formatDate(shiller.getEndMS()));
    // System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()),
    // Library.formatDate(tbills.getEndMS()));
    // System.out.printf("Common: [%s] -> [%s]\n", Library.formatDate(commonStart), Library.formatDate(commonEnd));

    // shiller = shiller.subseq(commonStart, commonEnd);
    tbills = null; // tbills.subseq(commonStart, commonEnd);

    File dir = new File("g:/web/");
    assert dir.isDirectory();

    buildCumulativeReturnsStore(shiller, tbills);

    // genInterestRateGraph(shiller, tbills, new File(dir, "interest-rates.html"));
    // compareRebalancingMethods(shiller, dir);
    // genReturnViz(shiller, dir);
    // genReturnChart(shiller, tbills, dir);
    // genSMASweepChart(shiller, dir);
    // genMomentumSweepChart(shiller, dir);
    // genStockBondMixSweepChart(shiller, dir);
    // genDuelViz(shiller, tbills, dir);
    // genEfficientFrontier(shiller, dir);
    // genCorrelationGraph(shiller, dir);
    // genEndBalanceCharts(shiller, dir);

    System.exit(0);

    // int retireAge = 65;
    // int ssAge = 70;
    // double expectedSocSecFraction = 0.7; // assume we'll only get a fraction of current SS estimate
    // double expectedMonthlySS = SOC_SEC_AT70 * expectedSocSecFraction;
    // int nYears = 105 - retireAge;
    // double expenseRatio = 0.1;
    // double likelihood = 0.99;
    // double taxRate = 0.30;
    // double desiredMonthlyCash = 6000.00;
    // double desiredRunwayYears = 1.0;
    // double salary = Shiller.calcAnnualSalary(desiredMonthlyCash, taxRate);
    // System.out.printf("Salary: %s\n", Shiller.FinLib.currencyFormatter.format(salary));
    // List<Integer> failures = shiller.calcSavingsTarget(salary, likelihood, nYears, expenseRatio, retireAge, ssAge,
    // expectedMonthlySS, desiredRunwayYears);
    // if (!failures.isEmpty()) {
    // System.out.println("Failures:");
    // for (int i : failures) {
    // System.out.printf(" [%s] -> [%s]\n", Library.formatDate(shiller.data.getTimeMS(i)),
    // Library.formatDate(shiller.data.getTimeMS(i + nYears * 12)));
    // }
    // }

    System.exit(0);
  }
}
