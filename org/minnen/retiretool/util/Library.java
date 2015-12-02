package org.minnen.retiretool.util;

import java.text.*;
import java.util.regex.*;
import java.util.*;

import org.minnen.retiretool.data.Sequence;

import static java.util.Calendar.*;

public final class Library
{
  public final static long          LNAN         = Long.MIN_VALUE;
  public final static int           INDEX_ERROR  = Integer.MIN_VALUE;

  public final static double        FPMIN        = Double.MIN_VALUE;
  public final static double        INF          = Double.POSITIVE_INFINITY;
  public final static double        NEGINF       = Double.NEGATIVE_INFINITY;

  /** log(0.0) = -infinity */
  public final static double        LOG_ZERO     = NEGINF;

  /** log(1.0) = 0.0 */
  public final static double        LOG_ONE      = 0.0;

  public final static double        LOG_TWO      = Math.log(2.0);
  public final static double        MINV_ABS     = 1.0e-9;
  public final static double        TWO_PI       = 2.0 * Math.PI;
  public final static double        PI_OVER_TWO  = Math.PI / 2.0;
  public final static double        SQRT_2PI     = Math.sqrt(TWO_PI);
  public final static double        SQRT_2       = Math.sqrt(2.0);
  public static final double        ONE_TWELFTH  = 1.0 / 12.0;

  public final static DecimalFormat df           = new DecimalFormat();
  public final static long          AppStartTime = TimeLib.getTime();
  public static final String        os           = System.getProperty("os.name");
  public static final boolean       bWindows     = os.startsWith("Win");

  public static enum MatrixOrder {
    RowMajor, ColumnMajor
  }

  static {
    df.setMaximumFractionDigits(4);
  }

  public static void copy(double from[], double[] to)
  {
    int n = Math.min(from.length, to.length);
    for (int i = 0; i < n; i++)
      to[i] = from[i];
  }

  public static void copy(double from[], double[] to, int iStartFrom, int iStartTo, int len)
  {
    for (int i = 0; i < len; i++)
      to[i + iStartTo] = from[i + iStartFrom];
  }

  public static double[][] allocMatrixDouble(int nRows, int nCols)
  {
    return allocMatrixDouble(nRows, nCols, 0.0);
  }

  public static double[][] allocMatrixDouble(int nRows, int nCols, double init)
  {
    double a[][] = new double[nRows][nCols];
    for (int i = 0; i < nRows; i++)
      Arrays.fill(a[i], init);
    return a;
  }

  /**
   * Try to parse the string as a double.
   * 
   * @param s string to parse
   * @param failValue return this value if parse fails
   * @return numeric value of s or failValue if parsing fails
   */
  public static double tryParse(String s, double failValue)
  {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nfe) {
      return failValue;
    }
  }

  /**
   * Sort the given array and return the resulting indices
   * 
   * @param a data to sort
   * @param bAscending true = ascending sort, false = descending
   * @return index of original location of data
   */
  public static int[] sort(double[] a, boolean bAscending)
  {
    int n = a.length;
    int[] ii = new int[n];
    for (int i = 0; i < n; i++) {
      ii[i] = i;
    }
    sort(a, ii, 0, n - 1);
    if (!bAscending) {
      for (int i = 0; i < n - i - 1; ++i) {
        swap(a, ii, i, n - i - 1);
      }
    }
    return ii;
  }

  /**
   * Internal insertion sort routine for subarrays that is used by quicksort.
   * 
   * @param a an array of Comparable items.
   * @param ii array of indices that will be rearranged to match sort
   * @param low the left-most index of the subarray.
   * @param n the number of items to sort.
   */
  private static void sort(double[] a, int[] ii, int low, int high)
  {
    for (int p = low + 1; p <= high; p++) {
      double tmp = a[p];
      int j;
      for (j = p; j > low && tmp < a[j - 1]; j--) {
        a[j] = a[j - 1];
        ii[j] = ii[j - 1];
      }
      a[j] = tmp;
      ii[j] = p;
    }
  }

  /**
   * Method to swap to elements in an array.
   * 
   * @param a an array of objects.
   * @param ii array of indices that will be rearranged to match sort
   * @param index1 the index of the first object.
   * @param index2 the index of the second object.
   */
  public static void swap(double[] a, int[] ii, int index1, int index2)
  {
    double tmp = a[index1];
    a[index1] = a[index2];
    a[index2] = tmp;

    int itmp = ii[index1];
    ii[index1] = ii[index2];
    ii[index2] = itmp;
  }

  public static double mean(double[] a)
  {
    double sum = 0.0;
    for (int i = 0; i < a.length; ++i) {
      sum += a[i];
    }
    return sum / a.length;
  }

  public static double variance(double[] a)
  {
    if (a.length < 2) {
      return 0.0;
    }

    double mean = mean(a);
    double s1 = 0.0, s2 = 0.0;
    for (int i = 0; i < a.length; ++i) {
      double diff = a[i] - mean;
      s1 += diff * diff;
      s2 += diff;
    }
    return (s1 - s2 * s2 / a.length) / (a.length - 1);
  }

  public static double stdev(double[] a)
  {
    return Math.sqrt(variance(a));
  }

  public static double correlation(double[] a, double[] b)
  {
    assert a.length == b.length;

    double sa = stdev(a);
    double sb = stdev(b);

    double cov = covariance(a, b);
    return cov / (sa * sb);
  }

  public static double covariance(double[] a, double[] b)
  {
    assert a.length == b.length;

    double ma = mean(a);
    double mb = mean(b);

    double sum = 0.0;
    for (int i = 0; i < a.length; ++i) {
      double da = a[i] - ma;
      double db = b[i] - mb;
      sum += da * db;
    }

    return sum / (a.length - 1);
  }

  public static int sum(int[] a)
  {
    return sum(a, 0, -1);
  }

  public static int sum(int[] a, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += a.length;
    }
    if (iEnd < 0) {
      iEnd += a.length;
    }
    int sum = 0;
    for (int i = iStart; i <= iEnd; ++i) {
      sum += a[i];
    }
    return sum;
  }

  public static double sum(double[] a)
  {
    return sum(a, 0, -1);
  }

  public static double sum(double[] a, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += a.length;
    }
    if (iEnd < 0) {
      iEnd += a.length;
    }
    double sum = 0;
    for (int i = iStart; i <= iEnd; ++i) {
      sum += a[i];
    }
    return sum;
  }

  public static int argmax(double[] a)
  {
    int iMax = 0;
    for (int i = 1; i < a.length; ++i) {
      if (a[i] > a[iMax]) {
        iMax = i;
      }
    }
    return iMax;
  }

  public static String prefix(String s, String marker)
  {
    int n = s.indexOf(marker);
    if (n < 0) {
      return s;
    } else {
      return s.substring(0, n);
    }
  }

  public static boolean almostEqual(double[] a, double[] b, double eps)
  {
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; ++i) {
      if (Math.abs(b[i] - a[i]) > eps) {
        return false;
      }
    }
    return true;
  }

  public static long numBits(long x)
  {
    x = x - ((x >>> 1) & 0x55555555);
    x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
    return (((x + (x >>> 4)) & 0x0F0F0F0F) * 0x01010101) >>> 24;
  }

  public static int numBits(int x)
  {
    x = x - ((x >>> 1) & 0x55555555);
    x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
    return (((x + (x >>> 4)) & 0x0F0F0F0F) * 0x01010101) >>> 24;
  }
}
