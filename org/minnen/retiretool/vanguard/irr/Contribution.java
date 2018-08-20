package org.minnen.retiretool.vanguard.irr;

public class Contribution
{
  public long   date;
  public double amount;
  public double yearsOfGrowth;

  public Contribution(long date, double amount)
  {
    this.date = date;
    this.amount = amount;
    this.yearsOfGrowth = Double.NaN;
  }
}
