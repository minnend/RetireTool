package org.minnen.retiretool.screener;

import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.StringSerializable;

public class StockInfo implements StringSerializable
{
  public String name;
  public String symbol;

  public String sector;
  public String industry;
  public int    nYearsDivIncrease;
  public int    nDivPaymentsPerYear;
  public double dividend;
  public double annualDividend;
  public double dividendYield;
  public double epsPayout;
  public double marketCap;

  public StockInfo(String name, String symbol)
  {
    this.name = name;
    this.symbol = symbol;
  }

  @Override
  public String toString()
  {
    return String.format("[%-5s %2d %5.2f  %s]", symbol, nYearsDivIncrease, annualDividend, name);
  }

  @Override
  public String serializeToString()
  {
    return String.format("%s|%s|%s|%s|%d|%d|%f|%f|%f|%f|%.1f", name, symbol, sector, industry, nYearsDivIncrease,
        nDivPaymentsPerYear, dividend, annualDividend, dividendYield, epsPayout, marketCap);

  }

  @Override
  public boolean parseString(String serialized)
  {
    String[] fields = serialized.split("\\|");
    if (fields.length != 11) return false;
    name = fields[0];
    symbol = fields[1];
    sector = fields[2];
    industry = fields[3];
    nYearsDivIncrease = Integer.parseInt(fields[4]);
    nDivPaymentsPerYear = Integer.parseInt(fields[5]);
    dividend = Double.parseDouble(fields[6]);
    annualDividend = Double.parseDouble(fields[7]);
    dividendYield = Double.parseDouble(fields[8]);
    epsPayout = Double.parseDouble(fields[9]);
    marketCap = Double.parseDouble(fields[10]);
    return true;
  }

  public static StockInfo fromString(String serialized)
  {
    StockInfo info = new StockInfo("error", "error");
    if (!info.parseString(serialized)) return null;
    return info;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((sector == null) ? 0 : sector.hashCode());
    result = prime * result + ((symbol == null) ? 0 : symbol.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    StockInfo other = (StockInfo) obj;
    if (industry == null) {
      if (other.industry != null) return false;
    } else if (!industry.equals(other.industry)) return false;
    if (nDivPaymentsPerYear != other.nDivPaymentsPerYear) return false;
    if (nYearsDivIncrease != other.nYearsDivIncrease) return false;
    if (name == null) {
      if (other.name != null) return false;
    } else if (!name.equals(other.name)) return false;
    if (sector == null) {
      if (other.sector != null) return false;
    } else if (!sector.equals(other.sector)) return false;
    if (symbol == null) {
      if (other.symbol != null) return false;
    } else if (!symbol.equals(other.symbol)) return false;

    final double eps = 1e-6;
    if (!Library.almostEqual(dividend, other.dividend, eps)) return false;
    if (!Library.almostEqual(annualDividend, other.annualDividend, eps)) return false;
    if (!Library.almostEqual(dividendYield, other.dividendYield, eps)) return false;
    if (!Library.almostEqual(epsPayout, other.epsPayout, eps)) return false;
    if (!Library.almostEqual(marketCap, other.marketCap, eps)) return false;
    return true;
  }

}
