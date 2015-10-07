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
public class DurationalStats extends ReturnStats
{
  public final Sequence cumulativeReturns;
  public final Sequence durationReturns;
  public final int      nMonthsPerPeriod;

  public static DurationalStats calc(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    int nMonths = cumulativeReturns.size() - 1;
    assert nMonths >= 2;
    Sequence durationReturns = FinLib.calcReturnsForDuration(cumulativeReturns, nMonthsPerPeriod);
    return new DurationalStats(cumulativeReturns, durationReturns, nMonthsPerPeriod);
  }

  public static DurationalStats calc(double[] returns, int nMonthsPerPeriod)
  {
    return new DurationalStats(returns, nMonthsPerPeriod);
  }

  /**
   * Create return statistics object from the given return sequence and duration.
   * 
   * @param cumulativeReturns sequence containing total return over some duration.
   * @param durationReturns sequence of returns over successive periods of length nMonthsPerPeriod
   * @param nMonthsPerPeriod duration (in months)
   */
  private DurationalStats(Sequence cumulativeReturns, Sequence durationReturns, int nMonthsPerPeriod)
  {
    super(durationReturns.extractDim(0));
    this.cumulativeReturns = cumulativeReturns;
    this.durationReturns = durationReturns;
    this.nMonthsPerPeriod = nMonthsPerPeriod;
  }

  /**
   * Create return statistics object from the given return sequence and duration.
   * 
   * @param returns array of returns over successive periods of length nMonthsPerPeriod
   * @param nMonthsPerPeriod duration (in months)
   */
  private DurationalStats(double[] returns, int nMonthsPerPeriod)
  {
    super(returns);
    this.cumulativeReturns = null;
    this.durationReturns = null;
    this.nMonthsPerPeriod = nMonthsPerPeriod;
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
