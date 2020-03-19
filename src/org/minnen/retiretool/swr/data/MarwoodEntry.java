package org.minnen.retiretool.swr.data;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.TimeLib;

/**
 * Represents a single DMSWR data point.
 */
public final class MarwoodEntry
{
  public final long       retireTime;
  public final long       currentTime;
  public final int        retirementYears;
  public final int        lookbackYears;
  public final int        percentStock;
  public final int        swr;
  public final int        virtualRetirementMonths;
  public final double     finalBalance;
  public final double     bengenSalary;
  public final double     marwoodSalary;
  public final double     crystalSalary;

  public static final int iSWR                     = 0;
  public static final int iVirtualRetirementMonths = 1;
  public static final int iFinalBalance            = 2;
  public static final int iBengenSalary            = 3;
  public static final int iMarwoodSalary           = 4;
  public static final int iCrystalSalary           = 5;

  /** Build a query for a sequence of starting DMSWR values. */
  public MarwoodEntry(int retirementYears, int lookbackYears, int percentStock)
  {
    this(TimeLib.TIME_ERROR, TimeLib.TIME_ERROR, retirementYears, lookbackYears, percentStock);
  }

  /** Build a query for a retirement path. */
  public MarwoodEntry(long retireTime, int retirementYears, int lookbackYears, int percentStock)
  {
    this(retireTime, retireTime, retirementYears, lookbackYears, percentStock);
  }

  /** Build a point query. */
  public MarwoodEntry(long retireTime, long currentTime, int retirementYears, int lookbackYears, int percentStock)
  {
    this(retireTime, currentTime, retirementYears, lookbackYears, percentStock, -1, -1, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN);
  }

  public MarwoodEntry(int retirementYears, int lookbackYears, int percentStock, FeatureVec v)
  {
    assert v.getNumDims() == 6;

    this.retireTime = (long) v.getMeta("retireTime");
    this.currentTime = v.getTime();
    this.retirementYears = retirementYears;
    this.lookbackYears = lookbackYears;
    this.percentStock = percentStock;
    this.swr = SwrLib.percentToBasisPoints(v.get(iSWR));
    this.virtualRetirementMonths = (int) Math.round(v.get(1));
    this.finalBalance = v.get(iFinalBalance);
    this.bengenSalary = v.get(iBengenSalary);
    this.marwoodSalary = v.get(iMarwoodSalary);
    this.crystalSalary = v.get(iCrystalSalary);
  }

  public MarwoodEntry(long retireTime, long currentTime, int retirementYears, int lookbackYears, int percentStock,
      int swr, int virtualRetirementMonths, double finalBalance, double bengenSalary, double marwoodSalary,
      double crystalSalary)
  {
    this.retireTime = retireTime;
    this.currentTime = currentTime;
    this.retirementYears = retirementYears;
    this.lookbackYears = lookbackYears;
    this.percentStock = percentStock;
    this.swr = swr;
    this.virtualRetirementMonths = virtualRetirementMonths;
    this.finalBalance = finalBalance;
    this.bengenSalary = bengenSalary;
    this.marwoodSalary = marwoodSalary;
    this.crystalSalary = crystalSalary;
  }

  public static MarwoodEntry fromCSV(String line)
  {
    String[] fields = line.split(",");
    assert fields.length == 11;

    final int retirementYears = Integer.parseInt(fields[0]);
    assert retirementYears > 0;

    final int lookbackYears = Integer.parseInt(fields[1]);
    assert lookbackYears >= 0;

    final int percentStock = Integer.parseInt(fields[2]);
    assert percentStock >= 0 && percentStock <= 100;

    final long retireTime = TimeLib.parseDate(fields[3]);
    assert retireTime != TimeLib.TIME_ERROR;

    final long currentTime = TimeLib.parseDate(fields[4]);
    assert currentTime != TimeLib.TIME_ERROR;
    assert currentTime >= retireTime;

    final int swr = Integer.parseInt(fields[5]);
    assert swr > 0 && swr <= 100000;

    final int virtualRetirementMonths = Integer.parseInt(fields[6]);
    assert virtualRetirementMonths >= 0;

    final double finalBalance = Double.parseDouble(fields[7]);
    final double bengenSalary = Double.parseDouble(fields[8]);
    final double marwoodSalary = Double.parseDouble(fields[9]);
    final double crystalSalary = Double.parseDouble(fields[10]);

    return new MarwoodEntry(retireTime, currentTime, retirementYears, lookbackYears, percentStock, swr,
        virtualRetirementMonths, finalBalance, bengenSalary, marwoodSalary, crystalSalary);
  }

  public String toCSV()
  {
    assert swr > 0;
    return String.format("%d,%d,%d,%s,%s,%d,%d,%.2f,%.2f,%.2f,%.2f", retirementYears, lookbackYears, percentStock,
        TimeLib.formatYM(retireTime), TimeLib.formatYM(currentTime), swr, virtualRetirementMonths, finalBalance,
        bengenSalary, marwoodSalary, crystalSalary);
  }

  @Override
  public String toString()
  {
    if (swr > 0) {
      return String.format("[%s (%d,%d,%d) %d]", TimeLib.formatYM(retireTime), retirementYears, lookbackYears,
          percentStock, swr);
    } else if (retireTime != TimeLib.TIME_ERROR) {
      return String.format("[%s, %d, %d, %d]", TimeLib.formatYM(retireTime), retirementYears, lookbackYears,
          percentStock);
    } else {
      return String.format("[%d, %d, %d]", retirementYears, lookbackYears, percentStock);
    }
  }

  /** @return True if this is the first month of a retirement. */
  public boolean isRetirementStart()
  {
    return currentTime == retireTime;
  }

  public FeatureVec toVector()
  {
    return buildVector(retireTime, currentTime, swr, virtualRetirementMonths, finalBalance, bengenSalary, marwoodSalary,
        crystalSalary);
  }

  public static FeatureVec buildVector(long retireTime, long currentTime, int swr, int virtualRetirementMonths,
      double finalBalance, double bengenSalary, double marwoodSalary, double crystalSalary)
  {
    FeatureVec v = new FeatureVec(6, // six dimensions
        swr / 100.0, // dmswr
        virtualRetirementMonths, // number of virtual retirement months
        finalBalance, // final balance
        bengenSalary, // salary if using Bengen SWR
        marwoodSalary, // salary if using DMSWR
        crystalSalary); // salary if we had a crystal ball and took best possible WR for this date
    v.setMeta("retireTime", retireTime); // save time as metadata to avoid long -> double conversion
    return v;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (currentTime ^ (currentTime >>> 32));
    result = prime * result + lookbackYears;
    result = prime * result + percentStock;
    result = prime * result + (int) (retireTime ^ (retireTime >>> 32));
    result = prime * result + retirementYears;
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MarwoodEntry other = (MarwoodEntry) obj;
    if (currentTime != other.currentTime) return false;
    if (lookbackYears != other.lookbackYears) return false;
    if (percentStock != other.percentStock) return false;
    if (retireTime != other.retireTime) return false;
    if (retirementYears != other.retirementYears) return false;
    return true;
  }

}
