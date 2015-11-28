package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.FinLib.DividendMethod;
import org.minnen.retiretool.predictor.daily.SMAPredictor;
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

  public static void runBroker()
  {
    final String riskyName = "Stock";
    final String safeName = "Cash";

    SMAPredictor smaDaily = null;

    Sequence stock = store.getMisc(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Broker broker = new Broker(store, guideSeq.getStartMS());
    Account account = broker.openAccount(Account.Type.Roth, true);
    smaDaily = new SMAPredictor(50, 200, 0.1, riskyName, safeName, broker.accessObject);

    final int T = guideSeq.length();
    final long principal = Fixed.toFixed(1000.0);
    int nFlips = 0;
    boolean bPrevOwnRisky = false;
    long timeLastFlip = TimeLib.TIME_ERROR;
    long prevTime = stock.getTimeMS(iStart - 1);
    Sequence returns = new Sequence("Returns");
    for (int t = 0; t < T; ++t) {
      long time = guideSeq.getTimeMS(t);
      // if (time > TimeLib.getTime(10, 9, 1952)) { // TODO for debug
      // System.exit(0);
      // }
      store.lock(TimeLib.TIME_BEGIN, time);
      long nextTime = (t == T - 1 ? TimeLib.toNextBusinessDay(time) : guideSeq.getTimeMS(t + 1));
      broker.setTime(time, prevTime, nextTime);
      TimeInfo timeInfo = broker.getTimeInfo();
      // System.out.printf("Broker: [%s]\n", TimeLib.formatDate(broker.getTime()));

      // TODO support jitter for trade day.

      // Handle initialization issues at t==0.
      if (t == 0) {
        account.deposit(principal, "Initial Deposit");
        // account.buyValue("Stock", account.getCash(), null);
        returns.addData(1.0, time);
      }

      // End of day business.
      broker.doEndOfDayBusiness();

      // Time for a prediction and possible asset change.
      boolean bOwnRisky = smaDaily.predict();
      if (bOwnRisky != bPrevOwnRisky) {
        bPrevOwnRisky = bOwnRisky;
        ++nFlips;
        System.out.printf("Flip [%s]: %d days\n", TimeLib.formatDate(time), (time - timeLastFlip) / TimeLib.MS_IN_DAY);
        timeLastFlip = time;

        Map<String, Double> desiredDistribution = new TreeMap<>();
        double fractionRisky = (bOwnRisky ? 1.0 : 0.0);
        double fractionSafe = 1.0 - fractionRisky;
        desiredDistribution.put(riskyName, fractionRisky);
        desiredDistribution.put(safeName, fractionSafe);

        // System.out.printf("Stock: %.1f%%  Cash: %.1f%%\n", 100.0 * fractionRisky, 100.0 * fractionSafe);
        account.rebalance(desiredDistribution);
      }

      // if (timeInfo.isLastDayOfMonth && time < TimeLib.getTime(2, 1, 1952)) {
      // System.out.printf("[%s]: %.2f + %.2f = %.2f\n", TimeLib.formatDate(time),
      // Fixed.toFloat(account.getValue() - account.getCash()), Fixed.toFloat(account.getCash()),
      // Fixed.toFloat(account.getValue()));
      // }
      //
      // account.printTransactions(time, TimeLib.getTime(2, 10, 1951));
      // if (bMonthlyPrediction) {
      // account.printPositions();
      // System.out.printf("[%s] $%s\n", TimeLib.formatMonth(time), Fixed.formatCurrency(account.getValue()));
      // }

      // int month = Library.calFromTime(time).get(Calendar.MONTH);
      // if (month != prevMonth) {
      // prevMonth = month;
      //
      // double value = account.getValue();
      // double tr = value / principal;
      // double nMonths = Library.monthsBetween(startSim, time);
      // double ar = FinLib.getAnnualReturn(tr, nMonths);
      // System.out.printf("[%s]: $%s (%.3f%%)\n", TimeLib.formatDate(time), FinLib.currencyFormatter.format(value), ar,
      // nMonths);
      // }

      store.unlock();
      if (timeInfo.isLastDayOfMonth) {
        returns.addData(Fixed.toFloat(Fixed.div(account.getValue(), principal)), time);
      }
      prevTime = time;
    }

    // account.printBuySell();

    long value = account.getValue();
    long tr = Fixed.div(value, principal);
    // System.out.printf("%.3f vs %.3f\n", Fixed.toFloat(tr), returns.getLast(0));
    // System.out.printf("[%s] vs. [%s]\n", TimeLib.formatDate(broker.getTime()),
    // TimeLib.formatDate(returns.getEndMS()));
    int nMonths = TimeLib.monthsBetween(returns.getStartMS(), returns.getEndMS());
    // System.out.printf("Returns #Months: %d\n", nMonths);
    double ar = FinLib.getAnnualReturn(Fixed.toFloat(tr), nMonths);
    System.out.printf("%11s| $%s (%.2f%%)\n", TimeLib.formatDate(returns.getEndMS()), Fixed.formatCurrency(value), ar);
    System.out.printf("#Flips: %d\n", nFlips);

    // account.printTransactions();
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance");
    File dir = new File("g:/web/");
    assert dataDir.isDirectory();
    assert dir.isDirectory();

    setupBroker(dataDir, dir);
    runBroker();

    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.VANGUARD_INVESTOR_FUNDS);
    // DataIO.downloadDailyDataFromYahoo(dataDir, FinLib.STOCK_MARKET_FUNDS);
  }
}
