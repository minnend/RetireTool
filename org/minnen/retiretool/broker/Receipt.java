package org.minnen.retiretool.broker;

public class Receipt
{
  public final String name;
  public final double longPL;
  public final double shortPL;

  public Receipt(String name, double longPL, double shortPL)
  {
    this.name = name;
    this.longPL = longPL;
    this.shortPL = shortPL;
  }
}
