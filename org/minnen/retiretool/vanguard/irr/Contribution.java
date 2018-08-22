package org.minnen.retiretool.vanguard.irr;

import java.util.List;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

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

  public Contribution(long date, double amount, long withdrawalDate)
  {
    this.date = date;
    this.amount = amount;
    this.yearsOfGrowth = TimeLib.yearsBetween(date, withdrawalDate);
  }

  /** @return final balance after growing the contributions with CAGR = `rate`. */
  public static double calcTotal(List<Contribution> contributions, double rate)
  {
    double r = FinLib.ret2mul(rate);
    double total = 0;
    for (Contribution contrib : contributions) {
      total += contrib.amount * Math.pow(r, contrib.yearsOfGrowth);
    }
    return total;
  }

  /**
   * Estimate the internal rate of return (IRR) for the given contributions and final balance.
   * 
   * The IRR is the effective rate (CAGR) at which all of the contributions would grow to match the final balance.
   * 
   * @param contributions list of contributions with an amount and (fractional) age in years.
   * @param balance the final balance of the investments
   * @return the IRR for the contributions
   */
  public static double solveIRR(List<Contribution> contributions, double balance)
  {
    double rmin = -100.0;
    double rmax = 1000.0;

    // Binary search for IRR.
    while (rmax - rmin > 1e-8) {
      double r = (rmin + rmax) / 2.0;
      double total = calcTotal(contributions, r);
      // System.out.printf("[%f -> %f] => %f = $%s\n", rmin, rmax, r, FinLib.currencyFormatter.format(total));
      if (total > balance) {
        rmax = r;
      } else {
        rmin = r;
      }
    }

    return (rmin + rmax) / 2.0;
  }
}
