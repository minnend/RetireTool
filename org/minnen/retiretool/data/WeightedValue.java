package org.minnen.retiretool.data;

public class WeightedValue
{
  public final String name;
  public final double value, weight;

  public WeightedValue(String name, double value, double weight)
  {
    this.name = name;
    this.value = value;
    this.weight = weight;
  }

  public WeightedValue(double value, double weight)
  {
    this(null, value, weight);
  }

  @Override
  public String toString()
  {
    if (name != null) {
      return String.format("[%s: %.3f, %.3f]", name, value, weight);
    } else {
      return String.format("[%.3f, %.3f]", value, weight);
    }
  }
}
