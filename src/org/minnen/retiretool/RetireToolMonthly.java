package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.minnen.retiretool.predictor.monthly.AssetPredictor;
import org.minnen.retiretool.predictor.monthly.ConstantPredictor;
import org.minnen.retiretool.predictor.monthly.MixedPredictor;
import org.minnen.retiretool.predictor.monthly.MomentumPredictor;
import org.minnen.retiretool.predictor.monthly.Multi3Predictor;
import org.minnen.retiretool.predictor.monthly.NewHighPredictor;
import org.minnen.retiretool.predictor.monthly.SMAPredictor;
import org.minnen.retiretool.predictor.monthly.WMAPredictor;
import org.minnen.retiretool.predictor.monthly.WTAPredictor;
import org.minnen.retiretool.predictor.monthly.Multi3Predictor.Disposition;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.yahoo.YahooIO;
import org.minnen.retiretool.data.SequenceStoreV1;
import org.minnen.retiretool.data.Shiller;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.stats.RetirementStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Histogram;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class RetireToolMonthly
{
  public static final String          GRAPH_WIDTH         = "710px";
  public static final String          GRAPH_HEIGHT        = "450px";

  public static final boolean         includeRiskAdjusted = false;
  public static final boolean         includeQuartiles    = false;

  public final static SequenceStoreV1 store               = new SequenceStoreV1();

  public static final Disposition[]   dispositions        = Disposition.values();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                   iPriceSMA           = 0;
  public static int                   nMinTradeGap        = 0;
  public static double                smaMargin           = 0.0;

  public static void setupShillerData(File dataDir, File dir, boolean buildComplexStrategies) throws IOException
  {
    Sequence shiller = Shiller.loadAll(new File(dataDir, "shiller.csv"));
    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData = FinLib.pad(tbillData, shiller, 0.0);

    // Sequence nikkeiDaily = DataIO.loadDateValueCSV(new File(dataDir, "nikkei225-daily.csv"));
    // Sequence nikkei = FinLib.daily2monthly(nikkeiDaily);

    long commonStart = TimeLib.calcCommonStart(shiller, tbillData);
    long commonEnd = TimeLib.calcCommonEnd(shiller, tbillData);
    // System.out.printf("Shiller: [%s] -> [%s]\n", TimeLib.formatDate(shiller.getStartMS()),
    // TimeLib.formatDate(shiller.getEndMS()));
    // System.out.printf("Stock: [%s] -> [%s]\n", TimeLib.formatMonth(nikkei.getStartMS()),
    // TimeLib.formatMonth(nikkei.getEndMS()));
    // System.out.printf("T-Bills: [%s] -> [%s]\n", TimeLib.formatDate(tbills.getStartMS()),
    // TimeLib.formatDate(tbills.getEndMS()));
    // System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    // long commonStart = TimeLib.getTime(1, 1, 1872);
    // long commonEnd = TimeLib.getTime(31, 12, 2014);
    shiller = shiller.subseq(commonStart, commonEnd);
    tbillData = tbillData.subseq(commonStart, commonEnd);
    // nikkei = nikkei.subseq(commonStart, commonEnd);

    buildCumulativeReturnsStore(shiller, tbillData, null, buildComplexStrategies);
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
    Sequence stockData = ShillerOld.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller, iStartData, iEndData);
    assert stockData.matches(bondData);
    System.out.printf("Build Store: [%s] -> [%s]\n", TimeLib.formatMonth(stockData.getStartMS()),
        TimeLib.formatMonth(stockData.getEndMS()));
    store.addMisc(stockData, "StockData");
    store.addMisc(bondData, "BondData");

    // Add CPI data.
    Sequence cpi = ShillerOld.getData(ShillerOld.CPI, "cpi", shiller, iStartData, iEndData);
    store.addMisc(cpi);
    store.alias("inflation", "cpi");

    // Calculate cumulative returns for full stock, bond, and t-bill data.
    Sequence stockNoDiv = FinLib.calcSnpReturns(stockData, 0, -1, DividendMethod.IGNORE_DIVIDENDS);
    Sequence stockAll = FinLib.calcSnpReturns(stockData, 0, -1, DividendMethod.QUARTERLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    assert stockAll.matches(bondsAll);
    store.addMisc(stockAll, "Stock-All");
    store.addMisc(bondsAll, "Bonds-All");

    assert cpi.matches(stockAll);
    // for (int i = 0; i < cpi.length(); ++i) {
    // System.out.printf("%s,%.4f,%.4f,%.4f,%.4f\n", TimeLib.formatYM(cpi.getTimeMS(i)), stockAll.get(i, 0),
    // stockNoDiv.get(i, 0), bondsAll.get(i, 0), cpi.get(i, 0));
    // }
    double nMonths = TimeLib.monthsBetween(stockAll.getStartMS(), stockAll.getEndMS());
    System.out.printf("%.3f, %.3f, %.3f\n", FinLib.getAnnualReturn(stockAll.getLast(0), nMonths),
        FinLib.getAnnualReturn(stockNoDiv.getLast(0), nMonths), FinLib.getAnnualReturn(bondsAll.getLast(0), nMonths));
    // System.exit(0);

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

    // Sequence stockMonthlyDiv = FinLib.calcSnpReturns(stockData, iStartSimulation, -1, DividendMethod.MONTHLY);
    // store.add(stockMonthlyDiv, "Stock [MonthlyDiv]");
    // Sequence stockNoDiv = FinLib.calcSnpReturns(stockData, iStartSimulation, -1, DividendMethod.NO_REINVEST);
    // store.add(stockNoDiv, "Stock [NoDiv]");
    // Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, iStartSimulation, -1);
    // store.add(bondsHold, "Bonds [Hold]");

    // Extract requested subsequences from cumulative returns for stock, bonds, and t-bills.
    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    stock._div(stock.getFirst(0));
    bonds._div(bonds.getFirst(0));
    assert stock.matches(bonds);
    store.add(stock, "Stock");
    store.add(bonds, "Bonds");

    // System.out.printf("%d: [%s] -> [%s]\n", stock.length(), TimeLib.formatDate(stock.getStartMS()),
    // TimeLib.formatDate(stock.getEndMS()));
    // int nMonths = TimeLib.monthsBetween(stock.getStartMS(), stock.getEndMS());
    // System.out.printf(" #months: %d\n", nMonths);

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

    // Setup risky and safe assets for use with dynamic asset allocation strategies.
    if (buildComplexStrategies) {
      Sequence risky = stockAll;
      Sequence safe = store.getMisc("cash"); // Bonds-All
      Sequence prices = store.getMisc("StockData");
      // Strategy.calcSmaStats(prices, risky, safe, store);
      addStrategiesToStore(risky, safe, prices, iStartSimulation);
    }

    System.out.printf("Finished Building Store (%d, %s).\n", store.size(),
        TimeLib.formatDuration(TimeLib.getTime() - startMS));
  }

  public static void addStrategiesToStore(Sequence risky, Sequence safe, Sequence prices, int iStartSimulation)
  {
    final int rebalanceMonths = 12;
    final double rebalanceBand = 0.0;
    final Slippage slippage = Slippage.None; // new Slippage(0.01, 0.1);

    // Stock / Bond mixes.
    // for (int i = 10; i <= 90; i += 10) {
    // RebalanceInfo rebalance = new RebalanceInfo(new int[] { i, 100 - i }, rebalanceMonths, rebalanceBand);
    // Sequence mix = Strategy.calcReturns(new Sequence[] { store.get("Stock"), store.get("bonds") }, rebalance);
    // mix.setName(String.format("Stock/Bonds-%d/%d", i, 100 - i));
    // store.alias(String.format("%d/%d", i, 100 - i), mix.getName());
    // store.add(mix);
    // }

    // Build Momentum and SMA predictors.
    AssetPredictor[] momPredictors = new AssetPredictor[12];
    AssetPredictor[] smaPredictors = new AssetPredictor[12];
    for (int i = 0; i < momPredictors.length; ++i) {
      momPredictors[i] = new MomentumPredictor(i + 1, store);
      smaPredictors[i] = new SMAPredictor(i + 1, smaMargin, prices.getName(), iPriceSMA, store);
    }

    // Momentum sweep.
    // for (int i = 0; i < momPredictors.length; ++i) {
    // Sequence mom = Strategy.calcReturns(momPredictors[i], iStartSimulation, nMinTradeGap, risky, safe);
    // store.add(mom);
    // }

    // store.add(mom);
    // // if (i == 0) {
    // // System.out.printf("Correct: %.2f%% / %.2f%% (%d / %d)\n", winStats.percentCorrect(0),
    // // winStats.percentCorrect(1), winStats.nCorrect[0], winStats.nCorrect[1]);
    // // }
    // // System.out.printf("Momentum-%d: %.2f%% Correct (%d vs. %d / %d)\n", momentumMonths[i],
    // // winStats.percentCorrect(),
    // // winStats.nPredCorrect, winStats.nPredWrong, winStats.total());
    // // System.out.printf(" %.2f%% / %.2f%% (%d / %d)\n", winStats.percentSelected(0), winStats.percentSelected(1),
    // // winStats.nSelected[0], winStats.nSelected[1]);
    // }

    // TODO for comparison to broker (daily) results.
    Strategy.calcReturns(smaPredictors[1], iStartSimulation, nMinTradeGap, risky, safe);

    // SMA sweep.
    // for (int i = 0; i < smaPredictors.length; ++i) {
    // Sequence sma = Strategy.calcReturns(smaPredictors[i], iStartSimulation, nMinTradeGap, risky, safe);
    // store.add(sma);
    // }

    // SMA margin sweep.
    // for (int i = 1; i <= 8; ++i) {
    // AssetPredictor predictor = new SMAPredictor(1, i * 0.25, prices.getName(), iPriceSMA, store);
    // Sequence sma = Strategy.calcReturns(predictor, iStartSimulation, nMinTradeGap, risky, safe);
    // store.add(sma);
    // }

    // NewHigh sweep.
    // for (int i = 1; i <= 12; ++i) {
    // AssetPredictor predictor = new NewHighPredictor(i, store);
    // Sequence seq = Strategy.calcReturns(predictor, iStartSimulation, slippage, null, risky, safe);
    // store.add(seq);
    // }

    // Multi-scale Momentum and SMA methods.
    // AssetPredictor[] multiMomPredictors = new AssetPredictor[4];
    // AssetPredictor[] multiSMAPredictors = new AssetPredictor[4];
    // for (Disposition disposition : Disposition.values()) {
    // AssetPredictor momPredictor = new Multi3Predictor("Mom[1,3,12]." + disposition, new AssetPredictor[] {
    // momPredictors[0], momPredictors[2], momPredictors[11] }, disposition, store);
    // multiMomPredictors[disposition.ordinal()] = momPredictor;
    // Sequence mom = Strategy.calcReturns(momPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    // store.add(mom);
    //
    // AssetPredictor smaPredictor = new Multi3Predictor("SMA[1,5,10]." + disposition, new AssetPredictor[] {
    // new SMAPredictor(1, smaMargin, prices.getName(), iPriceSMA, store),
    // new SMAPredictor(5, smaMargin, prices.getName(), iPriceSMA, store),
    // new SMAPredictor(10, smaMargin, prices.getName(), iPriceSMA, store) }, disposition, store);
    // multiSMAPredictors[disposition.ordinal()] = smaPredictor;
    // Sequence sma = Strategy.calcReturns(smaPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    // store.add(sma);
    // }

    // Full multi-momentum/sma sweep.
    // for (int assetMap = 255, i = 0; i < 8; ++i) {
    // Sequence mom = Strategy.calcMultiMomentumReturns(iStartSimulation, slippage, risky, safe, assetMap);
    // Sequence sma = Strategy.calcMultiSmaReturns(iStartSimulation, slippage, prices, risky, safe, assetMap);
    // store.add(mom);
    // store.add(sma);
    // assetMap &= ~(1 << i);
    // }

    // System.out.printf("Building multiscale variations... ");
    // long startMS = System.currentTimeMillis();
    // buildMultiscaleVariations(iStartSimulation, slippage, risky, safe, prices);
    // System.out.printf("done (%d, %s).\n", store.size(), TimeLib.formatDuration(System.currentTimeMillis() -
    // startMS));

    // System.out.printf("Building all mixes (%d)... ", store.size());
    // startMS = System.currentTimeMillis();
    // buildAllMixes(50, null, "NewHigh");
    // buildAllMixes("NewHigh[10]", null, "NewHigh");
    // buildAllMixes("NewHigh[6]", null, "NewHigh");
    // System.out.printf("done (%d, %s).\n", store.size(), TimeLib.formatDuration(System.currentTimeMillis() -
    // startMS));

    // int pctInc = 10;
    // buildMixes("mom[1,3,11].moderate", "mom[1,4].defensive", pctInc);
    // buildMixes("mom[1,3,11].moderate", "sma[1,3,11].aggressive", pctInc);
    // buildMixes("sma[1,4,10].aggressive", "sma[1,4,11].moderate", pctInc);
    // buildMixes("sma[1,3].defensive", "sma[1,4,11].moderate", pctInc);
    // buildMixes("sma[1,3,10].cautious", "sma[1,4,11].aggressive", pctInc);

    // buildMixes("SMA[1,2,9].Cautious", "SMA[1,3,10].Moderate", pctInc);
    // buildMixes("SMA[1,2,9].Moderate", "SMA[1,3,10].Moderate", pctInc);
    // buildMixes("SMA[1,2,9].Moderate", "SMA[1,3,10].Aggressive", pctInc);

    // pctInc = 10;
    // buildMixes(pctInc, "SMA[1,2,9].Cautious", "SMA[1,3,10].Moderate", "NewHigh[10]");
    // buildMixes(pctInc, "SMA[1,2,9].Moderate", "SMA[1,3,10].Moderate", "NewHigh[10]");
    // buildMixes(pctInc, "SMA[1,2,9].Moderate", "SMA[1,3,10].Aggressive", "NewHigh[10]");

    // WMA Momentum strategy.
    // AssetPredictor wmaMomPredictor = new WMAPredictor("MomentumWMA", momPredictors, store);
    // Sequence wmaMomentum = Strategy.calcReturns(wmaMomPredictor, iStartSimulation, slippage, null, risky, safe);
    // store.add(wmaMomentum);
    //
    // // WTA Momentum strategy.
    // AssetPredictor wtaMomPredictor = new WTAPredictor("MomentumWTA", momPredictors, store);
    // Sequence wtaMomentum = Strategy.calcReturns(wtaMomPredictor, iStartSimulation, slippage, null, risky, safe);
    // store.add(wtaMomentum);

    // Multi-Momentum WMA strategy.
    // AssetPredictor multiMomWMAPredictor = new WMAPredictor("MultiMomWMA", multiMomPredictors, 0.25, 0.1, store);
    // Sequence multiMomWMA = Strategy.calcReturns(multiMomWMAPredictor, iStartSimulation, slippage, null, risky, safe);
    // store.add(multiMomWMA);

    // Multi-Momentum WTA strategy.
    // AssetPredictor multiMomWTAPredictor = new WTAPredictor("MultiMomWTA", multiMomPredictors, 0.25, 0.1, store);
    // Sequence multiMomWTA = Strategy.calcReturns(multiMomWTAPredictor, iStartSimulation, slippage, null, risky, safe);
    // store.add(multiMomWTA);

    // Mixed multiscale momentum strategies.
    // for (int j = 0; j < dispositions.length; ++j) {
    // String name1 = "Mom[1,3,12]." + dispositions[j];
    // for (int k = j + 1; k < dispositions.length; ++k) {
    // String name2 = "Mom[1,3,12]." + dispositions[k];
    // for (int i = 0; i < percentRisky.length; ++i) {
    // Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
    // percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
    // store.add(seq, String.format("Mom.%s/Mom.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
    // 100 - percentRisky[i]));
    // }
    // }
    // }
    //
    // // Mixed multiscale sma strategies.
    // for (int j = 0; j < dispositions.length; ++j) {
    // String name1 = "SMA[1,5,10]." + dispositions[j];
    // for (int k = j + 1; k < dispositions.length; ++k) {
    // String name2 = "SMA[1,5,10]." + dispositions[k];
    // for (int i = 0; i < percentRisky.length; ++i) {
    // Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
    // percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
    // store.add(seq, String.format("SMA.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
    // 100 - percentRisky[i]));
    // }
    // }
    // }
    //
    // // Mixed Momentum-1 and multiscale strategies.
    // for (int j = 0; j < dispositions.length; ++j) {
    // String name2 = "Mom[1,3,12]." + dispositions[j];
    // for (int i = 0; i < percentRisky.length; ++i) {
    // Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get("Momentum-1"), store.get(name2) },
    // new double[] { percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
    // store.add(seq,
    // String.format("Momentum-1/Mom.%s-%d/%d", dispositions[j], percentRisky[i], 100 - percentRisky[i]));
    // }
    // }
    //
    // // Add strategies with fixed stock / bond positions.
    // for (int i = 0; i <= 90; i += 10) {
    // for (int j = 0; j <= 90 && i + j < 100; j += 10) {
    // int k = 100 - (i + j);
    // for (Disposition disposition : dispositions) {
    // // Mix in momentum.
    // Sequence seq = Strategy.calcMixedReturns(
    // new Sequence[] { store.get("stock"), store.get("bonds"), store.get("Mom[1,3,12]." + disposition) },
    // new double[] { i, j, k }, 12, 0.0);
    //
    // String name;
    // if (i == 0) {
    // name = String.format("Bonds/Mom.%s-%d/%d", disposition, j, k);
    // } else if (j == 0) {
    // name = String.format("Stock/Mom.%s-%d/%d", disposition, i, k);
    // } else {
    // name = String.format("Stock/Bonds/Mom.%s-%d/%d/%d", disposition, i, j, k);
    // }
    // store.add(seq, name);
    //
    // // Mix in SMA.
    // seq = Strategy.calcMixedReturns(
    // new Sequence[] { store.get("stock"), store.get("bonds"), store.get("SMA[1,5,10]." + disposition) },
    // new double[] { i, j, k }, 12, 0.0);
    //
    // if (i == 0) {
    // name = String.format("Bonds/SMA.%s-%d/%d", disposition, j, k);
    // } else if (j == 0) {
    // name = String.format("Stock/SMA.%s-%d/%d", disposition, i, k);
    // } else {
    // name = String.format("Stock/Bonds/SMA.%s-%d/%d/%d", disposition, i, j, k);
    // }
    // store.add(seq, name);
    // }
    // }
    // }
    //
    // // Mixed multiscale mom and sma strategies.
    // for (int j = 0; j < dispositions.length; ++j) {
    // String name1 = "Mom[1,3,12]." + dispositions[j];
    // for (int k = 0; k < dispositions.length; ++k) {
    // String name2 = "SMA[1,5,10]." + dispositions[k];
    // for (int i = 0; i < percentRisky.length; ++i) {
    // Sequence seq = Strategy.calcMixedReturns(new Sequence[] { store.get(name1), store.get(name2) }, new double[] {
    // percentRisky[i], 100 - percentRisky[i] }, 12, 0.0);
    // store.add(seq, String.format("Mom.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], percentRisky[i],
    // 100 - percentRisky[i]));
    // }
    // }
    // }

    // Strategy.calcMultiMomentumStats(risky, safe);

    // Perfect = impossible strategy that always picks the best asset.
    // Sequence perfect = Strategy.calcPerfectReturns(iStartSimulation, risky, safe);
    // store.add(perfect);
  }

  public static void setupVanguardData(File dataDir, File dir) throws IOException
  {
    iPriceSMA = FinLib.MonthlyClose; // should SMA use close or average?
    final int iStartSimulation = 12;

    Sequence stockDaily = YahooIO.loadData(new File(dataDir, "VTSMX.csv"));
    store.addMisc(stockDaily, "Stock-Daily");
    Sequence stock = FinLib.dailyToMonthly(stockDaily);
    Sequence stockNoDiv = FinLib.dailyToMonthly(stockDaily, 1, 0);
    System.out.printf("Stock: [%s] -> [%s]\n", TimeLib.formatMonth(stock.getStartMS()),
        TimeLib.formatMonth(stock.getEndMS()));

    Sequence bondsDaily = YahooIO.loadData(new File(dataDir, "VBMFX.csv"));
    store.addMisc(bondsDaily, "Bonds-Daily");
    Sequence bonds = FinLib.dailyToMonthly(bondsDaily);
    System.out.printf("Bond: [%s] -> [%s]\n", TimeLib.formatMonth(bonds.getStartMS()),
        TimeLib.formatMonth(bonds.getEndMS()));

    Sequence reitsDaily = YahooIO.loadData(new File(dataDir, "VGSIX.csv"));
    store.addMisc(reitsDaily, "REITs-Daily");
    Sequence reits = FinLib.dailyToMonthly(reitsDaily);
    System.out.printf("REIT: [%s] -> [%s]\n", TimeLib.formatMonth(reits.getStartMS()),
        TimeLib.formatMonth(reits.getEndMS()));

    Sequence istockDaily = YahooIO.loadData(new File(dataDir, "VGTSX.csv"));
    store.addMisc(istockDaily, "IntStock-Daily");
    Sequence istock = FinLib.dailyToMonthly(istockDaily);
    System.out.printf("Int Stock: [%s] -> [%s]\n", TimeLib.formatMonth(istock.getStartMS()),
        TimeLib.formatMonth(istock.getEndMS()));

    Sequence shiller = Shiller.loadAll(new File(dataDir, "shiller.csv"));

    long commonStart = TimeLib.calcCommonStart(stock, bonds, reits, istock, shiller);
    long commonEnd = TimeLib.calcCommonEnd(stock, bonds, reits, istock, shiller);
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatMonth(commonStart), TimeLib.formatMonth(commonEnd));

    stock = stock.subseq(commonStart, commonEnd);
    stockNoDiv = stockNoDiv.subseq(commonStart, commonEnd);
    bonds = bonds.subseq(commonStart, commonEnd);
    reits = reits.subseq(commonStart, commonEnd);
    istock = istock.subseq(commonStart, commonEnd);
    shiller = shiller.subseq(commonStart, commonEnd);

    store.addMisc(stock, "Stock-All");
    store.addMisc(stockNoDiv, "StockNoDiv-All");
    store.addMisc(bonds, "Bonds-All");
    store.addMisc(reits, "REITs-All");
    store.addMisc(istock, "IntStock-All");

    // Add CPI data.
    // Sequence cpi = Shiller.getInflationData(shiller);
    // store.addMisc(cpi, "cpi");
    // store.alias("inflation", "cpi");

    store.add(stock.dup().subseq(iStartSimulation), "Stock");
    store.add(bonds.dup().subseq(iStartSimulation), "Bonds");
    store.add(reits.dup().subseq(iStartSimulation), "REITs");
    store.add(istock.dup().subseq(iStartSimulation), "IntStock");

    Sequence risky = store.getMisc("Stock-All");
    Sequence safe = store.getMisc("Bonds-All");
    Sequence prices = store.getMisc("Stock-All");

    addStrategiesToStore(risky, safe, prices, iStartSimulation);

    Chart.saveLineChart(new File(dir, "vanguard-funds.html"), "Vanguard Funds", GRAPH_WIDTH, GRAPH_HEIGHT,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, store.getReturns("Stock", "Bonds", "REITs", "IntStock"));

    // Chart.saveLineChart(new File(dir, "vanguard-momentum.html"), "Vanguard Momentum", GRAPH_WIDTH, GRAPH_HEIGHT,
    // true,
    // store.getReturns(riskyName, safeName, "Momentum-1", "Mom.Defensive", "Mom.Cautious", "Mom.Moderate",
    // "Mom.Aggressive", "Mom.Aggressive/SMA.Defensive-50/50"));
    //
    // Chart.saveLineChart(new File(dir, "vanguard-sma.html"), "Vanguard SMA", GRAPH_WIDTH, GRAPH_HEIGHT, true, store
    // .getReturns(riskyName, safeName, "sma-1", "sma-3", "sma-5", "sma-10", "SMA.Defensive", "SMA.Cautious",
    // "SMA.Moderate", "SMA.Aggressive"));
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
        if (i + months >= nData) break; // not enough data
        double endBalance = FinLib.calcEndBalance(cumulativeReturns, cpi, principal, salary, expenseRatio, true, 0, 0,
            0.0, i, months);
        if (endBalance > 0.0) ++nOK;
        ++n;
      }
      System.out.printf("Withdrawal Rate: %.2f  %d / %d = %.2f%%\n", wrate, nOK, n, 100.0 * nOK / n);
    }
  }

  public static AssetPredictor[] buildMultiscaleVariations(int iStartSimulation, Slippage slippage, Sequence risky,
      Sequence safe, Sequence prices)
  {
    String priceName = prices.getName();
    List<AssetPredictor> predictors = new ArrayList<>();
    // int[][] params = new int[][] { new int[] { 1 }, new int[] { 3, 4, 5, 6, 7, 8 },
    // new int[] { 6, 7, 8, 9, 10, 11, 12 } };
    // int[][] params = new int[][] { new int[] { 1 }, new int[] { 2, 3, 4 }, new int[] { 9, 10 } };
    int[][] params = new int[][] { new int[] { 1 }, new int[] { 2, 3 }, new int[] { 9, 10 } };

    for (int i = 0; i < params[0].length; ++i) {
      int x = params[0][i];
      for (int j = 0; j < params[1].length; ++j) {
        int y = params[1][j];
        if (y <= x) {
          continue;
        }
        for (int k = 0; k < params[2].length; ++k) {
          int z = params[2][k];
          if (z <= y) {
            continue;
          }
          for (Disposition disposition : Disposition.values()) {
            String suffix = String.format("[%d,%d,%d].%s", x, y, z, disposition);
            if (disposition == Disposition.Defensive) {
              if (k > 0) {
                continue;
              }
              suffix = String.format("[%d,%d].%s", x, y, disposition);
            }
            String name;
            AssetPredictor predictor;
            Sequence seq;

            // System.out.printf("Build: %s\n", suffix);
            // name = "Mom" + suffix;
            // predictor = new Multi3Predictor(name, new AssetPredictor[] { new MomentumPredictor(x, store),
            // new MomentumPredictor(y, store), new MomentumPredictor(z, store) }, disposition, store);
            // predictors.add(predictor);
            // seq = Strategy.calcReturns(predictor, iStartSimulation, nMinTradeGap, risky, safe);
            // store.add(seq);

            name = "SMA" + suffix;
            predictor = new Multi3Predictor(name,
                new AssetPredictor[] { new SMAPredictor(x, smaMargin, priceName, iPriceSMA, store),
                    new SMAPredictor(y, smaMargin, priceName, iPriceSMA, store),
                    new SMAPredictor(z, smaMargin, priceName, iPriceSMA, store) },
                disposition, store);
            predictors.add(predictor);
            seq = Strategy.calcReturns(predictor, iStartSimulation, nMinTradeGap, risky, safe);
            store.add(seq);
          }
        }
      }
    }

    return predictors.toArray(new AssetPredictor[predictors.size()]);
  }

  public static void buildAllMixes(int pctInc, String rexUse, String rexSkip)
  {
    Pattern patUse = (rexUse == null ? null : Pattern.compile(rexUse, Pattern.CASE_INSENSITIVE));
    Pattern patSkip = (rexSkip == null ? null : Pattern.compile(rexSkip, Pattern.CASE_INSENSITIVE));

    List<String> names = new ArrayList<String>();
    for (String name : store.getNames()) {
      if (patSkip != null && patSkip.matcher(name).find()) {
        continue;
      }
      if (patUse != null && !patUse.matcher(name).find()) {
        continue;
      }
      names.add(name);
      // System.out.printf("buildAllMixes: %s\n", name);
    }
    Collections.sort(names);

    // int n = 0;
    // int N = 9 * (names.size() * (names.size() - 1)) / 2;

    for (int j = 0; j < names.size(); ++j) {
      String name1 = names.get(j);
      // System.out.printf("%s (%d / %d = %.2f%%)\n", name1, n, N, 100.0 * n / N);
      for (int k = j + 1; k < names.size(); ++k) {
        String name2 = names.get(k);
        buildMixes(name1, name2, pctInc);
        // n += 9;
      }
    }
  }

  public static void buildAllMixes(String name, String rexUse, String rexSkip)
  {
    System.out.printf("Mix: %s (%d)\n", name, store.size());
    List<String> names = new ArrayList<String>(store.getNames());
    Collections.sort(names);

    Pattern patUse = (rexUse == null ? null : Pattern.compile(rexUse, Pattern.CASE_INSENSITIVE));
    Pattern patSkip = (rexSkip == null ? null : Pattern.compile(rexSkip, Pattern.CASE_INSENSITIVE));

    for (int j = 0; j < names.size(); ++j) {
      String name1 = names.get(j);
      if (patSkip != null && patSkip.matcher(name1).find()) {
        continue;
      }
      if (patUse != null && !patUse.matcher(name1).find()) {
        continue;
      }
      buildMixes(name1, name, 20);
    }
  }

  public static void buildMixes(String name1, String name2, int pctInc)
  {
    for (int pct = pctInc; pct < 100; pct += pctInc) {
      RebalanceInfo rebalance = new RebalanceInfo(new int[] { pct, 100 - pct }, 12, 0.0);
      Sequence seq = Strategy.calcReturns(new Sequence[] { store.get(name1), store.get(name2) }, rebalance);
      if (name1.compareTo(name2) <= 0) {
        store.add(seq, String.format("%s/%s-%d/%d", name1, name2, pct, 100 - pct));
      } else {
        store.add(seq, String.format("%s/%s-%d/%d", name2, name1, 100 - pct, pct));
      }
    }
  }

  public static void buildMixes(int pctInc, String... names)
  {
    final int N = names.length;
    assert N >= 2;
    int[] percents = new int[N];

    Arrays.sort(names);
    buildMixesHelper(names, percents, pctInc, 0);
  }

  public static void buildMixesHelper(String[] names, int[] percents, int pctInc, int index)
  {
    assert names.length == percents.length;

    int sum = Library.sum(percents, 0, index - 1);
    int remainder = 100 - sum;
    if (remainder < pctInc) {
      return;
    }
    // System.out.printf("index=%d, sum=%d, remainder=%d\n", index, sum, remainder);

    if (index == percents.length - 1) {
      assert remainder >= pctInc;
      percents[index] = remainder;
      int[] pcts = new int[percents.length];
      for (int i = 0; i < pcts.length; ++i) {
        pcts[i] = percents[i];
      }
      Sequence[] seqs = store.getReturns(names).toArray(new Sequence[names.length]);
      RebalanceInfo rebalance = new RebalanceInfo(pcts, 12, 0.0);
      Sequence seq = Strategy.calcReturns(seqs, rebalance);
      store.add(seq, FinLib.buildMixedName(names, percents));
    } else {
      for (int pct = pctInc; pct <= remainder; pct += pctInc) {
        percents[index] = pct;
        buildMixesHelper(names, percents, pctInc, index + 1);
      }
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

    Sequence snpData = ShillerOld.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller, iStartData, iEndData);

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStartData, -1);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(snpData, iStartData, -1, DividendMethod.MONTHLY);
    stock.setName("Stock");

    Sequence[] assets = new Sequence[] { stock, bonds };
    RebalanceInfo rebalance = new RebalanceInfo(new int[] { percentStock, percentBonds }, 0, 0.0);

    Sequence mixedNone = Strategy.calcReturns(assets, rebalance);
    // mixedNone.setName(String.format("Stock/Bonds-%d/%d (No Rebalance)", percentStock, percentBonds));
    mixedNone.setName("No Rebalance");

    rebalance.setPolicy(6, 0.0);
    Sequence mixedM6 = Strategy.calcReturns(assets, rebalance);
    // mixedM6.setName(String.format("Stock/Bonds-%d/%d (Rebalance M6)", percentStock, percentBonds));
    mixedM6.setName("6 Months");

    rebalance.setPolicy(12, 0.0);
    Sequence mixedM12 = Strategy.calcReturns(assets, rebalance);
    // mixedM12.setName(String.format("Stock/Bonds-%d/%d (Rebalance M12)", percentStock, percentBonds));
    mixedM12.setName("12 Months");

    rebalance.setPolicy(0, 5.0);
    Sequence mixedB5 = Strategy.calcReturns(assets, rebalance);
    // mixedB5.setName(String.format("Stock/Bonds-%d/%d (Rebalance B5)", percentStock, percentBonds));
    mixedB5.setName("5% Band");

    rebalance.setPolicy(0, 10.0);
    Sequence mixedB10 = Strategy.calcReturns(assets, rebalance);
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
        GRAPH_HEIGHT, ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, all);
    Chart.saveStatsTable(new File(dir, "rebalance-table.html"), GRAPH_WIDTH, false, includeRiskAdjusted,
        includeQuartiles, cumulativeStats);

    int duration = 10 * 12;
    DurationalStats[] stats = new DurationalStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = DurationalStats.calcMonthly(all[i], duration);
    }
    Chart.saveBoxPlots(new File(dir, "rebalance-box.html"),
        String.format("Return Stats (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genReturnChart(File dir) throws IOException
  {
    // String[] names = new String[] { "stock", "bonds", "momentum-1", "momentum-3", "momentum-12", "Mom.Defensive",
    // "Mom.cautious", "Mom.moderate", "Mom.Aggressive", "MomentumWMA", "MomentumWTA", "MultiMomWMA", "MultiMomWTA" };

    String[] names = new String[] { "stock", "bonds", "60/40", "Mom.Aggressive/Mom.Cautious-20/80",
        "Bonds/Mom.Defensive-10/90" };

    // String[] names = new String[] { "stock", "bonds", "tbills" };

    final int duration = 10 * 12;

    store.recalcDurationalStats(duration, FinLib.Inflation.Nominal);
    List<Sequence> all = store.getReturns(names);

    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    List<DurationalStats> rstats = store.getDurationalStats(names);
    double[] scores = new double[names.length];

    Sequence scatter = new Sequence("Returns vs. Volatility");
    for (int i = 0; i < names.length; ++i) {
      scores[i] = cstats.get(i).scoreSimple();
      scatter.addData(new FeatureVec(all.get(i).getName(), 2, rstats.get(i).mean, rstats.get(i).sdev));
    }

    // Chart.saveLineChart(fileChart, "Cumulative Market Returns", GRAPH_WIDTH, GRAPH_HEIGHT, true, multiSmaAggressive,
    // daa,
    // multiMomDefensive, sma, raa, momentum, stock, mixed, bonds, bondsHold);
    Chart.saveLineChart(new File(dir, "cumulative-returns.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, all);

    int[] ii = Library.sort(scores, false);
    for (int i = 0; i < names.length; ++i) {
      System.out.printf("%d [%.1f]: %s\n", i + 1, scores[i], cstats.get(ii[i]));
    }
    Chart.saveStatsTable(new File(dir, "strategy-report.html"), GRAPH_WIDTH, true, includeRiskAdjusted,
        includeQuartiles, cstats);

    Chart.saveBoxPlots(new File(dir, "strategy-box.html"),
        String.format("Return Stats (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        rstats);

    Chart.saveScatterPlot(new File(dir, "strategy-scatter.html"),
        String.format("Momentum: Returns vs. Volatility (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH,
        GRAPH_HEIGHT, 5, new String[] { "Volatility", "CAGR" }, scatter);

    // Win rate vs. first strategy.
    double diffMargin = 0.25;
    Sequence defender = store.get("stock");
    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 0; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(all.get(i), defender, diffMargin));
    }
    Chart.saveComparisonTable(new File(dir, "win-vs-one.html"), GRAPH_WIDTH, comparisons);

    // Win rate vs. first multiple defenders.
    // List<Sequence> defenders = store.getReturns("stock", "bonds", "60/40", "80/20", "50/50");
    // comparisons.clear();
    // for (int i = 0; i < names.length; ++i) {
    // comparisons.add(ComparisonStats.calc(all.get(i), diffMargin, defenders.toArray(new Sequence[defenders.size()])));
    // }
    // Chart.saveComparisonTable(new File(dir, "win-vs-many.html"), GRAPH_WIDTH, comparisons);
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
      Chart.saveChart(new File(DataIO.getOutputPath(), String.format("return-likelihoods-%d-years.html", years[i])),
          ChartConfig.Type.Area, "Return Likelihoods", Histogram.getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH,
          GRAPH_HEIGHT, 0.0, 1.0, Double.NaN, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, liks[i]);
    }
    Chart.saveChart(new File(dir, "return-likelihoods.html"), ChartConfig.Type.Line, "Return Likelihoods",
        Histogram.getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 1.0, Double.NaN,
        ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, liks);

    // Sequence[] assets = new Sequence[] { multiSmaAggressive, daa, multiMomDefensive, momentum, sma, raa, stock,
    // bonds, mixed };
    Sequence[] assets = new Sequence[] { bonds };
    Sequence[] returns = new Sequence[assets.length];
    double vmin = 0.0, vmax = 0.0;
    for (int i = 0; i < assets.length; ++i) {
      DurationalStats.printDurationTable(assets[i]);
      returns[i] = FinLib.calcReturnsForMonths(assets[i], nMonths);

      // for (int j = 0; j < returns[i].length(); ++j) {
      // FeatureVec v = returns[i].get(j);
      // if (v.get(0) > 62.0 || v.get(0) < -42.0) {
      // System.out.printf("%d: %.2f [%s]\n", j, v.get(0), TimeLib.formatMonth(v.getTime()));
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
      histograms[i] = Histogram.computeHistogram(returns[i], vmin, vmax, 0.5, 0.0, 0);
      histograms[i].setName(assets[i].getName());
    }

    String title = "Histogram of Returns - " + TimeLib.formatDurationMonths(nMonths);
    String[] labels = Histogram.getLabelsFromHistogram(histograms[0]);
    Chart.saveChart(new File(dir, "histogram-returns.html"), ChartConfig.Type.Bar, title, labels, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, Double.NaN, ChartScaling.LINEAR, ChartTiming.MONTHLY, 1, histograms);

    // Generate histogram showing future returns.
    title = String.format("Future CAGR: %s (%s)", returns[0].getName(), TimeLib.formatDurationMonths(nMonths));
    Chart.saveChart(new File(dir, "future-returns.html"), ChartConfig.Type.Area, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, Double.NaN, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, returns[0]);
  }

  public static void genDuelViz(File dir) throws IOException
  {
    assert dir.isDirectory();

    final double diffMargin = 0.25;
    int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Nominal);

    String name1 = "sma[1,2,9].cautious/sma[1,3,10].moderate-50/50";
    // String name1 = "SMA[1,2,9].Moderate/SMA[1,3,10].Aggressive-60/40";
    // String name1 = "SMA[1,3,10].Aggressive";
    String name2 = "mom[1,3,12].aggressive";

    Sequence player1 = store.get(name1);
    Sequence player2 = store.get(name2);

    ComparisonStats comparison = ComparisonStats.calc(player1, player2, diffMargin);
    Chart.saveComparisonTable(new File(dir, "duel-comparison.html"), GRAPH_WIDTH, comparison);
    Chart.saveStatsTable(new File(dir, "duel-chart.html"), GRAPH_WIDTH, false, includeRiskAdjusted, includeQuartiles,
        store.getCumulativeStats(name1, name2));

    Chart.saveChart(new File(dir, "duel-cumulative.html"), ChartConfig.Type.Line, "Cumulative Market Returns", null,
        null, GRAPH_WIDTH, GRAPH_HEIGHT, 1, Double.NaN, 1.0, ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, 0, player2,
        player1);

    DurationalStats dstatsA = store.getDurationalStats(name1);
    DurationalStats dstatsB = store.getDurationalStats(name2);

    // ReturnStats.printDurationTable(player1);
    // ReturnStats.printDurationTable(player2);

    // Generate scatter plot comparing results.
    String title = String.format("%s vs. %s (%s)", name1, name2, TimeLib.formatDurationMonths(duration));
    Chart.saveScatter(new File(dir, "duel-scatter.html"), title, "730px", GRAPH_HEIGHT, 0, dstatsB.durationReturns,
        dstatsA.durationReturns);

    // Generate histogram summarizing excess returns of B over A.
    title = String.format("Excess Returns: %s vs. %s (%s)", name1, name2, TimeLib.formatDurationMonths(duration));
    Sequence excessReturns = dstatsA.durationReturns.sub(dstatsB.durationReturns);
    Sequence histogramExcess = Histogram.computeHistogram(excessReturns, 0.5, 0.0, 0);
    histogramExcess.setName(String.format("%s vs. %s", name1, name2));
    String[] colors = new String[excessReturns.length()];
    for (int i = 0; i < colors.length; ++i) {
      double x = excessReturns.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }

    Chart.saveChart(new File(dir, "duel-returns.html"), ChartConfig.Type.Line, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, dstatsB.durationReturns,
        dstatsA.durationReturns);
    Chart.saveChart(new File(dir, "duel-excess-histogram.html"), ChartConfig.Type.PosNegArea, title, null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN, Double.NaN, 1.0, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0,
        excessReturns);

    String[] labels = Histogram.getLabelsFromHistogram(histogramExcess);
    colors = new String[labels.length];
    for (int i = 0; i < colors.length; ++i) {
      double x = histogramExcess.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }
    Chart.saveChart(new File(dir, "duel-histogram.html"), ChartConfig.Type.Bar, title, labels, colors, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 32, ChartScaling.LINEAR, ChartTiming.MONTHLY, 1, histogramExcess);

    // double[] a = excessReturns.extractDim(0);
    // int[] ii = Library.sort(a, true);
    // for (int i = 0; i < excessReturns.length(); ++i) {
    // System.out.printf("[%s] %.3f\n", TimeLib.formatMonth(excessReturns.getTimeMS(ii[i])), a[i]);
    // }
  }

  public static void genStockBondMixSweepChart(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);

    Sequence snpData = ShillerOld.getStockData(shiller, iStart, iEnd);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller, iStart, iEnd);

    Sequence snp = FinLib.calcSnpReturns(snpData, iStart, -1, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, -1);

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;

    Sequence[] all = new Sequence[percentStock.length];
    for (int i = 0; i < percentStock.length; ++i) {
      RebalanceInfo rebalance = new RebalanceInfo(new int[] { percentStock[i], 100 - percentStock[i] }, rebalanceMonths,
          rebalanceBand);
      all[i] = Strategy.calcReturns(new Sequence[] { snp, bonds }, rebalance);
      all[i].setName(String.format("%d / %d", percentStock[i], 100 - percentStock[i]));
    }
    CumulativeStats[] cumulativeStats = CumulativeStats.calc(all);

    Chart.saveChart(new File(dir, "stock-bond-sweep.html"), ChartConfig.Type.Line,
        "Cumulative Market Returns: Stock/Bond Mix", null, null, GRAPH_WIDTH, GRAPH_HEIGHT, 1.0, 262144.0, 1.0,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, 0, all);
    Chart.saveStatsTable(new File(dir, "chart-stock-bond-sweep.html"), GRAPH_WIDTH, false, includeRiskAdjusted,
        includeQuartiles, cumulativeStats);

    // all[0].setName("Stock");
    // all[all.length - 1].setName("Bonds");
    // Chart.printDecadeTable(all[0], all[all.length - 1]);

    int duration = 1 * 12;
    DurationalStats[] stats = new DurationalStats[all.length];
    for (int i = 0; i < all.length; ++i) {
      stats[i] = DurationalStats.calcMonthly(all[i], duration);
      all[i].setName(String.format("%d/%d (%.2f%%)", percentStock[i], 100 - percentStock[i], stats[i].mean));
    }
    Chart.saveBoxPlots(new File(dir, "stock-bond-sweep-box.html"),
        String.format("Return Stats (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        stats);
  }

  public static void genEfficientFrontier(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    Sequence stockData = ShillerOld.getStockData(shiller, iStart, iEnd);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller, iStart, iEnd);

    Sequence stock = FinLib.calcSnpReturns(stockData, iStart, -1, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, iStart, -1);
    assert stock.length() == bonds.length();

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 12;
    double rebalanceBand = 10.0;
    int[] durations = new int[] { 1, 5, 10, 15, 20, 30 };

    // Generate curve for each decade.
    List<Sequence> decades = new ArrayList<Sequence>();
    int iDecadeStart = TimeLib.findStartofFirstDecade(stockData, false);
    for (int i = iDecadeStart; i + 120 < stockData.length(); i += 120) {
      LocalDate date = TimeLib.ms2date(stockData.getTimeMS(i));
      Sequence decadeStock = FinLib.calcSnpReturns(stockData, i, 120, DividendMethod.MONTHLY);
      Sequence decadeBonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, i, i + 120);
      assert decadeStock.length() == decadeBonds.length();

      Sequence decade = new Sequence(String.format("%ss", date.getYear()));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        RebalanceInfo rebalance = new RebalanceInfo(new int[] { pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        Sequence mixed = Strategy.calcReturns(new Sequence[] { decadeStock, decadeBonds }, rebalance);
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
      Sequence frontier = new Sequence(TimeLib.formatDurationMonths(duration));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        RebalanceInfo rebalance = new RebalanceInfo(new int[] { pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        Sequence mixed = Strategy.calcReturns(new Sequence[] { stock, bonds }, rebalance);
        DurationalStats stats = DurationalStats.calcMonthly(mixed, duration);
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
    final int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Nominal);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("stock");
    for (int i = 1; i <= 12; ++i) {
      nameList.add("sma-" + i);
    }
    nameList.addAll(store.getNames("sma-\\d+-"));
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "sma-sweep.html"), GRAPH_WIDTH, true, includeRiskAdjusted, includeQuartiles,
        store.getCumulativeStats(names));
  }

  public static List<String> genDominationChart(List<String> candidates, File dir) throws IOException
  {
    long startMS = TimeLib.getTime();

    // Filter candidates to find "dominating" strategies.
    store.recalcDurationalStats(10 * 12, FinLib.Inflation.Nominal);
    // FinLib.filterStrategies(candidates, store);
    Collections.sort(candidates);

    List<CumulativeStats> winners = new ArrayList<CumulativeStats>();
    for (String name : candidates) {
      winners.add(store.getCumulativeStats(name));
    }
    // Collections.sort(winners, Collections.reverseOrder());
    System.out.printf("Winners: %d\n", winners.size());
    Chart.saveStatsTable(new File(dir, "domination-chart.html"), GRAPH_WIDTH, true, includeRiskAdjusted,
        includeQuartiles, winners);

    System.out.printf("Done (%s).\n", TimeLib.formatDuration(TimeLib.getTime() - startMS));

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
    store.recalcDurationalStats(duration, FinLib.Inflation.Nominal);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("Stock");
    for (int i = 0; i < momentumMonths.length; ++i) {
      nameList.add("momentum-" + momentumMonths[i]);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "momentum-sweep.html"), GRAPH_WIDTH, true, includeRiskAdjusted, includeQuartiles,
        store.getCumulativeStats(names));
    // Chart.printDecadeTable(store.get("momentum-12"), store.get("stock"));

    Chart.saveBoxPlots(new File(dir, "momentum-box-plots.html"),
        String.format("Momentum Returns (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));
  }

  public static void genNewHighSweepChart(File dir) throws IOException
  {
    final int duration = 10 * 12;
    store.recalcDurationalStats(duration, FinLib.Inflation.Nominal);

    // Build list of names of assets/strategies that we care about for scatter plot.
    List<String> nameList = new ArrayList<>();
    nameList.add("Stock");
    for (int i = 1; i <= 12; ++i) {
      nameList.add("NewHigh-" + i);
    }
    String[] names = nameList.toArray(new String[nameList.size()]);
    Chart.saveStatsTable(new File(dir, "NewHigh-sweep.html"), GRAPH_WIDTH, true, includeRiskAdjusted, includeQuartiles,
        store.getCumulativeStats(names));
    System.out.print(Chart.genDecadeTable(store.get("NewHigh-12"), store.get("stock")));
    Chart.saveLineChart(new File(dir, "NewHigh-cumulative.html"), "Cumulative Market Returns: NewHigh Strategy",
        GRAPH_WIDTH, GRAPH_HEIGHT, ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, store.getReturns(names));
    Chart.saveBoxPlots(new File(dir, "NewHigh-box-plots.html"),
        String.format("NewHigh Returns (%s)", TimeLib.formatDurationMonths(duration)), GRAPH_WIDTH, GRAPH_HEIGHT, 2.0,
        store.getDurationalStats(names));
    List<CumulativeStats> cstats = store.getCumulativeStats(names);
    Chart.saveStatsTable(new File(dir, "NewHigh-chart.html"), GRAPH_WIDTH, true, includeRiskAdjusted, includeQuartiles,
        cstats);
  }

  public static void genDrawdownChart(File dir) throws IOException
  {
    String[] names = new String[] { "Stock", "80/20", "60/40", "Mom.Aggressive", "Mom.Aggressive/Mom.Cautious-20/80" };

    Sequence[] drawdowns = new Sequence[names.length];
    for (int i = 0; i < names.length; ++i) {
      drawdowns[i] = FinLib.calcDrawdown(store.get(names[i]));
    }
    Chart.saveLineChart(new File(dir, "drawdown.html"), "Drawdown", GRAPH_WIDTH, GRAPH_HEIGHT, ChartScaling.LINEAR,
        ChartTiming.MONTHLY, drawdowns);
  }

  public static void genBeatInflationChart(File dir) throws IOException
  {
    String[] names = new String[] { "stock", "bonds", "sma[1,2,9].cautious/sma[1,3,10].moderate-60/40" };

    final double diffMargin = 0.01;
    final double targetReturn = 3.0; // Annual return over inflation

    List<ComparisonStats> comparisons = new ArrayList<ComparisonStats>();
    for (int i = 0; i < names.length; ++i) {
      comparisons.add(ComparisonStats.calc(store.getReal(names[i]), targetReturn, diffMargin));
    }
    Chart.saveBeatInflationTable(new File(dir, "strategy-beat-inflation.html"), GRAPH_WIDTH, comparisons);
  }

  public static void genInterestRateGraph(Sequence shiller, Sequence tbills, File file) throws IOException
  {
    Sequence bonds = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller);
    Chart.saveChart(file, ChartConfig.Type.Line, "Interest Rates", null, null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 16.0,
        1.0, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, bonds, tbills);
  }

  public static void genCorrelationGraph(Sequence shiller, File dir) throws IOException
  {
    int iStartData = 0; // shiller.getIndexForDate(1999, 1);
    int iEndData = shiller.length() - 1;

    Sequence stockData = ShillerOld.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller, iStartData, iEndData);

    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    bonds.setName("Bonds");
    Sequence stock = FinLib.calcSnpReturns(stockData, 0, -1, DividendMethod.MONTHLY);
    stock.setName("Stock");

    Sequence corr = FinLib.calcCorrelation(stock, bonds, 3 * 12);
    Chart.saveChart(new File(dir, "stock-bond-correlation.html"), ChartConfig.Type.Area, corr.getName(), null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, -1.0, 1.0, 0.25, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, corr);
  }

  public static void genSavingsTargetChart(File dir) throws IOException
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
    double desiredMonthlyCash = 8000.00;
    double desiredRunwayYears = -2.0;
    double salary = FinLib.calcAnnualSalary(desiredMonthlyCash, taxRate);
    System.out.printf("Salary: $%s\n", FinLib.currencyFormatter.format(salary));

    // "sma[1,4,10].aggressive/sma[1,4,11].moderate-30/70",
    // String[] names = new String[] { "SMA[1,4,11].Moderate", "SMA[1,4].Defensive", "SMA[1,3,11].Cautious",
    // "SMA[1,4,11].Aggressive", "sma[1,3,10].cautious/sma[1,4,11].aggressive-60/40",
    // "sma[1,3].defensive/sma[1,4,11].moderate-40/60" };
    String[] names = store.getNames().toArray(new String[0]);
    // List<String> candidates = new ArrayList<String>(store.getNames());
    // List<String> winners = genDominationChart(candidates, dir);
    // String[] names = winners.toArray(new String[winners.size()]);

    RetirementStats[] results = new RetirementStats[names.length];

    final long key = Sequence.Lock.genKey();
    for (int i = 0; i < names.length; ++i) {
      Sequence cumulativeReturns = store.get(names[i]);
      Sequence cpi = store.getMisc("cpi").lockToMatch(cumulativeReturns, key);
      // List<Integer> failures =
      results[i] = FinLib.calcSavingsTarget(cumulativeReturns, cpi, salary, likelihood, nYears, expenseRatio, retireAge,
          ssAge, expectedMonthlySS, desiredRunwayYears);
      cpi.unlock(key);
      System.out.printf("(%.2f%%) %60s: $%s\n", 100.0 * (i + 1) / names.length, names[i],
          FinLib.dollarFormatter.format(results[i].principal));
    }

    System.out.println();
    results = RetirementStats.filter(results);
    Arrays.sort(results, Collections.reverseOrder());
    for (int i = 0; i < results.length; ++i) {
      System.out.printf("%60s: $%s  ($%s, $%.2fm, %.2f%%)\n", results[i].name,
          FinLib.dollarFormatter.format(results[i].principal), FinLib.dollarFormatter.format(results[i].min),
          results[i].percentile10 / 1e6, 100.0 * salary / results[i].principal);

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
    double desiredRunwayYears = 2.0;
    double salary = FinLib.calcAnnualSalary(desiredMonthlyCash, taxRate);

    RetirementStats[] results = new RetirementStats[names.size()];

    for (int i = 0; i < names.size(); ++i) {
      Sequence cumulativeReturns = store.get(names.get(i));
      results[i] = FinLib.calcSavingsTarget(cumulativeReturns, salary, nYears, expenseRatio, desiredRunwayYears);
      // System.out.printf("%60s: $%s\n", names.get(i), FinLib.dollarFormatter.format(results[i].principal));
    }

    System.out.println();
    Arrays.sort(results);
    for (int i = 0; i < names.size(); ++i) {
      System.out.printf("%60s: $%s  ($%s, $%.2fm)\n", results[i].name,
          FinLib.dollarFormatter.format(results[i].principal), FinLib.dollarFormatter.format(results[i].min),
          results[i].percentile10 / 1e6);

      // System.out.printf("<tr><td>%s</td><td>%s</td><td>%.1f</td><td>%.1f</td><td>%.1f</td></tr>\n",
      // FinLib.getBoldedName(results[i].name), FinLib.dollarFormatter.format(results[i].principal),
      // results[i].percentile10 / 1e6, results[i].median / 1e6, results[i].percentile90 / 1e6);
    }
    System.out.println();
  }

  public static void genChartsForDifficultTimePeriods(File dir) throws IOException
  {
    final long[][] timePeriods = new long[][] { { TimeLib.toMs(1924, 12, 31), TimeLib.toMs(1934, 12, 31) },
        { TimeLib.toMs(1994, 12, 31), TimeLib.toMs(2004, 12, 31) },
        { TimeLib.toMs(2004, 12, 31), TimeLib.toMs(2014, 12, 31) },
        { TimeLib.toMs(1999, 12, 31), TimeLib.toMs(2009, 12, 31) },
        { TimeLib.toMs(1999, 12, 31), TimeLib.toMs(2015, 8, 30) },
        { TimeLib.toMs(1994, 1, 1), TimeLib.toMs(2013, 12, 31) } };

    // String[] names = new String[] { "stock", "bonds", "60/40", "80/20",
    // "sma[1,2,9].cautious/sma[1,3,10].moderate-50/50", "SMA[1,2,9].Moderate/SMA[1,3,10].Aggressive-50/50",
    // "NewHigh[10]/SMA[1,2,9].Cautious/SMA[1,3,10].Moderate-30/30/40", "mom[1,3,12].aggressive" };

    String[] names = new String[] { "stock", "bonds", "60/40", "80/20",
        "sma[1,2,9].cautious/sma[1,3,10].moderate-50/50", "mom[1,3,12].aggressive" };

    Sequence[] returns = new Sequence[names.length];
    for (int iTimePeriod = 0; iTimePeriod < timePeriods.length; ++iTimePeriod) {
      long[] timePeriod = timePeriods[iTimePeriod];
      // System.out.printf("%d: %d -> %d\n", iTimePeriod + 1, timePeriod[0], timePeriod[1]);
      // System.out.printf(" [%s] -> [%s]\n", TimeLib.formatMonth(timePeriod[0]), TimeLib.formatMonth(timePeriod[1]));
      for (int i = 0; i < names.length; ++i) {
        Sequence seq = store.get(names[i]).subseq(timePeriod[0], timePeriod[1]);
        // System.out.printf("%d: [%s] -> [%s]\n", i + 1, TimeLib.formatMonth(seq.getStartMS()),
        // TimeLib.formatMonth(seq.getEndMS()));
        returns[i] = seq.div(seq.getFirst(0));
        double cagr = FinLib.getAnnualReturn(returns[i].getLast(0) / returns[i].getFirst(0), returns[i].length() - 1);
        returns[i].setName(String.format("%s (%.2f%%)", FinLib.getBaseName(seq.getName()), cagr));
      }
      String title = String.format("[%s] - [%s]", TimeLib.formatMonth(timePeriod[0]),
          TimeLib.formatMonth(timePeriod[1]));
      Chart.saveChart(new File(dir, String.format("time-period-%02d.html", iTimePeriod + 1)), ChartConfig.Type.Line,
          title, null, null, GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN, Double.NaN, 1.0, ChartScaling.LOGARITHMIC,
          ChartTiming.MONTHLY, 0, returns);
    }
  }

  public static void genFirstLastHalfResults(File dir)
  {
    List<Sequence[]> testSeqs = new ArrayList<>();

    Sequence stock = store.get("Stock");
    Sequence bonds = store.get("Bonds");

    final String priceSeqName = "StockData";

    final int iStart = 12;
    final int N = stock.length();

    testSeqs.add(new Sequence[] { stock, bonds });
    testSeqs.add(new Sequence[] { stock.subseq(0, N / 2).setName("Stock-FirstHalf"),
        bonds.subseq(0, N / 2).setName("Bonds-FirstHalf") });
    testSeqs.add(new Sequence[] { stock.subseq(N / 4, N / 2).setName("Stock-MiddleHalf"),
        bonds.subseq(N / 4, N / 2).setName("Bonds-MiddleHalf") });
    testSeqs.add(new Sequence[] { stock.subseq(N / 2).setName("Stock-SecondHalf"),
        bonds.subseq(N / 2).setName("Bonds-SecondHalf") });

    AssetPredictor mom1312Aggressive = new Multi3Predictor("Mom[1,3,12].Aggressive", new AssetPredictor[] {
        new MomentumPredictor(1, store), new MomentumPredictor(3, store), new MomentumPredictor(12, store) },
        Disposition.Aggressive, store);

    AssetPredictor sma129Cautious = new Multi3Predictor("SMA[1,2,9].Cautious",
        new AssetPredictor[] { new SMAPredictor(1, priceSeqName, iPriceSMA, store),
            new SMAPredictor(2, priceSeqName, iPriceSMA, store), new SMAPredictor(9, priceSeqName, iPriceSMA, store) },
        Disposition.Cautious, store);

    AssetPredictor sma129Moderate = new Multi3Predictor("SMA[1,2,9].Moderate",
        new AssetPredictor[] { new SMAPredictor(1, priceSeqName, iPriceSMA, store),
            new SMAPredictor(2, priceSeqName, iPriceSMA, store), new SMAPredictor(9, priceSeqName, iPriceSMA, store) },
        Disposition.Moderate, store);

    AssetPredictor sma1310Moderate = new Multi3Predictor("SMA[1,3,10].Moderate",
        new AssetPredictor[] { new SMAPredictor(1, priceSeqName, iPriceSMA, store),
            new SMAPredictor(3, priceSeqName, iPriceSMA, store), new SMAPredictor(10, priceSeqName, iPriceSMA, store) },
        Disposition.Moderate, store);

    AssetPredictor sma1310Aggressive = new Multi3Predictor("SMA[1,3,10].Aggressive",
        new AssetPredictor[] { new SMAPredictor(1, priceSeqName, iPriceSMA, store),
            new SMAPredictor(3, priceSeqName, iPriceSMA, store), new SMAPredictor(10, priceSeqName, iPriceSMA, store) },
        Disposition.Aggressive, store);

    AssetPredictor mix1 = new MixedPredictor(null, new AssetPredictor[] { sma129Cautious, sma1310Moderate },
        new int[] { 50, 50 }, store);
    AssetPredictor mix2 = new MixedPredictor(null, new AssetPredictor[] { sma129Moderate, sma1310Aggressive },
        new int[] { 50, 50 }, store);

    AssetPredictor[] predictors = new AssetPredictor[] { new ConstantPredictor("Stock", 0, store),
        new ConstantPredictor("Bonds", 1, store), mom1312Aggressive, sma129Cautious, sma1310Moderate, sma1310Aggressive,
        mix1, mix2 };

    for (int iTest = 0; iTest < testSeqs.size(); ++iTest) {
      Sequence[] seqs = testSeqs.get(iTest);
      System.out.printf("\n-- Test Sequences %d  [%s] -> [%s] --\n", iTest + 1,
          TimeLib.formatMonth(seqs[0].getStartMS()), TimeLib.formatMonth(seqs[0].getEndMS()));
      for (Sequence seq : seqs) {
        if (!store.has(seq.getName())) {
          store.addMisc(seq);
        }
      }
      for (AssetPredictor predictor : predictors) {
        Sequence returns = Strategy.calcReturns(predictor, iStart, nMinTradeGap, seqs);
        CumulativeStats cstats = CumulativeStats.calc(returns);
        System.out.println(cstats.toRowString());

        // if (predictor instanceof MixedPredictor) {
        // Sequence[] baseReturns = new Sequence[predictor.predictors.length];
        // for (int i = 0; i < baseReturns.length; ++i) {
        // baseReturns[i] = Strategy.calcReturns(predictor.predictors[i], iStart, slippage, null, seqs);
        // }
        // returns = Strategy.calcMixedReturns(baseReturns, new double[] { 0.5, 0.5 }, 12, 0);
        // returns.setName(predictor.name);
        // cstats = CumulativeStats.calc(returns);
        // System.out.printf(" %s <- Rebalanced\n", cstats);
        // }
      }
    }
  }

  public static Sequence synthesizeData(File dataDir, File dir) throws IOException
  {
    Sequence shiller = Shiller.loadAll(new File(dataDir, "shiller.csv"));
    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData = FinLib.pad(tbillData, shiller, 0.0);

    Sequence stock = FinLib.calcSnpReturns(ShillerOld.getStockData(shiller), 0, -1, DividendMethod.QUARTERLY);
    Sequence bondData = ShillerOld.getData(ShillerOld.GS10, "Bonds", shiller);
    Sequence bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    Sequence tbills = Bond.calcReturnsRebuy(BondFactory.bill3Month, tbillData, 0, -1);
    Sequence cpi = ShillerOld.getData(ShillerOld.CPI, "cpi", shiller);

    Sequence nikkeiDaily = DataIO.loadDateValueCSV(new File(dataDir, "nikkei225-daily.csv"));
    Sequence nikkei = FinLib.dailyToMonthly(nikkeiDaily);

    Sequence reitsDaily = YahooIO.loadData(new File(dataDir, "VGSIX.csv"));
    Sequence reits = FinLib.dailyToMonthly(reitsDaily);

    Sequence istockDaily = YahooIO.loadData(new File(dataDir, "VGTSX.csv"));
    Sequence istock = FinLib.dailyToMonthly(istockDaily);

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
        LocalDate date = TimeLib.ms2date(v.getTime());
        date = date.plusMonths(1);
        synth.addData(v, TimeLib.toMs(date));
        source.add(dRisky.getName());
      }
    }

    System.out.printf("%d, %d, %d, %d (%d)\n", counts[0], counts[1], counts[2], counts[3], Library.sum(counts));
    System.out.printf("Inflation: %.3f\n",
        FinLib.getAnnualReturn(FinLib.getTotalReturn(cpi, 0, cpi.length() - 1), cpi.length() - 1));
    System.out.printf("CAGR: %.3f\n",
        FinLib.getAnnualReturn(FinLib.getTotalReturn(synth, 0, synth.length() - 1), synth.length() - 1));

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, "synth.csv")))) {
      writer.write("Date,Stock,Bonds,TBills,Source\n");
      for (int i = 0; i < synth.length(); ++i) {
        FeatureVec v = synth.get(i);
        writer.write(String.format("%s,%.2f,%.2f,%.2f,%s\n", TimeLib.formatYMD(v.getTime()), v.get(0), v.get(1),
            v.get(2), Library.prefix(source.get(i), "-")));
      }
    }

    System.out.printf("Synthetic Data (%d): [%s] -> [%s]\n", synth.length(), TimeLib.formatMonth(synth.getStartMS()),
        TimeLib.formatMonth(synth.getEndMS()));

    Chart.saveLineChart(new File(dir, "synth.html"), "Synthetic Data", "1600px", "800px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, synth.extractDims(0), synth.extractDims(1), synth.extractDims(2));

    return synth;
  }

  public static void runSyntheticTest(File dataDir, File dir) throws IOException
  {
    Sequence synth = DataIO.loadCSV(new File(dataDir, "synth-500.csv"), new int[] { 1, 2, 3 });
    System.out.printf("Synthetic Data (%d): [%s] -> [%s]\n", synth.length(), TimeLib.formatMonth(synth.getStartMS()),
        TimeLib.formatMonth(synth.getEndMS()));

    Sequence stockAll = synth.extractDims(0);
    stockAll._div(stockAll.get(0, 0));
    store.addMisc(stockAll, "StockAll");

    Sequence bondsAll = synth.extractDims(1);
    bondsAll._div(bondsAll.get(0, 0));
    store.addMisc(bondsAll, "BondsAll");

    int iStartSimulation = 12;
    Slippage slippage = Slippage.None;

    Sequence stock = stockAll.subseq(iStartSimulation);
    Sequence bonds = bondsAll.subseq(iStartSimulation);
    store.add(stock, "Stock");
    store.add(bonds, "Bonds");

    Sequence risky = stockAll;
    Sequence safe = bondsAll;
    Sequence prices = stockAll;

    List<String> candidates = new ArrayList<String>();
    candidates.add("stock");
    candidates.add("bonds");

    // Build Momentum and SMA predictors.
    AssetPredictor[] momPredictors = new AssetPredictor[12];
    AssetPredictor[] smaPredictors = new AssetPredictor[12];
    for (int i = 0; i < momPredictors.length; ++i) {
      momPredictors[i] = new MomentumPredictor(i + 1, store);
      smaPredictors[i] = new SMAPredictor(i + 1, prices.getName(), iPriceSMA, store);
    }

    // Multi-scale Momentum and SMA methods.
    AssetPredictor[] multiMomPredictors = new AssetPredictor[4];
    AssetPredictor[] multiSMAPredictors = new AssetPredictor[4];
    for (Disposition disposition : Disposition.values()) {
      AssetPredictor momPredictor = new Multi3Predictor("Mom." + disposition, new AssetPredictor[] {
          new MomentumPredictor(1, store), new MomentumPredictor(3, store), new MomentumPredictor(12, store) },
          disposition, store);
      multiMomPredictors[disposition.ordinal()] = momPredictor;
      Sequence mom = Strategy.calcReturns(momPredictor, iStartSimulation, nMinTradeGap, risky, safe);

      AssetPredictor smaPredictor = new Multi3Predictor("SMA." + disposition,
          new AssetPredictor[] { new SMAPredictor(1, prices.getName(), iPriceSMA, store),
              new SMAPredictor(5, prices.getName(), iPriceSMA, store),
              new SMAPredictor(10, prices.getName(), iPriceSMA, store) },
          disposition, store);
      multiSMAPredictors[disposition.ordinal()] = smaPredictor;
      Sequence sma = Strategy.calcReturns(smaPredictor, iStartSimulation, nMinTradeGap, risky, safe);

      store.add(mom);
      store.add(sma);
      candidates.add(mom.getName());
      candidates.add(sma.getName());
    }

    // WMA Momentum strategy.
    AssetPredictor wmaMomPredictor = new WMAPredictor("MomentumWMA", momPredictors, store);
    Sequence wmaMomentum = Strategy.calcReturns(wmaMomPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    store.add(wmaMomentum);
    candidates.add(wmaMomentum.getName());

    // WTA Momentum strategy.
    AssetPredictor wtaMomPredictor = new WTAPredictor("MomentumWTA", momPredictors, store);
    Sequence wtaMomentum = Strategy.calcReturns(wtaMomPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    store.add(wtaMomentum);
    candidates.add(wtaMomentum.getName());

    // Multi-Momentum WMA strategy.
    AssetPredictor multiMomWMAPredictor = new WMAPredictor("MultiMomWMA", multiMomPredictors, 0.25, 0.1, store);
    Sequence multiMomWMA = Strategy.calcReturns(multiMomWMAPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    store.add(multiMomWMA);
    candidates.add(multiMomWMA.getName());

    // Multi-Momentum WTA strategy.
    AssetPredictor multiMomWTAPredictor = new WTAPredictor("MultiMomWTA", multiMomPredictors, 0.25, 0.1, store);
    Sequence multiMomWTA = Strategy.calcReturns(multiMomWTAPredictor, iStartSimulation, nMinTradeGap, risky, safe);
    store.add(multiMomWTA);
    candidates.add(multiMomWTA.getName());

    // Mixed multiscale mom and sma strategies.
    int pctInc = 10;
    for (int j = 0; j < dispositions.length; ++j) {
      String name1 = "Mom." + dispositions[j];
      for (int k = 0; k < dispositions.length; ++k) {
        String name2 = "SMA." + dispositions[k];
        for (int i = pctInc; i < 100; i += pctInc) {
          RebalanceInfo rebalance = new RebalanceInfo(new int[] { i, 100 - i }, 12, 0.0);
          Sequence seq = Strategy.calcReturns(new Sequence[] { store.get(name1), store.get(name2) }, rebalance);
          store.add(seq, String.format("Mom.%s/SMA.%s-%d/%d", dispositions[j], dispositions[k], i, 100 - i));
          candidates.add(seq.getName());
        }
      }
    }

    // Momentum sweep.
    for (int i = 1; i <= 12; ++i) {
      MomentumPredictor predictor = new MomentumPredictor(i, store);
      Sequence mom = Strategy.calcReturns(predictor, iStartSimulation, nMinTradeGap, risky, safe);
      store.add(mom);
      candidates.add(mom.getName());
    }

    List<String> winners = genDominationChart(candidates, dir);

    List<CumulativeStats> cstats = store.getCumulativeStats(winners.toArray(new String[winners.size()]));
    Collections.sort(cstats);
    for (CumulativeStats cs : cstats) {
      System.out.println(cs);
    }
    System.out.println();

    Chart.saveLineChart(new File(dir, "synth-cumulative.html"), "Synthetic Data", GRAPH_WIDTH, GRAPH_HEIGHT,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY,
        store.getReturns(candidates.toArray(new String[candidates.size()])));

    // Chart.saveBoxPlots(new File(dir, "synth-boxplots.html"), "Synthetic Box Plots", GRAPH_WIDTH, GRAPH_HEIGHT, 0.5,
    // cstats);
    // Chart.saveLineChart(new File(dir, "synth-cumulative.html"), "Synthetic Data", GRAPH_WIDTH, GRAPH_HEIGHT, true,
    // stock, bonds, store.get("Mom.Aggressive/SMA.Defensive-50/50"));
    //
    // genSavingsTargetChart(winners, dir);
    //
    // int duration = 30 * 12;
    // List<ReturnStats> results = new ArrayList<>();
    // for (String name : winners) {
    // Sequence returns = store.get(name);
    // double[] endBalances = new double[returns.length() - duration];
    // int iStart = 0;
    // for (; iStart + duration < returns.length(); ++iStart) {
    // endBalances[iStart] = FinLib.calcEndSavings(returns, 220000.0, 5500.0, 4400.0, 0.1, iStart, duration);
    // }
    // assert iStart == endBalances.length;
    // results.add(ReturnStats.calc(name, endBalances));
    // }
    //
    // results.sort(new Comparator<ReturnStats>()
    // {
    // @Override
    // public int compare(ReturnStats a, ReturnStats b)
    // {
    // if (a.min < b.min)
    // return -1;
    // if (a.min > b.min)
    // return 1;
    //
    // if (a.percentile10 < b.percentile10)
    // return -1;
    // if (a.percentile10 > b.percentile10)
    // return 1;
    //
    // return 0;
    // }
    // });
    //
    // for (ReturnStats stats : results) {
    // System.out.printf("%40s: [min=$%s, 10%%=$%s, 25%%=$%s, 50%%=$%s, 75%%=$%s, 90%%=$%s]\n", stats.name,
    // FinLib.formatDollars(stats.min), FinLib.formatDollars(stats.percentile10),
    // FinLib.formatDollars(stats.percentile25), FinLib.formatDollars(stats.median),
    // FinLib.formatDollars(stats.percentile75), FinLib.formatDollars(stats.percentile90));
    // }
  }

  public static void runJitterTest(File dataDir, File dir) throws IOException
  {
    setupVanguardData(dataDir, dir);
    Sequence stockDaily = store.getMisc("Stock-Daily");
    Sequence bondsDaily = store.getMisc("Bonds-Daily");
    final long commonStart = store.getMisc("Stock-All").getStartMS();
    final long commonEnd = store.getMisc("Stock-All").getEndMS();
    final int iStart = 12;
    final int nJitter = 3;

    iPriceSMA = FinLib.MonthlyAverage;

    Map<String, List<CumulativeStats>> map = new TreeMap<>();
    for (int iter = 0; iter < 200; ++iter) {
      System.out.printf("--------- %d ------------\n", iter + 1);
      store.clear();

      Sequence stock = FinLib.dailyToMonthly(stockDaily, 0, nJitter);
      Sequence bonds = FinLib.dailyToMonthly(bondsDaily, 0, nJitter);

      stock = stock.subseq(commonStart, commonEnd);
      bonds = bonds.subseq(commonStart, commonEnd);

      store.addMisc(stock, "Stock-All");
      store.addMisc(bonds, "Bonds-All");

      store.add(stock.dup().subseq(iStart), "Stock");
      store.add(bonds.dup().subseq(iStart), "Bonds");

      Sequence risky = store.getMisc("Stock-All");
      Sequence safe = store.getMisc("Bonds-All");
      Sequence prices = store.getMisc("Stock-All");

      addStrategiesToStore(risky, safe, prices, iStart);

      Collection<String> names = store.getNames();
      List<CumulativeStats> stats = store.getCumulativeStats(names.toArray(new String[names.size()]));

      for (CumulativeStats cstats : stats) {
        String name = FinLib.getBaseName(cstats.name());
        List<CumulativeStats> list;
        if (map.containsKey(name)) {
          list = map.get(name);
        } else {
          list = new ArrayList<>();
          map.put(name, list);
        }
        list.add(cstats);
      }
    }

    String[] names = new String[] { "Stock", "Bonds", "Stock/Bonds-60/40", "Stock/Bonds-80/20",
        "SMA[1,2,9].Cautious/SMA[1,3,10].Moderate-50/50", "SMA[1,2,9].Moderate/SMA[1,5,10].Aggressive-50/50",
        "SMA[1,2,9].Moderate/SMA[1,3,10].Aggressive-50/50", "SMA[1,3,10].Moderate/SMA[1,5,10].Defensive-50/50",
        "SMA[1,3,12].Moderate/SMA[1,5,10].Defensive-50/50", "SMA[1,2,9].Moderate", "SMA[1,5,10].Aggressive",
        "SMA[1,3,10].Aggressive/SMA[1,5,10].Aggressive-50/50", "Mom[1,3,12].Defensive", "Mom[1,3,12].Cautious",
        "Mom[1,3,12].Moderate", "Mom[1,3,12].Aggressive", };

    // String[] names = new String[] { "Stock", "SMA[1,2,9].Moderate", "SMA[1,5,10].Aggressive",
    // "Mom[1,3,12].Aggressive", };

    List<ReturnStats[]> results = new ArrayList<>();
    double[] cagr = null;
    double[] drawdown = null;
    for (String name : names) {// map.keySet()) {
      // System.out.println(name);
      List<CumulativeStats> stats = map.get(name);
      if (cagr == null) {
        cagr = new double[stats.size()];
        drawdown = new double[stats.size()];
      }
      for (int i = 0; i < cagr.length; ++i) {
        CumulativeStats cstats = stats.get(i);
        cagr[i] = cstats.cagr;
        drawdown[i] = cstats.drawdown;
      }
      ReturnStats rstatsCAGR = ReturnStats.calc(name, cagr);
      ReturnStats rstatsDrawdown = ReturnStats.calc(name, drawdown);
      results.add(new ReturnStats[] { rstatsCAGR, rstatsDrawdown });
    }

    Collections.sort(results, new Comparator<ReturnStats[]>()
    {
      @Override
      public int compare(ReturnStats[] a, ReturnStats[] b)
      {
        if (a[0].mean > b[0].mean) return -1;
        if (a[0].mean < b[0].mean) return 1;

        if (a[1].mean < b[1].mean) return -1;
        if (a[1].mean > b[1].mean) return -1;

        return 0;
      }
    });
    for (ReturnStats[] result : results) {
      System.out.printf("\n%s ----------\n", result[0].name);
      System.out.printf("     CAGR: %s\n", result[0]);
      System.out.printf(" Drawdown: %s\n", result[1]);
    }
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = DataIO.getFinancePath();
    File dir = DataIO.getOutputPath();
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    // synthesizeData(dataDir, dir);
    // runSyntheticTest(dataDir, dir);

    // runJitterTest(dataDir, dir);

    // setupVanguardData(0, dataDir, dir);
    setupShillerData(dataDir, dir, true);

    // Chart.printDecadeTable(store.get("Mom.risky"), store.get("stock"));
    // Chart.printDecadeTable(store.get("Mom.Risky/Mom.Cautious-20/80"), store.get("stock"));
    // Chart.printDecadeTable(store.get("Mom.Risky/Mom.Cautious-20/80"), store.get("80/20"));

    // genInterestRateGraph(shiller, tbills, new File(dir, "interest-rates.html"));
    // compareRebalancingMethods(shiller, dir);
    // genReturnViz(dir);
    // genReturnChart(dir);

    // List<String> candidates = new ArrayList<String>(store.getNames());
    // genDominationChart(candidates, dir);

    // genSMASweepChart(dir);
    // genMomentumSweepChart(dir);
    // genNewHighSweepChart(dir);
    // genStockBondMixSweepChart(shiller, dir);
    // genDuelViz(dir);
    // genEfficientFrontier(shiller, dir);
    // genCorrelationGraph(shiller, dir);
    // genEndBalanceCharts(shiller, dir);
    // genBeatInflationChart(dir);
    // genDrawdownChart(dir);
    genSavingsTargetChart(dir);
    // genChartsForDifficultTimePeriods(dir);
    // genFirstLastHalfResults(dir);
  }
}
