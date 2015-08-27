package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.minnen.retiretool.Chart.ChartType;
import org.minnen.retiretool.Strategy.Disposition;

public class RetireTool
{
  public static final double SOC_SEC_AT70 = 3480.00; // http://www.ssa.gov/oact/quickcalc/

  public static final int    GRAPH_WIDTH  = 710;
  public static final int    GRAPH_HEIGHT = 450;

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
      return 1.0;
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

  public static void printReturnLikelihoods(Sequence cumulativeReturns, boolean bInvert)
  {
    Sequence seqLik = calcReturnLikelihoods(cumulativeReturns, bInvert);
    for (FeatureVec fv : seqLik) {
      System.out.printf("%.3f", fv.get(0));
      for (int i = 0; i < seqLik.getNumDims() - 1; i++)
        System.out.printf(" %f", fv.get(i + 1));
      System.out.println();
    }
  }

  public static void printWithdrawalLikelihoods(Sequence cumulativeReturns, Sequence cpi, int numYears,
      double expenseRatio)
  {
    System.out.printf("Withdrawal Likelihoods over %d years:\n", numYears);
    double[] wrates = new double[] { 2.0, 2.5, 3.0, 3.5, 3.75, 4.0, 4.25, 4.5, 5.0 };
    int months = numYears * 12;
    for (double wrate : wrates) {
      // System.out.printf("Processing Withdrawal Rate: %.2f%%\n", wrate);
      double principal = 1000000.0;
      double salary = principal * wrate / 100.0;
      int nData = cumulativeReturns.length();
      int n = 0;
      int nOK = 0;
      for (int i = 0; i < nData; i++) {
        if (i + months >= nData)
          break; // not enough data
        double endBalance = calcEndBalance(cumulativeReturns, cpi, principal, salary, expenseRatio, true, 0, 0, 0.0, i,
            months);
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
   * @return sequence containing returns for each time period of the given duration, if nMonths<12 then the returns are
   *         raw, else they are CAGRs.
   */
  public static Sequence calcReturnsForDuration(Sequence cumulativeReturns, int nMonths)
  {
    final int N = cumulativeReturns.size();
    Sequence rois = new Sequence("ROIs: " + Library.formatDurationMonths(nMonths));
    for (int i = 0; i + nMonths < N; i++) {
      double roi = getReturn(cumulativeReturns, i, i + nMonths);
      if (nMonths >= 12) {
        roi = RetireTool.getAnnualReturn(roi, nMonths);
      } else {
        roi *= 100.0;
      }
      rois.addData(roi, cumulativeReturns.getTimeMS(i));
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
    double annualReturn = ret2mul(annualGrowthRate);
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
  public static List<Integer> calcSavingsTarget(Sequence cumulativeReturns, Sequence cpi, double salary,
      double minLikelihood, int nYears, double expenseRatio, int retireAge, int ssAge, double ssMonthly,
      double desiredRunwayYears)
  {
    int nMonths = nYears * 12;
    int nData = cumulativeReturns.length();

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
        double adjSalary = adjustForInflation(cpi, salary, nData - 1, i);
        double adjPrincipal = adjustForInflation(cpi, principal, nData - 1, i);
        double finalSalary = adjustForInflation(cpi, salary, nData - 1, i + nMonths);
        // System.out.printf("%.2f -> %d (%s): %.2f\n", salary, i, Library.formatDate(snp.getTimeMS(i)),
        // adjustedSalary);
        double endBalance = calcEndBalance(cumulativeReturns, cpi, adjPrincipal, adjSalary, expenseRatio, true,
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

  public static void compareRebalancingMethods(Sequence shiller, File dir) throws IOException
  {
    int iStartData = 0;
    int iEndData = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int percentStock = 60;
    int percentBonds = 40;

    Sequence snpData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence bonds = Bond.calcReturnsRebuy(bondData, iStartData, iEndData);
    bonds.setName("Bonds");
    Sequence stock = calcSnpReturns(snpData, iStartData, iEndData - iStartData, DividendMethod.MONTHLY);
    stock.setName("Stock");

    Sequence mixedNone = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 0.0);
    // mixedNone.setName(String.format("Stock/Bonds-%d/%d (No Rebalance)", percentStock, percentBonds));
    mixedNone.setName("No Rebalance");

    Sequence mixedM6 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 6, 0.0);
    // mixedM6.setName(String.format("Stock/Bonds-%d/%d (Rebalance M6)", percentStock, percentBonds));
    mixedM6.setName("6 Months");

    Sequence mixedM12 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 12, 0.0);
    // mixedM12.setName(String.format("Stock/Bonds-%d/%d (Rebalance M12)", percentStock, percentBonds));
    mixedM12.setName("12 Months");

    Sequence mixedB5 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 5.0);
    // mixedB5.setName(String.format("Stock/Bonds-%d/%d (Rebalance B5)", percentStock, percentBonds));
    mixedB5.setName("5% Band");

    Sequence mixedB10 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, 0, 10.0);
    // mixedB10.setName(String.format("Stock/Bonds-%d/%d (Rebalance B10)", percentStock, percentBonds));
    mixedB10.setName("10% Band");

    Sequence[] all = new Sequence[] { stock, bonds, mixedNone, mixedM6, mixedM12, mixedB5, mixedB10 };
    InvestmentStats[] stats = new InvestmentStats[all.length];

    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == all[0].length();
      stats[i] = InvestmentStats.calcInvestmentStats(all[i]);
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), stats[i].cagr));
      System.out.printf("%s\n", stats[i]);
    }

    Chart.saveLineChart(new File(dir, "rebalance-cumulative.html"), "Cumulative Market Returns", GRAPH_WIDTH,
        GRAPH_HEIGHT, true, all);
    Chart.saveStatsTable(new File(dir, "rebalance-table.html"), GRAPH_WIDTH, true, stats);
  }

  public static void genReturnChart(Sequence shiller, File fileChart, File fileTable) throws IOException
  {
    int iStartData = 0;
    int iEndData = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int percentStock = 60;
    int percentBonds = 40;

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 12;

    Sequence snpData = Shiller.getStockData(shiller, iStartData, iEndData);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEndData);

    Sequence bondsAll = Bond.calcReturnsRebuy(bondData, iStartData, iEndData);
    Sequence stockAll = calcSnpReturns(snpData, iStartData, iEndData - iStartData, DividendMethod.MONTHLY);
    // Sequence stockAllNoDiv = calcSnpReturns(snpData, iStartData, iEndData - iStartData, DividendMethod.NO_REINVEST);

    Sequence bonds = bondsAll.subseq(iStartReturns, iEndData - iStartReturns + 1);
    Sequence stock = stockAll.subseq(iStartReturns, iEndData - iStartReturns + 1);

    Sequence bondsHold = Bond.calcReturnsHold(bondData, iStartReturns, iEndData);

    Sequence stockNoDiv = calcSnpReturns(snpData, iStartReturns, iEndData - iStartReturns, DividendMethod.NO_REINVEST);
    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, rebalanceBand);
    mixed.setName(String.format("Stock/Bonds [%d/%d]", percentStock, percentBonds));
    Sequence momentum = Strategy.calcMomentumReturns(nMonthsMomentum, iStartReturns, stockAll, bondsAll);
    Sequence sma = Strategy.calcSMAReturns(nMonthsSMA, iStartReturns, snpData, stockAll, bondsAll);
    Sequence raa = Strategy.calcMixedReturns(new Sequence[] { sma, momentum }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    raa.setName("RAA");
    Sequence multiMomRisky = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Risky);
    Sequence multiMomMod = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Moderate);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Safe);
    Sequence multiSmaRisky = Strategy
        .calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Risky);
    Sequence multiSmaMod = Strategy.calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll,
        Disposition.Moderate);
    Sequence multiSmaSafe = Strategy.calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Safe);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiMomSafe }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    Sequence[] all = new Sequence[] { bonds, bondsHold, stock, stockNoDiv, mixed, momentum, sma, raa, daa,
        multiMomRisky, multiMomMod, multiMomSafe, multiSmaRisky, multiSmaMod, multiSmaSafe };
    InvestmentStats[] stats = new InvestmentStats[all.length];
    double[] scores = new double[all.length];

    for (int i = 0; i < all.length; ++i) {
      assert all[i].length() == all[0].length();
      stats[i] = InvestmentStats.calcInvestmentStats(all[i]);
      scores[i] = stats[i].calcScore();
      all[i].setName(String.format("%s (%.2f%%)", all[i].getName(), stats[i].cagr));
    }

    Chart.saveLineChart(fileChart, "Cumulative Market Returns", GRAPH_WIDTH, GRAPH_HEIGHT, true, multiSmaRisky, daa,
        multiMomSafe, sma, raa, momentum, stock, mixed, bonds, bondsHold);

    int[] ii = Library.sort(scores, false);
    for (int i = 0; i < all.length; ++i) {
      System.out.printf("%d [%.1f]: %s\n", i + 1, scores[i], stats[ii[i]]);
    }
    Chart.saveStatsTable(fileTable, GRAPH_WIDTH, false, stats);
  }

  public static String[] getLabelsFromHistogram(Sequence histogram)
  {
    String[] labels = new String[histogram.length()];
    for (int i = 0; i < labels.length; ++i) {
      labels[i] = String.format("%.1f", histogram.get(i, 0));
    }
    return labels;
  }

  public static void genReturnViz(Sequence shiller, File fileHistogram, File fileFuture) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = shiller.getIndexForDate(1980, 1);
    // iEnd = shiller.getIndexForDate(2010, 1);

    int nMonths = 20 * 12;
    int percentStock = 60;
    int percentBonds = 40;
    // int percentCash = 100 - (percentStock + percentBonds);

    int nMonthsSMA = 10;
    int nMonthsMomentum = 12;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 0;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stockAll = calcSnpReturns(snpData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(bondData, iStart, iEnd);

    // Chart.saveLineChart(new File(dir, "stock-prices.html"), "S&P 500 Prices", GRAPH_WIDTH, GRAPH_HEIGHT, false,
    // snpData);

    Sequence stock = stockAll.subseq(iStartReturns, iEnd - iStartReturns + 1);
    Sequence bonds = bondsAll.subseq(iStartReturns, iEnd - iStartReturns + 1);

    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, rebalanceBand);
    Sequence momentum = Strategy.calcMomentumReturns(nMonthsMomentum, iStartReturns, stockAll, bondsAll);
    Sequence sma = Strategy.calcSMAReturns(nMonthsSMA, iStartReturns, snpData, stockAll, bondsAll);
    Sequence raa = Strategy.calcMixedReturns(new Sequence[] { sma, momentum }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    raa.setName("RAA");

    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Safe);
    Sequence multiSmaRisky = Strategy
        .calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Risky);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiMomSafe }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    // System.out.printf("Stock Total Return: %f\n", getReturn(stock, 0, stock.length()-1));
    // Chart.printDecadeTable(stockAll);
    Sequence returnLiks = calcReturnLikelihoods(stockAll, true);
    returnLiks = returnLiks.subseq(60, 180);
    Sequence[] liks = new Sequence[returnLiks.getNumDims() - 1];
    int[] years = new int[] { 1, 2, 5, 10, 20, 30, 40 };
    for (int i = 0; i < liks.length; ++i) {
      liks[i] = returnLiks.extractDims(i + 1);
      liks[i].setName(String.format("%d year%s", years[i], years[i] == 1 ? "" : "s"));
      Chart.saveHighChart(new File(String.format("g:/web/return-likelihoods-%d-years.html", years[i])),
          Chart.ChartType.Area, "Return Likelihoods", getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH,
          GRAPH_HEIGHT, 0.0, 1.0, Double.NaN, false, 0, liks[i]);
    }
    Chart.saveHighChart(new File("g:/web/return-likelihoods.html"), Chart.ChartType.Line, "Return Likelihoods",
        getLabelsFromHistogram(returnLiks), null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 1.0, Double.NaN, false, 0, liks);

    // Sequence[] assets = new Sequence[] { multiSmaRisky, daa, multiMomSafe, momentum, sma, raa, stock, bonds, mixed };
    Sequence[] assets = new Sequence[] { bonds };
    Sequence[] returns = new Sequence[assets.length];
    double vmin = 0.0, vmax = 0.0;
    for (int i = 0; i < assets.length; ++i) {
      ReturnStats.printDurationTable(assets[i]);
      returns[i] = calcReturnsForDuration(assets[i], nMonths);

      // for (int j = 0; j < returns[i].length(); ++j) {
      // FeatureVec v = returns[i].get(j);
      // if (v.get(0) > 62.0 || v.get(0) < -42.0) {
      // System.out.printf("%d: %.2f  [%s]\n", j, v.get(0), Library.formatMonth(v.getTime()));
      // }
      // }
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

    Sequence[] histograms = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      histograms[i] = computeHistogram(returns[i], vmin, vmax, 0.5, 0.0);
      histograms[i].setName(assets[i].getName());
    }

    String title = "Histogram of Returns - " + Library.formatDurationMonths(nMonths);
    String[] labels = getLabelsFromHistogram(histograms[0]);
    Chart.saveHighChart(fileHistogram, Chart.ChartType.Bar, title, labels, null, GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN,
        Double.NaN, Double.NaN, false, 1, histograms);

    // Generate histogram showing future returns.
    title = String.format("Future CAGR: %s (%s)", returns[0].getName(), Library.formatDurationMonths(nMonths));
    Chart.saveHighChart(fileFuture, Chart.ChartType.Area, title, null, null, GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN,
        Double.NaN, Double.NaN, false, 0, returns[0]);
  }

  public static void genDuelViz(Sequence shiller, File dir) throws IOException
  {
    assert dir.isDirectory();

    int nMonths = 10 * 12;
    int rebalanceMonths = 12;
    double rebalanceBand = 0.0;
    int iStartReturns = 0;
    int iStartData = 0;
    int iEnd = shiller.length() - 1;

    Sequence snpData = Shiller.getStockData(shiller, iStartData, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStartData, iEnd);

    Sequence stockAll = calcSnpReturns(snpData, iStartData, iEnd - iStartData, DividendMethod.MONTHLY);
    Sequence bondsAll = Bond.calcReturnsRebuy(bondData, iStartData, iEnd);
    stockAll.setName("Stock");
    bondsAll.setName("Bonds");

    Sequence stock = stockAll.subseq(iStartReturns, iEnd - iStartReturns + 1);
    Sequence bonds = bondsAll.subseq(iStartReturns, iEnd - iStartReturns + 1);

    int percentStock = 60;
    int percentBonds = 40;
    assert percentStock + percentBonds == 100;
    Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
        percentBonds }, rebalanceMonths, 0.0);
    mixed.setName(String.format("Stock/Bonds-%d/%d (M12)", percentStock, percentBonds));

    // Sequence mixedB5 = Strategy.calcMixedReturns(new Sequence[] { stock, bonds }, new double[] { percentStock,
    // percentBonds }, 0, rebalanceBand);
    // mixedB5.setName(String.format("Stock/Bonds-%d/%d (B5)", percentStock, percentBonds));

    // Strategy.calcMomentumStats(stockAll, bondsAll);
    // Strategy.calcSmaStats(snpData, stockAll, bondsAll);

    Sequence multiMomRisky = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Risky);
    Sequence multiMomMod = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Moderate);
    Sequence multiMomSafe = Strategy.calcMultiMomentumReturns(iStartReturns, stockAll, bondsAll, Disposition.Safe);
    Sequence multiSmaRisky = Strategy
        .calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Risky);
    Sequence multiSmaMod = Strategy.calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll,
        Disposition.Moderate);
    Sequence multiSmaSafe = Strategy.calcMultiSmaReturns(iStartReturns, snpData, stockAll, bondsAll, Disposition.Safe);
    Sequence daa = Strategy.calcMixedReturns(new Sequence[] { multiSmaRisky, multiMomSafe }, new double[] { 50, 50 },
        rebalanceMonths, rebalanceBand);
    daa.setName("DAA");

    Sequence[] assets = new Sequence[] { stock, bonds, mixed, multiMomRisky, multiMomMod, multiMomSafe, multiSmaRisky,
        multiSmaMod, multiSmaSafe, daa };

    int player1 = 2;
    int player2 = 0;
    ComparisonStats comparison = ComparisonStats.calc(assets[player2], assets[player1]);
    Chart.saveComparisonTable(new File(dir, "duel-comparison.html"), GRAPH_WIDTH, comparison);
    InvestmentStats[] stats = InvestmentStats.calc(assets[player2], assets[player1]);
    Chart.saveStatsTable(new File(dir, "duel-chart.html"), GRAPH_WIDTH, false, stats);

    Chart.saveHighChart(new File(dir, "duel-cumulative.html"), ChartType.Line, "Cumulative Market Returns", null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, 1, 1048576, 1.0, true, 0, assets[player2], assets[player1]);

    // Calculate returns for each strategy for the requested duration.
    Sequence[] returns = new Sequence[assets.length];
    for (int i = 0; i < assets.length; ++i) {
      returns[i] = calcReturnsForDuration(assets[i], nMonths);
      returns[i].setName(assets[i].getName());
    }
    Sequence returnsA = returns[player1];
    Sequence returnsB = returns[player2];

    // Generate scatter plot comparing results.
    String title = String.format("%s vs. %s (%s)", returnsB.getName(), returnsA.getName(),
        Library.formatDurationMonths(nMonths));
    Chart.saveHighChartScatter(new File(dir, "duel-scatter.html"), title, 730, GRAPH_HEIGHT, 0, returnsA, returnsB);

    // Generate histogram summarizing excess returns of B over A.
    title = String.format("Excess Returns: %s vs. %s (%s)", returnsB.getName(), returnsA.getName(),
        Library.formatDurationMonths(nMonths));
    Sequence excessReturns = returnsB.sub(returnsA);
    Sequence histogramExcess = computeHistogram(excessReturns, 0.5, 0.0);
    histogramExcess.setName(String.format("%s vs. %s", returnsB.getName(), returnsA.getName()));
    String[] colors = new String[excessReturns.length()];
    for (int i = 0; i < colors.length; ++i) {
      double x = excessReturns.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }

    Chart.saveHighChart(new File(dir, "duel-returns.html"), Chart.ChartType.Line, title, null, null, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, false, 0, returnsB, returnsA);
    Chart.saveHighChart(new File(dir, "duel-excess-histogram.html"), Chart.ChartType.PosNegArea, title, null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, Double.NaN, Double.NaN, 2.0, false, 0, excessReturns);

    String[] labels = getLabelsFromHistogram(histogramExcess);
    colors = new String[labels.length];
    for (int i = 0; i < colors.length; ++i) {
      double x = histogramExcess.get(i, 0);
      colors[i] = x < -0.001 ? "#df5353" : (x > 0.001 ? "#53df53" : "#dfdf53");
    }
    Chart.saveHighChart(new File(dir, "duel-histogram.html"), Chart.ChartType.Bar, title, labels, colors, GRAPH_WIDTH,
        GRAPH_HEIGHT, Double.NaN, Double.NaN, 32, false, 1, histogramExcess);

    // double[] a = excessReturns.extractDim(0);
    // int[] ii = Library.sort(a, true);
    // for (int i = 0; i < excessReturns.length(); ++i) {
    // System.out.printf("[%s]  %.3f\n", Library.formatMonth(excessReturns.getTimeMS(ii[i])), a[i]);
    // }
  }

  public static void genStockBondMixSweepChart(Sequence shiller, File fileGraph, File fileChart) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence snp = calcSnpReturns(snpData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(bondData, iStart, iEnd);

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 6;
    double rebalanceBand = 0.0;

    Sequence[] all = new Sequence[percentStock.length];
    for (int i = 0; i < percentStock.length; ++i) {
      all[i] = Strategy.calcMixedReturns(new Sequence[] { snp, bonds }, new double[] { percentStock[i],
          100 - percentStock[i] }, rebalanceMonths, rebalanceBand);
      all[i].setName(String.format("%d / %d", percentStock[i], 100 - percentStock[i]));
    }
    InvestmentStats[] stats = InvestmentStats.calc(all);
    Chart.saveHighChart(fileGraph, ChartType.Line, "Cumulative Market Returns: Stock/Bond Mix", null, null,
        GRAPH_WIDTH, GRAPH_HEIGHT, 1.0, 262144.0, 1.0, true, 0, all);
    Chart.saveStatsTable(fileChart, GRAPH_WIDTH, true, stats);

    // all[0].setName("Stock");
    // all[all.length - 1].setName("Bonds");
    // Chart.printDecadeTable(all[0], all[all.length - 1]);
  }

  public static void genEfficientFrontier(Sequence shiller, File dir) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    Sequence stockData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stock = calcSnpReturns(stockData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(bondData, iStart, iEnd);
    assert stock.length() == bonds.length();

    int[] percentStock = new int[] { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 };
    int rebalanceMonths = 12;
    double rebalanceBand = 10.0;
    int[] durations = new int[] { 1, 5, 10, 15, 20, 30 };

    // Generate curve for each decade.
    Calendar cal = Library.now();
    List<Sequence> decades = new ArrayList<Sequence>();
    int iDecadeStart = Library.FindStartofFirstDecade(stockData);
    for (int i = iDecadeStart; i + 120 < stockData.length(); i += 120) {
      cal.setTimeInMillis(stockData.getTimeMS(i));
      Sequence decadeStock = calcSnpReturns(stockData, i, 120, DividendMethod.MONTHLY);
      Sequence decadeBonds = Bond.calcReturnsRebuy(bondData, i, i + 120);
      assert decadeStock.length() == decadeBonds.length();

      Sequence decade = new Sequence(String.format("%ss", cal.get(Calendar.YEAR)));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { decadeStock, decadeBonds }, new double[] {
            pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        InvestmentStats stats = InvestmentStats.calcInvestmentStats(mixed);
        decade.addData(new FeatureVec(2, stats.cagr, stats.devAnnualReturn));
        // System.out.printf("%ss: %d / %d => %.2f (%.2f), %.2f\n", cal.get(Calendar.YEAR), pctStock, pctBonds,
        // stats.cagr, stats.meanAnnualReturn, stats.devAnnualReturn);
      }
      decades.add(decade);
    }
    Chart.saveHighChartSplines(new File(dir, "stock-bond-mix-decade-curves.html"), "Stock/Bond Decade Curves",
        GRAPH_WIDTH, GRAPH_HEIGHT, decades.toArray(new Sequence[decades.size()]));

    // Generate curve for requested duration.
    Sequence[] frontiers = new Sequence[durations.length];
    for (int i = 0; i < durations.length; ++i) {
      int duration = durations[i] * 12;
      Sequence frontier = new Sequence(Library.formatDurationMonths(duration));
      for (int j = 0; j < percentStock.length; ++j) {
        int pctStock = percentStock[j];
        int pctBonds = 100 - percentStock[j];
        Sequence mixed = Strategy.calcMixedReturns(new Sequence[] { stock, bonds },
            new double[] { pctStock, pctBonds }, rebalanceMonths, rebalanceBand);
        ReturnStats stats = new ReturnStats(mixed, duration);
        String name = null;// String.format("%d", pctStock);
        // if (pctStock == 100) {
        // name = "100% Stock";
        // } else if (pctStock == 50) {
        // name = "50 / 50";
        // } else if (pctStock == 0) {
        // name = "100% Bonds";
        // }
        frontier.addData(new FeatureVec(name, 2, stats.mean, stats.sdev));
        System.out.printf("%d / %d: %.2f, %.2f\n", pctStock, pctBonds, stats.mean, stats.sdev);
      }
      frontiers[i] = frontier;
    }
    Chart.saveHighChartSplines(new File(dir, "stock-bond-mix-duration-curve.html"), "Stock / Bond Frontier",
        GRAPH_WIDTH, GRAPH_HEIGHT, frontiers);
  }

  public static void genSMASweepChart(Sequence shiller, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence stock = calcSnpReturns(snpData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(bondData, iStart, iEnd);

    int[] months = new int[] { 1, 2, 3, 4, 5, 6, 9, 10, 12 }; // , 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length];
    for (int i = 0; i < months.length; ++i) {
      Sequence sma = Strategy.calcSMAReturns(months[i], months[months.length - 1], snpData, stock, bonds);
      double cagr = RetireTool.getAnnualReturn(sma.getLast(0), N);
      sma.setName(String.format("SMA-%d (%.2f%%)", months[i], cagr));
      seqs[i] = sma;
      // System.out.println(InvestmentStats.calcInvestmentStats(sma));
    }

    Chart.saveLineChart(file, "Cumulative Market Returns: SMA Strategy", GRAPH_WIDTH, GRAPH_HEIGHT, true, seqs);
  }

  public static void genMomentumSweepChart(Sequence shiller, File file) throws IOException
  {
    int iStart = 0;
    int iEnd = shiller.length() - 1;

    // iStart = getIndexForDate(1900, 1);
    // iEnd = getIndexForDate(2010, 1);
    int N = iEnd - iStart + 1;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence snp = calcSnpReturns(snpData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence bonds = Bond.calcReturnsRebuy(bondData, iStart, iEnd);

    int[] months = new int[] { 1, 2, 3, 5, 6, 9, 10, 12 }; // , 15, 18, 21, 24, 30, 36 };

    Sequence[] seqs = new Sequence[months.length + 1];
    for (int i = 0; i < months.length; ++i) {
      Sequence mom = Strategy.calcMomentumReturns(months[i], months[months.length - 1], snp, bonds);
      double cagr = RetireTool.getAnnualReturn(mom.getLast(0), N);
      mom.setName(String.format("Momentum-%d (%.2f%%)", months[i], cagr));
      seqs[i] = mom;
      // System.out.println(InvestmentStats.calcInvestmentStats(mom));
    }

    Sequence multiMom = Strategy.calcMultiMomentumReturns(months[months.length - 1], snp, bonds, Disposition.Safe);
    double cagr = RetireTool.getAnnualReturn(multiMom.getLast(0), N);
    multiMom.setName(String.format("MultiMomentum (%.2f%%)", cagr));
    seqs[months.length] = multiMom;

    Chart.saveLineChart(file, "Cumulative Market Returns: Momentum Strategy", GRAPH_WIDTH, GRAPH_HEIGHT, true, seqs);
  }

  public static void genReturnComparison(Sequence shiller, int numMonths, File file) throws IOException
  {
    int iStart = 0;// getIndexForDate(1881, 1);
    int iEnd = shiller.length() - 1;

    int percentStock = 60;
    int percentBonds = 40;

    Sequence snpData = Shiller.getStockData(shiller, iStart, iEnd);
    Sequence bondData = Shiller.getBondData(shiller, iStart, iEnd);

    Sequence cumulativeSNP = calcSnpReturns(snpData, iStart, iEnd - iStart, DividendMethod.MONTHLY);
    Sequence cumulativeBonds = Bond.calcReturnsRebuy(bondData, iStart, iEnd);
    Sequence cumulativeMixed = Strategy.calcMixedReturns(new Sequence[] { cumulativeSNP, cumulativeBonds },
        new double[] { percentStock, percentBonds }, 6, 0.0);
    assert cumulativeSNP.length() == cumulativeBonds.length();

    Sequence snp = new Sequence("S&P");
    Sequence bonds = new Sequence("Bonds");
    Sequence mixed = new Sequence(String.format("Mixed (%d/%d)", percentStock, percentBonds));
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
    Chart.saveLineChart(file, title, GRAPH_WIDTH, GRAPH_HEIGHT, false, snp, bonds);
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
    if (inflationAccounting == Inflation.Include) {
      value *= cpi.get(iTo, 0) / cpi.get(iFrom, 0);
    }
    return value;
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
   * Calculates S&P ROI for the given range.
   * 
   * @param snp sequence of prices (d=0) and dividends (d=1)
   * @param iStart first index in SNP
   * @param nMonths number of months (ticks) in snp to consider
   * @param divMethod how should we handle dividend reinvestment
   * @return sequence of ROIs
   */
  public static Sequence calcSnpReturns(Sequence snp, int iStart, int nMonths, DividendMethod divMethod)
  {
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= snp.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths, snp.size()));
    }

    Sequence seq = new Sequence(divMethod == DividendMethod.NO_REINVEST ? "S&P-NoReinvest" : "S&P");

    // note: it's equivalent to keep track of total value or number of shares
    double divCash = 0.0;
    double baseValue = 1.0;
    double shares = baseValue / snp.get(iStart, 0);
    seq.addData(baseValue, snp.getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double div = 0.0;
      if (divMethod == DividendMethod.NO_REINVEST)
        divCash += shares * snp.get(i, 1);
      else if (divMethod == DividendMethod.MONTHLY) {
        // Dividends at the end of every month.
        div = snp.get(i, 1);
      } else if (divMethod == DividendMethod.QUARTERLY) {
        // Dividends at the end of every quarter (march, june, september, december).
        Calendar cal = Library.now();
        cal.setTimeInMillis(snp.getTimeMS(i));
        int month = cal.get(Calendar.MONTH);
        if (month % 3 == 2) { // time for a dividend!
          for (int j = 0; j < 3; j++) {
            if (i - j < iStart)
              break;
            div += snp.get(i - j, 1);
          }
        }
      }

      // Apply the dividends (if any).
      double price = snp.get(i + 1, 0);
      shares += shares * div / price;

      // Add data point for current value.
      double value = divCash + shares * price;
      seq.addData(value, snp.getTimeMS(i + 1));
    }
    return seq;
  }

  public static void genInterestRateGraph(Sequence shiller, File file) throws IOException
  {
    // Sequence bondData = Shiller.getBondData(shiller);
    Chart.saveHighChart(file, ChartType.Area, "Interest Rates", null, null, GRAPH_WIDTH, GRAPH_HEIGHT, 0.0, 16.0, 1.0,
        false, Shiller.GS10, shiller);
  }

  public static void main(String[] args) throws IOException
  {
    if (args.length != 2) {
      System.err.println("Usage: java ~.ShillerSnp <shiller-data-file> <t-bill-file>");
      System.exit(1);
    }

    Sequence shiller = DataIO.loadShillerData(args[0]);
    Sequence tbills = DataIO.loadDateValueCSV(args[1]);

    long commonStart = Library.calcCommonStart(shiller, tbills);
    long commonEnd = Library.calcCommonEnd(shiller, tbills);

    System.out.printf("Shiller: [%s] -> [%s]\n", Library.formatDate(shiller.getStartMS()),
        Library.formatDate(shiller.getEndMS()));
    System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()),
        Library.formatDate(tbills.getEndMS()));
    System.out.printf("Common: [%s] -> [%s]\n", Library.formatDate(commonStart), Library.formatDate(commonEnd));

    // shiller = shiller.subseq(commonStart, commonEnd);
    // tbills = tbills.subseq(commonStart, commonEnd);

    // shiller.printWithdrawalLikelihoods(30, 0.1);
    // shiller.genReturnChart(Inflation.Ignore, new File("g:/test.html"));
    // int[] years = new int[] { 1, 2, 5, 10, 15, 20, 30, 40, 50 };
    // for (int i = 0; i < years.length; ++i) {
    // shiller.genReturnComparison(years[i] * 12, Inflation.Ignore, new File("g:/test.html"));
    // }

    File dir = new File("g:/web/");
    assert dir.isDirectory();

    // genInterestRateGraph(shiller, new File(dir, "interest-rates.html"));
    // compareRebalancingMethods(shiller, dir);
    // genReturnViz(shiller, new File(dir, "histogram-returns.html"), new File(dir, "future-returns.html"));
    // genReturnChart(shiller, new File(dir, "cumulative-returns.html"), new File(dir, "strategy-report.html"));
    // genSMASweepChart(shiller, new File(dir, "sma-sweep.html"));
    // genMomentumSweepChart(shiller, new File(dir, "momentum-sweep.html"));
    // genStockBondMixSweepChart(shiller, new File(dir, "stock-bond-sweep.html"), new File(dir,
    // "chart-stock-bond-sweep.html"));
    // genDuelViz(shiller, dir);
    genEfficientFrontier(shiller, dir);

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
