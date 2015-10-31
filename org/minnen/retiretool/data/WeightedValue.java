package org.minnen.retiretool.data;

public class WeightedValue
{
  public final double value, weight;

  public WeightedValue(double value, double weight)
  {
    this.value = value;
    this.weight = weight;
  }

  @Override
  public String toString()
  {
    return String.format("[%.3f, %.3f]", value, weight);
  }
}
