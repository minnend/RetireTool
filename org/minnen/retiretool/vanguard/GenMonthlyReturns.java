package org.minnen.retiretool.vanguard;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class GenMonthlyReturns
{
  public static final SequenceStore             store        = new SequenceStore();

  public static final VanguardFund.FundSet      fundSet      = VanguardFund.FundSet.All;
  public static final Slippage                  slippage     = Slippage.None;
  public static final String[]                  fundSymbols  = VanguardFund.getFundNames(fundSet);
  public static final String[]                  assetSymbols = new String[fundSymbols.length + 1];
  public static final Map<String, VanguardFund> funds        = VanguardFund.getFundMap(fundSet);
  public static final String[]                  statNames    = new String[] { "CAGR", "MaxDrawdown", "Worst Period",
      "10th Percentile", "Median "                          };

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    File yahooDir = new File(dataDir, "yahoo/");
    if (!yahooDir.exists()) yahooDir.mkdirs();

    // Load CPI data.
    Sequence cpi = DataIO.loadDateValueCSV(new File(dataDir, "cpi.csv"));

    // Make sure we have the latest data.
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      DataIO.updateDailyDataFromYahoo(file, symbol, 8 * TimeLib.MS_IN_HOUR);
    }

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      Sequence seq = DataIO.loadYahooData(file);
      System.out.printf("%5s [%s] -> [%s]  %s\n", symbol, TimeLib.formatDate2(seq.getStartMS()),
          TimeLib.formatDate2(seq.getEndMS()), funds.get(symbol).description);
      seqs.add(seq);
    }

    // Find common start / end time across all funds.
    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    // Adjust start time to earliest end-of-month.
    LocalDate dateStart = TimeLib.ms2time(commonStart).toLocalDate();
    if (!TimeLib.toLastBusinessDayOfMonth(dateStart).equals(dateStart)) {
      dateStart = TimeLib.toLastBusinessDayOfMonth(dateStart);
      commonStart = TimeLib.toMs(dateStart);
      System.out.printf("New Start Time: [%s]\n", TimeLib.formatDate(commonStart));
    }

    // Adjust end time to latest end-of-month.
    LocalDate dateEnd = TimeLib.ms2time(commonEnd).toLocalDate();
    if (!TimeLib.toLastBusinessDayOfMonth(dateEnd).equals(dateEnd)) {
      dateEnd = TimeLib.toFirstBusinessDayOfMonth(dateEnd.minusMonths(1));
      commonEnd = TimeLib.toMs(dateEnd);
      System.out.printf("New End Time: [%s]\n", TimeLib.formatDate(commonEnd));
    }
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Monthly Data Range: [%s] -> [%s]  (%.1f months)\n", TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd), TimeLib.monthsBetween(commonStart, commonEnd));

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    // Calculate monthly expense ratio for each fund.
    Map<String, Double> monthlyER = new HashMap<>();
    for (String symbol : fundSymbols) {
      double annualER = funds.get(symbol).expenseRatio;
      double annualMul = FinLib.ret2mul(-annualER);
      double monthlyMul = Math.pow(annualMul, 1.0 / 12.0);
      double monthlyRate = -FinLib.mul2ret(monthlyMul);
      monthlyER.put(symbol, monthlyRate);
    }

    // Calculate monthly returns for each fund.
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputDir, "vanguard-monthly.csv")))) {
      // Write header.
      List<String> tokens = new ArrayList<>();
      tokens.add("Date");
      for (String symbol : fundSymbols) {
        tokens.add(symbol);
      }
      writer.write(String.join(",", tokens) + "\n");

      // Write monthly returns.
      PriceModel priceModel = PriceModel.adjCloseModel;
      long time = commonStart;
      while (time < commonEnd) {
        LocalDate nextDate = TimeLib.toLastBusinessDayOfMonth(TimeLib.ms2date(time).plusMonths(1));
        long nextTime = TimeLib.toMs(nextDate);
        // System.out.printf("[%s] -> [%s]\n", TimeLib.formatDate(time), TimeLib.formatDate(nextTime));

        // Calculate inflation for the current month.
        int i = cpi.getIndexAtOrBefore(nextTime);
        assert nextDate.getMonth() == TimeLib.ms2date(cpi.getTimeMS(i)).getMonth();
        double mcpi = cpi.get(i, 0) / cpi.get(i - 1, 0);
        double inflation = FinLib.mul2ret(mcpi);

        tokens.clear();
        tokens.add(String.format("%d-%02d", nextDate.getYear(), nextDate.getMonthValue()));
        for (String symbol : fundSymbols) {
          Sequence seq = store.get(symbol);
          int iStart = seq.getClosestIndex(time);
          int iEnd = seq.getClosestIndex(nextTime);
          assert seq.getTimeMS(iStart) == time;
          assert seq.getTimeMS(iEnd) == nextTime;
          double startPrice = priceModel.getPrice(seq.get(iStart));
          double endPrice = priceModel.getPrice(seq.get(iEnd));

          // Calculate monthly return minus expenses.
          double r = FinLib.mul2ret(endPrice / startPrice) - monthlyER.get(symbol) - inflation;
          tokens.add(String.format("%.4f", r));
        }
        writer.write(String.join(",", tokens) + "\n");
        time = nextTime;
      }
    }
  }
}
