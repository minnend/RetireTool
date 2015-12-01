package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.MixedPredictor;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.SMAPredictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.JitterStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;
import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

public class RetireTool
{
  public static final int           GRAPH_WIDTH    = 710;
  public static final int           GRAPH_HEIGHT   = 450;

  public final static SequenceStore store          = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                 iPriceSMA      = 0;
  public static int                 nMinTradeGap   = 0;
  public static double              smaMargin      = 0.0;

  public static final Slippage      GlobalSlippage = new Slippage(0.01, 0.05);
  public static final int           MaxDelay       = 0;
  public static final boolean       BuyAtNextOpen  = true;

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

  public static Sequence runBrokerSim(Predictor predictor, Broker broker, Sequence guideSeq, int maxDelay,
      boolean bBuyAtNextOpen)
  {
    final double DistributionEPS = 0.02;

    final Random rng = new Random();
    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(1000.0);

    long prevTime = guideSeq.getStartMS() - TimeLib.MS_IN_DAY;
    long lastRebalance = TimeLib.TIME_BEGIN;
    boolean bNeedRebalance = false;
    DiscreteDistribution prevDistribution = null;
    DiscreteDistribution desiredDistribution = null;
    Sequence returns = new Sequence("Returns");
    returns.addData(1.0, guideSeq.getStartMS());
    int rebalanceDelay = 0;

    Account account = broker.openAccount(Account.Type.Roth, true);
    for (int t = 0; t < T; ++t) {
      long time = guideSeq.getTimeMS(t);
      store.lock(TimeLib.TIME_BEGIN, time);
      long nextTime = (t == T - 1 ? TimeLib.toNextBusinessDay(time) : guideSeq.getTimeMS(t + 1));
      broker.setTime(time, prevTime, nextTime);
      TimeInfo timeInfo = broker.getTimeInfo();

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, "Initial Deposit");
      }

      // Handle case where we buy at the open, not the close.
      if (bBuyAtNextOpen) {
        if (bNeedRebalance && desiredDistribution != null && rebalanceDelay <= 0) {
          broker.setPriceIndex(FinLib.Open);
          account.rebalance(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
          broker.setPriceIndex(FinLib.Close);
        }
      }

      // End of day business.
      if (rebalanceDelay > 0) --rebalanceDelay;
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      desiredDistribution = predictor.selectDistribution();

      // Rebalance if desired distribution changes by more than 2% or if it's been more than a year.
      // Note: we're comparing the current request to the previous one, not to the actual
      // distribution in the account, which could change due to price movement.
      boolean bPrevRebalance = bNeedRebalance;
      bNeedRebalance = ((time - lastRebalance) / TimeLib.MS_IN_DAY > 365 || !desiredDistribution.isSimilar(
          prevDistribution, DistributionEPS));

      if (maxDelay > 0) {
        if (bNeedRebalance && !bPrevRebalance) {
          // Note: If buying at open, a one day delay is built-in.
          rebalanceDelay = rng.nextInt(maxDelay + 1);
        }
      }

      // Update account at end of the day.
      if (!bBuyAtNextOpen) {
        if (bNeedRebalance && rebalanceDelay <= 0) {
          account.rebalance(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
        }
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
          Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());
          ConfigSMA config = new ConfigSMA(i, ii, j, jj, margins[k], 0, 0L);
          Predictor predictor = new SMAPredictor(config, riskyName, safeName, broker.accessObject);

          Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
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

  public static JitterStats collectJitterStats(int N, PredictorConfig config, String[] assetNames, Slippage slippage,
      int maxDelay, boolean buyAtNextOpen)
  {
    Sequence stock = store.getMisc(assetNames[0]);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, slippage, guideSeq.getStartMS());

    double[] cagrs = new double[N];
    double[] drawdowns = new double[N];
    long startMS = TimeLib.getTime();
    for (int iter = 0; iter < N; ++iter) {
      // Build new predictor.
      PredictorConfig perturbed = config.genPerturbed();
      Predictor predictor = perturbed.build(broker.accessObject, assetNames);

      // Run simulation.
      broker.reset();
      Sequence returns = runBrokerSim(predictor, broker, guideSeq, maxDelay, buyAtNextOpen);
      CumulativeStats cstats = CumulativeStats.calc(returns);
      cagrs[iter] = cstats.cagr;
      drawdowns[iter] = cstats.drawdown;

      if (iter % 50 == 49) {
        long duration = TimeLib.getTime() - startMS;
        System.out.printf("Trials: %d [%s @ %.1f/s]\n", iter + 1, TimeLib.formatDuration(duration), 1000.0 * (iter + 1)
            / duration);
      }
    }
    ReturnStats cagrStats = ReturnStats.calc("CAGR", cagrs);
    ReturnStats drawdownStats = ReturnStats.calc("Drawdown", drawdowns);
    return new JitterStats(cagrStats, drawdownStats);
  }

  public static void runJitterTest(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";
    final String[] assetNames = new String[] { riskyName, safeName };

    final long gap = 2 * TimeLib.MS_IN_DAY;
    // ConfigSMA config = new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(50, 30, 80, 60, 0.25, FinLib.Close, gap);

    final Slippage slippage = new Slippage(0.01, 0.05);
    final long assetMap = 35476L;
    final int maxDelay = 0;
    final boolean buyAtNextOpen = true;

    PredictorConfig[] configsSMA = new PredictorConfig[] {
        // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
        new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
        new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };
    PredictorConfig config = new ConfigMulti(assetMap, configsSMA);

    System.out.println(config);

    final int N = 300;
    JitterStats jitterStats = collectJitterStats(N, config, assetNames, slippage, maxDelay, buyAtNextOpen);
    System.out.println(jitterStats);
    jitterStats.print();
  }

  public static void runMultiSweep(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());

    final long gap = 2 * TimeLib.MS_IN_DAY;
    ConfigSMA[] configs = new ConfigSMA[] {
        // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
        new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
        new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };

    Predictor[] predictors = new SMAPredictor[configs.length];
    for (int i = 0; i < predictors.length; ++i) {
      predictors[i] = new SMAPredictor(configs[i], riskyName, safeName, broker.accessObject);
    }

    int maxCode = (1 << predictors.length) - 1;
    long maxMap = (1 << (maxCode + 1)) - 1;
    long startCode = (1 << maxCode);
    System.out.printf("maxCode=%d  maxMap=%d  startCode=%d\n", maxCode, maxMap, startCode);
    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    long startTime = TimeLib.getTime();
    for (long assetMap = startCode; assetMap <= maxMap; assetMap += 2) {
      broker.reset();
      MultiPredictor predictor = new MultiPredictor(predictors, assetMap, riskyName, safeName, broker.accessObject);
      Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
      returns.setName(String.format("%d", assetMap));
      CumulativeStats cstats = CumulativeStats.calc(returns);
      allStats.add(cstats);
      // if (assetMap == 142 || assetMap == 222 || assetMap == 158)
      // System.out.println(cstats);
      ++n;
      if (n % 100 == 0) {
        long duration = TimeLib.getTime() - startTime;
        System.out.printf("%d in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration),
            1000.0 * allStats.size() / duration);
      }
    }
    long duration = TimeLib.getTime() - startTime;

    System.out.println();
    System.out.printf("Summary: %d runs in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration), 1000.0
        * allStats.size() / duration);
    FinLib.filterStrategies(allStats);
    Collections.sort(allStats);
    for (CumulativeStats cstats : allStats) {
      System.out.printf("%s\n", cstats);
    }
  }

  public static void runOne(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());

    final long gap = 2 * TimeLib.MS_IN_DAY;
    ConfigSMA[] configs = new ConfigSMA[] { new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
        // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap),
        new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap), new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap),
        new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };

    Predictor[] predictors = new SMAPredictor[configs.length];
    for (int i = 0; i < predictors.length; ++i) {
      predictors[i] = new SMAPredictor(configs[i], riskyName, safeName, broker.accessObject);
    }

    // DiscreteDistribution distribution = DiscreteDistribution.makeUniform(predictors.length);
    // Predictor predictor = new MixedPredictor(predictors, distribution, broker.accessObject);
    Predictor predictor = new MultiPredictor(predictors, 39828, riskyName, safeName, broker.accessObject);
    Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
    CumulativeStats cstats = CumulativeStats.calc(returns);
    System.out.println(cstats);
  }

  public static void runDelayJitterTest(File dir) throws IOException
  {
    final String riskyName = "stock";
    final String safeName = "cash";

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());

    final long gap = 2 * TimeLib.MS_IN_DAY;
    ConfigSMA[] configs = new ConfigSMA[] {
        // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
        new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
        new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };

    Predictor[] predictors = new SMAPredictor[configs.length];
    for (int i = 0; i < predictors.length; ++i) {
      predictors[i] = new SMAPredictor(configs[i], riskyName, safeName, broker.accessObject);
    }

    // DiscreteDistribution distribution = DiscreteDistribution.makeUniform(predictors.length);
    // Predictor predictor = new MixedPredictor(predictors, distribution, broker.accessObject);

    // final int assetMap = 39632;// 39824; // 39572; // 39828
    final int assetMap = 35476;// 36496;
    Predictor predictor = new MultiPredictor(predictors, assetMap, riskyName, safeName, broker.accessObject);

    final int N = 1000;
    double[] cagrs = new double[N];
    double[] drawdowns = new double[N];
    for (int i = 0; i < N; ++i) {
      broker.reset();
      predictor.reset();

      boolean bBuyAtNextOpen = true;
      int maxDelay = 3;
      Sequence returns = runBrokerSim(predictor, broker, guideSeq, maxDelay, bBuyAtNextOpen);
      CumulativeStats cstats = CumulativeStats.calc(returns);
      System.out.printf("%d: %s\n", i + 1, cstats);
      cagrs[i] = cstats.cagr;
      drawdowns[i] = cstats.drawdown;
    }

    ReturnStats cagrStats = ReturnStats.calc("CAGR", cagrs);
    ReturnStats drawdownStats = ReturnStats.calc("Drawdown", drawdowns);
    System.out.println(cagrStats);
    System.out.println(drawdownStats);
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance");
    File dir = new File("g:/web/");
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    setupBroker(dataDir, dir);
    // runSweep(dir);
    runJitterTest(dir);
    // runMultiSweep(dir);
    // runOne(dir);
    // runDelayJitterTest(dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
