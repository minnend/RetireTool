package org.minnen.retiretool.broker;

public class Receipt
{
  public final String name;
  public final double longPL;
  public final double shortPL;
  public final double balance;

  public Receipt(String name, double longPL, double shortPL, double balance)
  {
    this.name = name;
    this.longPL = longPL;
    this.shortPL = shortPL;
    this.balance = balance;
  }
}
