package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

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
import org.minnen.retiretool.stats.RetirementStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.stats.WinStats;

public class RetireTool
{
  public static final int                    GRAPH_WIDTH              = 710;
  public static final int                    GRAPH_HEIGHT             = 450;

  public final static SequenceStore          store                    = new SequenceStore();

  public static final int[]                  percentRisky             = new int[] { 90, 80, 70, 60, 50, 40, 30, 20, 10 };
  public static final Strategy.Disposition[] dispositions             = Strategy.Disposition.values();

  public static final boolean                BUILD_COMPLEX_STRATEGIES = false;

  public static void setupShillerData(File dataDir, File dir) throws IOException
  {
    Sequence shiller = DataIO.loadShillerData(new File(dataDir, "shiller.csv"));
    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData = FinLib.pad(tbillData, shiller, 0.0);

    // Sequence nikkeiDaily = DataIO.loadDateValueCSV(new File(dataDir, "nikkei225-daily.csv"));
    // Sequence nikkei = FinLib.daily2monthly(nikkeiDaily);

    // long commonStart = Library.calcCommonStart(shiller, tbillData);
    // long commonEnd = Library.calcCommonEnd(shiller, tbillData);
    // System.out.printf("Shiller: [%s] -> [%s]\n", Library.formatDate(shiller.getStartMS()),
    // Library.formatDate(shiller.getEndMS()));
    // System.out.printf("Stock: [%s] -> [%s]\n", Library.formatMonth(nikkei.getStartMS()),
    // Library.formatMonth(nikkei.getEndMS()));
    // System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()),
    // Library.formatDate(tbills.getEndMS()));
    // System.out.printf("Common: [%s] -> [%s]\n", Library.formatDate(commonStart), Library.formatDate(commonEnd));

    // shiller = shiller.subseq(commonStart, commonEnd);
    // tbillData = tbillData.subseq(commonStart, commonEnd);
    // nikkei = nikkei.subseq(commonStart, commonEnd);

    buildCumulativeReturnsStore(shiller, tbillData, null, BUILD_COMPLEX_STRATEGIES);
  }

  public static void setupVanguardData(File dataDir, File dir) throws IOException
  {
    final int iStartSimulation = 12;

    Sequence stockDaily = DataIO.loadYahooData(new File(dataDir, "VTSMX.csv"));
    Sequence stock = FinLib.daily2monthly(stockDaily);
    System.out.printf("Stock: [%s] -> [%s]\n", Library.formatMonth(stock.getStartMS()),
        Library.formatMonth(stock.getEndMS()));

    Sequence bondDaily = DataIO.loadYahooData(new File(dataDir, "VBMFX.csv"));
    Sequence bonds = FinLib.daily2monthly(bondDaily);
    System.out.printf("Bond: [%s] -> [%s]\n", Library.formatMonth(bonds.getStartMS()),
        Library.formatMonth(bonds.getEndMS()));

    Sequence reitsDaily = DataIO.loadYahooData(new File(dataDir, "VGSIX.csv"));
    Sequence reits = FinLib.daily2monthly(reitsDaily);
    System.out.printf("REIT: [%s] -> [%s]\n", Library.formatMonth(reits.getStartMS()),
        Library.formatMonth(reits.getEndMS()));

    Sequence istockDaily = DataIO.loadYahooData(new File(dataDir, "VGTSX.csv"));
    Sequence istock = FinLib.daily2monthly(istockDaily);
    System.out.printf("Int Stock: [%s] -> [%s]\n", Library.formatMonth(istock.getStartMS()),
        Library.formatMonth(istock.getEndMS()));

    Sequence shiller = DataIO.loadShillerData(new File(dataDir, "shiller.csv"));

    long commonStart = Library.calcCommonStart(stock, bonds, reits, istock, shiller);
    long commonEnd = Library.calcCommonEnd(stock, bonds, reits, istock, shiller);
    System.out.printf("Common: [%s] -> [%s]\n", Library.formatMonth(commonStart), Library.formatMonth(commonEnd));

    stock = stock.subseq(commonStart, commonEnd);
    bonds = bonds.subseq(commonStart, commonEnd);
    reits = reits.subseq(commonStart, commonEnd);
    istock = istock.subseq(commonStart, commonEnd);
    shiller = shiller.subseq(commonStart, commonEnd);

    store.addMisc(stock, "Stock-All");
    store.addMisc(bonds, "Bonds-All");
    store.addMisc(reits, "REITs-All");
    store.addMisc(istock, "IntStock-All");

    // Add CPI data.
    Sequence cpi = Shiller.getInflationData(shiller);
    store.addMisc(cpi, "cpi");

    store.add(stock.dup().subseq(iStartSimulation), "Stock");
    store.add(bonds.dup().subseq(iStartSimulation), "Bonds");
    store.add(reits.dup().subseq(iStartSimulation), "REITs");
    store.add(istock.dup().subseq(iStartSimulation), "IntStock");

    String riskyName = "intstock";
    String safeName = "bonds";
    Sequence risky = store.getMisc(riskyName + "-all");
    Sequence safe = store.getMisc(safeName + "-all");

    addStrategiesToStore(risky, safe, risky, iStartSimulation);

    Chart.saveLineChart(new File(dir, "vanguard-funds.html"), "Vanguard Funds", GRAPH_WIDTH, GRAPH_HEIGHT, true,
        store.getReturns("Stock", "Bonds", "REITs", "IntStock"));

    Chart.saveLineChart(new File(dir, "vanguard-momentum.html"), "Vanguard Momentum", GRAPH_WIDTH, GRAPH_HEIGHT, true,
        store.getReturns(riskyName, safeName, "Momentum-1", "MultiMom-Safe", "MultiMom-Cautious", "MultiMom-Moderate",
            "MultiMom-Risky", "Mom.Risky/SMA.Safe-50/50"));

    Chart.saveLineChart(new File(dir, "vanguard-sma.html"), "Vanguard SMA", GRAPH_WIDTH, GRAPH_HEIGHT, true, store
        .getReturns(riskyName, safeName, "sma-1", "sma-3", "sma-5", "sma-10", "MultiSMA-Safe", "MultiSMA-Cautious",
            "MultiSMA-Moderate", "MultiSMA-Risky"));
  }

  public static void buildCumulativeReturnsStore(Sequence shiller, Sequence tbillData, Sequence nikkeiAll,
      boolean buildComplexStrategies)
  {
    assert tbillData == null || tbillData.matches(shiller);
    assert nikkeiAll == null || nikkeiAll.matches(shiller);

    final long startMS = System.currentTimeMillis();

    final int iStartData = 0; // shiller.getIndexForDate(1987, 12);
    final int iEndData = -1; // shiller.getIndexForDate(2009, 12);

    final int iStartSimulation = 12;

    // Extract stock and bond data from shiller sequence.
    Sequence stockData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);
    assert stockData.matches(bondData);
    System.out.printf("Build Store: [%s] -> [%s]\n", Library.formatMonth(stockData.getStartMS()),
        Library.formatMonth(stockData.getEndMS()));
    store.addMisc(stockData, "StockData");
    store.addMisc(bondData, "BondData");

    // Add CPI data.
    store.addMisc(Shiller.getInflationData(shiller, iStartData, iEndData), "cpi");

    // Calculate cumulative returns for full stock, bond, and t-bill data.
    Sequence stockAll = FinLib.calcSnpReturns(stockData, 0, -1, FinLib.DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    assert stockAll.matches(bondsAll);
    store.addMisc(stockAll, "Stock-All");
    store.addMisc(bondsAll, "Bonds-All");
    Sequence tbillsAll = null;
    if (tbillData != null) {
      store.addMisc(tbillData, "TBillData");
      tbillsAll = Bond.calcReturnsRebuy(BondFactory.bill3Month, tbillData, iStartData, iEndData);
      assert stockAll.matches(tbillsAll);
      store.addMisc(tbillsAll, "TBills-All");

      Sequence cash = FinLib.calcInterestReturns(tbillData);
      assert tbillData.matches(cash);
      store.addMisc(cash, "cash");
    }

    // Extract requested subsequences from cumulative returns for stock, bonds, and t-bills.
    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    stock._div(stock.getFirst(0));
    bonds._div(bonds.getFirst(0));
    assert stock.matches(bonds);
    store.add(stock, "Stock");
    store.add(bonds, "Bonds");

    if (tbillsAll != null) {
      Sequence tbills = tbillsAll.subseq(iStartSimulation);
      tbills._div(tbills.getFirst(0));
      store.add(tbills, "TBills");
    }

    if (nikkeiAll != null) {
      nikkeiAll = nikkeiAll.subseq(iStartData, iEndData);
      store.addMisc(nikkeiAll, "Nikkei-All");
      Sequence nikkei = nikkeiAll.subseq(iStartSimulation);
      nikkei._div(nikkei.getFirst(0));
      store.add(nikkei, "Nikkei");
    }

    Sequence stockQuarterlyDiv = FinLib
        .calcSnpReturns(stockData, iStartSimulation, -1, FinLib.DividendMethod.QUARTERLY);
    store.add(stockQuarterlyDiv, "Stock [QuarterlyDiv]");
    Sequence stockNoDiv = FinLib.calcSnpReturns(stockData, iStartSimulation, -1, FinLib.DividendMethod.NO_REINVEST);
    store.add(stockNoDiv, "Stock [NoDiv]");
    Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, iStartSimulation, -1);
    store.add(bondsHold, "Bonds [Hold]");

    // Setup risky and safe assets for use with dynamic asset allocation strategies.
    if (buildComplexStrategies) {
      Sequence risky = stockAll;
      Sequence safe = bondsAll;
      addStrategiesToStore(risky, safe, risky, iStartSimulation);
    }

    System.out.printf("Finished Building Store (%s).\n", Library.formatDuration(Library.getTime() - startMS));
  }

  public static void addStrategiesToStore(Sequence risky, Sequence safe, Sequence prices, int iStartSimulation)
  {
    final int rebalanceMonths = 12;
    final double rebalanceBand = 0.0;
    final Slippage slippage = Slippage.None; // new Slippage(0.01, 0.1);

    // Stock / Bond mixes.
    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    for (int i = 0; i < percentStock.length; ++i) {
      int percentBonds = 100 - percentStock[i];
      Sequence mix = Strategy.calcMixedReturns(new Sequence[] { store.get("Stock"), store.get("bonds") }, new double[] {
          percentStock[i], percentBonds }, rebalanceMonths, rebalanceBand);
      mix.setName(String.format("Stock/Bonds [%d/%d]", percentStock[i], percentBonds));
      store.alias(String.format("%d/%d", percentStock[i], percentBonds), mix.getName());
      store.add(mix);
    }

    // Momentum sweep.
    final int[] momentumMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
    for (int i = 0; i < momentumMonths.length; ++i) {
      WinStats winStats = new WinStats();
      Sequence momOrig = Strategy.calcMomentumReturns(momentumMonths[i], iStartSimulation, slippage, winStats, risky,
          safe);
      MomentumPredictor predictor = new MomentumPredictor(momentumMonths[i], store);
      Sequence mom = Strategy.calcReturns(predictor, iStartSimulation, slippage, winStats, risky, safe);

      assert mom.length() == momOrig.length(); // TODO
      for (int ii = 0; ii < mom.length(); ++ii) {
        double a = mom.get(ii, 0);
        double b = momOrig.get(ii, 0);
        assert Math.abs(a - b) < 1e-6 : String.format("%f vs. %f", a, b);
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
    int[] smaMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
    for (int i = 0; i < smaMonths.length; ++i) {
      WinStats winStats = new WinStats();
      Sequence smaOrig = Strategy.calcSMAReturns(smaMonths[i], iStartSimulation, slippage, prices, risky, safe);
      SMAPredictor predictor = new SMAPredictor(smaMonths[i], prices.getName(), store);
      Sequence sma = Strategy.calcReturns(predictor, iStartSimulation, slippage, winStats, risky, safe);

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
      Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, slippage, risky, safe, disposition);
      Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, slippage, prices, risky, safe, disposition);
      store.add(mom);
      store.add(sma);

      // System.out.println(store.getCumulativeStats(mom.getName()));
      // System.out.println(store.getCumulativeStats(sma.getName()));
    }

    // Full multi-momentum/sma sweep.
    for (int assetMap = 255, i = 0; i < 8; ++i) {
      Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, slippage, risky, safe, assetMap);
      Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, slippage, prices, risky, safe, assetMap);
      store.add(mom);
      store.add(sma);
      assetMap &= ~(1 << i);
    }

    // Mixed multiscale momentum strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      String name1 = "MultiMom-" + dispositions[j];
      for (int k = j + 1; k < dispositions.length; ++k) {
        String name2 = "MultiMom-" + dispositions[k];
        for (int i = 0; i < percentRisky.length; ++i) {
          Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
              percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
          store.add(seq, String.format("Mom.%s/Mom.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
              100 - percentRisky[i]));
        }
      }
    }

    // Mixed multiscale sma strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      String name1 = "MultiSMA-" + dispositions[j];
      for (int k = j + 1; k < dispositions.length; ++k) {
        String name2 = "MultiSMA-" + dispositions[k];
        for (int i = 0; i < percentRisky.length; ++i) {
          Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
              percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
          store.add(seq, String.format("SMA.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
              100 - percentRisky[i]));
        }
      }
    }

    // Mixed Momentum-1 and multiscale strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      String name2 = "MultiMom-" + dispositions[j];
      for (int i = 0; i < percentRisky.length; ++i) {
        Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get("Momentum-1"), store.get(name2) },
            new double[] { percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
        store.add(seq,
            String.format("Momentum-1/Mom.%s-%d/%d", dispositions[j], percentRisky[i], 100 - percentRisky[i]));
      }
    }

    // Add strategies with fixed stock / bond positions.
    for (int i = 0; i <= 90; i += 10) {
      for (int j = 0; j <= 90 && i + j < 100; j += 10) {
        int k = 100 - (i + j);
        for (Strategy.Disposition disposition : dispositions) {
          // Mix in momentum.
          Sequence seq = Strategy.calcMixedReturns(
              new Sequence[] { store.get("stock"), store.get("bonds"), store.get("multimom-" + disposition) },
              new double[] { i, j, k }, 12, 0.0);

          String name;
          if (i == 0) {
            name = String.format("Bonds/Mom.%s-%d/%d", disposition, j, k);
          } else if (j == 0) {
            name = String.format("Stock/Mom.%s-%d/%d", disposition, i, k);
          } else {
            name = String.format("Stock/Bonds/Mom.%s-%d/%d/%d", disposition, i, j, k);
          }
          store.add(seq, name);

          // Mix in SMA.
          seq = Strategy.calcMixedReturns(
              new Sequence[] { store.get("stock"), store.get("bonds"), store.get("multisma-" + disposition) },
              new double[] { i, j, k }, 12, 0.0);

          if (i == 0) {
            name = String.format("Bonds/SMA.%s-%d/%d", disposition, j, k);
          } else if (j == 0) {
            name = String.format("Stock/SMA.%s-%d/%d", disposition, i, k);
          } else {
            name = String.format("Stock/Bonds/SMA.%s-%d/%d/%d", disposition, i, j, k);
          }
          store.add(seq, name);
        }
      }
    }

    // Mixed multiscale mom and sma strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      String name1 = "MultiMom-" + dispositions[j];
      for (int k = 0; k < dispositions.length; ++k) {
        String name2 = "MultiSMA-" + dispositions[k];
        for (int i = 0; i < percentRisky.length; ++i) {
          Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
              percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
          store.add(seq, String.format("Mom.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
              100 - percentRisky[i]));
        }
      }
    }

    // Strategy.calcMultiMomentumStats(risky, safe);

    // DAA = 50/50 SMA-risky & Momentum-safe.
    Sequence daa = Strategy.calcMixedReturns(
        new Sequence[] { store.get("multisma-risky"), store.get("multimom-moderate") }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    store.add(daa, "DAA");

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
      stats[i] = DurationalStats.calc(all[i], duration);
    }
    Chart.saveBoxPlots(new File(dir, "rebalance-box.html"),
        String.format("Return Stats (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genReturnChart(File dir) throws IOException
  {
    // String[] names = new String[] { "stock", "bonds", "momentum-1", "momentum-3", "momentum-12", "multimom-safe",
    // "multimom-cautious", "multimom-moderate", "multimom-risky" };

    // String[] names = new String[] { "stock", "bonds", "60/40", "momentum-1", "multimom-risky",
    // "Mom.Risky/Mom.Cautious-20/80", "Bonds/Mom.Safe-10/90" };

    // String[] names = new String[] { "stock", "momentum-1", "multimom-risky", "Mom.Risky/Mom.Moderate-30/70",
    // "Mom.Risky/Mom.Cautious-20/80", "Mom.Risky/Mom.Safe-10/90", "Bonds/Mom.Safe-10/90",
    // "Mom.Moderate/SMA.Safe-60/40" };

    String[] names = new String[] { "stock", "bonds", "tbills" };

    final int duration = 10 * 12;

    store.recalcDurationalStats(duration, FinLib.Inflation.Ignore);
    List<Sequence> all = store.getReturns(names);

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    List<DurationalStats> rstats = store.getDurationalStats(names);
    double[] scores = new double[names.length];

    Sequence scatter = new Sequence("Returns vs. Volatility");
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

    // Win rate vs. first strategy.
    double diffMargin = 0.25;
    Sequence defender = store.get("stock");
    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 0; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(all.get(i), defender, diffMargin));
    }
    Chart.saveComparisonTable(new File(dir, "win-vs-one.html"), GRAPH_WIDTH, comparisons);

    // Win rate vs. first multiple defenders.
    List<Sequence> defenders = store.getReturns("stock", "bonds", "60/40", "80/20", "50/50");
    comparisons.clear();
    for (int i = 0; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(all.get(i), diffMargin, defenders.toArray(new Sequence[defenders.size()])));
    }
    Chart.saveComparisonTable(new File(dir, "win-vs-many.html"), GRAPH_WIDTH, comparisons);
  }

  public static void genReturnViz(File dir) throws IOException
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

    final double diffMargin = 0.25;
    int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Ignore);

    String name1 = "Mom.Risky/Mom.Moderate-30/70";// "Mom.Risky/Mom.Cautious-20/80";//"MultiMom-Risky";
    String name2 = "Bonds/Mom.Cautious-30/70";

    Sequence player1 = store.get(name1);
    Sequence player2 = store.get(name2);

    ComparisonStats comparison = ComparisonStats.calc(player1, player2, diffMargin);
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
      stats[i] = DurationalStats.calc(all[i], duration);
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
        DurationalStats stats = DurationalStats.calc(mixed, duration);
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

  public static void genSMASweepChart(File dir) throws IOException
  {
    final int[] months = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
    final int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Ignore);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("stock");
    for (int i = 0; i < months.length; ++i) {
      nameList.add("sma-" + months[i]);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "sma-sweep.html"), GRAPH_WIDTH, true, store.getCumulativeStats(names));
    // Chart.printDecadeTable(store.get("sma-12"), store.get("stock"));

    // Create scatterplot of returns vs. volatility.
    List<DurationalStats> dstats = store.getDurationalStats(names);
    Sequence scatter = new Sequence("SMA Results");
    for (int i = 0; i < dstats.size(); ++i) {
      DurationalStats stats = dstats.get(i);
      String label = "Stock";
      if (i < months.length) {
        label = "" + months[i];
      }
      scatter.addData(new FeatureVec(label, 2, stats.mean, stats.sdev));
    }

    names = new String[] { "stock", "bonds", "60/40", "sma-1", "sma-3", "sma-12", "multisma-safe", "multisma-cautious",
        "multisma-moderate", "multisma-risky" };
    Chart.saveLineChart(new File(dir, "sma-cumulative.html"), "Cumulative Market Returns: sma Strategy", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, store.getReturns(names));

    Chart.saveBoxPlots(new File(dir, "sma-box-plots.html"),
        String.format("SMA Returns (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    Chart.saveStatsTable(new File(dir, "sma-chart.html"), GRAPH_WIDTH, true, cstats);
  }

  public static List<String> collectMomentumNames(boolean bIncludeMapBased, boolean bIncludeMixed)
  {
    List<String> names = new ArrayList<String>();

    // Add all-stock and all-bonds.
    names.add("stock");
    names.add("bonds");

    // Add single-scale momentum strategies.
    for (int i = 1; i <= 12; ++i) {
      String name = "momentum-" + i;
      if (store.hasName(name)) {
        names.add(name);
      }
    }

    // Add multi-scale momentum strategies.
    for (Strategy.Disposition disposition : dispositions) {
      names.add("multimom-" + disposition);
    }

    if (bIncludeMapBased) {
      // Add map-based momentum strategies.
      for (int assetMap = 255, i = 0; i < 8; ++i) {
        names.add("multimom-" + assetMap);
        assetMap &= ~(1 << i);
      }
    }

    if (bIncludeMixed) {
      // Add multi-scale and mixed momentum strategies.
      for (int j = 0; j < dispositions.length; ++j) {
        for (int k = j + 1; k < dispositions.length; ++k) {
          for (int i = 10; i < 100; i += 10) {
            String name = String.format("Mom.%s/Mom.%s-%d/%d", dispositions[j], dispositions[k], i, 100 - i);
            names.add(name);
          }
        }
      }

      // Add mom-1 + multimom strategies.
      for (int j = 0; j < dispositions.length; ++j) {
        for (int i = 10; i < 100; i += 10) {
          String name = String.format("Momentum-1/Mom.%s-%d/%d", dispositions[j], i, 100 - i);
          names.add(name);
        }
      }

      // Add strategies with fixed stock / bond positions.
      for (int i = 0; i <= 90; i += 10) {
        for (int j = 0; j <= 90 && i + j < 100; j += 10) {
          if (i == 0 && j == 0) {
            continue;
          }
          int k = 100 - (i + j);
          for (Strategy.Disposition disposition : dispositions) {
            String name;
            if (i == 0) {
              name = String.format("Bonds/Mom.%s-%d/%d", disposition, j, k);
            } else if (j == 0) {
              name = String.format("Stock/Mom.%s-%d/%d", disposition, i, k);
            } else {
              name = String.format("Stock/Bonds/Mom.%s-%d/%d/%d", disposition, i, j, k);
            }
            names.add(name);
          }
        }
      }
    }

    return names;
  }

  public static List<String> collectSMANames(boolean bIncludeMapBased, boolean bIncludeMixed)
  {
    List<String> names = new ArrayList<String>();

    // Add all-stock and all-bonds.
    names.add("stock");
    names.add("bonds");

    // Add single-scale sma strategies.
    for (int i = 1; i <= 12; ++i) {
      String name = "sma-" + i;
      if (store.hasName(name)) {
        names.add(name);
      }
    }

    // Add multi-scale sma strategies.
    for (Strategy.Disposition disposition : dispositions) {
      names.add("multisma-" + disposition);
    }

    if (bIncludeMapBased) {
      // Add map-based sma strategies.
      for (int assetMap = 255, i = 0; i < 8; ++i) {
        names.add("multisma-" + assetMap);
        assetMap &= ~(1 << i);
      }
    }

    if (bIncludeMixed) {
      // Add multi-scale and mixed sma strategies.
      for (int j = 0; j < dispositions.length; ++j) {
        for (int k = j + 1; k < dispositions.length; ++k) {
          for (int i = 10; i < 100; i += 10) {
            String name = String.format("SMA.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], i, 100 - i);
            names.add(name);
          }
        }
      }

      // Add strategies with fixed stock / bond positions.
      for (int i = 0; i <= 90; i += 10) {
        for (int j = 0; j <= 90 && i + j < 100; j += 10) {
          if (i == 0 && j == 0) {
            continue;
          }
          int k = 100 - (i + j);
          for (Strategy.Disposition disposition : dispositions) {
            String name;
            if (i == 0) {
              name = String.format("Bonds/SMA.%s-%d/%d", disposition, j, k);
            } else if (j == 0) {
              name = String.format("Stock/SMA.%s-%d/%d", disposition, i, k);
            } else {
              name = String.format("Stock/Bonds/SMA.%s-%d/%d/%d", disposition, i, j, k);
            }
            names.add(name);
          }
        }
      }
    }

    return names;
  }

  public static List<String> genDominationChart(File dir, boolean bIncludeMapBased, boolean bIncludeMixed)
      throws IOException
  {
    long startMS = Library.getTime();

    List<String> candidates = collectMomentumNames(bIncludeMapBased, bIncludeMixed);
    candidates.addAll(collectSMANames(bIncludeMapBased, bIncludeMixed));

    // Mixed multiscale mom and sma strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      for (int k = 0; k < dispositions.length; ++k) {
        for (int i = 0; i < percentRisky.length; ++i) {
          String name = String.format("Mom.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
              100 - percentRisky[i]);
          candidates.add(name);
        }
      }
    }

    // Filter candidates to find "dominating" strategies.
    store.recalcDurationalStats(10 * 12, FinLib.Inflation.Ignore);
    FinLib.filterStrategies(candidates, store);

    List<CumulativeStats> winners = new ArrayList<CumulativeStats>();
    for (String name : candidates) {
      winners.add(store.getCumulativeStats(name));
    }
    Collections.sort(winners, Collections.reverseOrder());
    System.out.printf("Winners: %d\n", winners.size());
    Chart.saveStatsTable(new File(dir, "domination-chart.html"), GRAPH_WIDTH, true, winners);

    System.out.printf("Done (%s).\n", Library.formatDuration(Library.getTime() - startMS));

    List<String> names = new ArrayList<String>();
    for (CumulativeStats cstats : winners) {
      names.add(cstats.name());
    }
    return names;
  }

  public static void genMomentumSweepChart(File dir) throws IOException
  {
    final int[] momentumMonths = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
    final int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Ignore);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("Stock");
    for (int i = 0; i < momentumMonths.length; ++i) {
      nameList.add("momentum-" + momentumMonths[i]);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "momentum-sweep.html"), GRAPH_WIDTH, true, store.getCumulativeStats(names));
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

    names = new String[] { "stock", "bonds", "60/40", "momentum-1", "momentum-3", "momentum-12", "multimom-safe",
        "multimom-cautious", "multimom-moderate", "multimom-risky" };
    Chart.saveLineChart(new File(dir, "momentum-cumulative.html"), "Cumulative Market Returns: Momentum Strategy",
        GRAPH_WIDTH, GRAPH_HEIGHT, true, store.getReturns(names));

    Chart.saveBoxPlots(new File(dir, "momentum-box-plots.html"),
        String.format("Momentum Returns (%s)", Library.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    Chart.saveStatsTable(new File(dir, "momentum-chart.html"), GRAPH_WIDTH, true, cstats);
  }

  public static void genDrawdownChart(File dir) throws IOException
  {
    String[] names = new String[] { "Stock", "80/20", "60/40", "MultiMom-Risky", "Mom.Risky/Mom.Cautious-20/80" };

    Sequence[] drawdowns = new Sequence[names.length];
    for (int i = 0; i < names.length; ++i) {
      drawdowns[i] = FinLib.calcDrawdown(store.get(names[i]));
    }
    Chart.saveLineChart(new File(dir, "drawdown.html"), "Drawdown", GRAPH_WIDTH, GRAPH_HEIGHT, false, drawdowns);
  }

  public static void genBeatInflationChart(File dir) throws IOException
  {
    String[] names = new String[] { "stock", "bonds", "60/40", "momentum-1", "multimom-risky",
        "Mom.Risky/Mom.Moderate-30/70", "Mom.Risky/Mom.Cautious-20/80", "Bonds/Mom.Safe-10/90" };

    final double diffMargin = 0.01;
    final double targetReturn = 3.0; // Annual return over inflation

    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 0; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(store.getReal(names[i]), targetReturn, diffMargin));
    }
    Chart.saveBeatInflationTable(new File(dir, "strategy-beat-inflation.html"), GRAPH_WIDTH, comparisons);
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

  public static void genSavingsTargetChart(File dir)
  {
    int retireAge = 65;
    int ssAge = 70;
    int deathAge = 95;
    double expectedSocSecFraction = 0.6; // assume we'll only get a fraction of current SS estimate
    int nSocSecEarners = 0;
    double expectedMonthlySS = FinLib.SOC_SEC_AT70 * expectedSocSecFraction * nSocSecEarners;
    int nYears = deathAge - retireAge;
    double expenseRatio = 0.1;
    double likelihood = 1.0;
    double taxRate = 0.0;
    double desiredMonthlyCash = 6000.00;
    double desiredRunwayYears = 22.0;
    double salary = FinLib.calcAnnualSalary(desiredMonthlyCash, taxRate);
    System.out.printf("Salary: $%s\n", FinLib.currencyFormatter.format(salary));

    // String[] names = new String[] { "stock", "bonds", "60/40", "80/20", "momentum-1", "momentum-3", "momentum-12",
    // "multimom-risky", "multimom-moderate", "multimom-cautious", "multimom-safe", "Mom.Risky/Mom.Moderate-30/70",
    // "Mom.Risky/Mom.Cautious-20/80", "Mom.Risky/Mom.Safe-10/90", "Bonds/Mom.Safe-10/90",
    // "Mom.Moderate/SMA.Safe-60/40", "Mom.Moderate/SMA.Risky-70/30", "Mom.Risky/SMA.Risky-70/30",
    // "Mom.Cautious/SMA.Safe-70/30", "MultiSMA-128" };
    String[] names = new String[] { "Mom.Moderate/SMA.Safe-60/40" };
    // String[] names = store.getNames().toArray(new String[0]);
    RetirementStats[] results = new RetirementStats[names.length];

    for (int i = 0; i < names.length; ++i) {
      Sequence cumulativeReturns = store.get(names[i]);
      Sequence cpi = store.getMisc("cpi").lockToMatch(cumulativeReturns);
      // List<Integer> failures =
      results[i] = FinLib.calcSavingsTarget(cumulativeReturns, cpi, salary, likelihood, nYears, expenseRatio,
          retireAge, ssAge, expectedMonthlySS, desiredRunwayYears);
      cpi.unlock();
      // System.out.printf("%40s: $%s\n", names[i], FinLib.dollarFormatter.format(results[i].principal));
    }

    System.out.println();
    Arrays.sort(results);
    for (int i = 0; i < names.length; ++i) {
      System.out.printf("%40s: $%s  ($%s, $%.2fm)\n", results[i].name,
          FinLib.dollarFormatter.format(results[i].principal), FinLib.dollarFormatter.format(results[i].min),
          results[i].percentile10 / 1e6);

      // System.out.printf("<tr><td>%s</td><td>%s</td><td>%.1f</td><td>%.1f</td><td>%.1f</td></tr>\n",
      // FinLib.getBoldedName(results[i].name), FinLib.dollarFormatter.format(results[i].principal),
      // results[i].percentile10 / 1e6, results[i].median / 1e6, results[i].percentile90 / 1e6);
    }
  }

  public static void genSavingsTargetChart(List<String> names, File dir)
  {
    int nYears = 30;
    double expenseRatio = 0.1;
    double taxRate = 0.0;
    double desiredMonthlyCash = 8000.00;
    double desiredRunwayYears = -2.0;
    double salary = FinLib.calcAnnualSalary(desiredMonthlyCash, taxRate);

    RetirementStats[] results = new RetirementStats[names.size()];

    for (int i = 0; i < names.size(); ++i) {
      Sequence cumulativeReturns = store.get(names.get(i));
      results[i] = FinLib.calcSavingsTarget(cumulativeReturns, salary, nYears, expenseRatio, desiredRunwayYears);
      System.out.printf("%40s: $%s\n", names.get(i), FinLib.dollarFormatter.format(results[i].principal));
    }

    System.out.println();
    Arrays.sort(results);
    for (int i = 0; i < names.size(); ++i) {
      System.out.printf("%40s: $%s  ($%s, $%.2fm)\n", results[i].name,
          FinLib.dollarFormatter.format(results[i].principal), FinLib.dollarFormatter.format(results[i].min),
          results[i].percentile10 / 1e6);

      // System.out.printf("<tr><td>%s</td><td>%s</td><td>%.1f</td><td>%.1f</td><td>%.1f</td></tr>\n",
      // FinLib.getBoldedName(results[i].name), FinLib.dollarFormatter.format(results[i].principal),
      // results[i].percentile10 / 1e6, results[i].median / 1e6, results[i].percentile90 / 1e6);
    }
    System.out.println();
  }

  public static Sequence synthesizeData(File dataDir, File dir) throws IOException
  {
    Sequence shiller = DataIO.loadShillerData(new File(dataDir, "shiller.csv"));
    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData = FinLib.pad(tbillData, shiller, 0.0);

    Sequence stock = FinLib.calcSnpReturns(Shiller.getStockData(shiller), 0, -1, FinLib.DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, Shiller.getBondData(shiller), 0, -1);
    Sequence tbills = Bond.calcReturnsRebuy(BondFactory.bill3Month, tbillData, 0, -1);
    Sequence cpi = Shiller.getInflationData(shiller);

    Sequence nikkeiDaily = DataIO.loadDateValueCSV(new File(dataDir, "nikkei225-daily.csv"));
    Sequence nikkei = FinLib.daily2monthly(nikkeiDaily);

    Sequence reitsDaily = DataIO.loadYahooData(new File(dataDir, "VGSIX.csv"));
    Sequence reits = FinLib.daily2monthly(reitsDaily);

    Sequence istockDaily = DataIO.loadYahooData(new File(dataDir, "VGTSX.csv"));
    Sequence istock = FinLib.daily2monthly(istockDaily);

    // Normalize CPI to start at 1.0.
    // cpi = cpi.div(cpi.get(0, 0));
    double baseCPI = cpi.getLast(0);
    System.out.printf("Base CPI: %.2f\n", baseCPI);

    assert stock.matches(bonds);
    assert stock.matches(tbills);
    assert stock.matches(cpi);

    final int N = stock.length();
    final int duration = 1 * 12;
    final int minSampleDur = 3;
    final int maxSampleDur = 10;
    final float chanceAlignedAssets = 0.5f;
    final float chanceSNP = 0.5f;
    final Random rng = new Random();// 1234L);

    Sequence dStock = stock.derivativeMul();
    Sequence dBonds = bonds.derivativeMul();
    Sequence dBills = tbills.derivativeMul();
    Sequence dNikkei = nikkei.derivativeMul();
    Sequence dReits = reits.derivativeMul();
    Sequence dIStock = istock.derivativeMul();
    Sequence dCPI = cpi.derivativeMul();
    Sequence[] derivs = new Sequence[] { dStock, dBonds, dBills, dCPI };
    Sequence[] riskyAlts = new Sequence[] { dNikkei, dReits, dIStock };
    int[] counts = new int[riskyAlts.length + 1];

    List<String> source = new ArrayList<String>();

    // Initialize synthetic data with real data.
    Sequence synth = new Sequence("Synthetic");
    for (int i = 0; i < N; ++i) {
      FeatureVec v = new FeatureVec(4, stock.get(i, 0), bonds.get(i, 0), tbills.get(i, 0), cpi.get(i, 0));
      v._mul(baseCPI / cpi.get(i, 0));
      synth.addData(v, stock.getTimeMS(i));
      source.add(stock.getName());
    }

    // Extend synthetic data by sampling from historical data.
    Calendar cal = Library.now();
    double[] dValues = new double[derivs.length];
    int[] offsets = new int[4];
    while (synth.length() < duration) {
      int sampleDur = Math.min(rng.nextInt(maxSampleDur - minSampleDur) + minSampleDur, duration - synth.length());

      // Assume aligned offsets to start.
      Arrays.fill(offsets, rng.nextInt(N - sampleDur));
      Sequence dRisky = dStock;

      // Chance of different offset for each asset class.
      if (rng.nextFloat() > chanceAlignedAssets) {
        for (int i = 1; i < offsets.length; ++i) {
          offsets[i] = rng.nextInt(N - sampleDur);
        }
      }

      // Chance to replace S&P with some other "risky" asset.
      if (rng.nextFloat() > chanceSNP) {
        int index = rng.nextInt(riskyAlts.length);
        dRisky = riskyAlts[index];
        offsets[0] = rng.nextInt(dRisky.length() - sampleDur);
        counts[index + 1] += sampleDur;
      } else {
        counts[0] += sampleDur;
      }

      for (int i = 0; i < sampleDur; ++i) {
        dValues[0] = dRisky.get(offsets[0] + i, 0);
        for (int d = 1; d < derivs.length; ++d) {
          dValues[d] = derivs[d].get(i + offsets[d], 0);
        }
        double mCPI = dCPI.get(i + offsets[3], 0);

        FeatureVec m = new FeatureVec(dValues);
        FeatureVec v = synth.getLast().mul(m);
        v._div(mCPI);
        cal.setTimeInMillis(v.getTime());
        cal.add(Calendar.MONTH, 1);
        synth.addData(v, cal.getTimeInMillis());
        source.add(dRisky.getName());
      }
    }

    System.out.printf("%d, %d, %d, %d (%d)\n", counts[0], counts[1], counts[2], counts[3], Library.sum(counts));
    System.out.printf("Inflation: %.3f\n",
        FinLib.getAnnualReturn(FinLib.getReturn(cpi, 0, cpi.length() - 1), cpi.length() - 1));
    System.out.printf("CAGR: %.3f\n",
        FinLib.getAnnualReturn(FinLib.getReturn(synth, 0, synth.length() - 1), synth.length() - 1));

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, "synth.csv")))) {
      writer.write("Date,Stock,Bonds,TBills,Source\n");
      for (int i = 0; i < synth.length(); ++i) {
        FeatureVec v = synth.get(i);
        writer.write(String.format("%s,%.2f,%.2f,%.2f,%s\n", Library.formatYMD(v.getTime()), v.get(0), v.get(1),
            v.get(2), Library.prefix(source.get(i), "-")));
      }
    }

    System.out.printf("Synthetic Data (%d): [%s] -> [%s]\n", synth.length(), Library.formatMonth(synth.getStartMS()),
        Library.formatMonth(synth.getEndMS()));

    Chart.saveLineChart(new File(dir, "synth.html"), "Synthetic Data", 1600, 800, true, synth.extractDims(0),
        synth.extractDims(1), synth.extractDims(2));

    return synth;
  }

  public static void runSyntheticTest(File dataDir, File dir) throws IOException
  {
    Sequence synth = DataIO.loadCSV(new File(dataDir, "synth-500.csv"), new int[] { 1, 2, 3 });
    System.out.printf("Synthetic Data (%d): [%s] -> [%s]\n", synth.length(), Library.formatMonth(synth.getStartMS()),
        Library.formatMonth(synth.getEndMS()));

    Sequence stockAll = synth.extractDims(0);
    stockAll.setName("Stock");
    stockAll._div(stockAll.get(0, 0));

    Sequence bondsAll = synth.extractDims(1);
    bondsAll.setName("Bonds");
    bondsAll._div(bondsAll.get(0, 0));

    int iStartSimulation = 12;
    Slippage slippage = Slippage.None;

    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    store.add(stock);
    store.add(bonds);

    Sequence risky = stockAll;
    Sequence safe = bondsAll;
    Sequence prices = stockAll;

    // Multi-scale Momentum and SMA methods.
    for (Disposition disposition : Disposition.values()) {
      Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, slippage, risky, safe, disposition);
      Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, slippage, prices, risky, safe, disposition);
      store.add(mom);
      store.add(sma);
    }

    // Mixed multiscale mom and sma strategies.
    for (int j = 0; j < dispositions.length; ++j) {
      String name1 = "MultiMom-" + dispositions[j];
      for (int k = 0; k < dispositions.length; ++k) {
        String name2 = "MultiSMA-" + dispositions[k];
        for (int i = 0; i < percentRisky.length; ++i) {
          Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
              percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
          store.add(seq, String.format("Mom.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
              100 - percentRisky[i]));
        }
      }
    }

    List<String> winners = genDominationChart(dir, false, false);

    List<CumulativeStats> cstats = store.getCumulativeStats(winners.toArray(new String[winners.size()]));
    Collections.sort(cstats);
    for (CumulativeStats cs : cstats) {
      System.out.println(cs);
    }
    System.out.println();

    int duration = 30 * 12;
    // Chart.saveBoxPlots(new File(dir, "synth-boxplots.html"), "Synthetic Box Plots", GRAPH_WIDTH, GRAPH_HEIGHT, 0.5,
    // cstats);
    Chart.saveLineChart(new File(dir, "synth-cumulative.html"), "Synthetic Data", GRAPH_WIDTH, GRAPH_HEIGHT, true,
        stock, bonds, store.get("Mom.Risky/SMA.Safe-50/50"));

    genSavingsTargetChart(winners, dir);

    List<ReturnStats> results = new ArrayList<>();
    for (String name : winners) {
      Sequence returns = store.get(name);
      double[] endBalances = new double[returns.length() - duration];
      int iStart = 0;
      for (; iStart + duration < returns.length(); ++iStart) {
        endBalances[iStart] = FinLib.calcEndSavings(returns, 220000.0, 5500.0, 4400.0, 0.1, iStart, duration);
      }
      assert iStart == endBalances.length;
      results.add(ReturnStats.calc(name, endBalances));
    }

    results.sort(new Comparator<ReturnStats>()
    {
      @Override
      public int compare(ReturnStats a, ReturnStats b)
      {
        if (a.min < b.min)
          return -1;
        if (a.min > b.min)
          return 1;

        if (a.percentile10 < b.percentile10)
          return -1;
        if (a.percentile10 > b.percentile10)
          return 1;

        return 0;
      }
    });

    for (ReturnStats stats : results) {
      System.out.printf("%40s: [min=$%s, 10%%=$%s, 25%%=$%s, 50%%=$%s, 75%%=$%s, 90%%=$%s]\n", stats.name,
          FinLib.formatDollars(stats.min), FinLib.formatDollars(stats.percentile10),
          FinLib.formatDollars(stats.percentile25), FinLib.formatDollars(stats.median),
          FinLib.formatDollars(stats.percentile75), FinLib.formatDollars(stats.percentile90));
    }
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance");
    File dir = new File("g:/web/");
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    // synthesizeData(dataDir, dir);
    // runSyntheticTest(dataDir, dir);

    setupVanguardData(dataDir, dir);
    // setupShillerData(dataDir, dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);

    // Chart.printDecadeTable(store.get("MultiMom-risky"), store.get("stock"));
    // Chart.printDecadeTable(store.get("Mom.Risky/Mom.Cautious-20/80"), store.get("stock"));
    // Chart.printDecadeTable(store.get("Mom.Risky/Mom.Cautious-20/80"), store.get("80/20"));

    // genInterestRateGraph(shiller, tbills, new File(dir, "interest-rates.html"));
    // compareRebalancingMethods(shiller, dir);
    // genReturnViz(dir);
    // genReturnChart(dir);
    // genDominationChart(dir);
    // genSMASweepChart(dir);
    // genMomentumSweepChart(dir);
    // genStockBondMixSweepChart(shiller, dir);
    // genDuelViz(shiller, tbills, dir);
    // genEfficientFrontier(shiller, dir);
    // genCorrelationGraph(shiller, dir);
    // genEndBalanceCharts(shiller, dir);
    // genBeatInflationChart(dir);
    // genDrawdownChart(dir);
    // genSavingsTargetChart(dir);
  }
}
