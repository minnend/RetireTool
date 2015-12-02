package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
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

  public static final String        riskyName      = "stock";
  public static final String        safeName       = "cash";
  public static final String[]      assetNames     = new String[] { riskyName, safeName };

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
    // final double[] margins = new double[] { 0.1, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0 };
    final double[] margins = new double[] { 0.25, 1.0, 2.0, 5.0 };
    final long gap = 2 * TimeLib.MS_IN_DAY;

    // Build list of configs.
    List<PredictorConfig> configs = new ArrayList<PredictorConfig>();
    for (int triggerA = 10; triggerA <= 100; triggerA += 10) {
      for (int triggerB = 0; triggerB + 10 <= triggerA; triggerB += 10) {
        for (int baseA = triggerA + 10; baseA <= 250; baseA += 10) {
          for (int baseB = 0; baseB + 20 <= baseA; baseB += 10) {
            for (int iMargin = 0; iMargin < margins.length; ++iMargin) {
              PredictorConfig config = new ConfigSMA(triggerA, triggerB, baseA, baseB, margins[iMargin], FinLib.Close,
                  gap);
              assert config.isValid();
              configs.add(config);
            }
          }
        }
      }
    }
    Collections.shuffle(configs);
    System.out.printf("Configurations: %d\n", configs.size());

    List<JitterStats> allStats = new ArrayList<JitterStats>();
    List<JitterStats> filteredStats = new ArrayList<JitterStats>();
    final int nSummaryTicks = 0;
    final int N = 50;
    long startMS = TimeLib.getTime();
    for (int iConfig = 0; iConfig < configs.size(); ++iConfig) {
      PredictorConfig config = configs.get(iConfig);
      JitterStats jitterStats = collectJitterStats(N, config, assetNames, GlobalSlippage, 1, true, nSummaryTicks);
      allStats.add(jitterStats);
      filteredStats.add(jitterStats);
      System.out.printf("%s -> %s\n", config, jitterStats);

      if (iConfig % 10 == 9 || iConfig >= configs.size() - 1) {
        JitterStats.filter(filteredStats);
        Collections.sort(filteredStats);
        long duration = TimeLib.getTime() - startMS;
        double perSec = 1000.0 * allStats.size() / duration;
        int nLeft = configs.size() - (iConfig + 1);
        System.out.printf("--- Summary (%d / %d, %s @ %.2f/s => %s left) ------ \n", filteredStats.size(),
            allStats.size(), TimeLib.formatDuration(duration), perSec,
            TimeLib.formatDuration(Math.round(1000 * nLeft / perSec)));
        for (int i = Math.max(0, filteredStats.size() - 20); i < filteredStats.size(); ++i) {
          JitterStats jstats = filteredStats.get(i);
          System.out.printf("%s <- [%s]  %s\n", jstats, jstats.config, jstats.cagr);
        }
        System.out.println();

        // Dump results to file.
        Collections.sort(allStats, Collections.reverseOrder());
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, "results-sma-jittered.txt")));
        writer.write(String.format("--- Summary (%d / %d, %s @ %.2f/s) ------ \n", allStats.size(), configs.size(),
            TimeLib.formatDuration(duration), perSec));
        for (JitterStats jstats : allStats) {
          writer.write(String.format("%s <- [%s]  %s  %s\n", jstats, jstats.config, jstats.cagr, jstats.drawdown));
        }
        writer.close();
      }
    }
  }

  public static JitterStats collectJitterStats(int N, PredictorConfig config, String[] assetNames, Slippage slippage,
      int maxDelay, boolean buyAtNextOpen, int nSummaryTick)
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
      PredictorConfig perturbed = (iter == 0 ? config : config.genPerturbed());
      Predictor predictor = perturbed.build(broker.accessObject, assetNames);

      // Run simulation.
      broker.reset();
      Sequence returns = runBrokerSim(predictor, broker, guideSeq, maxDelay, buyAtNextOpen);
      CumulativeStats cstats = CumulativeStats.calc(returns);
      cagrs[iter] = cstats.cagr;
      drawdowns[iter] = cstats.drawdown;

      if (nSummaryTick > 0 && iter % nSummaryTick == (nSummaryTick - 1)) {
        long duration = TimeLib.getTime() - startMS;
        System.out.printf("Trials: %d [%s @ %.1f/s]\n", iter + 1, TimeLib.formatDuration(duration), 1000.0 * (iter + 1)
            / duration);
      }
    }
    ReturnStats cagrStats = ReturnStats.calc("CAGR", cagrs);
    ReturnStats drawdownStats = ReturnStats.calc("Drawdown", drawdowns);
    return new JitterStats(config, cagrStats, drawdownStats);
  }

  public static void runJitterTest(File dir) throws IOException
  {
    final long gap = 2 * TimeLib.MS_IN_DAY;
    // ConfigSMA config = new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(50, 30, 80, 60, 0.25, FinLib.Close, gap);

    final long assetMap = 63412;// 254;
    final int maxDelay = 2;
    final boolean buyAtNextOpen = true;

    // Search without jitter.
    // PredictorConfig[] configsSMA = new PredictorConfig[] {
    // // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
    // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };
    // PredictorConfig config = new ConfigMulti(assetMap, configsSMA);

    // Search with jitter.
    PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
        new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap), new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
        new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };
    PredictorConfig config = new ConfigMulti(assetMap, configs);

    // PredictorConfig config = new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap);
    // System.out.println(config);

    final int nSummaryTicks = 50;
    final int N = 500;
    JitterStats jitterStats = collectJitterStats(N, config, assetNames, GlobalSlippage, maxDelay, buyAtNextOpen,
        nSummaryTicks);
    System.out.println(jitterStats);
    jitterStats.print();
  }

  public static void runMultiSweep(File dir) throws IOException
  {
    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());

    final long gap = 2 * TimeLib.MS_IN_DAY;
    // ConfigSMA[] configs = new ConfigSMA[] {
    // // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
    // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };

    PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
        new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap), new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
        new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };

    int maxCode = (1 << configs.length) - 1;
    long maxMap = (1 << (maxCode + 1)) - 1;
    long startCode = (1 << maxCode);
    System.out.printf("maxCode=%d  maxMap=%d  startCode=%d\n", maxCode, maxMap, startCode);
    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    long startTime = TimeLib.getTime();
    for (long assetMap = startCode; assetMap <= maxMap; assetMap += 2) {
      broker.reset();
      PredictorConfig config = new ConfigMulti(assetMap, configs);
      Predictor predictor = config.build(broker.accessObject, assetNames);

      Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
      // JitterStats jitterStats = collectJitterStats(0, config, assetNames, GlobalSlippage, 1, true, 50);
      returns.setName(String.format("%d", assetMap));

      CumulativeStats cstats = CumulativeStats.calc(returns);
      allStats.add(cstats);
      // System.out.println(cstats);
      ++n;
      if (n % 50 == 0) {
        long duration = TimeLib.getTime() - startTime;
        System.out.printf("%d in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration),
            1000.0 * allStats.size() / duration);
      }
    }
    long duration = TimeLib.getTime() - startTime;

    System.out.println();
    System.out.printf("Summary: %d runs in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration), 1000.0
        * allStats.size() / duration);
    CumulativeStats.filter(allStats);
    Collections.sort(allStats);
    for (CumulativeStats cstats : allStats) {
      System.out.printf("%s\n", cstats);
    }
  }

  public static void runOne(File dir) throws IOException
  {
    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq.getStartMS());

    final long gap = 2 * TimeLib.MS_IN_DAY;
    // ConfigSMA[] configs = new ConfigSMA[] { new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
    // // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap),
    // new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap), new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap),
    // new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };
    //
    // Predictor[] predictors = new SMAPredictor[configs.length];
    // for (int i = 0; i < predictors.length; ++i) {
    // predictors[i] = new SMAPredictor(configs[i], riskyName, safeName, broker.accessObject);
    // }

    // DiscreteDistribution distribution = DiscreteDistribution.makeUniform(predictors.length);
    // Predictor predictor = new MixedPredictor(predictors, distribution, broker.accessObject);
    // Predictor predictor = new MultiPredictor(predictors, 39828, riskyName, safeName, broker.accessObject);

    // PredictorConfig config = new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap);
    // Predictor predictor = config.build(broker.accessObject, assetNames);

//    final long assetMap = 63412;// 254;
//    PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
//        new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap), new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
//        new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };
//    PredictorConfig config = new ConfigMulti(assetMap, configs);
    PredictorConfig config = new ConfigConst(1);
    Predictor predictor = config.build(broker.accessObject, assetNames);

    Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
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

    runOne(dir);
    // runSweep(dir);
    // runJitterTest(dir);
    // runMultiSweep(dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
