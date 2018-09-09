package org.minnen.retiretool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.JitterStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.LinearFunc;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;
import org.minnen.retiretool.broker.Account;
import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.broker.transactions.Transaction.Flow;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Shiller;
import org.minnen.retiretool.data.YahooIO;

public class RetireTool
{
  public static final int           GRAPH_WIDTH    = 710;
  public static final int           GRAPH_HEIGHT   = 450;

  public static final SequenceStore store          = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                 iPriceSMA      = 0;
  public static int                 nMinTradeGap   = 0;
  public static double              smaMargin      = 0.0;

  public static final Slippage      GlobalSlippage = new Slippage(0.01, 0.05);
  public static final LinearFunc    PriceSDev      = LinearFunc.Zero;
  // public static final LinearFunc PriceSDev = new LinearFunc(0.004, 0.01);
  public static final int           MaxDelay       = 1;
  public static final boolean       BuyAtNextOpen  = true;
  public static final long          gap            = 2 * TimeLib.MS_IN_DAY;

  public static final String        riskyName      = "stock";
  public static final String        safeName       = "cash";
  public static final String[]      assetNames     = new String[] { riskyName, safeName };

  public static void setupBroker(File dataDir, File dir) throws IOException
  {
    File file = YahooIO.downloadDailyData("^GSPC", 8 * TimeLib.MS_IN_HOUR);
    Sequence stock = YahooIO.loadData(file);
    Sequence bondRate = DataIO.loadDateValueCSV(new File(dataDir, "treasury-10year-daily.csv"));
    Sequence shiller = Shiller.loadAll(new File(dataDir, "shiller.csv"));
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
    System.out.printf("Notes: [%s] -> [%s]\n", TimeLib.formatMonth(bondRate.getStartMS()),
        TimeLib.formatMonth(bondRate.getEndMS()));

    long commonStart = TimeLib.calcCommonStart(shiller, tbillData, stock, bondRate);
    commonStart = TimeLib.toMs(TimeLib.ms2date(commonStart).with(TemporalAdjusters.firstDayOfMonth()));
    long commonEnd = TimeLib.calcCommonEnd(shiller, tbillData, stock, bondRate);
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    stock = stock.subseq(commonStart, commonEnd);
    shiller = shiller.subseq(commonStart, commonEnd);
    tbillData = tbillData.subseq(commonStart, commonEnd);

    store.add(stock, "stock");
    store.add(shiller, "shiller");
    store.add(tbillData, "tbilldata");
    store.alias("interest-rates", "tbilldata");

    // Monthly S&P dividends.
    Sequence divPayments = ShillerOld.getDividendPayments(shiller, DividendMethod.QUARTERLY);
    store.add(divPayments, "stock-dividends");

    // Add CPI data.
    store.add(ShillerOld.getData(ShillerOld.CPI, "cpi", shiller));
    store.alias("inflation", "cpi");

    // Daily bond data.
    // TODO

    // Add integral sequence for stock data.
    Sequence stockIntegral = stock.getIntegralSeq();
    store.add(stockIntegral);

    // System.out.printf("#Store: %d #Misc: %d\n", store.getNumReturns(), store.getNumMisc());
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

    Account account = broker.openAccount("SimAccount", Account.Type.Roth, true);
    for (int t = 0; t < T; ++t) {
      long time = guideSeq.getTimeMS(t);
      final long key = Sequence.Lock.genKey();
      store.lock(TimeLib.TIME_BEGIN, time, key);
      long nextTime = (t == T - 1 ? TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(time)))
          : guideSeq.getTimeMS(t + 1));
      broker.setNewDay(new TimeInfo(time, prevTime, nextTime));
      TimeInfo timeInfo = broker.getTimeInfo();

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, Flow.InFlow, "Initial Deposit");
      }

      // Handle case where we buy at the open, not the close.
      if (bBuyAtNextOpen) {
        if (bNeedRebalance && desiredDistribution != null && rebalanceDelay <= 0) {
          broker.setQuoteModel(PriceModel.openModel);
          account.updatePositions(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
          broker.setQuoteModel(PriceModel.closeModel);
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
      bNeedRebalance = ((time - lastRebalance) / TimeLib.MS_IN_DAY > 365
          || !desiredDistribution.isSimilar(prevDistribution, DistributionEPS));

      if (maxDelay > 0) {
        if (bNeedRebalance && !bPrevRebalance) {
          // Note: If buying at open, a one day delay is built-in.
          rebalanceDelay = rng.nextInt(maxDelay + 1);
        }
      }

      // Update account at end of the day.
      if (!bBuyAtNextOpen) {
        if (bNeedRebalance && rebalanceDelay <= 0) {
          account.updatePositions(desiredDistribution);
          lastRebalance = time;
          if (prevDistribution == null) {
            prevDistribution = new DiscreteDistribution(desiredDistribution);
          } else {
            prevDistribution.copyFrom(desiredDistribution);
          }
        }
      }

      store.unlock(key);
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
      JitterStats jitterStats = collectJitterStats(N, config, assetNames, GlobalSlippage, PriceSDev, MaxDelay,
          BuyAtNextOpen, nSummaryTicks);
      allStats.add(jitterStats);
      filteredStats.add(jitterStats);
      System.out.printf("%s -> %s\n", config, jitterStats);

      if (iConfig % 10 == 9 || iConfig >= configs.size() - 1) {
        Collections.sort(filteredStats, Collections.reverseOrder());
        JitterStats.filter(filteredStats);
        long duration = TimeLib.getTime() - startMS;
        double perSec = 1000.0 * allStats.size() / duration;
        int nLeft = configs.size() - (iConfig + 1);
        System.out.printf("--- Summary (%d / %d, %s @ %.2f/s => %s left) ------ \n", filteredStats.size(),
            allStats.size(), TimeLib.formatDuration(duration), perSec,
            TimeLib.formatDuration(Math.round(1000 * nLeft / perSec)));
        for (int i = Math.min(19, filteredStats.size() - 1); i >= 0; --i) {
          JitterStats jstats = filteredStats.get(i);
          System.out.printf("%s <- [%s]  %s\n", jstats, jstats.config, jstats.cagrStats);
        }
        System.out.println();

        // Dump results to file.
        Collections.sort(allStats, Collections.reverseOrder());
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, "results-sma-jittered.txt")));
        writer.write(String.format("--- Summary (%d / %d, %s @ %.2f/s) ------ \n", allStats.size(), configs.size(),
            TimeLib.formatDuration(duration), perSec));
        for (JitterStats jstats : allStats) {
          writer.write(
              String.format("%s <- [%s]  %s  %s\n", jstats, jstats.config, jstats.cagrStats, jstats.drawdownStats));
        }
        writer.close();
      }
    }
  }

  public static JitterStats collectJitterStats(int N, PredictorConfig config, String[] assetNames, Slippage slippage,
      LinearFunc priceSDev, int maxDelay, boolean buyAtNextOpen, int nSummaryTick)
  {
    Sequence stock = store.get(assetNames[0]);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, slippage, guideSeq);

    double[] cagrs = new double[N];
    double[] drawdowns = new double[N];
    long startMS = TimeLib.getTime();
    for (int iter = 0; iter < N; ++iter) {
      // Build new predictor.
      PredictorConfig perturbed = (iter == 0 ? config : config.genPerturbed());
      Predictor predictor = perturbed.build(broker.accessObject, assetNames);

      // Generate noisy price data if requested.
      if (priceSDev != LinearFunc.Zero) {
        for (String name : assetNames) {
          if (name.equals("cash")) continue;
          store.genNoisy(name, priceSDev, iPriceSMA);
        }
      }

      // Run simulation.
      broker.reset();
      Sequence returns = runBrokerSim(predictor, broker, guideSeq, maxDelay, buyAtNextOpen);
      CumulativeStats cstats = CumulativeStats.calc(returns);
      cagrs[iter] = cstats.cagr;
      drawdowns[iter] = cstats.drawdown;

      // Remove noisy price data.
      if (priceSDev != LinearFunc.Zero) {
        for (String name : assetNames) {
          if (name.equals("cash")) continue;
          store.removeNoisy(name);
        }
      }

      if (nSummaryTick > 0 && iter % nSummaryTick == (nSummaryTick - 1)) {
        long duration = TimeLib.getTime() - startMS;
        System.out.printf("Trials: %d [%s @ %.1f/s]\n", iter + 1, TimeLib.formatDuration(duration),
            1000.0 * (iter + 1) / duration);
      }
    }
    ReturnStats cagrStats = ReturnStats.calc("CAGR", cagrs);
    ReturnStats drawdownStats = ReturnStats.calc("Drawdown", drawdowns);
    return new JitterStats(config, cagrStats, drawdownStats);
  }

  public static void runJitterTest(File dir) throws IOException
  {
    // ConfigSMA config = new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap);
    // ConfigSMA config = new ConfigSMA(50, 30, 80, 60, 0.25, FinLib.Close, gap);

    // final long assetMap = 63412;// 254;

    // Search without jitter.
    // PredictorConfig[] configsSMA = new PredictorConfig[] {
    // // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
    // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };
    // PredictorConfig config = new ConfigMulti(assetMap, configsSMA);

    // Search with jitter.
    // PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap), new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
    // new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };
    // PredictorConfig config = new ConfigMulti(assetMap, configs);

    // PredictorConfig[] singleConfigs = new PredictorConfig[] {
    // new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
    // new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
    // new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap) };

    // PredictorConfig[] singleConfigs = new PredictorConfig[] {
    // new ConfigSMA(30, 0, 220, 170, 3.0, FinLib.Close, gap),
    // new ConfigSMA(5, 0, 165, 5, 0.5, FinLib.Close, gap),
    // new ConfigSMA(5, 0, 145, 135, 1.0, FinLib.Close, gap) };

    PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(25, 0, 155, 125, 0.75, FinLib.Close, gap), new ConfigSMA(5, 0, 165, 5, 0.5, FinLib.Close, gap) };

    // Multi-predictor to make final decisions.
    PredictorConfig config = new ConfigMulti(254, singleConfigs);

    // PredictorConfig config = new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap);
    // System.out.println(config);

    final int nSummaryTicks = 50;
    final int N = 500;
    JitterStats jitterStats = collectJitterStats(N, config, assetNames, GlobalSlippage, PriceSDev, MaxDelay,
        BuyAtNextOpen, nSummaryTicks);
    System.out.println(jitterStats);
    jitterStats.print();
  }

  public static long findBestAssetMap(ConfigMulti config, int nJitterRuns, int maxDelay)
  {
    // TODO return all dominating assetMap values
    int maxCode = (1 << config.size()) - 1;
    long maxMap = (1 << (maxCode + 1)) - 1;
    long startCode = (1 << maxCode);
    // System.out.printf("maxCode=%d maxMap=%d startCode=%d\n", maxCode, maxMap, startCode);
    long bestAssetMap = -1;
    JitterStats bestStats = null;
    // for (long assetMap = startCode; assetMap <= maxMap; assetMap += 2) {
    {
      long assetMap = -2;
      config.assetMap = assetMap;
      JitterStats jitterStats = collectJitterStats(nJitterRuns, config, assetNames, GlobalSlippage, PriceSDev, maxDelay,
          BuyAtNextOpen, 0);
      if (bestStats == null || jitterStats.score() > bestStats.score()) {
        bestAssetMap = assetMap;
        bestStats = jitterStats;
      }

      if (jitterStats == bestStats || assetMap == maxCode) {
        System.out.printf(" %d: %s (%.3f)\n", assetMap, jitterStats, jitterStats.score());
        // bestStats == jitterStats ? " *" : "");
      }
    }
    config.assetMap = bestAssetMap;
    return bestAssetMap;
  }

  public static void runMultiSweep(File dir) throws IOException
  {
    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq);

    // ConfigSMA[] configs = new ConfigSMA[] {
    // // new ConfigSMA(5, 0, 160, 0, 0.5, FinLib.Close, gap),
    // new ConfigSMA(35, 0, 50, 10, 2.0, FinLib.Close, gap), new ConfigSMA(15, 5, 30, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(55, 30, 80, 70, 0.1, FinLib.Close, gap), new ConfigSMA(60, 0, 70, 10, 1.0, FinLib.Close, gap), };

    // PredictorConfig[] configs = new PredictorConfig[] {
    // new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap),
    // new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
    // new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };

    PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap), new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), };

    int maxCode = (1 << configs.length) - 1;
    long maxMap = (1 << (maxCode + 1)) - 1;
    long startCode = (1 << maxCode);
    System.out.printf("maxCode=%d  maxMap=%d  startCode=%d\n", maxCode, maxMap, startCode);
    List<CumulativeStats> allStats = new ArrayList<CumulativeStats>();
    int n = 0;
    // long[] maps = new long[] { 0, 128, 254, 255 };
    long startTime = TimeLib.getTime();
    // for (long assetMap : maps) {
    for (long assetMap = startCode; assetMap <= maxMap; assetMap += 2) {
      // int nBits = Library.numBits(assetMap);
      // if (nBits < 6) continue;
      broker.reset();
      PredictorConfig config = new ConfigMulti(assetMap, configs);
      Predictor predictor = config.build(broker.accessObject, assetNames);

      Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
      // JitterStats jitterStats = collectJitterStats(0, config, assetNames, GlobalSlippage, 1, true, 50);
      returns.setName(String.format("%d", assetMap));

      CumulativeStats cstats = CumulativeStats.calc(returns);
      allStats.add(cstats);
      System.out.println(cstats);
      ++n;
      if (n % 50 == 0) {
        long duration = TimeLib.getTime() - startTime;
        System.out.printf("%d in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration),
            1000.0 * allStats.size() / duration);
      }
    }
    long duration = TimeLib.getTime() - startTime;

    System.out.println();
    System.out.printf("Summary: %d runs in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration),
        1000.0 * allStats.size() / duration);
    CumulativeStats.filter(allStats);
    Collections.sort(allStats);
    for (CumulativeStats cstats : allStats) {
      System.out.printf("%s\n", cstats);
    }
  }

  public static void runMixSweep(File dir) throws IOException
  {
    PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(20, 0, 250, 50, 1.0, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
        new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap), };

    final int nSingle = singleConfigs.length;
    final int nMulti = (nSingle * (nSingle - 1)) / 2;
    System.out.printf("#single=%d  #multi=%d\n", nSingle, nMulti);
    PredictorConfig[] multiConfigs = new PredictorConfig[nMulti];

    int iMulti = 0;
    for (int i = 0; i < nSingle; ++i) {
      for (int j = i + 1; j < nSingle; ++j) {
        for (int k = j + 1; k < nSingle; ++k) {
          System.out.printf("%d: (%d,%d,%d)\n", iMulti, i, j, k);
          multiConfigs[iMulti++] = new ConfigMulti(254,
              new PredictorConfig[] { singleConfigs[i], singleConfigs[j], singleConfigs[k] });
        }
      }
    }
    assert iMulti == nMulti;

    List<JitterStats> allStats = new ArrayList<JitterStats>();
    long startTime = TimeLib.getTime();
    // for (int i = 0; i < nMulti; ++i) {
    // for (int j = i + 1; j < nMulti; ++j) {
    int i = 3, j = 4;
    {
      {
        for (int p = 0; p <= 10; ++p) {
          // if (p != 3) continue;
          double p2 = p / 10.0;
          double p1 = 1.0 - p2;
          DiscreteDistribution mix = new DiscreteDistribution(p1, p2);
          PredictorConfig config = new ConfigMixed(mix, multiConfigs[i], multiConfigs[j]);
          JitterStats stats = collectJitterStats(200, config, assetNames, GlobalSlippage, PriceSDev, MaxDelay,
              BuyAtNextOpen, 0);
          allStats.add(stats);
          System.out.printf("%d.%d [%.1f,%.1f]:  %s  (%.2f)\n", i, j, p1 * 100.0, p2 * 100.0, stats, stats.score());
        }
      }
    }
    long duration = TimeLib.getTime() - startTime;
    System.out.println();
    System.out.printf("Summary: %d runs in %s @ %.1f/s\n", allStats.size(), TimeLib.formatDuration(duration),
        1000.0 * allStats.size() / duration);
    JitterStats.filter(allStats);
    Collections.sort(allStats);
    for (JitterStats stats : allStats) {
      System.out.printf("%s (%.2f)\n", stats, stats.score());
      stats.print();
    }
  }

  public static void runOne(File dir) throws IOException
  {
    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, GlobalSlippage, guideSeq);

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

    // final long assetMap = 63412;// 254;
    // PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(10, 0, 240, 0, 2.0, FinLib.Close, gap),
    // new ConfigSMA(30, 0, 250, 40, 0.25, FinLib.Close, gap), new ConfigSMA(20, 0, 240, 130, 1.0, FinLib.Close, gap),
    // new ConfigSMA(50, 0, 180, 30, 0.25, FinLib.Close, gap), };
    // PredictorConfig config = new ConfigMulti(assetMap, configs);

    final long assetMap = 254;
    PredictorConfig[] configs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap), new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), };
    PredictorConfig config = new ConfigMulti(assetMap, configs);

    // PredictorConfig configStock = new ConfigConst(0);
    PredictorConfig configSafe = new ConfigConst(safeName);

    // Predictor predictorStock = configStock.build(broker.accessObject, assetNames);
    // Sequence stockReturns = runBrokerSim(predictorStock, broker, guideSeq, MaxDelay, BuyAtNextOpen);
    Predictor predictorSafe = configSafe.build(broker.accessObject, assetNames);
    Sequence safeReturns = runBrokerSim(predictorSafe, broker, guideSeq, MaxDelay, BuyAtNextOpen);

    Predictor predictor = config.build(broker.accessObject, assetNames);
    Sequence returns = runBrokerSim(predictor, broker, guideSeq, MaxDelay, BuyAtNextOpen);
    returns.setName(config.toString());
    double sharpe = FinLib.sharpeDaily(returns, safeReturns);
    CumulativeStats cstats = CumulativeStats.calc(returns);
    System.out.printf("%s (sharpe=%.2f, score=%.2f)\n", cstats, sharpe, cstats.scoreSimple());

    // final int code = 7;
    // List<Sequence> seqs = new ArrayList<Sequence>();
    // List<TimeCode> timeCodes = ((MultiPredictor) predictor).timeCodes;
    // for (int i = 0; i < timeCodes.size(); ++i) {
    // TimeCode timeCode = timeCodes.get(i);
    // if (timeCode.code == code) {
    // double va = Fixed.toFloat(broker.getPrice(riskyName, timeCode.time));
    // long nextTime = (i < timeCodes.size() - 1 ? timeCodes.get(i + 1).time : broker.getTime());
    // double vb = Fixed.toFloat(broker.getPrice(riskyName, nextTime));
    // System.out.printf("%d %.3f [%s] -> [%s]\n", timeCode.code, FinLib.mul2ret(vb / va),
    // TimeLib.formatDate(timeCode.time), TimeLib.formatDate(nextTime));
    //
    // Sequence seq = stock.subseq(timeCode.time, nextTime);
    // seqs.add(seq._div(seq.getFirst(0)));
    // }
    // }
    // Chart.saveLineChart(new File(dir, "code.html"), String.format("After Code %d", code), GRAPH_WIDTH, GRAPH_HEIGHT,
    // false, seqs);
  }

  public static List<ConfigSMA> loadSMAList(File file) throws IOException
  {
    Pattern patternSMA = Pattern.compile("\\[\\[(\\d+),(\\d+)\\] / \\[(\\d+),(\\d+)\\] m=([\\d\\.]+)\\]");

    List<ConfigSMA> all = new ArrayList<ConfigSMA>();
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();

      Matcher m = patternSMA.matcher(line);
      if (!m.find()) {
        System.err.printf("No SMA on line: [%s]\n", line);
        continue;
      }

      int triggerA = Integer.parseInt(m.group(1));
      int triggerB = Integer.parseInt(m.group(2));
      int baseA = Integer.parseInt(m.group(3));
      int baseB = Integer.parseInt(m.group(4));
      double margin = Double.parseDouble(m.group(5));

      ConfigSMA config = new ConfigSMA(triggerA, triggerB, baseA, baseB, margin, FinLib.Close, gap);
      all.add(config);
    }
    reader.close();
    System.out.printf("SMA Configurations: %d\n", all.size());
    return all;
  }

  public static List<JitterStats> loadResultsSMA(File file) throws IOException
  {
    Pattern patternStats = Pattern.compile("^\\[([\\.\\d]+)\\W*,\\W*([\\.\\d]+)\\]");
    Pattern patternSMA = Pattern.compile("\\[\\[(\\d+),(\\d+)\\] / \\[(\\d+),(\\d+)\\] m=([\\d\\.]+)[\\%]\\]");

    List<JitterStats> allStats = new ArrayList<JitterStats>();
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      Matcher m = patternStats.matcher(line);
      if (!m.find()) continue;

      double cagr = Double.parseDouble(m.group(1));
      double drawdown = Double.parseDouble(m.group(2));

      if (cagr < 9.0 || drawdown >= 40.0) continue;
      if (cagr < 10.0 && drawdown >= 30.0) continue;

      m = patternSMA.matcher(line);
      if (!m.find()) {
        System.err.printf("No SMA on line: [%s]\n", line);
        continue;
      }

      int triggerA = Integer.parseInt(m.group(1));
      int triggerB = Integer.parseInt(m.group(2));
      int baseA = Integer.parseInt(m.group(3));
      int baseB = Integer.parseInt(m.group(4));
      double margin = Double.parseDouble(m.group(5));

      PredictorConfig config = new ConfigSMA(triggerA, triggerB, baseA, baseB, margin, FinLib.Close, gap);
      JitterStats stats = new JitterStats(config, cagr, drawdown);
      allStats.add(stats);
    }
    reader.close();
    System.out.printf("SMA Configurations: %d\n", allStats.size());
    return allStats;
  }

  public static void searchSMA(File dataDir, File dir) throws IOException
  {
    List<ConfigSMA> allStats = loadSMAList(new File(dataDir, "sma-small.txt"));

    // Try all triplets from the dominating configs.
    List<JitterStats> bestStats = new ArrayList<JitterStats>();
    long startMS = TimeLib.getTime();
    final int nJitterRuns = 300;
    PredictorConfig[] configs = new PredictorConfig[3];
    for (int i = 0; i < allStats.size(); ++i) {
      configs[0] = allStats.get(i);
      for (int j = i + 1; j < allStats.size(); ++j) {
        configs[1] = allStats.get(j);
        for (int k = j + 1; k < allStats.size(); ++k) {
          configs[2] = allStats.get(k);

          System.out.printf("Analyze: [%d,%d,%d]\n", i, j, k);
          ConfigMulti config = new ConfigMulti(254, configs);
          // long assetMap = findBestAssetMap(config, nJitterRuns, MaxDelay);
          // assert config.assetMap == assetMap;

          JitterStats jitterStats = collectJitterStats(nJitterRuns, config, assetNames, GlobalSlippage, PriceSDev,
              MaxDelay, BuyAtNextOpen, 0);
          bestStats.add(jitterStats);

          System.out.println("--- Best So Far ---");
          Collections.sort(bestStats, Collections.reverseOrder());
          JitterStats.filter(bestStats);
          for (JitterStats stats : bestStats) {
            System.out.printf("%s (%.3f)\n", stats, stats.score());
            System.out.printf(" %s\n", stats.config);
          }
          System.out.println("-------------------");
        }
      }
    }
    long duration = TimeLib.getTime() - startMS;
    System.out.printf("Triplets: %s\n", TimeLib.formatDuration(duration));
  }

  public static void searchPredictors(File dataDir, File dir) throws IOException
  {
    List<JitterStats> allStats = loadResultsSMA(new File(dataDir, "results-sma-small.txt"));
    Collections.sort(allStats, Collections.reverseOrder());

    List<JitterStats> baseStats = new ArrayList<JitterStats>(allStats);
    JitterStats.filter(baseStats);
    for (JitterStats stats : baseStats) {
      System.out.printf("%s (%.3f) <- [%s]\n", stats, stats.score(), stats.config);
    }
    List<JitterStats> bestStats = new ArrayList<JitterStats>(baseStats);

    // Try all triplets from the dominating configs.
    List<JitterStats> triplets = new ArrayList<JitterStats>();
    long startMS = TimeLib.getTime();
    final int nJitterRuns = 50;
    PredictorConfig[] configs = new PredictorConfig[3];
    for (int i = 0; i < baseStats.size(); ++i) {
      configs[0] = baseStats.get(i).config;
      for (int j = i + 1; j < baseStats.size(); ++j) {
        configs[1] = baseStats.get(j).config;
        for (int k = j + 1; k < baseStats.size(); ++k) {
          configs[2] = baseStats.get(k).config;

          System.out.printf("Analyze: [%d,%d,%d]\n", i, j, k);
          ConfigMulti config = new ConfigMulti(254, configs);
          // long assetMap = findBestAssetMap(config, nJitterRuns, MaxDelay);
          // assert config.assetMap == assetMap;

          JitterStats jitterStats = collectJitterStats(nJitterRuns * 2, config, assetNames, GlobalSlippage, PriceSDev,
              MaxDelay, BuyAtNextOpen, 0);
          allStats.add(jitterStats);
          bestStats.add(jitterStats);
          triplets.add(jitterStats);

          System.out.println("--- Best So Far ---");
          Collections.sort(bestStats, Collections.reverseOrder());
          JitterStats.filter(bestStats);
          for (JitterStats stats : bestStats) {
            System.out.printf("%s (%.3f)\n", stats, stats.score());
            System.out.printf(" %s\n", stats.config);
          }
          System.out.println("-------------------");
        }
      }
    }
    long duration = TimeLib.getTime() - startMS;
    System.out.printf("Triplets: %s\n", TimeLib.formatDuration(duration));

    // int nSincePrint = 0;
    // for (int iTrial = 1;; ++iTrial) {
    // for (int i = 0; i < configs.length; ++i) {
    // configs[i] = ConfigSMA.genRandom(FinLib.Close, gap);
    // }
    // ConfigMulti config = new ConfigMulti(254, configs);
    // // System.out.println(config);
    // JitterStats jitterStats = collectJitterStats(nJitterRuns * 2, config, assetNames, GlobalSlippage, PriceSDev,
    // MaxDelay, BuyAtNextOpen, 0);
    // bestStats.add(jitterStats);
    // System.out.printf("%d: %s\n", iTrial, jitterStats);
    //
    // Collections.sort(bestStats, Collections.reverseOrder());
    // JitterStats.filter(bestStats);
    // ++nSincePrint;
    // if (nSincePrint >= 10 || bestStats.contains(jitterStats)) {
    // System.out.println("--- Best So Far ---");
    // for (JitterStats stats : bestStats) {
    // System.out.printf("%s (%.3f)\n", stats, stats.score());
    // System.out.printf(" %s\n", stats.config);
    // }
    // System.out.println("-------------------");
    // nSincePrint = 0;
    // }
    // }

    // for (JitterStats stats : triplets) {
    // System.out.printf("%s (%.3f)\n", stats, stats.score());
    // System.out.printf(" %s\n", stats.config);
    // }
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = DataIO.financePath;
    File dir = DataIO.outputPath;
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    setupBroker(dataDir, dir);

    // searchPredictors(dataDir, dir);
    // searchSMA(dataDir, dir);
    runOne(dir);
    // runSweep(dir);
    // runJitterTest(dir);
    // runMultiSweep(dir);
    // runMixSweep(dir);

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
