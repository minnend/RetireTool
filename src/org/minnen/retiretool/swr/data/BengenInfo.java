package org.minnen.retiretool.swr.data;

import org.minnen.retiretool.util.TimeLib;

/**
 * Represents a single Bengen SWR data point.
 */
public final class BengenInfo
{
  public final long time;
  public final int  retirementYears;
  public final int  percentStock;
  public final int  swr;

  public BengenInfo(long time, int retirementYears, int percentStock)
  {
    this(time, retirementYears, percentStock, -1);
  }

  public BengenInfo(long time, int retirementYears, int percentStock, int swr)
  {
    this.time = time;
    this.retirementYears = retirementYears;
    this.percentStock = percentStock;
    this.swr = swr;
  }

  public static BengenInfo fromCSV(String line)
  {
    String[] fields = line.split(",");
    assert fields.length == 4;

    final int retirementYears = Integer.parseInt(fields[0]);
    assert retirementYears > 0;

    final int percentStock = Integer.parseInt(fields[1]);
    assert percentStock >= 0 && percentStock <= 100;

    final long time = TimeLib.parseDate(fields[2]);

    final int swr = Integer.parseInt(fields[3]);
    assert swr > 0 && swr <= 100000;

    return new BengenInfo(time, retirementYears, percentStock, swr);
  }

  public String toCSV()
  {
    assert swr > 0;
    return String.format("%d,%d,%s,%d", retirementYears, percentStock, TimeLib.formatYM(time), swr);
  }

  @Override
  public String toString()
  {
    if (swr > 0) {
      return String.format("[%s (%d,%d) %d]", TimeLib.formatYM(time), retirementYears, percentStock, swr);
    } else {
      return String.format("[%s, %d, %d]", TimeLib.formatYM(time), retirementYears, percentStock);
    }
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
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
    BengenInfo other = (BengenInfo) obj;
    if (percentStock != other.percentStock) return false;
    if (retirementYears != other.retirementYears) return false;
    if (time != other.time) return false;
    return true;
  }

}
