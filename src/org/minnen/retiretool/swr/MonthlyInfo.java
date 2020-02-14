package org.minnen.retiretool.swr;

import org.minnen.retiretool.util.TimeLib;

/**
 * Holds information about the results of a retirement simulation for a particular month.
 */
public class MonthlyInfo
{
  /** Index into a data sequence (stock returns, bonds, cpi, etc.). */
  public final int    index;

  /** Time (in ms) corresponding to this month. */
  public final long   time;

  /** Monthly "salary", i.e. the withdrawal amount for this month. */
  public final double salary;

  /** Account balance for this month. */
  public final double balance;

  public MonthlyInfo(int index, long time, double salary, double balance)
  {
    this.index = index;
    this.time = time;
    this.salary = salary;
    this.balance = balance;
  }

  /** @return annualized withdrawal rate (salary / balance) as a percent (4.0 = 4.0% per year). */
  public double percent()
  {
    return salary * 12.0 / balance * 100.0;
  }

  /** @return true if this month failed, i.e. the balance couldn't cover the salary. */
  public boolean failed()
  {
    return salary >= balance;
  }

  /** @return true if this month succeeded, i.e. the balance was larger than the salary. */
  public boolean ok()
  {
    return !failed();
  }

  @Override
  public String toString()
  {
    return String.format("[%s|%s: $%.2f, $%.2f = %.3f%%]", TimeLib.formatMonth(time), index, salary, balance,
        percent());
  }

}
