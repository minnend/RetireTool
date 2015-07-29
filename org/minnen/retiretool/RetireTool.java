package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RetireTool
{
  public static final double SOC_SEC_AT70 = 3480.00; // http://www.ssa.gov/oact/quickcalc/

  public enum DividendMethod {
    NO_REINVEST, MONTHLY, QUARTERLY
  };

  public enum Inflation {
    Ignore, Include
  };

  public static DecimalFormat currencyFormatter = new DecimalFormat("#,###.00");

  /**
   * Compute compound annual growth rate (CAGR) based on total multiplier.
   * 
   * @param totalReturn principal multiplier over full term (e.g., 30% gain = 1.3)
   * @param nMonths number of months in term
   * @return average annual return as a percentage (e.g., 4.32 = 4.32%/yr)
   */
  public static double getAnnualReturn(double totalReturn, int nMonths)
  {
    if (nMonths < 1) {
      return 0.0;
    }
    // x^n = y -> x = y ^ (1/n)
    return (Math.pow(totalReturn, 12.0 / nMonths) - 1) * 100;
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
    System.out.printf("Data: %d entries in [%.2f%%, %.2f%%]\n", na, vmin, vmax);

    // figure out where to start
    double hleftCenter = binCenter - binWidth / 2.0;
    double hleft = hleftCenter + Math.floor((vmin - hleftCenter) / binWidth) * binWidth;
    System.out.printf("binCenter=%f   binWidth=%f  hleft=%f\n", binCenter, binWidth, hleft);
    Sequence h = new Sequence("Histogram: " + rois.getName());
    int i = 0;
    while (i < na) {
      assert (a[i] >= hleft);
      double hright = hleft + binWidth;

      // find all data points in [hleft, hright)
      int j = i;
      while (j < na) {
        if (a[j] >= hleft && a[j] < hright) {
          ++j;
        } else {
          break;
        }
      }

      // add data point for this bin
      int n = j - i;
      double frac = (double) n / na;
      h.addData(new FeatureVec(3, (hleft + hright) / 2, n, frac));

      // move to next bin
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

  public static void printReturnLikelihoods(Sequence cumulativeReturns)
  {
    Sequence seqLik = null;
    int[] years = new int[] { 1, 5, 10, 15, 20, 30, 40, 50 };
    for (int i = 0; i < years.length; i++) {
      System.out.printf("Processing %d: %d years\n", i + 1, years[i]);
      Sequence r = calcReturnsForDuration(cumulativeReturns, years[i] * 12);
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

  public static void printWithdrawalLikelihoods(Shiller shiller, Sequence cumulativeReturns, int numYears,
      double expenseRatio)
  {
    System.out.printf("Withdrawal Likelihoods over %d years:\n", numYears);
    double[] wrates = new double[] { 2.0, 2.5, 3.0, 3.5, 3.75, 4.0, 4.25, 4.5, 5.0 };
    int months = numYears * 12;
    for (double wrate : wrates) {
      // System.out.printf("Processing Withdrawal Rate: %.2f%%\n", wrate);
      double principal = 1000000.0;
      double salary = principal * wrate / 100.0;
      int nData = shiller.length();
      int n = 0;
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + months >= nData)
          break; // not enough data
        double endBalance = calcEndBalance(shiller, cumulativeReturns, principal, salary, expenseRatio, true, 0, 0,
            0.0, i, months);
        if (endBalance > 0.0)
          ++nOK;
        ++n;
      }
      System.out.printf("Withdrawal Rate: %.2f  %d / %d = %.2f%%\n", wrate, nOK, n, 100.0 * nOK / n);
    }
  }

  /**
   * Calculate returns for all periods with the given duration.
   * 
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
   * @param months number of months in the market
   * @return sequence containing CAGRs for each time period of the given duration.
   */
  public static Sequence calcReturnsForDuration(Sequence cumulativeReturns, int nMonths)
  {
    final int N = cumulativeReturns.size();
    Sequence rois = new Sequence("ROIs: " + Library.getDurationString(nMonths));
    for (int i = 0; i + nMonths < N; i++) {
      double roi = getReturn(cumulativeReturns, i, i + nMonths);
      double cagr = RetireTool.getAnnualReturn(roi, nMonths);
      rois.addData(cagr, cumulativeReturns.getTimeMS(i));
    }
    return rois;
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
   * @return list of starting indices where we run out of money.
   */
  public static List<Integer> calcSavingsTarget(Shiller shiller, Sequence cumulativeReturns, double salary,
      double minLikelihood, int nYears, double expenseRatio, int retireAge, int ssAge, double ssMonthly,
      double desiredRunwayYears)
  {
    int nMonths = nYears * 12;
    int nData = shiller.length();

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
        double adjSalary = shiller.adjustForInflation(salary, nData - 1, i);
        double adjPrincipal = shiller.adjustForInflation(principal, nData - 1, i);
        double finalSalary = shiller.adjustForInflation(salary, nData - 1, i + nMonths);
        // System.out.printf("%.2f -> %d (%s): %.2f\n", salary, i, Library.formatDate(snp.getTimeMS(i)),
        // adjustedSalary);
        double endBalance = calcEndBalance(shiller, cumulativeReturns, adjPrincipal, adjSalary, expenseRatio, true,
            retireAge, ssAge, ssMonthly, i, nMonths);
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

  public static void genReturnChart(Shiller shiller, Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    int percentStock = 60;
    int percentBonds = 40;
    // int percentCash = 100 - (percentStock + percentBonds);

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;
    int rebalanceMonths = 12;

    Sequence prices = shiller.getStockData();

    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence bondsHold = shiller.calcBondReturnSeqHold(iStart, iEnd, inflation);
    Sequence snp = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence snpNoDiv = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.NO_REINVEST, inflation);
    Sequence mixed = shiller.calcMixedReturnSeq(iStart, iEnd - iStart, percentStock, percentBonds, rebalanceMonths,
        inflation);
    Sequence momentum = Strategy.calcMomentumReturnSeq(nMonthsMomentum, snp, bonds);
    Sequence sma = Strategy.calcSMAReturnSeq(nMonthsSMA, prices, snp, bonds);
    Sequence raa = Strategy.calcMixedReturnSeq(new Sequence[] { sma, momentum }, new double[] { 50, 50 },
        rebalanceMonths);
    raa.setName("RAA");
    Sequence multiMomRisky = Strategy.calcMultiMomentumReturnSeq(snp, bonds, false);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturnSeq(snp, bonds, true);

    Sequence[] all = new Sequence[] { bonds, bondsHold, snp, snpNoDiv, mixed, momentum, sma, raa, multiMomRisky,
        multiMomSafe };
    InvestmentStats[] stats = new InvestmentStats[all.length];

    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == N;
      stats[i] = InvestmentStats.calcInvestmentStats(all[i]);
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), stats[i].cagr));
      System.out.println(stats[i]);
    }

    Chart.saveLineChart(file, "Cumulative Market Returns", 1200, 600, true, sma, raa, momentum, snp, mixed, bonds,
        bondsHold, multiMomRisky, multiMomSafe);
  }

  public static String[] getLabelsFromHistogram(Sequence histogram)
  {
    String[] labels = new String[histogram.length()];
    for (int i = 0; i < labels.length; ++i) {
      labels[i] = String.format("%.1f", histogram.get(i, 0));
    }
    return labels;
  }

  public static void genReturnViz(Shiller shiller, int nMonths, Inflation inflation, File fileScatter, File fileBar,
      File fileExcessReturns) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    int percentStock = 60;
    int percentBonds = 40;
    // int percentCash = 100 - (percentStock + percentBonds);

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;
    int rebalanceMonths = 12;

    Sequence prices = shiller.getStockData();

    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence snp = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence mixed = shiller.calcMixedReturnSeq(iStart, iEnd - iStart, percentStock, percentBonds, rebalanceMonths,
        inflation);
    Sequence momentum = Strategy.calcMomentumReturnSeq(nMonthsMomentum, snp, bonds);
    Sequence sma = Strategy.calcSMAReturnSeq(nMonthsSMA, prices, snp, bonds);
    Sequence raa = Strategy.calcMixedReturnSeq(new Sequence[] { sma, momentum }, new double[] { 50, 50 },
        rebalanceMonths);
    raa.setName("RAA");

    Sequence[] assets = new Sequence[] { momentum, sma, raa, snp, bonds, mixed };
    Sequence[] returns = new Sequence[assets.length];
    double vmin = 0.0, vmax = 0.0;
    for (int i = 0; i < assets.length; ++i) {
      returns[i] = calcReturnsForDuration(assets[i], nMonths);
      returns[i].setName(assets[i].getName());
      double[] a = returns[i].extractDim(0);
      Arrays.sort(a);
      if (i == 0 || a[0] < vmin) {
        vmin = a[0];
      }
      if (i == 0 || a[a.length - 1] > vmax) {
        vmax = a[a.length - 1];
      }
    }
    Sequence compareReturnsA = returns[3];
    Sequence compareReturnsB = returns[2];

    String title = "Paired Returns - " + Library.getDurationString(nMonths);
    Chart.saveHighChartScatter(fileScatter, title, 800, 600, 0, compareReturnsA, compareReturnsB);

    title = "Excess Returns - " + Library.getDurationString(nMonths);
    Sequence excessReturns = compareReturnsB.sub(compareReturnsA);
    Sequence histogramExcess = computeHistogram(excessReturns, 0.5, 0.0);
    histogramExcess.setName(String.format("%s vs. %s", compareReturnsB.getName(), compareReturnsA.getName()));
    String[] labels = getLabelsFromHistogram(histogramExcess);
    String[] colors = new String[labels.length];
    for (int i = 0; i < colors.length; ++i) {
      double x = histogramExcess.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }
    Chart.saveHighChart(fileExcessReturns, Chart.ChartType.Bar, title, labels, colors, 1200, 600, false, 1,
        histogramExcess);

    Sequence[] histograms = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      histograms[i] = computeHistogram(returns[i], vmin, vmax, 0.5, 0.0);
      histograms[i].setName(assets[i].getName());
    }

    title = "Histogram of Returns - " + Library.getDurationString(nMonths);
    labels = getLabelsFromHistogram(histograms[0]);
    Chart.saveHighChart(fileBar, Chart.ChartType.Bar, title, labels, null, 1200, 600, false, 1, histograms);
  }

  public static void genDuelViz(Shiller shiller, File dir) throws IOException
  {
    assert dir.isDirectory();

    int nMonths = 10 * 12;
    int iStart = 0;
    int iEnd = shiller.length() - 1;
    Inflation inflation = Inflation.Ignore;

    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence stock = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);

    Strategy.calcMomentumStats(stock, bonds);

    Sequence momentum1 = Strategy.calcMomentumReturnSeq(1, stock, bonds);
    Sequence momentum3 = Strategy.calcMomentumReturnSeq(3, stock, bonds);
    Sequence momentum12 = Strategy.calcMomentumReturnSeq(12, stock, bonds);
    Sequence multiMomRisky = Strategy.calcMultiMomentumReturnSeq(stock, bonds, false);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturnSeq(stock, bonds, true);

    Sequence[] assets = new Sequence[] { momentum1, momentum3, momentum12, multiMomRisky, multiMomSafe };
    Sequence[] returns = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      returns[i] = calcReturnsForDuration(assets[i], nMonths);
      returns[i].setName(assets[i].getName());
    }
    Sequence returnsA = returns[4];
    Sequence returnsB = returns[3];

    String title = String.format("Momentum Duel (%s)", Library.getDurationString(nMonths));
    Chart.saveHighChartScatter(new File(dir, "momentum-duel-scatter.html"), title, 800, 600, 0, returnsA, returnsB);

    title = "Excess Returns: " + Library.getDurationString(nMonths);
    Sequence excessReturns = returnsB.sub(returnsA);
    Sequence histogramExcess = computeHistogram(excessReturns, 0.5, 0.0);
    histogramExcess.setName(String.format("%s vs. %s", returnsB.getName(), returnsA.getName()));
    String[] labels = getLabelsFromHistogram(histogramExcess);
    String[] colors = new String[labels.length];
    for (int i = 0; i < colors.length; ++i) {
      double x = histogramExcess.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }
    Chart.saveHighChart(new File(dir, "momentum-duel-excess.html"), Chart.ChartType.Bar, title, labels, colors, 1200,
        600, false, 1, histogramExcess);
  }

  public static void genStockBondMixSweepChart(Shiller shiller, Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);

    int N = iEnd - iStart + 1;
    Sequence snp = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 6;

    Sequence[] seqs = new Sequence[percentStock.length];
    for (int i = 0; i < percentStock.length; ++i) {
      Sequence mix = Strategy.calcMixedReturnSeq(new Sequence[] { snp, bonds }, new double[] { percentStock[i],
          100 - percentStock[i] }, rebalanceMonths);

      double cagr = RetireTool.getAnnualReturn(mix.getLast(0), N);
      mix.setName(String.format("Mix-[%d/%d] (%.2f%%)", percentStock[i], 100 - percentStock[i], cagr));
      seqs[i] = mix;
      // System.out.println(InvestmentStats.calcInvestmentStats(mix));
    }

    Chart.saveLineChart(file, "Cumulative Market Returns: Stock/Bond Mix", 1200, 600, true, seqs);
  }

  public static void genSMASweepChart(Shiller shiller, Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence prices = shiller.getStockData();
    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence snp = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);

    int[] months = new int[] { 1, 2, 3, 4, 5, 6, 9, 10, 12, 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length];
    for (int i = 0; i < months.length; ++i) {
      Sequence sma = Strategy.calcSMAReturnSeq(months[i], prices, snp, bonds);
      double cagr = RetireTool.getAnnualReturn(sma.getLast(0), N);
      sma.setName(String.format("SMA-%d (%.2f%%)", months[i], cagr));
      seqs[i] = sma;
      // System.out.println(InvestmentStats.calcInvestmentStats(sma));
    }

    Chart.saveLineChart(file, "Cumulative Market Returns: SMA Strategy", 1200, 600, true, seqs);
  }

  public static void genMomentumSweepChart(Shiller shiller, Inflation inflation, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence bonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence snp = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);

    int[] months = new int[] { 1, 2, 3, 5, 6, 9, 10, 12 }; // , 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length + 1];
    for (int i = 0; i < months.length; ++i) {
      Sequence mom = Strategy.calcMomentumReturnSeq(months[i], snp, bonds);
      double cagr = RetireTool.getAnnualReturn(mom.getLast(0), N);
      mom.setName(String.format("Momentum-%d (%.2f%%)", months[i], cagr));
      seqs[i] = mom;
      // System.out.println(InvestmentStats.calcInvestmentStats(mom));
    }

    Sequence multiMom = Strategy.calcMultiMomentumReturnSeq(snp, bonds, true);
    double cagr = RetireTool.getAnnualReturn(multiMom.getLast(0), N);
    multiMom.setName(String.format("MultiMomentum (%.2f%%)", cagr));
    seqs[months.length] = multiMom;

    Chart.saveLineChart(file, "Cumulative Market Returns: Momentum Strategy", 1200, 600, true, seqs);
  }

  public static void genReturnComparison(Shiller shiller, int numMonths, Inflation inflation, File file)
      throws IOException
  {
    int iStart = 0;// getIndexForDate(1881, 1);
    int iEnd = shiller.length() - 1;

    int percentStocks = 60;
    int percentBonds = 40;

    Sequence cumulativeSNP = shiller.calcSnpReturnSeq(iStart, iEnd - iStart, DividendMethod.MONTHLY, inflation);
    Sequence cumulativeBonds = shiller.calcBondReturnSeqRebuy(iStart, iEnd, inflation);
    Sequence cumulativeMixed = shiller.calcMixedReturnSeq(iStart, iEnd - iStart, percentStocks, percentBonds, 6,
        inflation);
    assert cumulativeSNP.length() == cumulativeBonds.length();

    Sequence snp = new Sequence("S&P");
    Sequence bonds = new Sequence("Bonds");
    Sequence mixed = new Sequence(String.format("Mixed (%d/%d)", percentStocks, percentBonds));
    Sequence snpPremium = new Sequence("S&P Premium");
    int numBondWins = 0;
    int numStockWins = 0;
    for (int i = 0; i + numMonths < cumulativeSNP.size(); ++i) {
      int j = i + numMonths;
      double snpCAGR = RetireTool.getAnnualReturn(cumulativeSNP.get(j, 0) / cumulativeSNP.get(i, 0), numMonths);
      double bondCAGR = RetireTool.getAnnualReturn(cumulativeBonds.get(j, 0) / cumulativeBonds.get(i, 0), numMonths);
      double mixedCAGR = RetireTool.getAnnualReturn(cumulativeMixed.get(j, 0) / cumulativeMixed.get(i, 0), numMonths);
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

    String title = numMonths % 12 == 0 ? String.format("%d-Year Future Market Returns", numMonths / 12) : String
        .format("%d-Month Future Market Returns", numMonths);
    Chart.saveLineChart(file, title, 1200, 800, false, snp, bonds);
  }

  public static double getReturn(Sequence cumulativeReturns, int iFrom, int iTo)
  {
    return cumulativeReturns.get(iTo, 0) / cumulativeReturns.get(iFrom, 0);
  }

  /**
   * Compute ending balance; not inflation-adjusted, assuming we re-invest dividends
   * 
   * @param shiller shiller data object with S&P prices and dividends
   * @param cumulativeReturns sequence of cumulative returns for the investment strategy
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
  public static double calcEndBalance(Shiller shiller, Sequence cumulativeReturns, double principal,
      double annualWithdrawal, double expenseRatio, boolean adjustWithdrawalForInflation, double retireAge,
      double ssAge, double ssMonthly, int iStart, int nMonths)
  {
    int nData = shiller.size();
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
        adjMonthlyWithdrawal = shiller.adjustForInflation(monthlyWithdrawal, iStart, i);
      }

      // Get paid by social security if we're old enough.
      if (age >= ssAge) {
        balance += shiller.adjustForInflation(ssMonthly, nData - 1, i);
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

  public static void main(String[] args) throws IOException
  {
    if (args.length != 2) {
      System.err.println("Usage: java ~.ShillerSnp <shiller-data-file> <t-bill-file>");
      System.exit(1);
    }

    Shiller shiller = new Shiller(args[0]);
    Sequence tbills = TBills.loadData(args[1]);

    // long commonStart = Library.calcCommonStart(shiller, tbills);
    // long commonEnd = Library.calcCommonEnd(shiller, tbills);
    //
    // System.out.printf("Shiller: [%s] -> [%s]\n", Library.formatDate(shiller.getStartMS()),
    // Library.formatDate(shiller.getEndMS()));
    // System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()),
    // Library.formatDate(tbills.getEndMS()));
    // System.out.printf("Common: [%s] -> [%s]\n", Library.formatDate(commonStart), Library.formatDate(commonEnd));
    //
    // Sequence tbillsCommon = tbills.subseq(commonStart, commonEnd);
    // System.out.printf("T-Bills (Common): [%s] -> [%s]\n", Library.formatDate(tbillsCommon.getStartMS()),
    // Library.formatDate(tbillsCommon.getEndMS()));

    // shiller.printReturnLikelihoods();
    // shiller.printWithdrawalLikelihoods(30, 0.1);
    // shiller.genReturnChart(Inflation.Ignore, new File("g:/test.html"));
    // int[] years = new int[] { 1, 2, 5, 10, 15, 20, 30, 40, 50 };
    // for (int i = 0; i < years.length; ++i) {
    // shiller.genReturnComparison(years[i] * 12, Inflation.Ignore, new File("g:/test.html"));
    // }

    // genReturnViz(shiller, 30 * 12, Inflation.Ignore, new File("g:/web/scatter-returns.html"), new File(
    // "g:/web/histogram-returns.html"), new File("g:/web/histogram-excess-returns.html"));
    genReturnChart(shiller, Inflation.Ignore, new File("g:/web/cumulative-returns.html"));
    // genSMASweepChart(shiller, Inflation.Ignore, new File("g:/web/sma-sweep.html"));
    genMomentumSweepChart(shiller, Inflation.Ignore, new File("g:/web/momentum-sweep.html"));
    // genStockBondMixSweepChart(shiller, Inflation.Ignore, new File("g:/web/stock-bond-mix-sweep.html"));
    genDuelViz(shiller, new File("g:/web/"));
    System.exit(0);

    // int retireAge = 65;
    // int ssAge = 70;
    // double expectedSocSecFraction = 0.7; // assume we'll only get a fraction of current SS estimate
    // double expectedMonthlySS = SOC_SEC_AT70 * expectedSocSecFraction;
    // int nYears = 105 - retireAge;
    // double expenseRatio = 0.1;
    // double likelihood = 0.99;
    // double taxRate = 0.30;
    // double desiredMonthlyCash = 6000.00;
    // double desiredRunwayYears = 1.0;
    // double salary = Shiller.calcAnnualSalary(desiredMonthlyCash, taxRate);
    // System.out.printf("Salary: %s\n", Shiller.currencyFormatter.format(salary));
    // List<Integer> failures = shiller.calcSavingsTarget(salary, likelihood, nYears, expenseRatio, retireAge, ssAge,
    // expectedMonthlySS, desiredRunwayYears);
    // if (!failures.isEmpty()) {
    // System.out.println("Failures:");
    // for (int i : failures) {
    // System.out.printf(" [%s] -> [%s]\n", Library.formatDate(shiller.data.getTimeMS(i)),
    // Library.formatDate(shiller.data.getTimeMS(i + nYears * 12)));
    // }
    // }

    System.exit(0);
  }
}
