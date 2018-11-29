package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.time.Month;

import org.junit.Test;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class TestCumulativeStats
{
  @Test
  public void testCalc_Monthly()
  {
    Sequence seq = new Sequence("test");
    LocalDate date = LocalDate.of(2000, Month.JANUARY, 1);
    double x = 1.0;
    for (int i = 0; i < 500; ++i) {
      seq.addData(x, date);
      x *= (i % 2 == 0 ? 1.02 : 0.99);
      date = date.plusMonths(1);
    }

    CumulativeStats stats = CumulativeStats.calc(seq);
    assertEquals(seq, stats.cumulativeReturns);
    assertEquals(6.0640215, stats.cagr, 1e-6);
    assertEquals(1.0, stats.drawdown, 1e-6);
    assertEquals(11.566618, stats.totalReturn, 1e-6);
  }

  @Test
  public void testCalc_Daily()
  {
    Sequence daily = new Sequence("test");
    LocalDate date = LocalDate.of(2000, Month.JANUARY, 1);
    int i = 0;
    while (daily.size() < 1000) {
      if (TimeLib.isBusinessDay(date)) {
        daily.addData(100.0 + Math.sin(i / 10.0) * 10 + i / 5.0, date);
      }
      date = date.plusDays(1);
      ++i;
    }

    // Validate stats for daily data.
    CumulativeStats stats = CumulativeStats.calc(daily);
    assertEquals(40.325513, stats.cagr, 1e-6);
    assertEquals(12.452768, stats.drawdown, 1e-6);
    assertEquals(3.821361, stats.totalReturn, 1e-6);

    // Validate stats for monthly data.
    Sequence monthly = FinLib.dailyToMonthly(daily, 0, 0, 2);
    stats = CumulativeStats.calc(monthly);
    assertEquals(39.169111, stats.cagr, 1e-6);
    assertEquals(7.508083, stats.drawdown, 1e-6);
    assertEquals(3.642605, stats.totalReturn, 1e-6);
  }
}
