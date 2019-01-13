package org.minnen.retiretool.broker;

public class Receipt
{
  public final String name;
  public final long longPL;
  public final long shortPL;
  public final long balance;

  public Receipt(String name, long longPL, long shortPL, long balance)
  {
    this.name = name;
    this.longPL = longPL;
    this.shortPL = shortPL;
    this.balance = balance;
  }
}
