package org.minnen.retiretool.swr;

public class MonthlyInfo
{
  public final int    index;
  public final double salary;
  public final double balance;

  public MonthlyInfo(int index, double salary, double balance)
  {
    this.index = index;
    this.salary = salary;
    this.balance = balance;
  }

  public double percent()
  {
    return salary * 12.0 / balance * 100.0;
  }

  @Override
  public String toString()
  {
    return String.format("[%d: $%.2f, $%.2f = %.3f%%]", index, salary, balance, percent());
  }

}
