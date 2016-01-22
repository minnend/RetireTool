package org.minnen.retiretool.util;

/**
 * Provides fixed-point arithmetic.
 * 
 * Rounding is "away from zero".
 * 
 * @author David Minnen
 */
public class Fixed
{
  /** SCALE => Base Unit: 1 = dollars, 10 = dimes, 100 = cents, etc. */
  public static final long SCALE      = 100000;
  public static final long HALF_SCALE = SCALE / 2L;

  public static final long ONE        = SCALE;
  public static final long ZERO       = 0L;

  public static final long PENNY      = SCALE / 100L;
  public static final long DIME       = SCALE / 10L;

  public static final long THOUSANDTH = SCALE / 1000L;
  public static final long HUNDREDTH  = SCALE / 100L;
  public static final long TENTH      = SCALE / 10L;

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
    return Math.multiplyExact(x, SCALE);
  }

  public static double toFloat(long x)
  {
    return (double) x / SCALE;
  }

  public static long mul(long x, long y)
  {
    // Handle zero now; makes overflow check easier.
    if (x == 0L || y == 0L) {
      return 0L;
    }

    // Easier to work with positive numbers and fix sign at end.
    long sign = 1L;
    if (x < 0L) {
      sign *= -1L;
      x = -x;
    }
    if (y < 0L) {
      sign *= -1L;
      y = -y;
    }

    // TODO use BigInteger to handle case where result fits but intermediate value overflows.
    long z = Math.multiplyExact(x, y);
    z = Math.addExact(z, HALF_SCALE);

    return sign * (z / SCALE);
  }

  /** Divide values and round (midpoint moves away from zero). */
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

    // TODO use BigInteger to handle case where result fits but intermediate value overflows.
    long z = Math.multiplyExact(x, SCALE);
    z = Math.addExact(z, y / 2);

    return sign * z / y;
  }

  /** Divide values and truncate toward zero. */
  public static long divTrunc(long x, long y)
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

    long z = Math.multiplyExact(x, SCALE);
    return sign * z / y;
  }

  public static long sign(long x)
  {
    return (x == 0L) ? 0L : (x > 0L) ? 1L : -1L;
  }

  public static String formatCurrency(long x)
  {
    return FinLib.currencyFormatter.format(toFloat(x));
  }

  public static long truncate(long x, long unit)
  {
    long y = (x / unit) * unit;
    assert Math.abs(x - y) < unit;
    assert y % unit == 0;
    return y;
  }

  public static long round(long x, long unit)
  {
    long sign = 1L;
    if (x < 0) {
      sign = -1L;
      x = -x;
    }
    long y = truncate(x, unit);
    if (Math.abs(x - y) > Math.abs(x - (y + unit))) {
      y += unit;
    }
    assert Math.abs(x - y) < unit;
    assert y % unit == 0;
    return sign * y;
  }
}
