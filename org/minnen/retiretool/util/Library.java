package org.minnen.retiretool.util;

import java.text.*;
import java.util.*;

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

  public static final Random        rng          = new Random();

  static {
    df.setMaximumFractionDigits(4);
  }

  public static void copy(double from[], double[] to)
  {
    // TODO use System.arraycopy.
    int n = Math.min(from.length, to.length);
    for (int i = 0; i < n; i++)
      to[i] = from[i];
  }

  public static void copy(double from[], double[] to, int iStartFrom, int iStartTo, int len)
  {
    // TODO use System.arraycopy.
    for (int i = 0; i < len; i++)
      to[i + iStartTo] = from[i + iStartFrom];
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

  /** @return array of length n with a[i] == i */
  public static int[] genIdentityArray(int n)
  {
    int[] a = new int[n];
    for (int i = 0; i < n; i++) {
      a[i] = i;
    }
    return a;
  }

  /** @return array of length n with values [0,n-1] in random order. */
  public static int[] shuffle(int n)
  {
    int[] a = genIdentityArray(n);
    for (int i = a.length - 1; i > 0; i--) {
      int index = rng.nextInt(i + 1);
      int t = a[index];
      a[index] = a[i];
      a[i] = t;
    }
    return a;
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
    int[] ii = genIdentityArray(n);
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

  /** Reorder the elements of a according to the indices of ii. */
  public static void reorder(double[] a, int[] ii)
  {
    double[] b = Arrays.copyOf(a, a.length);
    for (int i = 0; i < a.length; ++i) {
      b[i] = a[ii[i]];
    }
    System.arraycopy(b, 0, a, 0, a.length);
  }

  /**
   * Sort the given array and return the resulting indices
   * 
   * @param a data to sort
   * @param bAscending true = ascending sort, false = descending
   * @return index of original location of data
   */
  public static <T extends Comparable<T>> int[] sort(T[] a, boolean bAscending)
  {
    int n = a.length;
    int[] ii = genIdentityArray(n);
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
  private static <T extends Comparable<T>> void sort(T[] a, int[] ii, int low, int high)
  {
    for (int p = low + 1; p <= high; p++) {
      T tmp = a[p];
      int j;
      for (j = p; j > low && tmp.compareTo(a[j - 1]) < 0; j--) {
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
  public static <T extends Comparable<T>> void swap(T[] a, int[] ii, int index1, int index2)
  {
    T tmp = a[index1];
    a[index1] = a[index2];
    a[index2] = tmp;

    int itmp = ii[index1];
    ii[index1] = ii[index2];
    ii[index2] = itmp;
  }

  /** @return mean (average) of the values in the given array. */
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

  public static double[][] correlation(double[][] r)
  {
    final int n = r.length;
    double[][] cm = new double[n][n];
    for (int i = 0; i < n; ++i) {
      cm[i][i] = 1.0;
      for (int j = i + 1; j < n; ++j) {
        double p = correlation(r[i], r[j]);
        assert !Double.isNaN(p);
        cm[i][j] = cm[j][i] = p;
      }
    }
    return cm;
  }

  public static double[] cov2dev(double[][] cov)
  {
    final int n = cov.length;
    double[] dev = new double[n];
    for (int i = 0; i < n; ++i) {
      dev[i] = Math.sqrt(cov[i][i]);
    }
    return dev;
  }

  public static double correlation(double[] a, double[] b)
  {
    assert a.length == b.length;

    double sa = stdev(a);
    double sb = stdev(b);
    if (Math.abs(sa) < 1e-8 || Math.abs(sb) < 1e-8) return 0.0;

    double cov = covariance(a, b);
    return cov / (sa * sb);
  }

  public static double[][] covariance(double[][] r)
  {
    final int n = r.length;
    double[][] cm = new double[n][n];
    for (int i = 0; i < n; ++i) {
      cm[i][i] = variance(r[i]);
      for (int j = i + 1; j < n; ++j) {
        double p = covariance(r[i], r[j]);
        assert !Double.isNaN(p);
        cm[i][j] = cm[j][i] = p;
      }
    }
    return cm;
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

  /** Return the NxN identity matrix. */
  public static double[][] eye(int n)
  {
    double[][] m = new double[n][n];
    for (int i = 0; i < n; ++i) {
      m[i][i] = 1.0;
    }
    return m;
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

  public static long sum(long[] a)
  {
    return sum(a, 0, -1);
  }

  public static long sum(long[] a, int iStart, int iEnd)
  {
    if (iStart < 0) {
      iStart += a.length;
    }
    if (iEnd < 0) {
      iEnd += a.length;
    }
    long sum = 0;
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

  public static int numBits(long x)
  {
    x = x - ((x >>> 1) & 0x55555555);
    x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
    return (int) ((((x + (x >>> 4)) & 0x0F0F0F0F) * 0x01010101) >>> 24);
  }

  public static int numBits(int x)
  {
    x = x - ((x >>> 1) & 0x55555555);
    x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
    return (((x + (x >>> 4)) & 0x0F0F0F0F) * 0x01010101) >>> 24;
  }

  /** @return 1.0 / (1 + e^[-k*(x - x0)]) */
  public static double sigmoid(double x, double k, double x0)
  {
    double t = k * (x - x0);
    double y = 1.0 + Math.exp(-t);
    return 1.0 / y;
  }
}
