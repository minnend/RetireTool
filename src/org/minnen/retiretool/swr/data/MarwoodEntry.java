package org.minnen.retiretool.swr.data;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.util.TimeLib;

/**
 * Represents a single DM-SWR data point.
 */
public final class MarwoodEntry
{
  public final long   time;
  public final int    retirementYears;
  public final int    lookbackYears;
  public final int    percentStock;
  public final int    swr;
  public final int    virtualRetirementMonths;
  public final double finalBalance;
  public final double bengenSalary;
  public final double marwoodSalary;

  /** Build a sequence query . */
  public MarwoodEntry(int retirementYears, int lookbackYears, int percentStock)
  {
    this(TimeLib.TIME_ERROR, retirementYears, lookbackYears, percentStock);
  }

  /** Build a point query. */
  public MarwoodEntry(long time, int retirementYears, int lookbackYears, int percentStock)
  {
    this(time, retirementYears, lookbackYears, percentStock, -1, -1, Double.NaN, Double.NaN, Double.NaN);
  }

  public MarwoodEntry(int retirementYears, int lookbackYears, int percentStock, FeatureVec v)
  {
    this.time = v.getTime();
    this.retirementYears = retirementYears;
    this.lookbackYears = lookbackYears;
    this.percentStock = percentStock;
    this.swr = (int) Math.round(v.get(0) * 100.0); // store withdrawal rate as basis points
    this.virtualRetirementMonths = (int) Math.round(v.get(1));
    this.finalBalance = v.get(2);
    this.bengenSalary = v.get(3);
    this.marwoodSalary = v.get(4);
  }

  public MarwoodEntry(long time, int retirementYears, int lookbackYears, int percentStock, int swr,
      int virtualRetirementMonths, double finalBalance, double bengenSalary, double marwoodSalary)
  {
    this.time = time;
    this.retirementYears = retirementYears;
    this.lookbackYears = lookbackYears;
    this.percentStock = percentStock;
    this.swr = swr;
    this.virtualRetirementMonths = virtualRetirementMonths;
    this.finalBalance = finalBalance;
    this.bengenSalary = bengenSalary;
    this.marwoodSalary = marwoodSalary;
  }

  public static MarwoodEntry fromCSV(String line)
  {
    String[] fields = line.split(",");
    assert fields.length == 9;

    final int retirementYears = Integer.parseInt(fields[0]);
    assert retirementYears > 0;

    final int lookbackYears = Integer.parseInt(fields[1]);
    assert lookbackYears >= 0;

    final int percentStock = Integer.parseInt(fields[2]);
    assert percentStock >= 0 && percentStock <= 100;

    final long time = TimeLib.parseDate(fields[3]);
    assert time != TimeLib.TIME_ERROR;

    final int swr = Integer.parseInt(fields[4]);
    assert swr > 0 && swr <= 100000;

    final int virtualRetirementMonths = Integer.parseInt(fields[5]);
    assert virtualRetirementMonths >= 0;

    final double finalBalance = Double.parseDouble(fields[6]);
    final double bengenSalary = Double.parseDouble(fields[7]);
    final double marwoodSalary = Double.parseDouble(fields[8]);

    return new MarwoodEntry(time, retirementYears, lookbackYears, percentStock, swr, virtualRetirementMonths,
        finalBalance, bengenSalary, marwoodSalary);
  }

  public String toCSV()
  {
    assert swr > 0;
    return String.format("%d,%d,%d,%s,%d,%d,%.2f,%.2f,%.2f", retirementYears, lookbackYears, percentStock,
        TimeLib.formatYM(time), swr, virtualRetirementMonths, finalBalance, bengenSalary, marwoodSalary);
  }

  @Override
  public String toString()
  {
    if (swr > 0) {
      return String.format("[%s (%d,%d,%d) %d]", TimeLib.formatYM(time), retirementYears, lookbackYears, percentStock,
          swr);
    } else if (time != TimeLib.TIME_ERROR) {
      return String.format("[%s, %d, %d, %d]", TimeLib.formatYM(time), retirementYears, lookbackYears, percentStock);
    } else {
      return String.format("[%d, %d, %d]", retirementYears, lookbackYears, percentStock);
    }
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + lookbackYears;
    result = prime * result + percentStock;
    result = prime * result + retirementYears;
    result = prime * result + (int) (time ^ (time >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MarwoodEntry other = (MarwoodEntry) obj;
    if (lookbackYears != other.lookbackYears) return false;
    if (percentStock != other.percentStock) return false;
    if (retirementYears != other.retirementYears) return false;
    if (time != other.time) return false;
    return true;
  }

}
