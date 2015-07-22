package org.minnen.retiretool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.minnen.retiretool.FeatureVec;
import org.minnen.retiretool.Sequence;
import org.minnen.retiretool.Library;

import org.minnen.retiretool.RetireTool.DividendMethod;
import org.minnen.retiretool.RetireTool.Inflation;

/*
 * Store Shiller's data (S&P price, dividends, CPI, GS10, CAPE).
 * Data: http://www.econ.yale.edu/~shiller/data.htm
 */
public class Shiller extends Sequence
{
  public static int PRICE = 0;
  public static int DIV   = 1;
  public static int CPI   = 2;
  public static int GS10  = 3;
  public static int CAPE  = 4;

  /**
   * Create a Shiller data object without any data.
   */
  public Shiller()
  {
    super("Shiller Financial Data");
  }

  /**
   * Create a Shiller data object and load data from the given file.
   * 
   * @param filename path to file with Shiller CSV data.
   * @throws IOException if there is a problem reading the file.
   */
  public Shiller(String filename) throws IOException
  {
    super("Shiller Financial Data");
    loadData(filename);
  }

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet.
   * 
   * @param filename name of file to load
   * @return true on success
   * @throws IOException if there is a problem reading the file.
   */
  public void loadData(String filename) throws IOException
  {
    File file = new File(filename);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read shiller file (%s)", filename));
    }
    System.out.printf("Loading data file: [%s]\n", filename);

    BufferedReader in = new BufferedReader(new FileReader(filename));
    String line;
    while ((line = in.readLine()) != null) {
      try {
        String[] toks = line.trim().split(",");
        if (toks == null || toks.length < 5) {
          continue; // want at least: date, p, d, e, cpi
        }

        // date
        double date = Double.parseDouble(toks[0]);
        int year = (int) Math.floor(date);
        int month = (int) Math.round((date - year) * 100);

        // snp price
        double price = Double.parseDouble(toks[1]);

        // snp dividend -- data is annual yield, we want monthly
        double div = Double.parseDouble(toks[2]) / 12;

        // cpi
        double cpi = Double.parseDouble(toks[4]);

        // GS10 rate
        double gs10 = Double.parseDouble(toks[6]);

        // CAPE
        double cape = Library.tryParse(toks[10], 0.0);

        Calendar cal = Library.now();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        addData(new FeatureVec(5, price, div, cpi, gs10, cape), cal.getTimeInMillis());

        // System.out.printf("%d/%d:  $%.2f  $%.2f  $%.2f\n", year,
        // month, price, div, cpi);

      } catch (NumberFormatException nfe) {
        // something went wrong so skip this line
        System.err.println("Bad Line: " + line);
        continue;
      }

    }
    in.close();
  }

  /**
   * Calculates S&P ROI for the given range.
   * 
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in snp to consider
   * @param divMethod how should we handle dividend reinvestment
   * @param inflationAccounting how should we handle inflation
   * @return sequence of ROIs
   */
  public Sequence calcSnpReturnSeq(int iStart, int nMonths, DividendMethod divMethod, Inflation inflationAccounting)
  {
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths, size()));
    }

    Sequence seq = new Sequence("S&P");

    // note: it's equivalent to keep track of total value or number of shares
    double divCash = 0.0;
    double baseValue = 1.0;
    double shares = baseValue / get(iStart, PRICE);
    seq.addData(baseValue, getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double div = 0.0;
      if (divMethod == DividendMethod.NO_REINVEST)
        divCash += shares * get(i, DIV);
      else if (divMethod == DividendMethod.MONTHLY) {
        // Dividends at the end of every month.
        div = get(i, DIV);
      } else if (divMethod == DividendMethod.QUARTERLY) {
        // Dividends at the end of every quarter (march, june, september, december).
        Calendar cal = Library.now();
        cal.setTimeInMillis(getTimeMS(i));
        int month = cal.get(Calendar.MONTH);
        if (month % 3 == 2) { // time for a dividend!
          for (int j = 0; j < 3; j++) {
            if (i - j < iStart)
              break;
            div += get(i - j, DIV);
          }
        }
      }

      // Apply the dividends (if any).
      double price = get(i + 1, PRICE);
      shares += shares * div / price;

      // Add data point for current value.
      double value = adjustValue(divCash + shares * price, i + 1, iStart, inflationAccounting);
      seq.addData(value, getTimeMS(i + 1));
    }

    return seq;
  }

  /**
   * Calculates S&P ROI for the given range.
   * 
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in snp to consider
   * @param divMethod how should we handle dividend reinvestment
   * @param inflationAccounting how should we handle inflation
   * @return ROI over given time period
   */
  public double calcSnpReturn(int iStart, int nMonths, DividendMethod divMethod, Inflation inflationAccounting)
  {
    Sequence seq = calcSnpReturnSeq(iStart, nMonths, divMethod, inflationAccounting);
    return seq.getLast(0);
  }

  /**
   * Calculate S&P returns for all periods with the given duration.
   * 
   * @param years number of years in the market
   * @param divMethod how dividends are handled
   * @param inflationAccounting should we adjust for inflation?
   * @return sequence of CAGRs for S&P all time periods of the given duration.
   */
  public Sequence calcSnpReturns(int years, DividendMethod divMethod, Inflation inflationAccounting)
  {
    Sequence rois = new Sequence(String.format("SnP ROIs - %d years", years));
    int months = years * 12;

    int n = size();
    for (int i = 0; i < n; i++) {
      if (i + months >= n)
        break; // not enough data
      double roi = calcSnpReturn(i, months, divMethod, inflationAccounting);
      double cagr = RetireTool.getAnnualReturn(roi, months);
      // System.out.printf("%.2f\t%f\n", getYearAsFrac(i), meanAnnualReturn);
      rois.addData(cagr, getTimeMS(i));
    }

    return rois;
  }

  /**
   * Calculates bond ROI for the given range using the rebuy approach.
   * 
   * Each month, the existing bond is sold and a new one is purchased.
   * 
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence
   * @param inflationAccounting how should we handle inflation
   * @return sequence of ROIs
   */
  public Sequence calcBondReturnSeqRebuy(int iStart, int iEnd, Inflation inflationAccounting)
  {
    if (iStart < 0 || iEnd < iStart || iEnd >= size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, size()));
    }

    Sequence bondData = getBondData();
    double cash = 1.0; // start with one dollar
    Sequence seq = new Sequence("Bonds (Rebuy)");
    seq.addData(cash, getTimeMS(iStart));

    for (int i = iStart; i < iEnd; ++i) {
      // Buy bond at start of this month.
      Bond bond = new Bond(bondData, cash, i);
      // System.out.printf("Bought %d: cash=%f, price=%f\n", i, cash, bond.price(i));

      // Sell bond at end of the month (we use start of next month).
      cash = bond.price(i + 1);
      // System.out.printf("  Sell %d: price=%f\n", i+1, cash);

      // Add sequence data point for new month.
      double currentValue = adjustValue(cash, i + 1, iStart, inflationAccounting);
      seq.addData(currentValue, getTimeMS(i + 1));
    }

    return seq;
  }

  /**
   * Calculates bond ROI for the given range using the hold-to-maturity approach.
   * 
   * All bonds are held to maturity and coupon payments are used to buy more bonds.
   * 
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence
   * @param inflationAccounting how should we handle inflation
   * @return sequence of ROIs
   */
  public Sequence calcBondReturnSeqHold(int iStart, int iEnd, Inflation inflationAccounting)
  {
    if (iStart < 0 || iEnd < iStart || iEnd >= size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, size()));
    }

    final double principal = 1000.0;
    final double bondQuantum = 10.0; // bonds can only be purchased in fixed increments
    double cash = principal;
    Sequence bondData = getBondData();
    Sequence seq = new Sequence("Bonds (Hold)");
    seq.addData(cash, getTimeMS(iStart));
    List<Bond> bonds = new ArrayList<Bond>(); // TODO not an efficient data structure

    for (int i = iStart; i < iEnd; ++i) {
      // Collect from existing bonds.
      Iterator<Bond> it = bonds.iterator();
      while (it.hasNext()) {
        Bond bond = it.next();
        cash += bond.paymentThisMonth(i);
        assert bond.isActive(i);
        if (bond.isExpiring(i)) {
          it.remove();
        }
      }

      // Buy new bonds.
      double bondValue = bondQuantum * Math.floor(cash / bondQuantum);
      if (bondValue > 0.0) {
        bonds.add(new Bond(bondData, bondValue, i));
        cash -= bondValue;
      }

      // Add sequence data point for new month.
      double value = cash;
      for (Bond bond : bonds) {
        value += bond.price(i);
      }
      double currentValue = adjustValue(value, i + 1, iStart, inflationAccounting);
      seq.addData(currentValue, getTimeMS(i + 1));
    }

    return seq._div(principal);
  }

  /**
   * Calculates bond ROI for the given range using the rebuy approach.
   * 
   * <p>
   * Each month, the existing bond is sold and a new one is purchased.
   * 
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence
   * @param inflationAccounting how should we handle inflation
   * @return ROI across the given time period
   */
  public double calcBondReturnRebuy(int iStart, int iEnd, Inflation inflationAccounting)
  {
    Sequence seq = calcBondReturnSeqRebuy(iStart, iEnd, inflationAccounting);
    return seq.getLast(0);
  }

  /**
   * Convert cash value at one point in time into equivalent value at another according to relative CPI.
   * 
   * @param value current value
   * @param iFrom current time
   * @param iTo query time
   * @return equivalent value at query time
   */
  public double adjustForInflation(double value, int iFrom, int iTo)
  {
    return adjustValue(value, iFrom, iTo, Inflation.Include);
  }

  /**
   * Convert value at one point in time into equivalent value at another according to inflation handling option.
   * 
   * @param value current value
   * @param iFrom current time
   * @param iTo query time
   * @param inflationAccounting should we adjust for inflation?
   * @return equivalent value at query time
   */
  public double adjustValue(double value, int iFrom, int iTo, Inflation inflationAccounting)
  {
    if (inflationAccounting == Inflation.Include) {
      value *= get(iTo, CPI) / get(iFrom, CPI);
    }
    return value;
  }

  /**
   * Compute ending balance; not inflation-adjusted, assuming we re-invest dividends
   * 
   * @param principal initial funds
   * @param annualWithdrawal annual withdrawal amount (annual "salary")
   * @param adjustWithdrawalForInflation use CPI to adjust withdrawal for constant purchasing power
   * @param expenseRatio percentage of portfolio taken by manager (2.1 = 2.1% per year)
   * @param retireAge age at retirement (start of simulation)
   * @param ssAge age when you first receive SS benefits
   * @param ssMonthly expected monthly SS benefit in today's dollar
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in S&P to consider
   * @return ending balance
   */
  public double calcEndBalance(double principal, double annualWithdrawal, double expenseRatio,
      boolean adjustWithdrawalForInflation, double retireAge, double ssAge, double ssMonthly, int iStart, int nMonths)
  {
    int nData = size();
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= nData) {
      return Double.NaN;
    }

    double age = retireAge;
    double monthlyWithdrawal = annualWithdrawal / 12.0;
    double monthlyExpenseRatio = (expenseRatio / 100.0) / 12.0;
    double balance = principal;
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double adjMonthlyWithdrawal = monthlyWithdrawal;
      if (adjustWithdrawalForInflation) {
        // update monthly withdrawal for inflation
        adjMonthlyWithdrawal = adjustForInflation(monthlyWithdrawal, iStart, i);
      }

      // withdraw money at beginning of month
      // System.out.printf("Balance: $%.2f  monthly=$%.2f\n", balance, monthlyWithdrawal);
      if (age >= ssAge) {
        balance += adjustForInflation(ssMonthly, nData - 1, i);
      }
      balance -= adjMonthlyWithdrawal;
      if (balance < 0.0)
        return 0.0; // ran out of money!

      double price1 = get(i, PRICE);
      double price2 = get(i + 1, PRICE);
      double shares = balance / price1;
      balance *= price2 / price1;
      balance += shares * get(i, DIV);
      balance *= (1.0 - monthlyExpenseRatio);
      age += Library.ONE_TWELFTH;
    }

    return balance;
  }

  /** @return Sequence containing all CAPE data. */
  public Sequence getCapeData()
  {
    return getCapeData(0, size() - 1);
  }

  /** @return Sequence containing CAPE data in the given range (inclusive). */
  public Sequence getCapeData(int iStart, int iEnd)
  {
    Sequence cape = new Sequence("CAPE");
    for (int i = iStart; i <= iEnd; ++i) {
      cape.addData(get(i, CAPE), getTimeMS(i));
    }
    return cape;
  }

  /** @return Sequence containing all stock and dividend data. */
  public Sequence getStockData()
  {
    return getStockData(0, size() - 1);
  }

  /** @return Sequence containing stock and dividend data in the given range (inclusive). */
  public Sequence getStockData(int iStart, int iEnd)
  {
    Sequence seq = new Sequence("Stock");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(new FeatureVec(2, get(i, PRICE), get(i, DIV)), getTimeMS(i));
    }
    return seq;
  }

  /** @return Sequence containing all bond data. */
  public Sequence getBondData()
  {
    return getBondData(0, size() - 1);
  }

  /** @return Sequence containing bond data in the given range (inclusive). */
  public Sequence getBondData(int iStart, int iEnd)
  {
    Sequence seq = new Sequence("Bonds");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(get(i, GS10), getTimeMS(i));
    }
    return seq;
  }

  public Sequence calcMixedReturnSeq(int iStart, int nMonths, double targetPercentStock, double targetPercentBonds,
      int rebalanceMonths, Inflation inflationAccounting)
  {
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths, size()));
    }

    double targetPercentCash = 100.0 - (targetPercentStock + targetPercentBonds);
    if (targetPercentCash < 0.0 || targetPercentCash > 100.0) {
      throw new IllegalArgumentException(String.format("Invalid asset allocation (%stock=%.3f, %bonds=%.3f)",
          targetPercentStock, targetPercentBonds));
    }

    Sequence bondData = getBondData();
    Sequence seq = new Sequence("Mixed");

    final double principal = 1000.0;

    double cash = principal;
    double bondValue = 0.0;
    double shares = 0.0;
    seq.addData(cash, getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double stockPrice = get(i + 1, PRICE);
      double stockValue = shares * stockPrice;
      double total = bondValue + stockValue + cash;
      // System.out.printf("%d: [%.2f, %.2f, %.2f]  [%.1f, %.1f, %.1f]  %.2f\n", i, stockValue, bondValue, cash, 100.0
      // * stockValue / total, 100.0 * bondValue / total, 100.0 * cash / total, total);

      // Collect dividends from stocks for previous month.
      double divCash = shares * get(i, DIV);
      cash += divCash;

      // Update bond value for this month.
      if (bondValue > 0.0) {
        Bond bond = new Bond(bondData, bondValue, i);
        bondValue = bond.price(i + 1);
      }
      total = bondValue + stockValue + cash;

      // Rebalance as requested.
      int months = i - iStart;
      if (rebalanceMonths > 0 && months % rebalanceMonths == 0) {
        // Sell stocks if we're over the requested allocation.
        double percentStock = 100.0 * stockValue / total;
        if (percentStock > targetPercentStock) {
          double sell = total * (percentStock - targetPercentStock) / 100.0;
          stockValue -= sell;
          cash += sell;
          shares = stockValue / stockPrice;
        }

        // Sell bonds if we're over the requested allocation.
        double percentBonds = 100.0 * bondValue / total;
        if (percentBonds > targetPercentBonds) {
          double sell = total * (percentBonds - targetPercentBonds) / 100.0;
          bondValue -= sell;
          cash += sell;
        }
      }

      // Use cash to buy stocks / bonds to get closer to desired asset allocation.
      if (cash > 0.0) {
        double percentStock = 100.0 * stockValue / total;
        if (percentStock < targetPercentStock) {
          double spend = Math.min(total * (targetPercentStock - percentStock) / 100.0, cash);
          stockValue += spend;
          cash -= spend;
          shares = stockValue / stockPrice;
        }

        double percentBonds = 100.0 * bondValue / total;
        if (percentBonds < targetPercentBonds) {
          double spend = Math.min(total * (targetPercentBonds - percentBonds) / 100.0, cash);
          bondValue += spend;
          cash -= spend;
        }
      }
      total = bondValue + stockValue + cash;

      // System.out.printf("      [%.2f, %.2f, %.2f]  [%.1f, %.1f, %.1f]  %.2f\n", stockValue, bondValue, cash, 100.0
      // * stockValue / total, 100.0 * bondValue / total, 100.0 * cash / total, total);

      // Add data point for current value.
      double adjustedValue = adjustValue(total, i + 1, iStart, inflationAccounting);
      seq.addData(adjustedValue, getTimeMS(i + 1));
    }

    return seq._div(principal);
  }
}
