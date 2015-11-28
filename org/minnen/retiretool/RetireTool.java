package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib.DividendMethod;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.SMAPredictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class RetireTool
{
  public static final int           GRAPH_WIDTH  = 710;
  public static final int           GRAPH_HEIGHT = 450;

  public final static SequenceStore store        = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                 iPriceSMA    = 0;
  public static int                 nMinTradeGap = 0;
  public static double              smaMargin    = 0.0;

  public static void setupBroker(File dataDir, File dir) throws IOException
  {
    Sequence stock = DataIO.loadYahooData(new File(dataDir, "^GSPC.csv"));
    Sequence shiller = DataIO.loadShillerData(new File(dataDir, "shiller.csv"));
    shiller.adjustDatesToEndOfMonth();
    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData.adjustDatesToEndOfMonth();
    tbillData = FinLib.pad(tbillData, shiller, 0.0);

    System.out.printf("S&P (Daily): [%s] -> [%s]\n", TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    System.out.printf("Shiller: [%s] -> [%s]\n", TimeLib.formatMonth(shiller.getStartMS()),
        TimeLib.formatMonth(shiller.getEndMS()));
    System.out.printf("TBills: [%s] -> [%s]\n", TimeLib.formatMonth(tbillData.getStartMS()),
        TimeLib.formatMonth(tbillData.getEndMS()));

    long commonStart = TimeLib.calcCommonStart(shiller, tbillData, stock);
    commonStart = TimeLib.toFirstOfMonth(commonStart);
    long commonEnd = TimeLib.calcCommonEnd(shiller, tbillData, stock);
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    stock = stock.subseq(commonStart, commonEnd);
    shiller = shiller.subseq(commonStart, commonEnd);
    tbillData = tbillData.subseq(commonStart, commonEnd);

    store.addMisc(stock, "Stock");
    store.addMisc(shiller, "Shiller");
    store.addMisc(tbillData, "TBillData");
    store.alias("interest-rates", "TBillData");

    // {
    // for (int i = 1; i <= 12; ++i) {
    // long ta = TimeLib.getTime(1, i, 1951);
    // long tb = TimeLib.toEndOfMonth(ta);
    // Sequence seq = stockAll.subseq(ta, tb, EndpointBehavior.Inside);
    // double mean = seq.average(0, -1).get(0);
    // System.out.printf("[%s] -> [%s]: %.2f\n", TimeLib.formatDate(ta), TimeLib.formatDate(tb), mean);
    // }
    // }

    // Monthly S&P dividends.
    Sequence divPayments = Shiller.getDividendPayments(shiller, DividendMethod.QUARTERLY);
    store.addMisc(divPayments, "Stock-Dividends");

    // Add CPI data.
    store.addMisc(Shiller.getData(Shiller.CPI, "cpi", shiller));
    store.alias("inflation", "cpi");

    System.out.printf("#Store: %d  #Misc: %d\n", store.getNumReturns(), store.getNumMisc());
  }

  public static Sequence runBrokerSim(Predictor predictor, Broker broker, Sequence guideSeq)
  {
    final String riskyName = "Stock";
    final String safeName = "Cash";

    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(1000.0);
    boolean bPrevOwnRisky = false;
    long prevTime = guideSeq.getStartMS() - TimeLib.MS_IN_DAY;
    Sequence returns = new Sequence("Returns");
    Account account = broker.openAccount(Account.Type.Roth, true);
    for (int t = 0; t < T; ++t) {
      long time = guideSeq.getTimeMS(t);
      store.lock(TimeLib.TIME_BEGIN, time);
      long nextTime = (t == T - 1 ? TimeLib.toNextBusinessDay(time) : guideSeq.getTimeMS(t + 1));
      broker.setTime(time, prevTime, nextTime);
      TimeInfo timeInfo = broker.getTimeInfo();

      // TODO support jitter for trade day.

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, "Initial Deposit");
        returns.addData(1.0, time);
      }

      // End of day business.
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      String assetToOwn = predictor.selectAsset();
      assert assetToOwn.equals(riskyName) || assetToOwn.equals(safeName);
      boolean bOwnRisky = (assetToOwn.equals(riskyName));
      if (bOwnRisky != bPrevOwnRisky) {
        bPrevOwnRisky = bOwnRisky;

        Map<String, Double> desiredDistribution = new TreeMap<>();
        double fractionRisky = (bOwnRisky ? 1.0 : 0.0);
        double fractionSafe = 1.0 - fractionRisky;
        desiredDistribution.put(riskyName, fractionRisky);
        desiredDistribution.put(safeName, fractionSafe);

        // System.out.printf("Stock: %.1f%%  Cash: %.1f%%\n", 100.0 * fractionRisky, 100.0 * fractionSafe);
        account.rebalance(desiredDistribution);
      }

      store.unlock();
      if (timeInfo.isLastDayOfMonth) {
        returns.addData(Fixed.toFloat(Fixed.div(account.getValue(), principal)), time);
      }
      prevTime = time;
    }

    return returns;
  }
  
  public static void runSweep(File dir) throws IOException
  {
    final String riskyName = "Stock";
    final String safeName = "Cash";

    final double[] margins = new double[] { 0.1, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0 };

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);

    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    for (int i = 5; i <= 60; i += 5) {
      for (int ii = 0; ii < i; ii += 5) {
        for (int j = 10; j <= 250; j += 10) {
          if (j <= i) {
            continue;
          }
          int nn = 0;
          long startMS = TimeLib.getTime();
          for (int jj = 0; jj < j; jj += 10) {
            for (int k = 0; k < margins.length; ++k) {
              Broker broker = new Broker(store, guideSeq.getStartMS());
              ConfigSMA config = new ConfigSMA(i, ii, j, jj, margins[k], 0, 0L);
              Predictor predictor = new SMAPredictor(config, riskyName, safeName, broker.accessObject);

              Sequence returns = runBrokerSim(predictor, broker, guideSeq);
              returns.setName(config.toString());
              CumulativeStats cstats = CumulativeStats.calc(returns);
              allStats.add(cstats);
              ++n;
              ++nn;
              // System.out.printf("%s = %s\n", config, cstats);
            }
          }
          FinLib.filterStrategies(allStats);
          Collections.sort(allStats);
          long duration = TimeLib.getTime() - startMS;
          System.out.printf("--- Summary (%d / %d @ %.1f/s) ------ \n", allStats.size(), n, 1000.0 * nn / duration);
          for (CumulativeStats cstats : allStats) {
            System.out.printf("%s\n", cstats.toRowString());
          }
        }
      }
    }
  }

  public static void runJitterTest(File dir) throws IOException
  {
    final String riskyName = "Stock";
    final String safeName = "Cash";

    final double[] margins = new double[] { 0.1, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0 };

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);

    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    for (int i = 5; i <= 60; i += 5) {
      for (int ii = 0; ii < i; ii += 5) {
        for (int j = 10; j <= 250; j += 10) {
          if (j <= i) {
            continue;
          }
          int nn = 0;
          long startMS = TimeLib.getTime();
          for (int jj = 0; jj < j; jj += 10) {
            for (int k = 0; k < margins.length; ++k) {
              Broker broker = new Broker(store, guideSeq.getStartMS());
              ConfigSMA config = new ConfigSMA(i, ii, j, jj, margins[k], 0, 0L);
              Predictor predictor = new SMAPredictor(config, riskyName, safeName, broker.accessObject);

              Sequence returns = runBrokerSim(predictor, broker, guideSeq);
              returns.setName(config.toString());
              CumulativeStats cstats = CumulativeStats.calc(returns);
              allStats.add(cstats);
              ++n;
              ++nn;
              // System.out.printf("%s = %s\n", config, cstats);
            }
          }
          FinLib.filterStrategies(allStats);
          Collections.sort(allStats);
          long duration = TimeLib.getTime() - startMS;
          System.out.printf("--- Summary (%d / %d @ %.1f/s) ------ \n", allStats.size(), n, 1000.0 * nn / duration);
          for (CumulativeStats cstats : allStats) {
            System.out.printf("%s\n", cstats.toRowString());
          }
        }
      }
    }
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance");
    File dir = new File("g:/web/");
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    setupBroker(dataDir, dir);
    runSweep(dir);
    //runJitterTest(dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
