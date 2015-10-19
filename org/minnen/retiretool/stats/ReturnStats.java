package org.minnen.retiretool.stats;

import java.util.Arrays;
import org.minnen.retiretool.Library;

public class ReturnStats implements Comparable<ReturnStats>
{
  public final String name;
  public final double mean;
  public final double sdev;
  public final double min;
  public final double percentile10;
  public final double percentile25;
  public final double median;
  public final double percentile75;
  public final double percentile90;
  public final double max;

  public static ReturnStats calc(String name, double[] r)
  {
    return new ReturnStats(name, r);
  }

  public ReturnStats(String name, double[] r)
  {
    this.name = name;
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
  public String toString()
  {
    return String.format("%.2f [%.2f, %.2f, %.2f, %.2f, %.2f]", mean, min, percentile25, median, percentile75, max);
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
