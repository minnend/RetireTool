package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.minnen.retiretool.Chart.ChartType;
import org.minnen.retiretool.Strategy.Disposition;

public class RetireTool
{
  public static final int GRAPH_WIDTH  = 710;
  public static final int GRAPH_HEIGHT = 450;

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

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStartData, iEndData);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(snpData, iStartData, iEndData - iStartData, FinLib.DividendMethod.MONTHLY);
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
    InvestmentStats[] cumulativeStats = new InvestmentStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == all[0].length();
      cumulativeStats[i] = InvestmentStats.calcInvestmentStats(all[i]);
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), cumulativeStats[i].cagr));
      System.out.printf("%s\n", cumulativeStats[i]);
    }

    Chart.saveLineChart(new File(dir, "rebalance-cumulative.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, all);
    Chart.saveStatsTable(new File(dir, "rebalance-table.html"), GRAPH_WIDTH, false, cumulativeStats);

    int duration = 10 * 12;
    ReturnStats[] stats = new ReturnStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = new ReturnStats(all[i], duration);
    }
    Chart.saveBoxPlots(new File(dir, "rebalance-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genReturnChart(Sequence shiller, Sequence tbills, File dir) throws IOException
  {
    assert tbills == null || shiller.length() == tbills.length();

    int iStartData = 0;
    int iEndData = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int nMonthsSMA = 10;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 12;

    Sequence snpData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStartData, iEndData);
    Sequence stockAll = FinLib
        .calcSnpReturns(snpData, iStartData, iEndData - iStartData, FinLib.DividendMethod.MONTHLY);
    // Sequence stockAllNoDiv = calcSnpReturns(snpData, iStartData, iEndData - iStartData,
    // FinLib.DividendMethod.NO_REINVEST);
    Sequence billsAll = tbills == null ? null : Bond.calcReturnsRebuy(BondFactory.bill3Month, tbills, iStartData,
        iEndData);

    Sequence stock = stockAll.subseq(iStartReturns);
    Sequence bonds = bondsAll.subseq(iStartReturns);

    Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, iStartReturns, iEndData);

    Sequence bills = null, billsHold = null;
    if (tbills != null) {
      bills = billsAll.subseq(iStartReturns);
      billsHold = Bond.calcReturnsHold(BondFactory.bill3Month, tbills, iStartReturns, iEndData);
    }

    Sequence stockNoDiv = FinLib.calcSnpReturns(snpData, iStartReturns, iEndData - iStartReturns,
        FinLib.DividendMethod.NO_REINVEST);

    int percentStock = 80;
    int percentBonds = 20;
    Sequence mix1 = Strategy.calcMixedReturns(new Sequence[] { stockAll, bondsAll }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, rebalanceBand);
    mix1.setName(String.format("Stock/Bonds [%d/%d]", percentStock, percentBonds));

    percentStock = 20;
    percentBonds = 80;
    Sequence mix2 = Strategy.calcMixedReturns(new Sequence[] { stockAll, bondsAll }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, rebalanceBand);
    mix2.setName(String.format("Stock/Bonds [%d/%d]", percentStock, percentBonds));

    Sequence risky = stockAll;
    Sequence safe = bondsAll;

    Sequence mom1 = Strategy.calcMomentumReturns(1, iStartReturns, risky, safe);
    Sequence mom3 = Strategy.calcMomentumReturns(3, iStartReturns, risky, safe);
    Sequence mom12 = Strategy.calcMomentumReturns(12, iStartReturns, risky, safe);
    Sequence sma = Strategy.calcSMAReturns(nMonthsSMA, iStartReturns, snpData, risky, safe);
    Sequence raa = Strategy.calcMixedReturns(new Sequence[] { sma, mom12 }, new double[] { 50, 50 }, rebalanceMonths,
        rebalanceBand);
    raa.setName("RAA");
    Sequence multiMomRisky = Strategy.calcMultiMomentumReturns(iStartReturns, risky, safe, Disposition.Risky);
    Sequence multiMomMod = Strategy.calcMultiMomentumReturns(iStartReturns, risky, safe, Disposition.Moderate);
    Sequence multiMomCautious = Strategy.calcMultiMomentumReturns(iStartReturns, risky, safe, Disposition.Cautious);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, risky, safe, Disposition.Safe);
    Sequence multiSmaRisky = Strategy.calcMultiSmaReturns(iStartReturns, snpData, risky, safe, Disposition.Risky);
    Sequence multiSmaMod = Strategy.calcMultiSmaReturns(iStartReturns, snpData, risky, safe, Disposition.Moderate);
    Sequence multiSmaCautious = Strategy.calcMultiSmaReturns(iStartReturns, snpData, risky, safe, Disposition.Cautious);
    Sequence multiSmaSafe = Strategy.calcMultiSmaReturns(iStartReturns, snpData, risky, safe, Disposition.Safe);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiSmaSafe }, new double[] { 30, 70 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    // ReturnStats.printDurationTable(mom12);
    // Chart.printDecadeTable(mom12, stock);

    // Sequence[] all = new Sequence[] { bills, billsHold, bonds, bondsHold, stock, stockNoDiv };
    // Sequence[] all = new Sequence[] { bonds, bondsHold, stock, stockNoDiv, mixed, momentum, sma, raa, daa,
    // multiMomRisky, multiMomMod, multiMomSafe, multiSmaRisky, multiSmaMod, multiSmaSafe };
    // Sequence[] all = new Sequence[] { stock, momentum, multiMomRisky, multiMomMod, multiMomCautious, multiMomSafe };
    // Sequence[] all = new Sequence[] { stock, mom12, sma, raa, daa, multiMomRisky, multiMomMod, multiMomCautious,
    // multiMomSafe, multiSmaRisky, multiSmaMod, multiSmaCautious, multiSmaSafe };
    Sequence[] all = new Sequence[] { stock, mom1, mom3, mom12, multiMomSafe, multiMomCautious, multiMomMod,
        multiMomRisky };
    InvestmentStats[] cumulativeStats = new InvestmentStats[all.length];
    ReturnStats[] rstats = new ReturnStats[all.length];
    double[] scores = new double[all.length];

    Sequence scatter = new Sequence("Returns vs. Volatility");
    int duration = 10 * 12;
    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == all[0].length();
      cumulativeStats[i] = InvestmentStats.calcInvestmentStats(all[i]);
      rstats[i] = ReturnStats.calc(all[i], duration);
      scores[i] = cumulativeStats[i].calcScore();
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), cumulativeStats[i].cagr));
      scatter.addData(new FeatureVec(all[i].getName(), 2, rstats[i].mean, rstats[i].sdev));
    }

    // Chart.saveLineChart(fileChart, "Cumulative Market Returns", GRAPH_WIDTH, GRAPH_HEIGHT, true, multiSmaRisky, daa,
    // multiMomSafe, sma, raa, momentum, stock, mixed, bonds, bondsHold);
    Chart.saveLineChart(new File(dir, "cumulative-returns.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, all);

    int[] ii = Library.sort(scores, false);
    for (int i = 0; i < all.length; ++i) {
      System.out.printf("%d [%.1f]: %s\n", i + 1, scores[i], cumulativeStats[ii[i]]);
    }
    Chart.saveStatsTable(new File(dir, "strategy-report.html"), GRAPH_WIDTH, true, cumulativeStats);

    Chart.saveBoxPlots(new File(dir, "strategy-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        rstats);

    Chart.saveScatterPlot(new File(dir, "strategy-scatter.html"),
        String.format("Momentum: Returns vs. Volatility (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH,
        GRAPH_HEIGHT, 5, scatter);

    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 1; i < all.length; ++i) {
      comparisons.add(ComparisonStats.calc(all[i], all[0]));
    }
    Chart.saveComparisonTable(new File(dir, "strategy-comparisons.html"), GRAPH_WIDTH, comparisons);
  }

  public static void genReturnViz(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int nMonths = 20 * 12;
    int percentStock = 60;
    int percentBonds = 40;
    // int percentCash = 100 - (percentStock + percentBonds);

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 0;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stockAll = FinLib.calcSnpReturns(snpData, iStart, iEnd - iStart, FinLib.DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, iEnd);

    // Chart.saveLineChart(new File(dir, "stock-prices.html"), "S&P 500 Prices", GRAPH_WIDTH, GRAPH_HEIGHT, false,
    // snpData);

    Sequence stock = stockAll.subseq(iStartReturns);
    Sequence bonds = bondsAll.subseq(iStartReturns);

    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, rebalanceBand);
    Sequence momentum = Strategy.calcMomentumReturns(nMonthsMomentum, iStartReturns, stockAll, bondsAll);
    Sequence sma = Strategy.calcSMAReturns(nMonthsSMA, iStartReturns, snpData, stockAll, bondsAll);
    Sequence raa = Strategy.calcMixedReturns(new Sequence[] { sma, momentum }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    raa.setName("RAA");

    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Safe);
    Sequence multiSmaRisky = Strategy
        .calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Risky);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiMomSafe }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    // System.out.printf("Stock Total Return: %f\n", getReturn(stock, 0, stock.length()-1));
    // Chart.printDecadeTable(stockAll);
    Sequence returnLiks = FinLib.calcReturnLikelihoods(stockAll, true);
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
      ReturnStats.printDurationTable(assets[i]);
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
    assert tbills == null || shiller.length() == tbills.length();

    int nMonths = 10 * 12;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 12;
    int iStartData = 0;
    int iEnd = shiller.length() - 1;

    Sequence stockData = Shiller.getStockData(shiller, iStartData, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEnd);

    Sequence stockAll = FinLib.calcSnpReturns(stockData, iStartData, iEnd - iStartData, FinLib.DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStartData, iEnd);
    stockAll.setName("Stock");
    bondsAll.setName("Bonds");

    Sequence stock = stockAll.subseq(iStartReturns);
    Sequence bonds = bondsAll.subseq(iStartReturns);

    Sequence billsAll = null, bills = null;
    if (tbills != null) {
      billsAll = Bond.calcReturnsRebuy(BondFactory.bill3Month, tbills, iStartData, iEnd);
      billsAll.setName("Bills");
      bills = billsAll.subseq(iStartReturns);
    }

    // double leverage = 1.8794;
    // bondsAll = FinLib.calcLeveragedReturns(bondsAll, leverage);

    int percentStock = 60;
    int percentBonds = 40;
    assert percentStock + percentBonds == 100;
    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, 0.0);
    mixed.setName(String.format("Stock/Bonds-%d/%d (M12)", percentStock, percentBonds));

    // double leverage = 1.167; // 1.8794;
    // mixed = FinLib.calcLeveragedReturns(mixed, leverage);

    // Sequence mixedB5 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
    // percentBonds }, 0, rebalanceBand);
    // mixedB5.setName(String.format("Stock/Bonds-%d/%d (B5)", percentStock, percentBonds));

    Strategy.calcMultiMomentumStats(stockAll, bondsAll);
    // Strategy.calcSmaStats(snpData, stockAll, bondsAll);

    Sequence mom1 = Strategy.calcMomentumReturns(1, iStartReturns, stockAll, bondsAll);
    Sequence mom3 = Strategy.calcMomentumReturns(3, iStartReturns, stockAll, bondsAll);
    Sequence mom12 = Strategy.calcMomentumReturns(12, iStartReturns, stockAll, bondsAll);
    Sequence multiMomRisky = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Risky);
    Sequence multiMomMod = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Moderate);
    Sequence multiMomCautious = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll,
        Disposition.Cautious);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Safe);
    Sequence multiSmaRisky = Strategy.calcMultiSmaReturns(iStartReturns, stockData, stockAll, bondsAll,
        Disposition.Risky);
    Sequence multiSmaMod = Strategy.calcMultiSmaReturns(iStartReturns, stockData, stockAll, bondsAll,
        Disposition.Moderate);
    Sequence multiSmaCautious = Strategy.calcMultiSmaReturns(iStartReturns, stockData, stockAll, bondsAll,
        Disposition.Cautious);
    Sequence multiSmaSafe = Strategy
        .calcMultiSmaReturns(iStartReturns, stockData, stockAll, bondsAll, Disposition.Safe);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiMomSafe }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    Sequence[] assets = new Sequence[] { stock, bonds, mixed, mom1, mom3, mom12, multiMomRisky, multiMomMod,
        multiMomCautious, multiMomSafe, multiSmaRisky, multiSmaMod, multiSmaCautious, multiSmaSafe, daa };

    int player1 = 0;
    int player2 = 6;
    ComparisonStats comparison = ComparisonStats.calc(assets[player2], assets[player1]);
    Chart.saveComparisonTable(new File(dir, "duel-comparison.html"), GRAPH_WIDTH, comparison);
    InvestmentStats[] stats = InvestmentStats.calc(assets[player2], assets[player1]);
    Chart.saveStatsTable(new File(dir, "duel-chart.html"), GRAPH_WIDTH, false, stats);

    Chart.saveHighChart(new File(dir, "duel-cumulative.html"), ChartType.Line, "Cumulative Market Returns", null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, 1, Double.NaN, 1.0, true, 0, assets[player2], assets[player1]);

    // Calculate returns for each strategy for the requested duration.
    Sequence[] returns = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      returns[i] = FinLib.calcReturnsForDuration(assets[i], nMonths);
      returns[i].setName(assets[i].getName());
    }
    Sequence returnsA = returns[player1];
    Sequence returnsB = returns[player2];

    // ReturnStats.printDurationTable(assets[player1]);
    // ReturnStats.printDurationTable(assets[player2]);

    // Generate scatter plot comparing results.
    String title = String.format("%s vs. %s (%s)", returnsB.getName(), returnsA.getName(),
        Library.formatDurationMonths(nMonths));
    Chart.saveHighChartScatter(new File(dir, "duel-scatter.html"), title, 730, GRAPH_HEIGHT, 0, returnsA, returnsB);

    // Generate histogram summarizing excess returns of B over A.
    title = String.format("Excess Returns: %s vs. %s (%s)", returnsB.getName(), returnsA.getName(),
        Library.formatDurationMonths(nMonths));
    Sequence excessReturns = returnsB.sub(returnsA);
    Sequence histogramExcess = FinLib.computeHistogram(excessReturns, 0.5, 0.0);
    histogramExcess.setName(String.format("%s vs. %s", returnsB.getName(), returnsA.getName()));
    String[] colors = new String[excessReturns.length()];
    for (int i = 0; i < colors.length; ++i) {
      double x = excessReturns.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }

    Chart.saveHighChart(new File(dir, "duel-returns.html"), Chart.ChartType.Line, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, false, 0, returnsB, returnsA);
    Chart.saveHighChart(new File(dir, "duel-excess-histogram.html"), Chart.ChartType.PosNegArea, title, null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, false, 0, excessReturns);

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

    Sequence snp = FinLib.calcSnpReturns(snpData, iStart, iEnd - iStart, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, iEnd);

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
    InvestmentStats[] cumulativeStats = InvestmentStats.calc(all);

    if (useLeverage) {
      for (int i = 0; i < all.length; ++i) {
        double leverage = FinLib.calcEqualizingLeverage(all[i], cumulativeStats[0].cagr);
        all[i] = FinLib.calcLeveragedReturns(all[i], leverage);
        cumulativeStats[i] = InvestmentStats.calcInvestmentStats(all[i]);
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
    ReturnStats[] stats = new ReturnStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = new ReturnStats(all[i], duration);
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

    Sequence stock = FinLib.calcSnpReturns(stockData, iStart, iEnd - iStart, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, iEnd);
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
        InvestmentStats stats = InvestmentStats.calcInvestmentStats(mixed);
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
        ReturnStats stats = new ReturnStats(mixed, duration);
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

  public static void genSMASweepChart(Sequence shiller, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stock = FinLib.calcSnpReturns(snpData, iStart, iEnd - iStart, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, iEnd);

    int[] months = new int[] { 1, 2, 3, 4, 5, 6, 9, 10, 12 }; // , 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length];
    for (int i = 0; i < months.length; ++i) {
      Sequence sma = Strategy.calcSMAReturns(months[i], months[months.length - 1], snpData, stock, bonds);
      double cagr = FinLib.getAnnualReturn(sma.getLast(0), N);
      sma.setName(String.format("SMA-%d (%.2f%%)", months[i], cagr));
      seqs[i] = sma;
      // System.out.println(InvestmentStats.calcInvestmentStats(sma));
    }

    Chart.saveLineChart(file, "Cumulative Market Returns: SMA Strategy", GRAPH_WIDTH, GRAPH_HEIGHT, true, seqs);
  }

  public static void genMomentumSweepChart(Sequence shiller, File dir) throws IOException
  {
    // final int[] momentumMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18 }; // , 21, 24, 30, 36 };
    final int[] momentumMonths = new int[] { 1, 3, 12 };
    // final int[] durations = new int[] { 5, 10, 15, 20, 25, 30 };

    // iStart = shiller.getIndexForDate(1900, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    final int iStart = shiller.getIndexForDate(2003, 12);
    // final int iEnd = shiller.length() - 1;
    final int iEnd = shiller.getIndexForDate(2014, 12);
    final int iStartSimulation = momentumMonths[momentumMonths.length - 1];
    int duration = 10 * 12;

    Sequence stockData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);
    assert stockData.length() == bondData.length();
    System.out.printf("Data: [%s] - [%s]\n", Library.formatMonth(stockData.getStartMS()),
        Library.formatMonth(stockData.getEndMS()));

    Sequence stockAll = FinLib.calcSnpReturns(stockData, 0, stockData.size() - 1, FinLib.DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, bondData.size() - 1);
    assert stockAll.length() == bondsAll.length();
    assert Math.abs(stockAll.getFirst(0) - 1.0) < 1e-8;
    assert Math.abs(bondsAll.getFirst(0) - 1.0) < 1e-8;
    System.out.printf("All: [%s] - [%s]\n", Library.formatMonth(stockAll.getStartMS()),
        Library.formatMonth(bondsAll.getEndMS()));

    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    stock._div(stock.getFirst(0));
    bonds._div(bonds.getFirst(0));
    assert stock.length() == bonds.length();
    assert Math.abs(stock.getFirst(0) - 1.0) < 1e-8;
    assert Math.abs(bonds.getFirst(0) - 1.0) < 1e-8;
    System.out.printf("Sim: [%s] - [%s]\n", Library.formatMonth(stock.getStartMS()),
        Library.formatMonth(bonds.getEndMS()));

    InvestmentStats stockStats = InvestmentStats.calcInvestmentStats(stock);
    stock.setName(String.format("Stock (%.2f%%)", stockStats.cagr));
    ReturnStats stockReturns = ReturnStats.calc(stock, duration);

    InvestmentStats bondStats = InvestmentStats.calcInvestmentStats(bonds);
    bonds.setName(String.format("Bonds (%.2f%%)", bondStats.cagr));
    ReturnStats bondReturns = ReturnStats.calc(bonds, duration);

    Sequence perfect = Strategy.calcPerfectReturns(iStartSimulation, stockAll, bondsAll);
    InvestmentStats perfectStats = InvestmentStats.calcInvestmentStats(perfect);
    perfect.setName(String.format("Perfect (%.2f%%)", perfectStats.cagr));
    ReturnStats perfectReturns = ReturnStats.calc(perfect, duration);

    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { 60, 40 }, 12, 0.0);
    assert Math.abs(mixed.getFirst(0) - 1.0) < 1e-8;
    InvestmentStats mixedStats = InvestmentStats.calcInvestmentStats(mixed);
    mixed.setName(String.format("60/40 (%.2f%%)", mixedStats.cagr));

    Sequence scatter = new Sequence("Momentum Results");
    List<Sequence> seqs = new ArrayList<Sequence>();
    List<InvestmentStats> cumulativeStats = new ArrayList<InvestmentStats>();
    List<ReturnStats> rstats = new ArrayList<ReturnStats>();
    for (int i = 0; i < momentumMonths.length; ++i) {
      Sequence mom = Strategy.calcMomentumReturns(momentumMonths[i], iStartSimulation, stockAll, bondsAll);
      assert Math.abs(mom.getFirst(0) - 1.0) < 1e-8;
      assert mom.length() == stock.length();
      System.out.printf("Mom-%d: [%s] - [%s]\n", momentumMonths[i], Library.formatMonth(mom.getStartMS()),
          Library.formatMonth(mom.getEndMS()));
      cumulativeStats.add(InvestmentStats.calcInvestmentStats(mom));
      rstats.add(ReturnStats.calc(mom, duration));
      mom.setName(String.format("Momentum-%d (%.2f%%)", momentumMonths[i], cumulativeStats.get(i).cagr));
      scatter.addData(new FeatureVec("" + momentumMonths[i], 2, rstats.get(i).mean, rstats.get(i).sdev));
      seqs.add(mom);
      // System.out.println(InvestmentStats.calcInvestmentStats(mom));
    }
    seqs.add(0, stock);
    seqs.add(1, bonds);
    seqs.add(2, mixed);
    scatter.addData(new FeatureVec("Stock", 2, stockReturns.mean, stockReturns.sdev));

    // Sequence multiMom = Strategy.calcMultiMomentumReturns(months[months.length - 1], snp, bonds, Disposition.Safe);
    // double cagr = FinLib.getAnnualReturn(multiMom.getLast(0), N);
    // multiMom.setName(String.format("MultiMomentum (%.2f%%)", cagr));
    // seqs[months.length] = multiMom;

    Chart.saveLineChart(new File(dir, "momentum-cumulative.html"), "Cumulative Market Returns: Momentum Strategy",
        GRAPH_WIDTH, GRAPH_HEIGHT, true, seqs.toArray(new Sequence[seqs.size()]));

    rstats.add(0, stockReturns);
    rstats.add(1, bondReturns);
    // rstats.add(2, perfectReturns);
    cumulativeStats.add(0, stockStats);
    cumulativeStats.add(1, bondStats);
    cumulativeStats.add(2, perfectStats);
    Chart.saveBoxPlots(new File(dir, "momentum-box-plots.html"),
        String.format("Momentum Returns (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        rstats);
    Chart.saveStatsTable(new File(dir, "momentum-chart.html"), GRAPH_WIDTH, true, cumulativeStats);

    Chart.saveScatterPlot(new File(dir, "momentum-scatter.html"),
        String.format("Momentum: Returns vs. Volatility (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH,
        GRAPH_HEIGHT, 5, scatter);

    // Generate efficient frontier curves.
    // Sequence[] frontiers = new Sequence[durations.length];
    // for (int i = 0; i < durations.length; ++i) {
    // duration = durations[i] * 12;
    // Sequence frontier = new Sequence(Library.formatDurationMonths(duration));
    // for (int j = 0; j < momentumMonths.length; ++j) {
    // Sequence mom = Strategy.calcMomentumReturns(momentumMonths[j], iStartSimulation, stock, bonds);
    // ReturnStats stats = new ReturnStats(mom, duration);
    // String name = String.format("%d", momentumMonths[j]);
    // frontier.addData(new FeatureVec(name, 2, stats.mean, stats.sdev));
    // }
    // frontiers[i] = frontier;
    // }
    // Chart.saveHighChartSplines(new File(dir, "momentum-duration-curves.html"), "Momentum Frontier", GRAPH_WIDTH,
    // GRAPH_HEIGHT, frontiers);
  }

  public static void genReturnComparison(Sequence shiller, int numMonths, File file) throws IOException
  {
    int iStart = 0;// getIndexForDate(1881, 1);
    int iEnd = shiller.length() - 1;

    int percentStock = 60;
    int percentBonds = 40;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence cumulativeSNP = FinLib.calcSnpReturns(snpData, iStart, iEnd - iStart, FinLib.DividendMethod.MONTHLY);
    Sequence cumulativeBonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, iEnd);
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

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, bondData.length() - 1);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(stockData, 0, stockData.length() - 1, FinLib.DividendMethod.MONTHLY);
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

    Sequence stock = FinLib.calcSnpReturns(stockData, 0, stockData.length() - 1, FinLib.DividendMethod.MONTHLY);
    stock.setName("Stock");
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, bondData.length() - 1);
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

    long commonStart = Library.calcCommonStart(shiller, tbills);
    long commonEnd = Library.calcCommonEnd(shiller, tbills);

    System.out.printf("Shiller: [%s] -> [%s]\n", Library.formatDate(shiller.getStartMS()),
        Library.formatDate(shiller.getEndMS()));
    System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()),
        Library.formatDate(tbills.getEndMS()));
    System.out.printf("Common: [%s] -> [%s]\n", Library.formatDate(commonStart), Library.formatDate(commonEnd));

    // shiller = shiller.subseq(commonStart, commonEnd);
    tbills = null; // tbills.subseq(commonStart, commonEnd);

    // shiller.printWithdrawalLikelihoods(30, 0.1);
    // shiller.genReturnChart(Inflation.Ignore, new File("g:/test.html"));
    // int[] years = new int[] { 1, 2, 5, 10, 15, 20, 30, 40, 50 };
    // for (int i = 0; i < years.length; ++i) {
    // shiller.genReturnComparison(years[i] * 12, Inflation.Ignore, new File("g:/test.html"));
    // }

    File dir = new File("g:/web/");
    assert dir.isDirectory();

    // genInterestRateGraph(shiller, tbills, new File(dir, "interest-rates.html"));
    // compareRebalancingMethods(shiller, dir);
    // genReturnViz(shiller, dir);
    // genReturnChart(shiller, tbills, dir);
    // genSMASweepChart(shiller, new File(dir, "sma-sweep.html"));
    genMomentumSweepChart(shiller, dir);
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
