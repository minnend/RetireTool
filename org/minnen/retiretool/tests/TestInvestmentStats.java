package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.util.Calendar;

import org.junit.Test;
import org.minnen.retiretool.TimeLib;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;

public class TestInvestmentStats
{
  public static final double eps = 1e-6;

  @Test
  public void testCalcInvestmentStats_Empty()
  {
    Sequence cumulativeReturns = new Sequence(new double[] {});
    CumulativeStats stats = CumulativeStats.calc(cumulativeReturns);
    assertEquals(1.0, stats.totalReturn, eps);
    assertEquals(1.0, stats.cagr, eps);
    assertEquals(0.0, stats.drawdown, eps);
    assertEquals(0.0, stats.percentNewHigh, eps);
    assertEquals(0.0, stats.percentDown10, eps);
    assertEquals(0, stats.percentUp, eps);
    assertEquals(0, stats.percentDown, eps);
  }

  @Test
  public void testCalcInvestmentStats_Normal()
  {
    Sequence cumulativeReturns = new Sequence();
    double[] data = new double[] { 1.0, 2.0, 3.0, 4.0, 2.0, 3.0, 4.0 };
    Calendar cal = TimeLib.setTime(TimeLib.now(), 1, 1, 2000);
    for (double x : data) {
      cumulativeReturns.addData(x, cal.getTimeInMillis());
      cal.add(Calendar.MONTH, 1);
    }

    CumulativeStats stats = CumulativeStats.calc(cumulativeReturns);
    assertEquals(4.0, stats.totalReturn, eps);
    assertEquals(1500.0, stats.cagr, eps);
    assertEquals(50.0, stats.drawdown, eps);
    assertEquals(50.0, stats.percentNewHigh, eps);
    assertEquals(100.0 / 3.0, stats.percentDown10, eps);
    assertEquals(500.0 / 6.0, stats.percentUp, eps);
    assertEquals(100.0 / 6.0, stats.percentDown, eps);
  }

  @Test
  public void testCalcInvestmentStats_TwoDrops()
  {
    Sequence cumulativeReturns = new Sequence();
    double[] data = new double[] { 1.0, 2.0, 1.5, 2.1, 1.4, 2.2, 1.9 };
    Calendar cal = TimeLib.setTime(TimeLib.now(), 1, 1, 2000);
    for (double x : data) {
      cumulativeReturns.addData(x, cal.getTimeInMillis());
      cal.add(Calendar.MONTH, 1);
    }

    CumulativeStats stats = CumulativeStats.calc(cumulativeReturns);
    assertEquals(1.9, stats.totalReturn, eps);
    assertEquals(261.0, stats.cagr, eps);
    assertEquals(100.0 / 3.0, stats.drawdown, eps);
    assertEquals(50.0, stats.percentNewHigh, eps);
    assertEquals(50.0, stats.percentDown10, eps);
    assertEquals(50.0, stats.percentUp, eps);
    assertEquals(50.0, stats.percentDown, eps);
  }
}
