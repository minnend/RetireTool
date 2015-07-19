package org.minnen.retiretool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.minnen.retiretool.FeatureVec;
import org.minnen.retiretool.Sequence;
import org.minnen.retiretool.Library;

/*
 * Analyze Shiller's SNP and CPI data to generate basic charts and stats
 * Data: http://www.econ.yale.edu/~shiller/data.htm
 */
public class Shiller
{
  public static int PRICE = 0;
  public static int DIV   = 1;
  public static int CPI   = 2;
  public static int GS10  = 3;
  public static int CAPE  = 4;

  public enum DividendMethod {
    NO_REINVEST, MONTHLY, QUARTERLY
  };

  public enum Inflation {
    Ignore, Include
  };

  public static DecimalFormat currencyFormatter = new DecimalFormat("#,###.00");

  private Sequence            shillerData;
  private Sequence            bondData;

  public static final double  SOC_SEC_AT70      = 3480.00;                      // http://www.ssa.gov/oact/quickcalc/

  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet
   * 
   * @param fname name of file to load
   * @return true on success
   */
  public void loadData(String fname) throws IOException
  {
    File file = new File(fname);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read shiller file (%s)", fname));
    }
    System.out.printf("Loading data file: [%s]\n", fname);

    BufferedReader in = new BufferedReader(new FileReader(fname));

    shillerData = new Sequence("Shiller Financial Data");
    bondData = new Sequence("Bonds");
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
        double cape = tryParse(toks[10], 0.0);

        Calendar cal = Library.now();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        shillerData.addData(new FeatureVec(5, price, div, cpi, gs10, cape), cal.getTimeInMillis());
        bondData.addData(gs10, cal.getTimeInMillis());

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
   * Try to parse the string as a double.
   * 
   * @param s string to parse
   * @param failValue return this value if parse fails
   * @return numeric value of s or failValue if parsing fails
   */
  public static double tryParse(String s, double failValue)
  {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nfe) {
      return failValue;
    }
  }

  /** @return index in data sequence for the given year and month (January == 1). */
  public int getIndexForDate(int year, int month)
  {
    long ms = Library.getTime(1, month, year);
    return shillerData.getClosestIndex(ms);
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
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= shillerData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths,
          shillerData.size()));
    }

    Sequence seq = new Sequence("S&P");

    // note: it's equivalent to keep track of total value or number of shares
    double divCash = 0.0;
    double baseValue = 1.0;
    double shares = baseValue / shillerData.get(iStart, PRICE);
    seq.addData(baseValue, shillerData.getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double div = 0.0;
      if (divMethod == DividendMethod.NO_REINVEST)
        divCash += shares * shillerData.get(i, DIV);
      else if (divMethod == DividendMethod.MONTHLY) {
        // Dividends at the end of every month.
        div = shillerData.get(i, DIV);
      } else if (divMethod == DividendMethod.QUARTERLY) {
        // Dividends at the end of every quarter (march, june, september, december).
        Calendar cal = Library.now();
        cal.setTimeInMillis(shillerData.getTimeMS(i));
        int month = cal.get(Calendar.MONTH);
        if (month % 3 == 2) { // time for a dividend!
          for (int j = 0; j < 3; j++) {
            if (i - j < iStart)
              break;
            div += shillerData.get(i - j, DIV);
          }
        }
      }

      // Apply the dividends (if any).
      double price = shillerData.get(i + 1, PRICE);
      shares += shares * div / price;

      // Add data point for current value.
      double value = adjustValue(divCash + shares * price, i + 1, iStart, inflationAccounting);
      seq.addData(value, shillerData.getTimeMS(i + 1));
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
    if (iStart < 0 || iEnd < iStart || iEnd >= shillerData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, shillerData.size()));
    }

    double cash = 1.0; // start with one dollar
    Sequence seq = new Sequence("Bonds (Rebuy)");
    seq.addData(cash, shillerData.getTimeMS(iStart));

    for (int i = iStart; i < iEnd; ++i) {
      // Buy bond at start of this month.
      Bond bond = new Bond(bondData, cash, i);
      // System.out.printf("Bought %d: cash=%f, price=%f\n", i, cash, bond.price(i));

      // Sell bond at end of the month (we use start of next month).
      cash = bond.price(i + 1);
      // System.out.printf("  Sell %d: price=%f\n", i+1, cash);

      // Add sequence data point for new month.
      double currentValue = adjustValue(cash, i + 1, iStart, inflationAccounting);
      seq.addData(currentValue, shillerData.getTimeMS(i + 1));
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
    if (iStart < 0 || iEnd < iStart || iEnd >= shillerData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, shillerData.size()));
    }

    final double principal = 1000.0;
    final double bondQuantum = 10.0; // bonds can only be purchased in fixed increments
    double cash = principal;
    Sequence seq = new Sequence("Bonds (Hold)");
    seq.addData(cash, shillerData.getTimeMS(iStart));
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
      seq.addData(currentValue, shillerData.getTimeMS(i + 1));
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

  public Sequence calcMixedReturnSeq(int iStart, int nMonths, double targetPercentStock, double targetPercentBonds,
      int rebalanceMonths, Inflation inflationAccounting)
  {
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= shillerData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths,
          shillerData.size()));
    }

    double targetPercentCash = 100.0 - (targetPercentStock + targetPercentBonds);
    if (targetPercentCash < 0.0 || targetPercentCash > 100.0) {
      throw new IllegalArgumentException(String.format("Invalid asset allocation (%stock=%.3f, %bonds=%.3f)",
          targetPercentStock, targetPercentBonds));
    }

    Sequence seq = new Sequence("Mixed");

    final double principal = 1000.0;

    double cash = principal;
    double bondValue = 0.0;
    double shares = 0.0;
    seq.addData(cash, shillerData.getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double stockPrice = shillerData.get(i + 1, PRICE);
      double stockValue = shares * stockPrice;
      double total = bondValue + stockValue + cash;
      // System.out.printf("%d: [%.2f, %.2f, %.2f]  [%.1f, %.1f, %.1f]  %.2f\n", i, stockValue, bondValue, cash, 100.0
      // * stockValue / total, 100.0 * bondValue / total, 100.0 * cash / total, total);

      // Collect dividends from stocks for previous month.
      double divCash = shares * shillerData.get(i, DIV);
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
      seq.addData(adjustedValue, shillerData.getTimeMS(i + 1));
    }

    return seq._div(principal);
  }

  public Sequence calcMomentumReturnSeq(int numMonths, Sequence... seqs)
  {
    assert seqs.length > 0;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence momentum = new Sequence("Momentum");
    double balance = 1.0;
    for (int i = 0; i < N; ++i) {
      // Select asset with best return over previous 12 months.
      int a = Math.max(0, i - numMonths - 1);
      int b = Math.max(0, i - 1);
      Sequence bestSeq = null;
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = seq.get(b, 0) / seq.get(a, 0);
        if (bestSeq == null || r > bestReturn) {
          bestSeq = seq;
          bestReturn = r;
        }
      }

      // Invest everything in best asset for this month.
      double lastMonthReturn = bestSeq.get(i, 0) / bestSeq.get(b, 0);
      balance *= lastMonthReturn;
      momentum.addData(balance, seqs[0].getTimeMS(i));
    }

    return momentum;
  }

  public Sequence calcSMAReturnSeq(int iStart, int numMonthsForAverage, Sequence risky, Sequence safe)
  {
    assert risky.length() == safe.length();

    Sequence sma = new Sequence("SMA");
    double balance = 1.0;
    sma.addData(balance, risky.getStartMS());
    for (int i = 1; i < risky.length(); ++i) {
      // Calculate trailing moving average.
      int a = Math.max(0, i - numMonthsForAverage - 1);
      double ma = 0.0;
      for (int j = a; j < i; ++j) {
        ma += shillerData.get(j, PRICE);
      }
      ma /= (i - a);

      // Test above / below moving average.
      double lastMonthReturn;
      double price = shillerData.get(i - 1, PRICE);
      if (price > ma) {
        lastMonthReturn = risky.get(i, 0) / risky.get(i - 1, 0);
      } else {
        lastMonthReturn = safe.get(i, 0) / safe.get(i - 1, 0);
      }
      balance *= lastMonthReturn;
      sma.addData(balance, risky.getTimeMS(i));
    }
    return sma;
  }

  public Sequence calcPerfectReturnSeq(Sequence... seqs)
  {
    assert seqs.length > 0;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence perfect = new Sequence("Perfect");
    double balance = 1.0;
    perfect.addData(balance, seqs[0].getStartMS());
    for (int i = 1; i < seqs[0].length(); ++i) {
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = seq.get(i, 0) / seq.get(i - 1, 0);
        if (r > bestReturn) {
          bestReturn = r;
        }
      }
      balance *= bestReturn;
      perfect.addData(balance, seqs[0].getTimeMS(i));
    }
    return perfect;
  }

  /**
   * Compute year as fraction (e.g., February 1997 = 1997 + 1/12)
   * 
   * @param i index into snp data
   * @return fractional year for data point at i
   */
  public double getYearAsFrac(int i)
  {
    Calendar cal = Library.now();
    cal.setTimeInMillis(shillerData.getTimeMS(i));
    int year = cal.get(Calendar.YEAR);
    int month = cal.get(Calendar.MONTH);
    return year + month / 12.0;
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
      value *= shillerData.get(iTo, CPI) / shillerData.get(iFrom, CPI);
    }
    return value;
  }

  /**
   * Compute compound annual growth rate (CAGR) based on total multiplier.
   * 
   * @param totalReturn principal multiplier over full term (e.g., 30% gain = 1.3)
   * @param nMonths number of months in term
   * @return average annual return as a percentage (e.g., 4.32 = 4.32%/yr)
   */
  protected static double getAnnualReturn(double totalReturn, int nMonths)
  {
    if (nMonths < 1) {
      return 0.0;
    }
    // x^n = y -> x = exp(log(y)/n) = y^(1/n)
    return (Math.pow(totalReturn, 12.0 / nMonths) - 1) * 100;
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

    int n = shillerData.size();
    for (int i = 0; i < n; i++) {
      if (i + months >= n)
        break; // not enough data
      double roi = calcSnpReturn(i, months, divMethod, inflationAccounting);
      double cagr = getAnnualReturn(roi, months);
      // System.out.printf("%.2f\t%f\n", getYearAsFrac(i), meanAnnualReturn);
      rois.addData(cagr, shillerData.getTimeMS(i));
    }

    return rois;
  }

  public static Sequence computeHistogram(Sequence rois, double binWidth, double binCenter)
  {
    Sequence h = new Sequence("Histogram - " + rois.getName());

    // sort rois to find min/max and ease histogram generation
    double[] a = rois.extractDim(0);
    Arrays.sort(a);
    int na = a.length;
    double vmin = a[0];
    double vmax = a[na - 1];

    System.out.printf("Data: %d entries in [%.2f%%, %.2f%%]\n", na, vmin, vmax);

    // figure out where to start
    double hleftCenter = binCenter - binWidth / 2.0;
    double hleft = hleftCenter + Math.floor((vmin - hleftCenter) / binWidth) * binWidth;
    // System.out.printf("binCenter=%f   binWidth=%f  hleft=%f\n", binCenter, binWidth, hleft);
    int i = 0;
    while (i < na) {
      assert (a[i] >= hleft);
      double hright = hleft + binWidth;

      // find all data points in [hleft, hright)
      int j = i;
      while (j < na) {
        if (a[j] >= hleft && a[j] < hright)
          j++;
        else
          break;
      }

      // add data point for this bin
      int n = j - i;
      double frac = (double) n / na;
      h.addData(new FeatureVec(3, (hleft + hright) / 2, n, frac));

      // move to next bin
      i = j;
      hleft = hright;
    }

    return h;
  }

  /**
   * Append dimension that stores likelihood of meeting or exceeding the current ROI
   * 
   * @param h histogram sequence (frequencies expected in dim=2)
   * @return new sequence with new dimension storing likelihood of higher return
   */
  public static Sequence addReturnLikelihoods(Sequence h)
  {
    int n = h.size();
    double[] freq = h.extractDim(2);
    double[] cum = new double[n];

    for (int i = 1; i < n; i++)
      cum[i] = cum[i - 1] + freq[i - 1];

    // we want likelihood of getting a higher return, not lower
    for (int i = 0; i < n; i++)
      cum[i] = 1.0 - cum[i];

    return h._appendDims(new Sequence(cum));
  }

  public static Sequence appendROISeq(Sequence a, Sequence b)
  {
    if (a == null)
      return b.extractDims(0, 3);

    double gap = a.get(1, 0) - a.get(0, 0);
    int na = a.size();
    int nb = b.size();
    double startA = a.get(0, 0);
    double startB = b.get(0, 0);
    double endA = a.get(na - 1, 0);
    double endB = b.get(nb - 1, 0);
    double start = Math.min(startA, startB);
    double end = Math.max(endA, endB);

    int n = (int) Math.round((end - start) / gap) + 1;
    System.out.printf("Merge: (%.2f -> %.2f) + (%.2f -> %.2f) = (%.2f -> %.2f) %d\n", startA, endA, startB, endB,
        start, end, n);

    double eps = 1e-5;
    int nd = a.getNumDims(); // one for roi, rest is real data
    Sequence seq = new Sequence();
    double roi = start;
    int ia = 0, ib = 0;
    for (int i = 0; i < n; i++, roi += gap) {
      // start current feature vec with data from seqA
      FeatureVec fv = null;
      if (ia >= na) { // seqA ran out of data
        fv = new FeatureVec(nd, roi, 0.0);
      } else {
        double ra = a.get(ia, 0);
        if (ra - eps > roi) { // have not started seqA yet
          fv = new FeatureVec(nd, roi, 1.0);
        } else { // copy current entry from seqA
          assert (Math.abs(ra - roi) < eps);
          fv = new FeatureVec(a.get(ia++));
        }
      }

      // now add data from seqB
      if (ib >= nb) { // seqB ran out of data
        fv._appendDim(0.0);
      } else {
        double rb = b.get(ib, 0);
        if (rb - eps > roi) { // have not started seqB yet
          fv._appendDim(1.0);
        } else { // copy current entry from seqB
          assert (Math.abs(rb - roi) < eps);
          fv._appendDim(b.get(ib++, 3));
        }
      }

      // add the new entry to the combined sequence
      seq.addData(fv);
    }

    return seq;
  }

  /**
   * Compute ending balance; not inflation-adjusted, assuming we re-invest dividends
   * 
   * @param principal initial funds
   * @param annualWithdrawal annual withdrawal amount (annual "salary")
   * @param adjustWithdrawalForInflation use CPI to adjust withdrawal for costant purchasing power
   * @param expenseRatio percentage of portfolio taken by manager (2.1 = 2.1% per year)
   * @param retireAge age at retirement (start of simulation)
   * @param ssAge age when you first receive SS benefits
   * @param ssMonthly expected monthly SS benefit in today's dollar
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in snp to consider
   * @return ending balance
   */
  public double calcEndBalance(double principal, double annualWithdrawal, double expenseRatio,
      boolean adjustWithdrawalForInflation, double retireAge, double ssAge, double ssMonthly, int iStart, int nMonths)
  {
    int nData = shillerData.size();
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= nData)
      return Double.NaN;

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

      double price1 = shillerData.get(i, PRICE);
      double price2 = shillerData.get(i + 1, PRICE);
      double shares = balance / price1;
      balance *= price2 / price1;
      balance += shares * shillerData.get(i, DIV);
      balance *= (1.0 - monthlyExpenseRatio);
      age += Library.ONE_TWELFTH;
    }

    return balance;
  }

  public void printReturnLikelihoods()
  {
    Sequence seqLik = null;
    int[] years = new int[] { 1, 5, 10, 15, 20, 30, 40, 50 };
    for (int i = 0; i < years.length; i++) {
      System.out.printf("Processing %d: %d years\n", i + 1, years[i]);
      Sequence r = calcSnpReturns(years[i], DividendMethod.MONTHLY, Inflation.Include);
      Sequence h = computeHistogram(r, 0.5, 0.0);
      h = addReturnLikelihoods(h);
      seqLik = appendROISeq(seqLik, h);
    }

    for (FeatureVec fv : seqLik) {
      System.out.printf("%.3f", fv.get(0));
      for (int i = 0; i < years.length; i++)
        System.out.printf("\t%f", fv.get(i + 1));
      System.out.println();
    }
  }

  public void printWithdrawalLikelihoods(int numYears, double expenseRatio)
  {
    System.out.printf("Withdrawal Likelihoods over %d years:\n", numYears);
    double[] wrates = new double[] { 2.0, 2.5, 3.0, 3.5, 3.75, 4.0, 4.25, 4.5, 5.0 };
    int months = numYears * 12;
    for (double wrate : wrates) {
      // System.out.printf("Processing Withdrawal Rate: %.2f%%\n", wrate);
      double principal = 1000000.0;
      double salary = principal * wrate / 100.0;
      int nData = shillerData.size();
      int n = 0;
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + months >= nData)
          break; // not enough data
        double endBalance = calcEndBalance(principal, salary, expenseRatio, true, 0, 0, 0.0, i, months);
        if (endBalance > 0.0)
          ++nOK;
        ++n;
      }
      System.out.printf("Withdrawal Rate: %.2f  %d / %d = %.2f%%\n", wrate, nOK, n, 100.0 * nOK / n);
    }
  }

  /**
   * Calculates annual salary needed to generate the given monthly take-home cash.
   * 
   * @param monthlyCash desired monthly take-home cash
   * @param effectiveTaxRate effective (total) tax rate
   * @return required annual salary to generate monthly cash
   */
  public static double calcAnnualSalary(double monthlyCash, double effectiveTaxRate)
  {
    return monthlyCash * 12 / (1.0 - effectiveTaxRate);
  }

  /**
   * Estimate the needed principal to generate the given salary.
   * 
   * Note that the salary is given in today's dollars but will be adjusted for inflation for different time periods and
   * increased over time to maintain buying power.
   * 
   * @param salary desired annual salary in today's dollars
   * @param minLikelihood desired likelihood
   * @param nYears number of years to draw salary
   * @param expenseRatio fraction of portfolio taken by manager
   * @param retireAge age at retirement (start of simulation)
   * @param ssAge age when you first receive SS benefits
   * @param ssMonthly expected monthly SS benefit in today's dollar
   * @param desiredRunwayYears desired value at end of retirement in terms of final salary
   * 
   * @return list of starting indices where we run out of money.
   */
  public List<Integer> calcSavingsTarget(double salary, double minLikelihood, int nYears, double expenseRatio,
      int retireAge, int ssAge, double ssMonthly, double desiredRunwayYears)
  {
    int nMonths = nYears * 12;
    int nData = shillerData.size();

    List<Integer> failures = new ArrayList<Integer>();
    double minSavings = 0.0;
    double maxSavings = salary * 1000.0; // assume needed savings is less than 1000x salary
    while (maxSavings - minSavings > 1.0) {
      failures.clear();
      double principal = (maxSavings + minSavings) / 2.0;
      int n = 0;
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + nMonths >= nData)
          break; // not enough data
        double adjSalary = adjustForInflation(salary, nData - 1, i);
        double adjprincipal = adjustForInflation(principal, nData - 1, i);
        double finalSalary = adjustForInflation(salary, nData - 1, i + nMonths);
        // System.out.printf("%.2f -> %d (%s): %.2f\n", salary, i, Library.formatDate(snp.getTimeMS(i)),
        // adjustedSalary);
        double endBalance = calcEndBalance(adjprincipal, adjSalary, expenseRatio, true, retireAge, ssAge, ssMonthly, i,
            nMonths);
        if (endBalance > finalSalary * (1 + desiredRunwayYears * 12)) {
          ++nOK;
        } else {
          failures.add(i);
        }
        ++n;
      }
      double successRate = (double) nOK / n;
      System.out.printf("$%s  %d/%d = %.2f%%\n", currencyFormatter.format(principal), nOK, n, 100.0 * successRate);
      if (successRate >= minLikelihood)
        maxSavings = principal;
      else
        minSavings = principal;
    }
    double principal = Math.ceil(maxSavings / 1000) * 1000;
    System.out.printf("$%s\n", currencyFormatter.format(principal));
    return failures;
  }

  public static void calcSavings(double principal, double depStartOfYear, double depMonthly, double annualGrowthRate,
      int years)
  {
    double annualReturn = 1.0 + annualGrowthRate / 100.0;
    double monthlyReturn = Math.pow(annualReturn, Library.ONE_TWELFTH);
    double balance = principal;
    System.out.println("Starting Balance: $" + currencyFormatter.format(balance));
    for (int year = 0; year < years; ++year) {
      balance += depStartOfYear;
      for (int month = 0; month < 12; ++month) {
        balance += depMonthly;
        balance *= monthlyReturn;
      }
      System.out.printf("End of Year %d (%d): $%s\n", year + 2015, year + 35, currencyFormatter.format(balance));
    }
  }

  public static void saveLineChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head><script type=\"text/javascript\"\n");
      writer
          .write(" src=\"https://www.google.com/jsapi?autoload={ 'modules':[{ 'name':'visualization', 'version':'1', 'packages':['corechart'] }] }\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("  google.setOnLoadCallback(drawChart);\n");
      writer.write("   function drawChart() {\n");
      writer.write("    var data = google.visualization.arrayToDataTable([\n");
      writer.write("     ['Date', ");
      for (int i = 0; i < seqs.length; ++i) {
        writer.write("'" + seqs[i].getName() + "'");
        if (i < seqs.length - 1) {
          writer.write(", ");
        }
      }
      writer.write("],\n");
      int dt = 1;
      for (int t = 0; t < seqs[0].length(); t += dt) {
        writer.write("     ['" + Library.formatMonth(seqs[0].getTimeMS(t)) + "', ");
        for (int i = 0; i < seqs.length; ++i) {
          writer.write(String.format("%.2f%s", seqs[i].get(t, 0), i == seqs.length - 1 ? "" : ", "));
        }
        writer.write(t + dt >= seqs[0].length() ? "]\n" : "],\n");
      }
      writer.write("    ]);\n");
      writer.write("    var options = {\n");
      writer.write("     title: '" + title + "',\n");
      writer.write("     legend: { position: 'right' },\n");
      writer.write("     vAxis: {\n");
      writer.write("      logScale: " + (logarithmic ? "true" : "false") + "\n");
      writer.write("     },\n");
      writer.write("     chartArea: {\n");
      writer.write("      left: 100,\n");
      writer.write("      width: \"75%\",\n");
      writer.write("      height: \"80%\"\n");
      writer.write("     }\n");
      writer.write("    };\n");
      writer.write("    var chart = new google.visualization.LineChart(document.getElementById('chart'));\n");
      writer.write("    chart.draw(data, options);\n");
      writer.write("  }\n");
      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width: " + width + "px; height: " + height + "px\"></div>\n");
      writer.write("</body></html>\n");
    }
  }

  public static Sequence divide(Sequence a, Sequence b)
  {
    assert a.length() == b.length();
    Sequence seq = new Sequence(a.getName() + " / " + b.getName());
    for (int i = 0; i < a.length(); ++i) {
      FeatureVec x = a.get(i);
      FeatureVec y = b.get(i);
      seq.addData(x.div(y), a.getTimeMS(i));
    }
    return seq;
  }

  public void genReturnChart(Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shillerData.size() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    int percentStock = 60;
    int percentBonds = 40;
    // int percentCash = 100 - (percentStock + percentBonds);

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;

    Sequence bonds = calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence bondsHold = calcBondReturnSeqHold(iStart, iEnd, inflation);
    Sequence snp = calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence snpNoDiv = calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.NO_REINVEST, inflation);
    Sequence mixed = calcMixedReturnSeq(iStart, iEnd - iStart, percentStock, percentBonds, 6, inflation);
    Sequence momentum = calcMomentumReturnSeq(nMonthsMomentum, snp, bonds);
    Sequence sma = calcSMAReturnSeq(iStart, nMonthsSMA, snp, bonds);
    Sequence perfect = calcPerfectReturnSeq(snp, bonds);

    assert snp.length() == N;
    assert snpNoDiv.length() == N;
    assert bonds.length() == N;
    assert bondsHold.length() == N;
    assert mixed.length() == N;
    assert momentum.length() == N;
    assert sma.length() == N;
    assert perfect.length() == N;

    // Sequence telltale = divide(snp, bonds);
    // saveLineChart(file, "Telltale Chart: S&P vs. Bonds", 1200, 800, false, telltale);

    double snpCAGR = getAnnualReturn(snp.getLast(0), N);
    double snpNoDivCAGR = getAnnualReturn(snpNoDiv.getLast(0), N);
    double bondCAGR = getAnnualReturn(bonds.getLast(0), N);
    double bondHoldCAGR = getAnnualReturn(bondsHold.getLast(0), N);
    double mixedCAGR = getAnnualReturn(mixed.getLast(0), N);
    double momentumCAGR = getAnnualReturn(momentum.getLast(0), N);
    double smaCAGR = getAnnualReturn(sma.getLast(0), N);
    double perfectCAGR = getAnnualReturn(perfect.getLast(0), N);

    snp.setName(String.format("Stocks (%.2f%%)", snpCAGR));
    snpNoDiv.setName(String.format("Stocks w/o Dividend Reinvestment (%.2f%%)", snpNoDivCAGR));
    bonds.setName(String.format("Bonds Rebuy (%.2f%%)", bondCAGR));
    bondsHold.setName(String.format("Bonds Hold (%.2f%%)", bondHoldCAGR));
    mixed.setName(String.format("Mixed [%d/%d] (%.2f%%)", percentStock, percentBonds, mixedCAGR));
    momentum.setName(String.format("Momentum-%d (%.2f%%)", nMonthsMomentum, momentumCAGR));
    sma.setName(String.format("SMA-%d (%.2f%%)", nMonthsSMA, smaCAGR));
    perfect.setName(String.format("Perfect (%.2f%%)", perfectCAGR));

    saveLineChart(file, "Cumulative Market Returns", 1200, 600, true, sma, momentum, snp, mixed, bonds, bondsHold);
  }

  public void genSMASweepChart(Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shillerData.size() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence bonds = calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence snp = calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);

    int[] months = new int[] { 1, 2, 3, 5, 6, 9, 10, 12, 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length];
    for (int i = 0; i < months.length; ++i) {
      Sequence sma = calcSMAReturnSeq(iStart, months[i], snp, bonds);
      double cagr = getAnnualReturn(sma.getLast(0), N);
      sma.setName(String.format("SMA-%d (%.2f%%)", months[i], cagr));
      seqs[i] = sma;
    }

    saveLineChart(file, "Cumulative Market Returns", 1200, 600, true, seqs);
  }

  public void genMomentumSweepChart(Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shillerData.size() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence bonds = calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence snp = calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);

    int[] months = new int[] { 1, 2, 3, 5, 6, 9, 10, 12, 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length];
    for (int i = 0; i < months.length; ++i) {
      Sequence mom = calcMomentumReturnSeq(months[i], snp, bonds);
      double cagr = getAnnualReturn(mom.getLast(0), N);
      mom.setName(String.format("Momentum-%d (%.2f%%)", months[i], cagr));
      seqs[i] = mom;
    }

    saveLineChart(file, "Cumulative Market Returns", 1200, 600, true, seqs);
  }

  public Sequence getCAPE(int iStart, int iEnd)
  {
    Sequence cape = new Sequence("CAPE");
    for (int i = iStart; i <= iEnd; ++i) {
      cape.addData(shillerData.get(i, CAPE), shillerData.getTimeMS(i));
    }
    return cape;
  }

  public void genReturnComparison(int numMonths, Inflation inflation, File file) throws IOException
  {
    int iStart = 0;// getIndexForDate(1881, 1);
    int iEnd = shillerData.size() - 1;

    int percentStocks = 60;
    int percentBonds = 40;

    Sequence cumulativeSNP = calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence cumulativeBonds = calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence cumulativeMixed = calcMixedReturnSeq(iStart, iEnd - iStart, percentStocks, percentBonds, 6, inflation);
    assert cumulativeSNP.length() == cumulativeBonds.length();

    Sequence snp = new Sequence("S&P");
    Sequence bonds = new Sequence("Bonds");
    Sequence mixed = new Sequence(String.format("Mixed (%d/%d)", percentStocks, percentBonds));
    Sequence snpPremium = new Sequence("S&P Premium");
    int numBondWins = 0;
    int numStockWins = 0;
    for (int i = 0; i + numMonths < cumulativeSNP.size(); ++i) {
      int j = i + numMonths;
      double snpCAGR = getAnnualReturn(cumulativeSNP.get(j, 0) / cumulativeSNP.get(i, 0), numMonths);
      double bondCAGR = getAnnualReturn(cumulativeBonds.get(j, 0) / cumulativeBonds.get(i, 0), numMonths);
      double mixedCAGR = getAnnualReturn(cumulativeMixed.get(j, 0) / cumulativeMixed.get(i, 0), numMonths);
      if (bondCAGR >= snpCAGR) {
        ++numBondWins;
      } else {
        ++numStockWins;
      }
      long timestamp = cumulativeBonds.getTimeMS(i);
      bonds.addData(bondCAGR, timestamp);
      snp.addData(snpCAGR, timestamp);
      mixed.addData(mixedCAGR, timestamp);
      snpPremium.addData(snpCAGR - bondCAGR, timestamp);
    }
    int N = numBondWins + numStockWins;
    double percentBondWins = 100.0 * numBondWins / N;
    double percentStockWins = 100.0 * numStockWins / N;
    System.out.printf("Future %d-months (Bonds vs. Stocks): %d vs. %d (%.1f%% vs. %.1f%%)\n", numMonths, numBondWins,
        numStockWins, percentBondWins, percentStockWins);

    snp.setName(String.format("S&P (%.1f%%)", percentStockWins));
    bonds.setName(String.format("Bonds (%.1f%%)", percentBondWins));
    Sequence cape = getCAPE(iStart, iEnd - numMonths);
    assert cape.length() == snp.length();
    assert cape.length() == N;

    String title = numMonths % 12 == 0 ? String.format("%d-Year Future Market Returns", numMonths / 12) : String
        .format("%d-Month Future Market Returns", numMonths);
    saveLineChart(file, title, 1200, 800, false, snp, bonds);
    // snp, bonds, snpPremium, cape);
  }

  /**
   * Calculate present value for a given value, interest rate, and time period.
   * 
   * @param futureValue value in the future
   * @param interestRate current interest rate (annual, 4.0 = 4%)
   * @param nMonths number of months from now future value will be paid
   * @return present value
   */
  public static double presentValue(double futureValue, double interestRate, int nMonths)
  {
    double monthlyRate = Math.pow(1.0 + interestRate / 100.0, Library.ONE_TWELFTH);
    return futureValue / Math.pow(monthlyRate, nMonths);
  }

  public void bondLifetime()
  {
    double cash = 0.0;
    Bond bond = new Bond(bondData, 1000.0, 100);
    for (int i = bond.startIndex - 2; i <= bond.endIndex + 2; ++i) {
      double payment = bond.paymentThisMonth(i);
      cash += payment;
      double price = bond.price(i);
      System.out.printf("%d: %.2f + %.2f (%.2f) = %.2f\n", i, price, cash, payment, price + cash);
    }
  }
}
