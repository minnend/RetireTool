package org.minnen.retiretool.predictor.optimize;

import java.util.Arrays;

public class IntScanner extends ValueScanner<Integer>
{
  private final int[] values;

  private IntScanner(int... values)
  {
    this.values = Arrays.copyOf(values, values.length);
    this.nValues = values.length;
  }

  @Override
  public Integer getValue()
  {
    return values[index];
  }

  public static IntScanner fromRange(int min, int step, int max)
  {
    final int n = (max - min) / step + 1;
    int[] values = new int[n];
    int i = 0;
    for (int x = min; x <= max; x += step) {
      values[i++] = x;
    }
    assert i == n;
    return new IntScanner(values);
  }

  public static IntScanner fromList(int... values)
  {
    return new IntScanner(values);
  }
}
