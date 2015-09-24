package org.minnen.retiretool.stats;

import java.util.Arrays;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.data.Sequence;

/**
 * Holds statistics that characterize the results of an investment strategy over a given duration.
 * 
 * @author David Minnen
 */
public class DurationalStats
{
  public final Sequence cumulativeReturns;
  public final Sequence durationReturns;
  public final double   mean;
  public final double   sdev;
  public final double   min;
  public final double   percentile10;
  public final double   percentile25;
  public final double   median;
  public final double   percentile75;
  public final double   percentile90;
  public final double   max;
  public final int      nMonthsPerPeriod;

  public static DurationalStats calc(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    return new DurationalStats(cumulativeReturns, nMonthsPerPeriod);
  }

  /**
   * Create return statistics object from the given return sequence and duration.
   * 
   * @param cumulativeReturns sequence containing total return over some duration.
   * @param nMonthsPerPeriod duration (in months)
   */
  private DurationalStats(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    this.cumulativeReturns = cumulativeReturns;
    this.nMonthsPerPeriod = nMonthsPerPeriod;

    int nMonths = cumulativeReturns.size() - 1;
    assert nMonths >= 2;
    if (nMonths < nMonthsPerPeriod) {
      double growth = cumulativeReturns.getLast(0) / cumulativeReturns.getFirst(0);
      mean = FinLib.getAnnualReturn(growth, nMonths);
      sdev = 0.0;
      min = percentile10 = percentile25 = median = percentile75 = percentile90 = max = mean;
      durationReturns = null;
    } else {
      // Calculate returns for all periods of the requested duration.
      durationReturns = FinLib.calcReturnsForDuration(cumulativeReturns, nMonthsPerPeriod);

      double[] r = durationReturns.extractDim(0);
      mean = Library.mean(r);
      sdev = Library.stdev(r);

      // Calculate percentiles.
      Arrays.sort(r);
      min = r[0];
      percentile10 = r[Math.min(Math.round(r.length * 0.1f), r.length - 1)];
      percentile25 = r[Math.min(Math.round(r.length * 0.25f), r.length - 1)];
      median = r[Math.min(Math.round(r.length * 0.5f), r.length - 1)];
      percentile75 = r[Math.min(Math.round(r.length * 0.75f), r.length - 1)];
      percentile90 = r[Math.min(Math.round(r.length * 0.9f), r.length - 1)];
      max = r[r.length - 1];
    }
  }

  private void printDurationTableRow(String label)
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
      DurationalStats rstats = DurationalStats.calc(cumulativeReturns, 12 * dur[d]);
      rstats.printDurationTableRow(String.format("%d", dur[d]));
    }
    System.out.printf("</tbody></table>\n");
  }
}
