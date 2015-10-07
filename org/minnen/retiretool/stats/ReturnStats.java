package org.minnen.retiretool.stats;

import java.util.Arrays;
import org.minnen.retiretool.Library;

public class ReturnStats implements Comparable<ReturnStats>
{
  public final double mean;
  public final double sdev;
  public final double min;
  public final double percentile10;
  public final double percentile25;
  public final double median;
  public final double percentile75;
  public final double percentile90;
  public final double max;

  public ReturnStats(double[] r)
  {
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

  @Override
  public int compareTo(ReturnStats other)
  {
    if (this == other)
      return 0;

    if (mean < other.mean)
      return -1;
    if (mean > other.mean)
      return 1;

    if (median < other.median)
      return -1;
    if (median > other.median)
      return 1;

    if (sdev > other.sdev)
      return -1;
    if (sdev < other.sdev)
      return 1;

    return 0;
  }

}
