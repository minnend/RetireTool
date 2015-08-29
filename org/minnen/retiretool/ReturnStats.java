package org.minnen.retiretool;

import java.util.Arrays;

public class ReturnStats
{
  // TODO merge with InvestmentStats
  public final Sequence cumulativeReturns;
  public final double   mean;
  public final double   sdev;
  public final double   min;
  public final double   percentile25;
  public final double   median;
  public final double   percentile75;
  public final double   max;
  public final int      nMonthsPerPeriod;

  /**
   * Create return statistics object from the given return sequence and duration.
   * 
   * @param cumulativeReturns sequence containing total return over some duration.
   * @param nMonthsPerPeriod duration (in months)
   */
  public ReturnStats(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    this.cumulativeReturns = cumulativeReturns;
    this.nMonthsPerPeriod = nMonthsPerPeriod;

    int nMonths = cumulativeReturns.size() - 1;
    assert nMonths >= 2;
    if (nMonths < nMonthsPerPeriod) {
      mean = FinLib.getAnnualReturn(cumulativeReturns.getLast(0) / cumulativeReturns.getFirst(0), nMonths);
      sdev = 0.0;
      min = percentile25 = median = percentile75 = max = mean;
    } else {
      // Calculate returns for all periods of the requested duration.
      Sequence returns = FinLib.calcReturnsForDuration(cumulativeReturns, nMonthsPerPeriod);

      double[] r = returns.extractDim(0);
      mean = Library.mean(r);
      sdev = Library.stdev(r);

      // Calculate percentiles.
      Arrays.sort(r);
      min = r[0];
      percentile25 = r[Math.round(r.length * 0.25f)];
      median = r[Math.round(r.length * 0.5f)];
      percentile75 = r[Math.round(r.length * 0.75f)];
      max = r[r.length - 1];
    }
  }

  private void printRow(String label)
  {
    System.out.printf(" <tr><td>%s</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td>"
        + "<td>%.2f</td><td>%.2f</td></tr>\n", label, mean, sdev, min, percentile25, median, percentile75, max);
  }

  public static void printDurationTable(Sequence cumulativeReturns)
  {
    System.out.printf("<table id=\"durationTable\" class=\"tablesorter\"><thead>\n");
    System.out.printf("<tr><th>Duration<br/>(Years)</th><th>Mean AR</th><th>StdDev</th>"
        + "<th>Worst AR</th><th>25%% AR</th><th>Median AR</th><th>75%% AR</th><th>Best AR</th></tr>\n");
    System.out.printf("</thead><tbody>\n");
    int[] dur = new int[] { 1, 2, 5, 10, 20, 30, 40 };
    for (int d = 0; d < dur.length; ++d) {
      ReturnStats rstats = new ReturnStats(cumulativeReturns, 12 * dur[d]);
      rstats.printRow(String.format("%d", dur[d]));
    }
    System.out.printf("</tbody></table>\n");
  }
}
