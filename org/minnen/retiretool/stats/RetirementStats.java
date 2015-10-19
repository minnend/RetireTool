package org.minnen.retiretool.stats;

public class RetirementStats extends ReturnStats
{
  public final double principal;

  public RetirementStats(String name, double principal, double[] returns)
  {
    super(name, returns);
    this.principal = principal;
  }

  @Override
  public int compareTo(ReturnStats other)
  {
    if (this == other)
      return 0;

    // TODO breaks comparison transitivity across parent/subclass!
    if (other instanceof RetirementStats) {
      RetirementStats rstats = (RetirementStats) other;
      if (principal < rstats.principal)
        return -1;
      if (principal > rstats.principal)
        return 1;
    }

    return super.compareTo(other);
  }
}
