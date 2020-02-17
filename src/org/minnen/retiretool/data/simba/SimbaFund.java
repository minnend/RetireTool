package org.minnen.retiretool.data.simba;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

public class SimbaFund
{
  public final String   name;
  public final String   symbol;
  public final Sequence annualReturns;
  public final int      startYear;
  public final int      endYear;

  public SimbaFund(String name, String symbol, Sequence annualReturns)
  {
    this.name = name;
    this.symbol = symbol;
    this.annualReturns = annualReturns;
    this.startYear = TimeLib.ms2date(annualReturns.getStartMS()).getYear();
    this.endYear = TimeLib.ms2date(annualReturns.getEndMS()).getYear();
  }

  @Override
  public String toString()
  {
    return String.format("%16s  %5s  [%d -> %s]", name, symbol, startYear, endYear);
  }
}
