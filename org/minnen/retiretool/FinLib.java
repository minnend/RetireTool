package org.minnen.retiretool;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.stats.RetirementStats;

public final class FinLib
{
  public static final double SOC_SEC_AT70 = 3480.00; // http://www.ssa.gov/oact/quickcalc/

  public enum DividendMethod {
    NO_REINVEST_MONTHLY, NO_REINVEST_QUARTERLY, MONTHLY, QUARTERLY, IGNORE_DIVIDENDS
  };

  public enum Inflation {
    Ignore, Include
  };

  public static final int      MonthlyClose            = 0;
  public static final int      MonthlyAverage          = 1;
  public static final int      MonthlyLow              = 2;
  public static final int      MonthlyHigh             = 3;
  public static final int      MonthlyMisc             = 4;

  public static DecimalFormat  currencyFormatter       = new DecimalFormat("#,##0.00");
  public static DecimalFormat  dollarFormatter         = new DecimalFormat("#,##0");

  // VFIAX = S&P 500
  // VEXAX = Extended Market (completion fund)
  // VTSAX = Total Stock Market
  // VBTLX = Total Bond Market (US)
  // VGSLX = REITs
  // VTIAX = International Stock
  public static final String[] VANGUARD_ADMIRAL_FUNDS  = new String[] { "VFIAX", "VEXAX", "VTSAX", "VBTLX", "VGSLX",
      "VTIAX"                                         };

  // VTSMX = Total Stock Market (~3800 stocks)
  // VBMFX = Total Bond Market (~7650 bonds)
  // VGSIX = REITs
  // VGTSX = International Stock
  public static final String[] VANGUARD_INVESTOR_FUNDS = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX" };

  public static final String[] STOCK_MARKET_FUNDS      = new String[] { "^GSPC" };

  /**
   * Compute compound annual growth rate (CAGR) based on total multiplier.
   * 
   * @param totalReturn principal multiplier over full term (e.g., 30% gain = 1.3)
   * @param nMonths number of months in term
   * @return average annual return as a percentage (e.g., 4.32 = 4.32%/yr)
   */
  public static double getAnnualReturn(double totalReturn, double nMonths)
  {
    if (nMonths <= 0.0) {
      return 1.0;
    }

    // x^n = y -> x = y ^ (1/n)
    return mul2ret(Math.pow(totalReturn, 12.0 / nMonths));
  }

  public static double getFutureValue(double presentValue, double interestRate, int nMonths)
  {
    return presentValue * Math.pow(FinLib.ret2mul(interestRate), nMonths / 12.0);
  }

  public static double getPresentValue(double futureValue, double interestRate, int nMonths)
  {
    return futureValue * Math.pow(FinLib.ret2mul(interestRate), -nMonths / 12.0);
  }

  /**
   * Convert value at one point in time into equivalent value at another according to inflation handling option.
   * 
   * @param cpi Sequence of CPI values
   * @param value current value
   * @param iFrom current time
   * @param iTo query time
   * @param inflationAccounting should we adjust for inflation?
   * @return equivalent value at query time
   */
  public static double adjustValue(Sequence cpi, double value, int iFrom, int iTo, Inflation inflationAccounting)
  {
    if (iFrom < 0) {
      iFrom += cpi.length();
    }
    if (iTo < 0) {
      iTo += cpi.length();
    }
    if (inflationAccounting == Inflation.Include) {
      value *= cpi.get(iTo, 0) / cpi.get(iFrom, 0);
    }
    return value;
  }

  /**
   * Generates a sequence of real returns (inflation-adjusted) from nominal returns.
   * 
   * @param cumulativeReturns nominal returns
   * @param cpi cumulative inflation
   * @return Sequence corresponding to real returns (inflation-adjusted)
   */
  public static Sequence calcRealReturns(Sequence cumulativeReturns, Sequence cpi)
  {
    // Check pre-conditions.
    assert cumulativeReturns.length() == cpi.length();
    assert cumulativeReturns.getStartMS() == cpi.getStartMS();
    assert cumulativeReturns.getEndMS() == cpi.getEndMS();

    // Build sequence of real returns from nominal.
    Sequence seq = new Sequence(cumulativeReturns.getName() + " (real)");
    seq.addData(1.0, cumulativeReturns.getStartMS());
    for (int i = 1; i < cumulativeReturns.length(); ++i) {
      double growth = getReturn(cumulativeReturns, i - 1, i);
      double inflation = getReturn(cpi, i - 1, i);
      double real = seq.getLast(0) * growth / inflation;
      seq.addData(real, cumulativeReturns.getTimeMS(i));
    }

    // Check post-conditions.
    assert cumulativeReturns.length() == seq.length();
    assert cumulativeReturns.getStartMS() == seq.getStartMS();
    assert cumulativeReturns.getEndMS() == seq.getEndMS();

    return seq;
  }

  /**
   * Convert cash value at one point in time into equivalent value at another according to relative CPI.
   * 
   * @param cpi Sequence of CPI values
   * @param value current value
   * @param iFrom current time
   * @param iTo query time
   * @return equivalent value at query time
   */
  public static double adjustForInflation(Sequence cpi, double value, int iFrom, int iTo)
  {
    return adjustValue(cpi, value, iFrom, iTo, Inflation.Include);
  }

  /**
   * Convert a multiplier to a return (1.042 -> 4.2%)
   * 
   * @param multiplier a multiplier (1.042 = 4.2% ROI)
   * @return a return value (4.2 = 4.2% ROI)
   */
  public static double mul2ret(double multiplier)
  {
    return (multiplier - 1.0) * 100.0;
  }

  /**
   * Convert a return to a multiplier (4.2% -> 1.042)
   * 
   * @param ret a return value (4.2 = 4.2% ROI)
   * @return a multiplier (1.042 = 4.2% ROI)
   */
  public static double ret2mul(double ret)
  {
    return ret / 100.0 + 1.0;
  }

  /**
   * Returns the "speedup factor" = number of extra years per year needed for rslow to catch rfast.
   * 
   * @param rslow slower return that needs more time to catch up
   * @param rfast faster return that sets the pace
   * @return time multiplier for rslow to catch up to rfast
   */
  public static double speedup(double rfast, double rslow)
  {
    assert rfast >= rslow;
    double mfast = ret2mul(rfast);
    double mslow = ret2mul(rslow);
    return Math.log(mfast) / Math.log(mslow) - 1.0;
  }

  /**
   * Compute histogram for the given returns.
   * 
   * @param rois sequence containing list of ROIs for some strategy
   * @param binWidth width of each bin
   * @param binCenter center bin has this value
   * @return sequence of 3D vectors: [center of bin, count, frequence]
   */
  public static Sequence computeHistogram(Sequence rois, double binWidth, double binCenter)
  {
    return computeHistogram(rois, Double.NaN, Double.NaN, binWidth, binCenter);
  }

  /**
   * Compute histogram for the given returns.
   * 
   * @param rois sequence containing list of ROIs for some strategy
   * @param vmin histogram starts with bin containing this value (NaN to compute from ROIs)
   * @param vmax histogram ends with bin containing this value (NaN to compute from ROIs)
   * @param binWidth width of each bin
   * @param binCenter center bin has this value
   * @return sequence of 3D vectors: [center of bin, count, frequence]
   */
  public static Sequence computeHistogram(Sequence rois, double vmin, double vmax, double binWidth, double binCenter)
  {
    // Sort ROIs to find min/max and ease histogram generation.
    double[] a = rois.extractDim(0);
    Arrays.sort(a);
    int na = a.length;
    if (Double.isNaN(vmin)) {
      vmin = a[0];
    }
    if (Double.isNaN(vmax)) {
      vmax = a[na - 1];
    }
    // System.out.printf("Data: %d entries in [%.2f%%, %.2f%%]\n", na, vmin, vmax);

    // figure out where to start
    double hleftCenter = binCenter - binWidth / 2.0;
    double hleft = hleftCenter + Math.floor((vmin - hleftCenter) / binWidth) * binWidth;
    // System.out.printf("binCenter=%f   binWidth=%f  hleft=%f\n", binCenter, binWidth, hleft);
    Sequence h = new Sequence("Histogram: " + rois.getName());
    int i = 0;
    while (i < na) {
      assert (i == 0 || a[i] >= hleft);
      double hright = hleft + binWidth;
      double hrightTest = hright;

      // Did we reach the requested end?
      if (hright >= vmax) {
        hrightTest = Double.POSITIVE_INFINITY;
      }

      // Find all data points in [hleft, hright).
      int j = i;
      while (j < na) {
        if (a[j] < hrightTest) {
          ++j;
        } else {
          break;
        }
      }

      // Add data point for this bin.
      int n = j - i;
      double frac = (double) n / na;
      h.addData(new FeatureVec(3, (hleft + hright) / 2, n, frac));

      // Move to next bin.
      i = j;
      hleft = hright;
    }

    // Add zeroes to reach vmax.
    while (hleft <= vmax) {
      double hright = hleft + binWidth;
      h.addData(new FeatureVec(3, (hleft + hright) / 2, 0, 0));
      hleft = hright;
    }

    return h;
  }

  public static String[] getLabelsFromHistogram(Sequence histogram)
  {
    String[] labels = new String[histogram.length()];
    for (int i = 0; i < labels.length; ++i) {
      labels[i] = String.format("%.1f", histogram.get(i, 0));
    }
    return labels;
  }

  /**
   * Append dimension that stores likelihood of meeting or exceeding the current ROI
   * 
   * @param h histogram sequence (frequencies expected in dim=2)
   * @return new sequence with new dimension storing likelihood of higher return
   */
  public static Sequence addReturnLikelihoods(Sequence h, boolean bInvert)
  {
    int n = h.size();
    double[] freq = h.extractDim(2);
    double[] cum = new double[n];

    for (int i = 1; i < n; i++)
      cum[i] = cum[i - 1] + freq[i - 1];

    if (bInvert) {
      // we want likelihood of getting a higher return, not lower
      for (int i = 0; i < n; i++)
        cum[i] = 1.0 - cum[i];
    }

    return h._appendDims(new Sequence(cum));
  }

  public static Sequence appendROISeq(Sequence a, Sequence b)
  {
    if (a == null)
      return b.extractDims(0, 3);

    final int indexDataB = 3;
    int D = a.getNumDims();
    double gap = a.get(1, 0) - a.get(0, 0);
    int na = a.size();
    int nb = b.size();
    double startA = a.get(0, 0);
    double startB = b.get(0, 0);
    double endA = a.get(na - 1, 0);
    double endB = b.get(nb - 1, 0);
    double start = Math.min(startA, startB);
    double end = Math.max(endA, endB);

    double firstValueA = a.get(0, D - 1);
    double lastValueA = a.get(na - 1, D - 1);
    double firstValueB = b.get(0, indexDataB);
    double lastValueB = b.get(nb - 1, indexDataB);

    int n = (int) Math.round((end - start) / gap) + 1;
    // System.out.printf("Merge: (%.2f -> %.2f) + (%.2f -> %.2f) = (%.2f -> %.2f) %d\n", startA, endA, startB, endB,
    // start, end, n);
    // System.out.printf(" A: [%.3f -> %.3f]  B: [%.3f -> %.3f]\n", firstValueA, lastValueA, firstValueB, lastValueB);

    double eps = 1e-5;
    int nd = a.getNumDims(); // one for roi, rest is real data
    Sequence seq = new Sequence();
    double roi = start;
    int ia = 0, ib = 0;
    for (int i = 0; i < n; i++, roi += gap) {
      // start current feature vec with data from seqA
      FeatureVec fv = null;
      if (ia >= na) { // seqA ran out of data
        fv = new FeatureVec(nd, roi, lastValueA);
      } else {
        double ra = a.get(ia, 0);
        if (ra - eps > roi) { // have not started seqA yet
          fv = new FeatureVec(nd, roi, firstValueA);
        } else { // copy current entry from seqA
          assert (Math.abs(ra - roi) < eps);
          fv = new FeatureVec(a.get(ia++));
        }
      }

      // now add data from seqB
      if (ib >= nb) { // seqB ran out of data
        fv._appendDim(lastValueB);
      } else {
        double rb = b.get(ib, 0);
        if (rb - eps > roi) { // have not started seqB yet
          fv._appendDim(firstValueB);
        } else { // copy current entry from seqB
          assert (Math.abs(rb - roi) < eps);
          fv._appendDim(b.get(ib++, indexDataB));
        }
      }

      // add the new entry to the combined sequence
      seq.addData(fv);
    }

    return seq;
  }

  public static Sequence calcReturnLikelihoods(Sequence cumulativeReturns, boolean bInvert)
  {
    Sequence seqLik = null;
    int[] years = new int[] { 1, 2, 5, 10, 20, 30, 40 };
    for (int i = 0; i < years.length; i++) {
      Sequence r = calcReturnsForDuration(cumulativeReturns, years[i] * 12);
      Sequence h = computeHistogram(r, 0.5, 0.0);
      h = addReturnLikelihoods(h, bInvert);
      seqLik = appendROISeq(seqLik, h);
    }
    return seqLik;
  }

  /**
   * Calculate returns for all periods with the given duration.
   * 
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param months number of months in the market
   * @return sequence containing returns for each time period of the given duration, if nMonths<12 then the returns are
   *         raw, else they are CAGRs.
   */
  public static Sequence calcReturnsForDuration(Sequence cumulativeReturns, int nMonths)
  {
    final int N = cumulativeReturns.size();
    String name = String.format("%s (%s)", cumulativeReturns.getName(), TimeLib.formatDurationMonths(nMonths));
    Sequence rois = new Sequence(name);
    if (N <= nMonths) {
      double growth = FinLib.getReturn(cumulativeReturns, 0, N - 1);
      double roi = FinLib.getAnnualReturn(growth, nMonths);
      rois.addData(roi, cumulativeReturns.getStartMS());
    } else {
      for (int i = 0; i + nMonths < N; i++) {
        double roi = getReturn(cumulativeReturns, i, i + nMonths);
        if (nMonths >= 12) {
          roi = getAnnualReturn(roi, nMonths);
        } else {
          roi = mul2ret(roi);
        }
        rois.addData(roi, cumulativeReturns.getTimeMS(i));
      }
      assert rois.size() > 0;
    }
    return rois;
  }

  /**
   * Calculates annual salary needed to generate the given monthly take-home cash.
   * 
   * @param monthlyCash desired monthly take-home cash
   * @param effectiveTaxRate effective (total) tax rate (0.3 = 30%)
   * @return required annual salary to generate monthly cash
   */
  public static double calcAnnualSalary(double monthlyCash, double effectiveTaxRate)
  {
    return monthlyCash * 12 / (1.0 - effectiveTaxRate);
  }

  /**
   * Calculate total return for the given segment.
   * 
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param iFrom index of starting point for calculation
   * @param iTo index of ending point for calculation (inclusive)
   * @return total return from iFrom to iTo.
   */
  public static double getReturn(Sequence cumulativeReturns, int iFrom, int iTo)
  {
    return cumulativeReturns.get(iTo, 0) / cumulativeReturns.get(iFrom, 0);
  }

  /**
   * Compute ending balance; not inflation-adjusted, assuming we re-invest dividends
   * 
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param cpi sequence of CPI values
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
  public static double calcEndBalance(Sequence cumulativeReturns, Sequence cpi, double principal,
      double annualWithdrawal, double expenseRatio, boolean adjustWithdrawalForInflation, double retireAge,
      double ssAge, double ssMonthly, int iStart, int nMonths)
  {
    int nData = cumulativeReturns.size();
    assert cpi.size() == nData;
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
        // Update monthly withdrawal for inflation.
        adjMonthlyWithdrawal = adjustForInflation(cpi, monthlyWithdrawal, iStart, i);
      }

      // Get paid by social security if we're old enough.
      if (age >= ssAge) {
        balance += adjustForInflation(cpi, ssMonthly, nData - 1, i);
      }

      // Withdraw money at beginning of month.
      balance -= adjMonthlyWithdrawal;
      if (balance < 0.0) {
        return 0.0; // ran out of money!
      }

      // Update balance based on investment returns.
      balance *= getReturn(cumulativeReturns, i, i + 1);

      // Broker gets their cut.
      balance *= (1.0 - monthlyExpenseRatio);

      // Now we're one month older.
      age += Library.ONE_TWELFTH;
    }

    return balance;
  }

  public static double calcEndBalance(Sequence inflationAdjustedReturns, double principal, double annualWithdrawal,
      double expenseRatio, int iStart, int nMonths)
  {
    int nData = inflationAdjustedReturns.size();
    assert (iStart >= 0 && nMonths >= 1 && iStart + nMonths < nData);

    double monthlyWithdrawal = annualWithdrawal / 12.0;
    double monthlyExpenseRatio = (expenseRatio / 100.0) / 12.0;
    double balance = principal;
    for (int i = iStart; i < iStart + nMonths; ++i) {
      // Withdraw money at beginning of month.
      balance -= monthlyWithdrawal;
      if (balance < 0.0) {
        return 0.0; // ran out of money!
      }

      // Update balance based on investment returns.
      balance *= getReturn(inflationAdjustedReturns, i, i + 1);

      // Broker gets their cut.
      balance *= (1.0 - monthlyExpenseRatio);
    }

    return balance;
  }

  /**
   * Compute ending balance; not inflation-adjusted, assuming we re-invest dividends
   * 
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param principal initial funds
   * @param depStartOfYear deposit at the beginning of each calendar year
   * @param depEndOfMonth deposit at the end of each month
   * @param expenseRatio percentage of portfolio taken by manager (2.1 = 2.1% per year)
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in S&P to consider
   * @return ending balance
   */
  public static double calcEndSavings(Sequence cumulativeReturns, double principal, double depStartOfYear,
      double depEndOfMonth, double expenseRatio, int iStart, int nMonths)
  {
    final int nData = cumulativeReturns.size();
    assert (iStart >= 0 && nMonths >= 1 && iStart + nMonths < nData);

    Calendar cal = TimeLib.now();
    final double monthlyExpenseRatio = (expenseRatio / 100.0) / 12.0;
    double balance = principal;
    double totalDeposit = principal;
    for (int i = iStart; i < iStart + nMonths; ++i) {
      // Make deposit at start of year.
      cal.setTimeInMillis(cumulativeReturns.getTimeMS(i));
      if (cal.get(Calendar.MONTH) == 0) {
        balance += depStartOfYear;
        totalDeposit += depStartOfYear;
      }

      // Update balance based on investment returns.
      balance *= getReturn(cumulativeReturns, i, i + 1);

      // Broker gets their cut.
      balance *= (1.0 - monthlyExpenseRatio);

      // Deposit at end of the month.
      balance += depEndOfMonth;
      totalDeposit += depEndOfMonth;
    }

    return balance;
  }

  public static void calcSavings(double principal, double depStartOfYear, double depStartOfMonth, double cagr, int years)
  {
    double annualReturn = ret2mul(cagr);
    double monthlyReturn = Math.pow(annualReturn, Library.ONE_TWELFTH);
    double balance = principal;
    System.out.println("Starting Balance: $" + currencyFormatter.format(balance));
    for (int year = 0; year < years; ++year) {
      balance += depStartOfYear;
      for (int month = 0; month < 12; ++month) {
        balance += depStartOfMonth;
        balance *= monthlyReturn;
      }
      System.out.printf("End of Year %d (%d): $%s\n", year + 2015, year + 35, currencyFormatter.format(balance));
    }
  }

  /**
   * Estimate the needed principal to generate the given salary.
   * 
   * Note that the salary is given in today's dollars but will be adjusted for inflation for different time periods and
   * increased over time to maintain buying power.
   * 
   * @param shiller shiller data object
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param salary desired annual salary in today's dollars
   * @param minLikelihood desired likelihood
   * @param nYears number of years to draw salary
   * @param expenseRatio fraction of portfolio taken by manager
   * @param retireAge age at retirement (start of simulation)
   * @param ssAge age when you first receive SS benefits
   * @param ssMonthly expected monthly SS benefit in today's dollar
   * @param desiredRunwayYears desired value at end of retirement in terms of final salary
   * 
   * @return retirement statistics based on principal required to retire safely
   */
  public static RetirementStats calcSavingsTarget(Sequence cumulativeReturns, Sequence cpi, double salary,
      double minLikelihood, int nYears, double expenseRatio, int retireAge, int ssAge, double ssMonthly,
      double desiredRunwayYears)
  {
    int nMonths = nYears * 12;
    int nData = cumulativeReturns.length();

    List<Integer> failures = new ArrayList<Integer>();
    double minSavings = 0.0;
    double maxSavings = salary * 1000.0; // assume needed savings is less than 1000x salary
    boolean bRequireReplacement = (desiredRunwayYears < 0.0);
    double[] endBalances = new double[nData - nMonths];
    int nAllowedFailures = (int) Math.floor((1.0 - minLikelihood) * endBalances.length);
    boolean done = false;
    while (!done) {
      if (maxSavings - minSavings < 1.0) {
        minSavings = maxSavings = Math.ceil(maxSavings / 1000) * 1000;
        done = true;
      }
      double principal = (maxSavings + minSavings) / 2.0;
      failures.clear();
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + nMonths >= nData) {
          assert i == endBalances.length;
          break; // not enough data
        }
        double adjSalary = adjustForInflation(cpi, salary, nData - 1, i);
        double adjPrincipal = adjustForInflation(cpi, principal, nData - 1, i);
        double endBalance = calcEndBalance(cumulativeReturns, cpi, adjPrincipal, adjSalary, expenseRatio, true,
            retireAge, ssAge, ssMonthly, i, nMonths);
        double finalSalary = adjustForInflation(cpi, salary, nData - 1, i + nMonths);
        double replacementPrincipal = adjustForInflation(cpi, principal, nData - 1, i + nMonths);
        double goal = (bRequireReplacement ? replacementPrincipal : finalSalary * desiredRunwayYears);
        if (endBalance > goal) {
          ++nOK;
          // Save end balance after adjusting back to today's dollars.
          if (done) {
            endBalances[i] = adjustForInflation(cpi, endBalance, i + nMonths, nData - 1);
            // System.out.printf("[%s] -> [%s]: %.0f -> %.0f  (%.0f, %.3f)  %s\n",
            // Library.formatMonth(cpi.getTimeMS(i)),
            // Library.formatMonth(cpi.getTimeMS(i + nMonths)), endBalance, endBalances[i], finalSalary, endBalance
            // / finalSalary, endBalance / finalSalary < 23.0 ? "************************" : "");
          }
        } else {
          failures.add(i);
          endBalances[i] = 0.0;
          if (failures.size() > nAllowedFailures) {
            break;
          }
        }
      }
      double successRate = (double) nOK / endBalances.length;
      // System.out.printf("$%s  %d/%d = %.2f%%\n", currencyFormatter.format(principal), nOK, n, 100.0 * successRate);
      if (successRate >= minLikelihood) {
        maxSavings = principal;
      } else {
        minSavings = principal;
      }
    }

    RetirementStats stats = new RetirementStats(cumulativeReturns.getName(), maxSavings, endBalances);

    return stats;
  }

  public static RetirementStats calcSavingsTarget(Sequence inflationAdjustedReturns, double salary, int nYears,
      double expenseRatio, double desiredRunwayYears)
  {
    final int nMonths = nYears * 12;
    final int nData = inflationAdjustedReturns.length();

    boolean bRequireReplacement = (desiredRunwayYears < 0.0);
    double[] endBalances = new double[nData - nMonths];
    double minSavings = 0.0;
    double maxSavings = salary * 1000.0; // assume needed savings is less than 1000x salary
    boolean done = false;
    boolean bFailed = false;
    while (!done) {
      if (maxSavings - minSavings < 1.0) {
        minSavings = maxSavings = Math.ceil(maxSavings / 1000) * 1000;
        done = true;
      }
      bFailed = false;
      double principal = (maxSavings + minSavings) / 2.0;
      for (int i = 0; i < nData; i++) {
        if (i + nMonths >= nData) {
          assert i == endBalances.length;
          break; // not enough data
        }
        endBalances[i] = calcEndBalance(inflationAdjustedReturns, principal, salary, expenseRatio, i, nMonths);
        if ((bRequireReplacement && endBalances[i] < principal) || (endBalances[i] < salary * desiredRunwayYears)) {
          bFailed = true;
          break;
        }
      }
      if (bFailed) {
        minSavings = principal;
      } else {
        maxSavings = principal;
      }
    }

    // assert !bFailed;
    RetirementStats stats = new RetirementStats(inflationAdjustedReturns.getName(), maxSavings, endBalances);

    return stats;
  }

  private static double getQuarterlyDividendsPerShare(Sequence snp, int index, int iMinIndex)
  {
    double div = 0.0;

    // Dividends at the end of every quarter (march, june, september, december).
    Calendar cal = TimeLib.ms2cal(snp.getTimeMS(index));
    int month = cal.get(Calendar.MONTH);
    if (month % 3 == 2) { // time for a dividend!
      for (int j = 0; j < 3; j++) {
        if (index - j < iMinIndex)
          break;
        div += snp.get(index - j, Shiller.DIV);
      }
    }
    return div;
  }

  /**
   * Calculates S&P ROI for the given range.
   * 
   * @param snp sequence of prices (d=0) and dividends (d=1)
   * @param iStart first index in S&P to consider (negative => count back from end of sequence)
   * @param iEnd last index in S&P to consider (negative => count back from end of sequence)
   * @param divMethod how should we handle dividend reinvestment
   * @return sequence of ROIs
   */
  public static Sequence calcSnpReturns(Sequence snp, int iStart, int iEnd, DividendMethod divMethod)
  {
    if (iStart < 0) {
      iStart += snp.length();
    }
    if (iEnd < 0) {
      iEnd += snp.length();
    }
    if (iStart < 0 || iEnd < iStart || iEnd >= snp.length()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, length=%d", iStart, iEnd, snp.length()));
    }

    Sequence seq = new Sequence("S&P-" + divMethod);

    // note: it's equivalent to keep track of total value or number of shares
    double divCash = 0.0;
    double baseValue = 1.0;
    double shares = baseValue / snp.get(iStart, 0);
    seq.addData(new FeatureVec(2, baseValue, 0.0), snp.getTimeMS(iStart));
    for (int i = iStart + 1; i <= iEnd; ++i) {
      long timeMS = snp.getTimeMS(i);
      double divReinvest = 0.0;
      if (divMethod == DividendMethod.NO_REINVEST_MONTHLY)
        // No dividend reinvestment so all dividends go to cash.
        divCash += shares * snp.get(i, Shiller.DIV);
      else if (divMethod == DividendMethod.MONTHLY) {
        // Dividends at the end of every month.
        divReinvest = shares * snp.get(i, Shiller.DIV);
      } else if (divMethod == DividendMethod.QUARTERLY) {
        // Dividends at the end of every quarter (march, june, september, december).
        divReinvest += shares * getQuarterlyDividendsPerShare(snp, i, iStart);
      } else if (divMethod == DividendMethod.NO_REINVEST_QUARTERLY) {
        // Dividends at the end of every quarter (march, june, september, december).
        divCash += shares * getQuarterlyDividendsPerShare(snp, i, iStart);
      } else {
        assert divMethod == DividendMethod.IGNORE_DIVIDENDS;
        // Nothing to do when we're ignoring dividends.
      }

      // Apply the dividends (if any).
      double price = snp.get(i, Shiller.PRICE);
      shares += divReinvest / price;

      // Add data point for current value.
      double value = divCash + shares * price;
      seq.addData(new FeatureVec(2, value, divCash), timeMS);
    }
    return seq;
  }

  public static Sequence calcCorrelation(Sequence returns1, Sequence returns2, int window)
  {
    double[] r1 = new double[returns1.length() - 1];
    double[] r2 = new double[r1.length];
    for (int i = 1; i <= r1.length; ++i) {
      r1[i - 1] = returns1.get(i, 0) / returns1.get(i - 1, 0);
      r2[i - 1] = returns2.get(i, 0) / returns2.get(i - 1, 0);
    }

    Sequence corr = new Sequence(String.format("Correlation (%s): %s vs. %s", TimeLib.formatDurationMonths(window),
        returns1.getName(), returns2.getName()));
    double[] a = new double[window];
    double[] b = new double[window];
    for (int i = window; i < r1.length; ++i) {
      Library.copy(r1, a, i - window, 0, window);
      Library.copy(r2, b, i - window, 0, window);
      corr.addData(Library.correlation(a, b), returns1.getTimeMS(i - window / 2));
    }
    return corr;
  }

  public static Sequence calcLeveragedReturns(Sequence cumulativeReturns, double leverage)
  {
    if (leverage == 1.0) {
      return cumulativeReturns;
    }

    Sequence leveraged = new Sequence(String.format("%s (L=%.3f)", cumulativeReturns.getName(), leverage));
    leveraged.addData(new FeatureVec(1, cumulativeReturns.get(0, 0)), cumulativeReturns.getTimeMS(0));
    for (int i = 1; i < cumulativeReturns.size(); ++i) {
      double r = getReturn(cumulativeReturns, i - 1, i);
      double lr = leverage * (r - 1.0) + 1.0;
      double v = Math.max(0.0, lr * leveraged.get(i - 1, 0));
      leveraged.addData(new FeatureVec(1, v), cumulativeReturns.getTimeMS(i));
    }
    return leveraged;
  }

  public static double calcEqualizingLeverage(Sequence cumulativeReturns, double desiredCAGR)
  {
    CumulativeStats stats = CumulativeStats.calc(cumulativeReturns);
    double ratio = desiredCAGR / stats.cagr;
    double low = ratio / 2.0;
    double high = ratio * 2.0;
    double leverage = 1.0;

    int iter = 1;
    while (iter < 100 && Math.abs(stats.cagr - desiredCAGR) > 1e-5) {
      leverage = (low + high) / 2.0;
      Sequence levSeq = calcLeveragedReturns(cumulativeReturns, leverage);
      stats = CumulativeStats.calc(levSeq);
      // System.out.printf("%d: [%f, %f] -> %f (%f)\n", iter, low, high, stats.cagr, desiredCAGR);
      if (stats.cagr < desiredCAGR) {
        low = leverage;
      } else {
        high = leverage;
      }
      ++iter;
    }

    return leverage;
  }

  /**
   * Returns the same name with a {@literal <br/>} inserted before the last open paren.
   * 
   * Examples:
   * <ul>
   * <li>"foo" -> "foo"
   * <li>"foo (bar)" -> "foo{@literal <br/>} (bar)"
   * <li>"foo (bar) (buzz)" -> "foo (bar){@literal <br/>}(buzz)"
   * </ul>
   * 
   * @param name the name to modify
   * @return name with {@literal <br/>} inserted before last open paren
   */
  public static String getNameWithBreak(String name)
  {
    if (name == null) {
      return "";
    }
    int i = name.lastIndexOf(" (");
    if (i >= 0) {
      return name.substring(0, i) + "<br/>" + name.substring(i + 1);
    } else {
      return name;
    }
  }

  /**
   * Returns the same name with base name bolded.
   * 
   * Examples:
   * <ul>
   * <li>"foo" -> "<b>foo</b>"
   * <li>"foo (bar)" -> "<b>foo</b> (bar)"
   * <li>"foo (bar) (buzz)" -> "<b>foo (bar)</b> (buzz)"
   * </ul>
   * 
   * @param name the name to modify
   * @return name with {@literal <br/>} inserted before last open paren
   */
  public static String getBoldedName(String name)
  {
    String base = getBaseName(name);
    if (base.length() > 0) {
      base = String.format("<b>%s</b>", base);
    }
    String suffix = getNameSuffix(name);
    if (suffix.length() > 0) {
      suffix = " " + suffix;
    }
    return base + suffix;
  }

  /**
   * Returns all text before the last open paren.
   * 
   * Examples:
   * <ul>
   * <li>"foo" -> "foo"
   * <li>"foo (bar)" -> "foo"
   * <li>"foo (bar) (buzz)" -> "foo (bar)"
   * </ul>
   * 
   * @param name the name to modify
   * @return all text before last open paren
   */
  public static String getBaseName(String name)
  {
    if (name == null) {
      return "";
    }
    int i = name.lastIndexOf(" (");
    if (i >= 0) {
      return name.substring(0, i);
    } else {
      return name;
    }
  }

  /**
   * Returns everything after (and including) last paren.
   * 
   * Examples:
   * <ul>
   * <li>"foo" -> ""
   * <li>"foo (bar)" -> "(bar)"
   * <li>"foo (bar) (buzz)" -> "(buzz)"
   * </ul>
   * 
   * @param name the name to modify
   * @return all text in last parenthetical
   */
  public static String getNameSuffix(String name)
  {
    if (name == null) {
      return "";
    }
    int i = name.lastIndexOf(" (");
    if (i >= 0) {
      return name.substring(i + 1);
    } else {
      return "";
    }
  }

  public static Sequence calcDrawdown(Sequence cumulativeReturns)
  {
    Sequence seq = new Sequence(cumulativeReturns.getName() + " - Drawdown");
    final int N = cumulativeReturns.size();
    double peakReturn = 1.0;
    double drawdown = 0.0;

    final double firstValue = cumulativeReturns.getFirst(0);
    seq.addData(0.0, cumulativeReturns.getStartMS());
    for (int i = 1; i < N; ++i) {
      double value = cumulativeReturns.get(i, 0) / firstValue;
      if (value < peakReturn) {
        drawdown = 100.0 * (peakReturn - value) / peakReturn;
      } else if (value > peakReturn) {
        peakReturn = value;
        drawdown = 0.0;
      }
      seq.addData(-drawdown, cumulativeReturns.getTimeMS(i));
    }

    assert seq.length() == cumulativeReturns.length();
    return seq;
  }

  public static RetirementStats[] filter(RetirementStats[] results)
  {
    Arrays.sort(results, new Comparator<RetirementStats>()
    {
      @Override
      public int compare(RetirementStats a, RetirementStats b)
      {
        if (a.principal < b.principal)
          return -1;
        if (a.principal > b.principal)
          return 1;

        if (a.percentile10 > b.percentile10)
          return -1;
        if (a.percentile10 > b.percentile10)
          return 1;

        if (a.percentile25 > b.percentile25)
          return -1;
        if (a.percentile25 > b.percentile25)
          return 1;

        return a.name.compareTo(b.name);
      }
    });
    List<RetirementStats> winners = new ArrayList<>();
    for (int i = 0; i < results.length;) {
      double maxPrincipal = Math.ceil(results[i].principal / 5000.0) * 5000.0;
      int iBest = i;
      int j = i + 1;
      while (j < results.length && results[j].principal <= maxPrincipal) {
        if (results[j].percentile10 > results[iBest].percentile10) {
          iBest = j;
        }
        ++j;
      }
      winners.add(results[iBest]);
      i = j;
    }
    return winners.toArray(new RetirementStats[winners.size()]);
  }

  /** Filter a list of strategies so that only "dominating" ones remain. */
  public static void filterStrategies(List<String> candidates, SequenceStore store)
  {
    final double diffMargin = 0.01;
    final int N = candidates.size();
    for (int i = 0; i < N; ++i) {
      String name1 = candidates.get(i);
      if (name1 == null) {
        continue;
      }
      CumulativeStats cstats1 = store.getCumulativeStats(name1);
      DurationalStats dstats1 = store.getDurationalStats(name1);
      for (int j = i + 1; j < N; ++j) {
        String name2 = candidates.get(j);
        if (name2 == null) {
          continue;
        }
        CumulativeStats cstats2 = store.getCumulativeStats(name2);

        if (cstats1.dominates(cstats2) || cstats1.compareTo(cstats2) == 0) {
          candidates.set(j, null);
          continue;
        }

        if (cstats2.dominates(cstats1)) {
          candidates.set(i, null);
          break;
        }

        final double eps = 1e-6;
        DurationalStats dstats2 = store.getDurationalStats(name2);
        if (dstats1 != null && dstats2 != null) {
          ComparisonStats.Results comp = ComparisonStats.calcFromDurationReturns(dstats1.durationReturns,
              dstats2.durationReturns, store.getLastStatsDuration(), diffMargin);
          if (comp.winPercent1 > 100.0 - eps) {
            candidates.set(j, null);
            continue;
          }

          if (comp.winPercent2 > 100.0 - eps) {
            candidates.set(i, null);
            break;
          }
        }
      }
    }

    // Remove all null entries.
    candidates.removeIf(new Predicate<String>()
    {
      @Override
      public boolean test(String name)
      {
        return name == null;
      }
    });
  }

  public static void filterStrategies(List<CumulativeStats> cstats)
  {
    final int N = cstats.size();
    for (int i = 0; i < N; ++i) {
      CumulativeStats cstats1 = cstats.get(i);
      if (cstats1 == null) {
        continue;
      }
      for (int j = i + 1; j < N; ++j) {
        CumulativeStats cstats2 = cstats.get(j);
        if (cstats2 == null) {
          continue;
        }

        if (cstats1.dominates(cstats2) || cstats1.compareTo(cstats2) == 0) {
          cstats.set(j, null);
          continue;
        }

        if (cstats2.dominates(cstats1)) {
          cstats.set(i, null);
          break;
        }
      }
    }

    // Remove all null entries.
    cstats.removeIf(new Predicate<CumulativeStats>()
    {
      @Override
      public boolean test(CumulativeStats cstats)
      {
        return (cstats == null);
      }
    });
  }

  public static Sequence daily2monthly(Sequence daily)
  {
    return daily2monthly(daily, 0, 0);
  }

  public static Sequence daily2monthly(Sequence daily, int dim, int nJitter)
  {
    // TODO verify that we don't miss/skip any months.
    final int minDaysData = 12;
    final int N = daily.length();
    Calendar cal = TimeLib.now();
    List<Integer> monthEnds = new ArrayList<>();

    Sequence monthly = new Sequence(daily.getName());
    int i = 0;
    while (i < N) {
      FeatureVec v = daily.get(i);
      cal.setTimeInMillis(v.getTime());
      int month = cal.get(Calendar.MONTH);
      FeatureVec m = new FeatureVec(5);
      m.fill(v.get(0));
      int nn = 1;
      int j = i + 1;
      for (; j < N; ++j) {
        FeatureVec w = daily.get(j);
        cal.setTimeInMillis(w.getTime());
        if (cal.get(Calendar.MONTH) == month) {
          double x = w.get(dim);
          m.set(MonthlyClose, x);
          m.set(MonthlyAverage, m.get(MonthlyAverage) + x);
          ++nn;
          if (x < m.get(MonthlyLow)) {
            m.set(MonthlyLow, x);
          }
          if (x > m.get(MonthlyHigh)) {
            m.set(MonthlyHigh, x);
          }
          m.set(MonthlyMisc, x);
        } else {
          break;
        }
      }

      int n = j - i;
      assert nn == n;
      if (n >= minDaysData) {
        monthEnds.add(j - 1);
        m.set(MonthlyAverage, m.get(MonthlyAverage) / n);
        cal.setTimeInMillis(v.getTime());
        cal.set(Calendar.DATE, 1); // Monthly data is always first of month
        cal.set(Calendar.MILLISECOND, 0);
        monthly.addData(m, cal.getTimeInMillis());
      }

      i = j;
    }

    if (nJitter > 0) {
      assert monthEnds.size() == monthly.size();
      Random rng = new Random();
      int offset = -nJitter + rng.nextInt(nJitter * 2 + 1);
      for (i = 0; i < monthly.size(); ++i) {
        // int offset = -nJitter + rng.nextInt(nJitter * 2 + 1);
        assert offset >= -nJitter && offset <= nJitter;
        int x = monthEnds.get(i);
        int y = Math.min(Math.max(x + offset, 0), daily.length() - 1);
        monthly.get(i).set(MonthlyClose, daily.get(y, dim));
        monthEnds.set(i, y);
        // System.out.printf("%d: %d=[%s] -> [%s]\n", i, x, Library.formatDate(daily.getTimeMS(x)),
        // Library.formatDate(daily.getTimeMS(y)));
      }

      double alpha = 0.8;
      double ema = daily.get(0, dim);
      int iNext = 0;
      for (i = 0; i < daily.size() && iNext < monthEnds.size(); ++i) {
        ema = ema * alpha + daily.get(i, dim) * (1.0 - alpha);
        if (i == monthEnds.get(iNext)) {
          monthly.get(iNext).set(MonthlyMisc, ema);
          ema = daily.get(Math.min(i + 1, daily.length() - 1), dim);
          ++iNext;
        }
      }
    }

    return monthly;
  }

  public static Sequence pad(Sequence seq, Sequence ref, double value)
  {
    Sequence padded = new Sequence(seq.getName());

    // Pad front of sequence.
    if (seq.getStartMS() > ref.getStartMS()) {
      int offset = ref.getClosestIndex(seq.getStartMS());
      assert seq.getStartMS() == ref.getTimeMS(offset);
      Sequence prefix = new Sequence();
      for (int i = 0; i < offset; ++i) {
        prefix.addData(value, ref.getTimeMS(i));
      }
      padded.append(prefix);
    }

    // Copy sequence.
    padded.append(seq);

    // Pad back of sequence.
    if (seq.getEndMS() < ref.getEndMS()) {
      int offset = ref.getClosestIndex(seq.getEndMS());
      assert seq.getEndMS() == ref.getTimeMS(offset);
      Sequence suffix = new Sequence();
      for (int i = offset + 1; i < ref.length(); ++i) {
        suffix.addData(value, ref.getTimeMS(i));
      }
      padded.append(suffix);
    }

    assert padded.length() >= ref.length();
    assert padded.getStartMS() <= ref.getStartMS();
    assert padded.getEndMS() >= ref.getEndMS();

    return padded;
  }

  /**
   * Calculate growth of cash given interest rates
   * 
   * @param interestRates interest rates (annualized, 4.3=4.3%)
   * @return cumulative returns
   */
  public static Sequence calcInterestReturns(Sequence interestRates)
  {
    Sequence seq = new Sequence("cash");
    double balance = 1.0;
    seq.addData(balance, interestRates.getStartMS());
    for (int i = 1; i < interestRates.length(); ++i) {
      double annualRate = interestRates.get(i, 0);
      double annualMul = FinLib.ret2mul(annualRate);
      double monthlyRate = Math.pow(annualMul, Library.ONE_TWELFTH);
      balance *= monthlyRate;
      seq.addData(balance, interestRates.getTimeMS(i));
    }
    return seq;
  }

  public static String formatDollars(double x)
  {
    if (x < 1500.0) {
      return dollarFormatter.format(x);
    } else if (x < 100000.0) {
      return String.format("%.1fk", x / 1000.0);
    } else if (x < 1000000.0) {
      return String.format("%.0fk", x / 1000.0);
    } else {
      return String.format("%.1fm", x / 1000000.0);
    }
  }

  public static String buildMixedName(String[] names, int[] percents)
  {
    assert names.length == percents.length;

    StringBuilder sb = new StringBuilder(names[0]);
    for (int i = 1; i < names.length; ++i) {
      sb.append("/");
      sb.append(names[i]);
    }
    sb.append("-");
    sb.append(percents[0]);
    for (int i = 1; i < percents.length; ++i) {
      sb.append("/");
      sb.append(percents[i]);
    }
    return sb.toString();
  }

  /** Compare dollar amounts. */
  public static boolean equiv(double a, double b)
  {
    return Math.abs(a - b) < 1e-4;
  }

  public static boolean isLTG(long buy, long sell)
  {
    if (sell <= buy) { // handle nonsense case of sell before buy
      return false;
    }

    Calendar cbuy = TimeLib.borrowCal(buy);
    Calendar csell = TimeLib.borrowCal(sell);

    // May be able to determine from year alone.
    int year1 = cbuy.get(Calendar.YEAR);
    int year2 = csell.get(Calendar.YEAR);
    if (year2 <= year1) { // same year => stg
      TimeLib.returnCals(cbuy, csell);
      return false;
    }
    if (year2 > year1 + 1) { // more than one year => ltg
      TimeLib.returnCals(cbuy, csell);
      return true;
    }
    assert year2 == year1 + 1;

    // Next year so month may be enough.
    int month1 = cbuy.get(Calendar.MONTH);
    int month2 = csell.get(Calendar.MONTH);
    if (month2 < month1) {
      TimeLib.returnCals(cbuy, csell);
      return false;
    }
    if (month2 > month1) {
      TimeLib.returnCals(cbuy, csell);
      return true;
    }
    assert month1 == month2;

    // Next year and same month so check day.
    int day1 = cbuy.get(Calendar.DAY_OF_MONTH);
    int day2 = csell.get(Calendar.DAY_OF_MONTH);

    // Special case for Feb 28: sell on following Feb 29 is still STG.
    if (month1 == 1 && day1 == 28) {
      TimeLib.returnCals(cbuy, csell);
      return false;
    }

    TimeLib.returnCals(cbuy, csell);
    return day2 > day1;
  }

  public static long compareCash(double a, double b)
  {
    long x = Math.round(a * 1000.0);
    long y = Math.round(b * 1000.0);
    return x - y;
  }
}
