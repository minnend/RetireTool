package org.minnen.retiretool.stats;

public class JitterStats implements Comparable<JitterStats>
{
  public ReturnStats cagr;
  public ReturnStats drawdown;

  public JitterStats(ReturnStats cagr, ReturnStats drawdown)
  {
    this.cagr = cagr;
    this.drawdown = drawdown;
  }

  public void print()
  {
    System.out.println(cagr);
    System.out.println(drawdown);
  }

  @Override
  public String toString()
  {
    return String.format("[%.2f, %.2f]", cagr.percentile10, drawdown.percentile90);
  }

  public boolean dominates(JitterStats stats)
  {
    final double epsCAGR = 0.008;
    final double epsDrawdown = 0.4;

    return (cagr.percentile10 > stats.cagr.percentile10 - epsCAGR)
        && (drawdown.percentile90 < stats.drawdown.percentile90 + epsDrawdown);
  }

  @Override
  public int compareTo(JitterStats stats)
  {
    if (cagr.percentile10 > stats.cagr.percentile10) return 1;
    if (cagr.percentile10 < stats.cagr.percentile10) return -1;

    if (drawdown.percentile90 < stats.drawdown.percentile90) return 1;
    if (drawdown.percentile90 > stats.drawdown.percentile90) return -1;

    return 0;
  }
}
