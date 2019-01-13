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

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.data.tiingo.Tiingo;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;

public class GenMonthlyReturns
{
  public static final SequenceStore             store       = new SequenceStore();
  // public static final String[] fundSymbols = VanguardFund.getAllFunds();
  public static final String[]                  fundSymbols = VanguardFund.getOldFunds();
  public static final Map<String, VanguardFund> funds       = VanguardFund.fundMap;
  public static final String[]                  statNames   = new String[] { "CAGR", "MaxDrawdown", "Worst Period",
      "10th Percentile", "Median " };

  public static void main(String[] args) throws IOException
  {
    // Load CPI data (https://fred.stlouisfed.org/series/CPIAUCSL).
    FredSeries fredCPI = FredSeries.fromName("cpi");
    System.out.println(fredCPI);
    Sequence cpi = fredCPI.data;

    // Make sure we have the latest data.
    Simulation sim = Tiingo.setupSimulation(fundSymbols, Slippage.None, null, store, cpi);

    // Adjust start time to earliest end-of-month.
    long commonStart = sim.getStartMS();
    LocalDate dateStart = TimeLib.ms2time(commonStart).toLocalDate();
    if (!TimeLib.toLastBusinessDayOfMonth(dateStart).equals(dateStart)) {
      dateStart = TimeLib.toLastBusinessDayOfMonth(dateStart);
      commonStart = TimeLib.toMs(dateStart);
      System.out.printf("New Start Time: [%s]\n", TimeLib.formatDate(commonStart));
    }

    // Adjust end time to latest end-of-month.
    long commonEnd = sim.getEndMS();
    LocalDate dateEnd = TimeLib.ms2time(commonEnd).toLocalDate();
    if (!TimeLib.toLastBusinessDayOfMonth(dateEnd).equals(dateEnd)) {
      dateEnd = TimeLib.toLastBusinessDayOfMonth(dateEnd.minusMonths(1));
      commonEnd = TimeLib.toMs(dateEnd);
      System.out.printf("New End Time: [%s]\n", TimeLib.formatDate(commonEnd));
    }
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Monthly Data Range: [%s] -> [%s]  (%.1f months)\n", TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd), TimeLib.monthsBetween(commonStart, commonEnd));

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (Sequence seq : store.getSeqs()) {
      assert (seq.getStartMS() == commonStart);
      assert (seq.getEndMS() == commonEnd);
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
    File file = new File(DataIO.getFinancePath(), "vanguard-monthly.csv");
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
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
