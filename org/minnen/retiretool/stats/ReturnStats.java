package org.minnen.retiretool.stats;

import java.util.Arrays;

import org.minnen.retiretool.util.Library;

public class ReturnStats implements Comparable<ReturnStats>
{
  public final String name;
  public final int    count;
  public final double mean;
  public final double sdev;
  public final double min;
  public final double percentile10;
  public final double percentile25;
  public final double median;
  public final double percentile75;
  public final double percentile90;
  public final double max;
  public final double percentUp;
  public final double percentDown;
  public final double meanUp;
  public final double meanDown;

  public static ReturnStats calc(String name, double[] r)
  {
    return new ReturnStats(name, r);
  }

  public ReturnStats(String name, double[] r)
  {
    this.name = name;
    count = r.length;
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

    // Calculate percent positive returns
    int index = Arrays.binarySearch(r, 0.0);
    if (index < 0) {
      index = -index - 1;  // insertion point if exact key is not found
    }
    while (index < r.length && r[index] < 1e-8) {
      ++index;
    }
    assert index == r.length || (r[index] > 0.0 && (index == 0 || r[index - 1] <= 1e-8)) : String.format(
        "index=%d, r.length=%d, r[index-1]=%f, r[index]=%f", index, r.length, index > 0 ? r[index - 1] : 0.0, r[index]);
    int nDown = index;
    int nUp = r.length - index;
    percentUp = 100.0 * nUp / r.length;
    percentDown = 100.0 - percentUp;
    meanUp = (nUp == 0 ? 0.0 : Library.sum(r, index, r.length - 1) / nUp);
    meanDown = (nDown == 0 ? 0.0 : Library.sum(r, 0, index - 1) / nDown);
  }

  @Override
  public String toString()
  {
    return String.format("%.2f [%.2f, %.2f, %.2f, %.2f, %.2f]", mean, min, percentile10, median, percentile90, max);
  }

  public String toLongString()
  {
    return String.format("%.2f (sdev=%.2f, up=%.1f%%) [%6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f, %6.2f]", mean, sdev,
        percentUp, min, percentile10, percentile25, median, percentile75, percentile90, max);
  }

  @Override
  public int compareTo(ReturnStats other)
  {
    if (this == other) return 0;

    if (mean < other.mean) return -1;
    if (mean > other.mean) return 1;

    if (median < other.median) return -1;
    if (median > other.median) return 1;

    if (sdev > other.sdev) return -1;
    if (sdev < other.sdev) return 1;

    return 0;
  }

}
