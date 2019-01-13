package org.minnen.retiretool.playground;

import java.math.BigDecimal;
import java.math.MathContext;

import org.minnen.retiretool.util.Fixed;
import org.minnen.retiretool.util.TimeLib;

public class PerfFixed
{
  public static double perfLong(final int N)
  {
    long start = TimeLib.getTime();
    long sum = 0L;
    for (int i = 0; i < N; ++i) {
      long x = i;
      long y = i * 2;
      long z = Fixed.mul(x, y);
      if (i % 2 == 0) {
        sum += z;
      } else {
        sum -= z;
      }
    }
    long end = TimeLib.getTime();
    System.out.printf("long: %dms\n", end - start);
    return Fixed.toFloat(sum);
  }

  public static double perfBigDec(final int N)
  {
    final MathContext mc = MathContext.UNLIMITED;
    long start = TimeLib.getTime();
    BigDecimal sum = BigDecimal.ZERO;
    for (int i = 0; i < N; ++i) {
      BigDecimal x = new BigDecimal((double) i / Fixed.SCALE, mc);
      BigDecimal y = new BigDecimal((double) i * 2.0 / Fixed.SCALE, mc);
      BigDecimal z = x.multiply(y, mc);
      if (i % 2 == 0) {
        sum = sum.add(z, mc);
      } else {
        sum = sum.subtract(z, mc);
      }
    }
    long end = TimeLib.getTime();
    System.out.printf("long: %dms\n", end - start);
    return sum.doubleValue();
  }

  public static void main(String[] args)
  {
    final int N = 1000000;
    double valueLong = perfLong(N);
    double valueBigDec = perfBigDec(N);
    System.out.printf("%f vs. %f\n", valueLong, valueBigDec);
    assert Math.abs(valueLong - valueBigDec) < 1e-4;
  }
}
