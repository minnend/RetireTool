package org.minnen.retiretool.util;

import java.util.Arrays;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;

public class Histogram
{
  /**
   * Compute histogram for the given returns.
   * 
   * @param data sequence containing list of data
   * @param binWidth width of each bin
   * @param binCenter center bin has this value
   * @param iDim dimension from sequence to build histogram around
   * @return sequence of 3D vectors: [center of bin, count, frequency]
   */
  public static Sequence computeHistogram(Sequence data, double binWidth, double binCenter, int iDim)
  {
    return computeHistogram(data, Double.NaN, Double.NaN, binWidth, binCenter, iDim);
  }

  /**
   * Compute histogram for the given returns.
   * 
   * @param data sequence containing list of ROIs for some strategy
   * @param vmin histogram starts with bin containing this value (NaN to compute from ROIs)
   * @param vmax histogram ends with bin containing this value (NaN to compute from ROIs)
   * @param binWidth width of each bin
   * @param binCenter center bin has this value
   * @param iDim dimension from sequence to build histogram around
   * @return sequence of 3D vectors: [center of bin, count, frequency]
   */
  public static Sequence computeHistogram(Sequence data, double vmin, double vmax, double binWidth, double binCenter,
      int iDim)
  {
    // Sort ROIs to find min/max and ease histogram generation.
    double[] a = data.extractDim(iDim);
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
    Sequence h = new Sequence("Histogram: " + data.getName());
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
}
