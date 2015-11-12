package org.minnen.retiretool;

/**
 * Provides fixed-point arithmetic.
 * 
 * @author David Minnen
 *
 *         Rounding is "away from zero".
 */
public class FixedPoint
{
  /** Basic unit: 1 => dollars, 10 = > dimes, 100 => cents, etc. */
  public static final long SCALE      = 1000;
  public static final long HALF_SCALE = SCALE / 2L;

  public static final long ONE        = SCALE;
  public static final long ZERO       = 0L;

  public static long toFixed(double x)
  {
    return Math.round(x * SCALE);
  }

  public static long toFixed(int x)
  {
    return x * SCALE;
  }

  public static long toFixed(long x)
  {
    return x * SCALE;
  }

  public static double toFloat(long x)
  {
    return (double) x / SCALE;
  }

  public static long mul(long x, long y)
  {
    long sign = 1L;
    if (x < 0L) {
      sign *= -1L;
      x = -x;
    }
    if (y < 0L) {
      sign *= -1L;
      y = -y;
    }

    return sign * ((x * y + HALF_SCALE) / SCALE);
  }

  public static long div(long x, long y)
  {
    long sign = 1L;
    if (x < 0L) {
      sign *= -1L;
      x = -x;
    }
    if (y < 0L) {
      sign *= -1L;
      y = -y;
    }

    return sign * (x * SCALE + y / 2) / y;
  }

  public static long sign(long x)
  {
    return (x == 0L) ? 0L : (x > 0L) ? 1L : -1L;
  }
}
