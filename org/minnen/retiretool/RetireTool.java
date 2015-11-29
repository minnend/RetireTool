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
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
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

    store.addMisc(stock, "stock");
    store.addMisc(shiller, "shiller");
    store.addMisc(tbillData, "tbilldata");
    store.alias("interest-rates", "tbilldata");

    // Monthly S&P dividends.
    Sequence divPayments = Shiller.getDividendPayments(shiller, DividendMethod.QUARTERLY);
    store.addMisc(divPayments, "stock-dividends");

    // Add CPI data.
    store.addMisc(Shiller.getData(Shiller.CPI, "cpi", shiller));
    store.alias("inflation", "cpi");

    // Add integral sequence for stock data.
    Sequence stockIntegral = stock.getIntegralSeq();
    store.addMisc(stockIntegral);

    // System.out.printf("#Store: %d  #Misc: %d\n", store.getNumReturns(), store.getNumMisc());
    // for (String name : store.getMiscNames()) {
    // System.out.printf(" - %s\n", name);
    // }
  }

  public static Sequence runBrokerSim(Predictor predictor, Broker broker, Sequence guideSeq)
  {
    // TODO shouldn't need to know asset names here!
    final String riskyName = "stock";
    final String safeName = "cash";

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
    final String riskyName = "stock";
    final String safeName = "cash";

    final double[] margins = new double[] { 0.1, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0 };

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);

    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    long startMS = TimeLib.getTime();
    for (int i = 5; i <= 100; i += 5) {
      // for (int ii = 0; ii + 10 <= i; ii += 5) {
      int ii = 0;
      for (int j = 10; j <= 300; j += 5) {
        if (j <= i) {
          continue;
        }
        // for (int jj = 0; jj + 20 <= j; jj += 10) {
        int jj = 0;
        for (int k = 0; k < margins.length; ++k) {
          Broker broker = new Broker(store, guideSeq.getStartMS());
          ConfigSMA config = new ConfigSMA(i, ii, j, jj, margins[k], 0, 0L);
          Predictor predictor = new SMAPredictor(config, riskyName, safeName, broker.accessObject);

          Sequence returns = runBrokerSim(predictor, broker, guideSeq);
          returns.setName(config.toString());
          CumulativeStats cstats = CumulativeStats.calc(returns);
          allStats.add(cstats);
          ++n;
          // System.out.printf("%s = %s\n", config, cstats);
        }
      }
      FinLib.filterStrategies(allStats);
      Collections.sort(allStats);
      long duration = TimeLib.getTime() - startMS;
      System.out.printf("--- Summary (%d / %d, %s @ %.1f/s) ------ \n", allStats.size(), n,
          TimeLib.formatDuration(duration), 1000.0 * n / duration);
      for (CumulativeStats cstats : allStats) {
        System.out.printf("%s\n", cstats.toRowString());
      }
    }
    // System.out.printf("n=%d\n", n);
  }

  public static void runJitterTest(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";

    final long gap = 2 * TimeLib.MS_IN_DAY;
    // ConfigSMA config = new ConfigSMA(55, 30, 80, 70, 0.1, 0, gap);
    // ConfigSMA config = new ConfigSMA(60, 0, 70, 10, 1.0, 0, gap);
    ConfigSMA config = new ConfigSMA(50, 30, 80, 60, 0.25, 0, gap);

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);

    Sequence results = new Sequence();

    long startMS = TimeLib.getTime();
    for (int da = -2; da <= 2; da += 2) {
      for (int db = -2; db <= 2; db += 2) {
        for (int dc = -2; dc <= 2; dc += 2) {
          for (int dd = -2; dd <= 2; dd += 2) {
            for (int dm = -2; dm <= 2; dm += 2) {
              Broker broker = new Broker(store, guideSeq.getStartMS());
              ConfigSMA jitteredConfig = new ConfigSMA(config.nLookbackTriggerA + da, config.nLookbackTriggerB + db,
                  config.nLookbackBaseA + dc, config.nLookbackBaseB + dd, config.margin * (1.0 + dm / 10.0),
                  config.iPrice, config.minTimeBetweenFlips);
              if (!jitteredConfig.isValid()) {
                continue;
              }
              Predictor predictor = new SMAPredictor(jitteredConfig, riskyName, safeName, broker.accessObject);

              Sequence returns = runBrokerSim(predictor, broker, guideSeq);
              returns.setName(jitteredConfig.toString());
              CumulativeStats cstats = CumulativeStats.calc(returns);
              results.addData(new FeatureVec(2, cstats.cagr, cstats.drawdown));
              // System.out.println(cstats);
            }
          }
        }
      }
    }

    ReturnStats cagrStats = ReturnStats.calc("CAGR", results.extractDim(0));
    ReturnStats drawdownStats = ReturnStats.calc("Drawdown", results.extractDim(1));

    long duration = TimeLib.getTime() - startMS;
    System.out.printf("Trials: %d  (%s, %.1f/s)\n", results.size(), TimeLib.formatDuration(duration),
        1000.0 * results.size() / duration);
    System.out.println(cagrStats);
    System.out.println(drawdownStats);
  }

  public static void runOne(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";

    ConfigSMA config = new ConfigSMA(55, 30, 80, 70, 0.1, 0, 0L);
    // ConfigSMA config = new ConfigSMA(50, 0, 200, 0, 0.5, 0, 0L);

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, guideSeq.getStartMS());
    Predictor predictor = new SMAPredictor(config, riskyName, safeName, broker.accessObject);

    Sequence returns = runBrokerSim(predictor, broker, guideSeq);
    returns.setName(config.toString());
    CumulativeStats cstats = CumulativeStats.calc(returns);
    System.out.println(cstats);
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance");
    File dir = new File("g:/web/");
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    setupBroker(dataDir, dir);
    runSweep(dir);
    // runJitterTest(dir);
    // runOne(dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
