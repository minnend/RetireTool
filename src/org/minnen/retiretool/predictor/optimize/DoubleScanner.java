package org.minnen.retiretool.predictor.optimize;

import java.util.Arrays;

public class DoubleScanner extends ValueScanner<Double>
{
  private final double[] values;

  private DoubleScanner(double... values)
  {
    this.values = Arrays.copyOf(values, values.length);
  }

  @Override
  public Double getValue()
  {
    return values[index];
  }

  public static DoubleScanner fromRange(int min, int step, int max, double divisor)
  {
    final int n = (max - min + 1) / step;
    double[] values = new double[n];
    int i = 0;
    for (int x = min; x <= max; x += step) {
      values[i++] = x / divisor;
    }
    assert i == n;
    return new DoubleScanner(values);
  }

  public static DoubleScanner fromList(double... values)
  {
    return new DoubleScanner(values);
  }
}
