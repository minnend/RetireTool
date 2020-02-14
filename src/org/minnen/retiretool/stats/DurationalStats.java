package org.minnen.retiretool.stats;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

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

  /** @return stats for the given returns (converts to monthly as needed). */
  public static DurationalStats calc(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    long timestep = cumulativeReturns.getMeanTimeStep();
    if (timestep > 25 * TimeLib.MS_IN_DAY) {
      // TODO also distinguish between monthly and annual data.
      return calcMonthly(cumulativeReturns, nMonthsPerPeriod);
    } else {
      assert timestep < 3 * TimeLib.MS_IN_DAY;
      return calcDaily(cumulativeReturns, nMonthsPerPeriod);
    }
  }

  /**
   * Daily returns will be converted to monthly returns before calculation durational stats.
   * 
   * The default conversion will be used, which requries at least 12 days of data per month.
   * 
   * @param daily daily cumulative returns
   * @param nMonthsPerPeriod number of months in each duration
   * @return stats for the given returns
   */
  public static DurationalStats calcDaily(Sequence daily, int nMonthsPerPeriod)
  {
    Sequence monthly = FinLib.dailyToMonthly(daily);
    return calcMonthly(monthly, nMonthsPerPeriod);
  }

  public static DurationalStats calcMonthly(Sequence cumulativeReturns, int nMonthsPerPeriod)
  {
    assert TimeLib.isMonthly(cumulativeReturns);
    Sequence durationReturns = FinLib.calcReturnsForMonths(cumulativeReturns, nMonthsPerPeriod);
    return new DurationalStats(cumulativeReturns, durationReturns, nMonthsPerPeriod);
  }

  /** @return stats for the given returns (assumed to be one per month). */
  public static DurationalStats calc(String name, double[] returns, int nMonthsPerPeriod)
  {
    return new DurationalStats(name, returns, nMonthsPerPeriod);
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
    super(cumulativeReturns.getName(), durationReturns.extractDim(0));
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
  private DurationalStats(String name, double[] returns, int nMonthsPerPeriod)
  {
    super(name, returns);
    this.cumulativeReturns = null;
    this.durationReturns = null;
    this.nMonthsPerPeriod = nMonthsPerPeriod;
  }

  private String getDurationTableRow(String label)
  {
    return String.format("<tr><td>%s</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td>"
        + "<td>%.2f</td><td>%.2f</td></tr>", label, mean, sdev, min, percentile25, median, percentile75, max);
  }

  public static void printDurationTable(Sequence cumulativeReturns)
  {
    System.out.printf("<table id=\"durationTable\" class=\"tablesorter\"><thead>\n");
    System.out.printf("<tr><th>Duration<br/>(Years)</th><th>Mean AR</th><th>StdDev</th>"
        + "<th>Worst AR</th><th>25%% AR</th><th>Median AR</th><th>75%% AR</th><th>Best AR</th></tr>\n");
    System.out.printf("</thead><tbody>\n");
    int[] dur = new int[] { 1, 2, 5, 10, 20, 30, 40 };
    for (int d = 0; d < dur.length; ++d) {
      DurationalStats stats = DurationalStats.calcMonthly(cumulativeReturns, 12 * dur[d]);
      String row = stats.getDurationTableRow(String.format("%d", dur[d]));
      System.out.printf(" %s\n", row);
    }
    System.out.printf("</tbody></table>\n");
  }
}
