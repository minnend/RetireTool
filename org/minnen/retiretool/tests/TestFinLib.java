package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.time.LocalDate;

import org.junit.Test;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;

public class TestFinLib
{
  @Test
  public void testCalcLeveragedReturns()
  {
    final double leverage = 1.1;
    Sequence base = new Sequence(new double[] { 1.0, 1.2, 1.44, 1.296 });
    Sequence leveragedSeq = FinLib.calcLeveragedReturns(base, leverage);
    assertEquals(base.size(), leveragedSeq.size());

    double[] leveraged = leveragedSeq.extractDim(0);
    double[] expected = new double[] { 1.0, 1.22, 1.4884, 1.324676 };
    assertArrayEquals(expected, leveraged, 0.0001);
  }

  @Test
  public void testGetReturn()
  {
    Sequence cumulativeReturns = new Sequence(new double[] { 1.0, 1.2, 1.44, 1.296 });
    assertEquals(1.2, FinLib.getTotalReturn(cumulativeReturns, 0, 1), 1e-6);
    assertEquals(1.2, FinLib.getTotalReturn(cumulativeReturns, 1, 2), 1e-6);
    assertEquals(0.9, FinLib.getTotalReturn(cumulativeReturns, 2, 3), 1e-6);
    assertEquals(1.44, FinLib.getTotalReturn(cumulativeReturns, 0, 2), 1e-6);
    assertEquals(1.08, FinLib.getTotalReturn(cumulativeReturns, 1, 3), 1e-6);
    assertEquals(1.296, FinLib.getTotalReturn(cumulativeReturns, 0, 3), 1e-6);
  }

  @Test
  public void testMul2Ret()
  {
    assertEquals(0.0, FinLib.mul2ret(1.0), 1e-6);

    assertEquals(20.0, FinLib.mul2ret(1.2), 1e-6);
    assertEquals(2.0, FinLib.mul2ret(1.02), 1e-6);
    assertEquals(100.0, FinLib.mul2ret(2.0), 1e-6);
    assertEquals(210.0, FinLib.mul2ret(3.1), 1e-6);

    assertEquals(-10.0, FinLib.mul2ret(0.9), 1e-6);
    assertEquals(-2.0, FinLib.mul2ret(0.98), 1e-6);
    assertEquals(-50.0, FinLib.mul2ret(0.5), 1e-6);
    assertEquals(-90.0, FinLib.mul2ret(0.1), 1e-6);
  }

  @Test
  public void testRet2Mul()
  {
    assertEquals(1.0, FinLib.ret2mul(0.0), 1e-6);

    assertEquals(1.02, FinLib.ret2mul(2.0), 1e-6);
    assertEquals(1.5, FinLib.ret2mul(50.0), 1e-6);
    assertEquals(2.0, FinLib.ret2mul(100.0), 1e-6);

    assertEquals(0.98, FinLib.ret2mul(-2.0), 1e-6);
    assertEquals(0.8, FinLib.ret2mul(-20.0), 1e-6);
    assertEquals(0.2, FinLib.ret2mul(-80.0), 1e-6);
  }

  @Test
  public void testGetAnnualReturn()
  {
    assertEquals(0.0, FinLib.getAnnualReturn(1.0, 1), 1e-6);
    assertEquals(0.0, FinLib.getAnnualReturn(1.0, 6), 1e-6);
    assertEquals(0.0, FinLib.getAnnualReturn(1.0, 12), 1e-6);
    assertEquals(0.0, FinLib.getAnnualReturn(1.0, 24), 1e-6);

    assertEquals(30.0, FinLib.getAnnualReturn(1.3, 12), 1e-6);
    assertEquals(110.0, FinLib.getAnnualReturn(2.1, 12), 1e-6);
    assertEquals(-10.0, FinLib.getAnnualReturn(0.9, 12), 1e-6);
    assertEquals(-50.0, FinLib.getAnnualReturn(0.5, 12), 1e-6);

    assertEquals(100.0, FinLib.getAnnualReturn(4.0, 24), 1e-6);
    assertEquals(25.992105, FinLib.getAnnualReturn(2.0, 36), 1e-6);
    assertEquals(-2.599625357, FinLib.getAnnualReturn(0.9, 48), 1e-6);
  }

  @Test
  public void testGetNameWithBreak()
  {
    assertEquals("", FinLib.getNameWithBreak(null));
    assertEquals("", FinLib.getNameWithBreak(""));
    assertEquals(" ", FinLib.getNameWithBreak(" "));
    assertEquals("foo", FinLib.getNameWithBreak("foo"));
    assertEquals("foo<br/>(bar)", FinLib.getNameWithBreak("foo (bar)"));
    assertEquals("foo (bar)<br/>(buzz)", FinLib.getNameWithBreak("foo (bar) (buzz)"));
  }

  @Test
  public void testGetBaseName()
  {
    assertEquals("", FinLib.getBaseName(null));
    assertEquals("", FinLib.getBaseName(""));
    assertEquals(" ", FinLib.getBaseName(" "));
    assertEquals("foo", FinLib.getBaseName("foo"));
    assertEquals("foo", FinLib.getBaseName("foo (bar)"));
    assertEquals("foo (bar)", FinLib.getBaseName("foo (bar) (buzz)"));
  }

  @Test
  public void testGetNameSuffix()
  {
    assertEquals("", FinLib.getNameSuffix(null));
    assertEquals("", FinLib.getNameSuffix(""));
    assertEquals("", FinLib.getNameSuffix(" "));
    assertEquals("", FinLib.getNameSuffix("foo"));
    assertEquals("(bar)", FinLib.getNameSuffix("foo (bar)"));
    assertEquals("(buzz)", FinLib.getNameSuffix("foo (bar) (buzz)"));
  }

  @Test
  public void testGetBoldedName()
  {
    assertEquals("", FinLib.getBoldedName(null));
    assertEquals("", FinLib.getBoldedName(""));
    assertEquals("<b> </b>", FinLib.getBoldedName(" "));
    assertEquals("<b>foo</b>", FinLib.getBoldedName("foo"));
    assertEquals("<b>foo</b> (bar)", FinLib.getBoldedName("foo (bar)"));
    assertEquals("<b>foo (bar)</b> (buzz)", FinLib.getBoldedName("foo (bar) (buzz)"));
  }

  @Test
  public void testCalcRealReturns_InflationCancelsGains()
  {
    Sequence nominal = new Sequence(new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 });
    Sequence cpi = new Sequence(new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 });
    Sequence real = FinLib.calcRealReturns(nominal, cpi);

    double[] expected = new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 };
    assertArrayEquals(expected, real.extractDim(0), 1e-8);
  }

  @Test
  public void testCalcRealReturns_NoInflation()
  {
    Sequence nominal = new Sequence(new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 });
    Sequence cpi = new Sequence(new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 });
    Sequence real = FinLib.calcRealReturns(nominal, cpi);

    double[] expected = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 };
    assertArrayEquals(expected, real.extractDim(0), 1e-8);
  }

  @Test
  public void testCalcRealReturns_Deflation()
  {
    Sequence nominal = new Sequence(new double[] { 1.0, 1.0, 1.0, 1.0, 1.0 });
    Sequence cpi = new Sequence(new double[] { 5.0, 4.0, 3.0, 2.0, 1.0 });
    Sequence real = FinLib.calcRealReturns(nominal, cpi);

    double[] expected = new double[] { 1.0, 1.25, 5.0 / 3.0, 2.5, 5.0 };
    assertArrayEquals(expected, real.extractDim(0), 1e-8);
  }

  @Test
  public void testCalcRealReturns_DeflationPlusGains()
  {
    Sequence nominal = new Sequence(new double[] { 1.0, 2.0, 4.0, 8.0, 16.0 });
    Sequence cpi = new Sequence(new double[] { 5.0, 4.0, 3.0, 2.0, 1.0 });
    Sequence real = FinLib.calcRealReturns(nominal, cpi);

    double[] expected = new double[] { 1.0, 2.5, 20.0 / 3.0, 20.0, 80.0 };
    assertArrayEquals(expected, real.extractDim(0), 1e-8);
  }

  @Test
  public void testIsLTG()
  {
    LocalDate d1, d2;

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(1999, 1, 1);
    assertFalse(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(2000, 1, 1);
    assertFalse(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(2001, 1, 1);
    assertFalse(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(2001, 1, 2);
    assertTrue(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(2001, 2, 1);
    assertTrue(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(2000, 1, 1);
    d2 = LocalDate.of(2002, 1, 1);
    assertTrue(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(1999, 2, 28);
    d2 = LocalDate.of(2000, 2, 29);
    assertFalse(FinLib.isLTG(d1, d2));

    d1 = LocalDate.of(1999, 2, 28);
    d2 = LocalDate.of(2000, 3, 1);
    assertTrue(FinLib.isLTG(d1, d2));
  }

  @Test
  public void testIsMarketHoliday()
  {
    // https://www.nyse.com/markets/hours-calendars

    // New Years Day.
    LocalDate date = LocalDate.of(2015, 1, 1);
    assertTrue(FinLib.isMarketHoliday(date));

    // MLK Day.
    date = LocalDate.of(2015, 1, 19);
    assertTrue(FinLib.isMarketHoliday(date));

    // Washington's Birthday.
    date = LocalDate.of(2015, 2, 16);
    assertTrue(FinLib.isMarketHoliday(date));

    // Good Friday.
    date = LocalDate.of(2015, 4, 3);
    assertTrue(FinLib.isMarketHoliday(date));

    // Memorial Day.
    date = LocalDate.of(2015, 5, 25);
    assertTrue(FinLib.isMarketHoliday(date));

    // Independence Day.
    date = LocalDate.of(2015, 7, 4);
    assertTrue(FinLib.isMarketHoliday(date));

    // Labor Day.
    date = LocalDate.of(2015, 9, 7);
    assertTrue(FinLib.isMarketHoliday(date));

    // Thanksgiving.
    date = LocalDate.of(2015, 11, 26);
    assertTrue(FinLib.isMarketHoliday(date));

    // Christmas.
    date = LocalDate.of(2015, 12, 25);
    assertTrue(FinLib.isMarketHoliday(date));

    // Not holidays.
    date = LocalDate.of(2015, 1, 2);
    assertFalse(FinLib.isMarketHoliday(date));

    date = LocalDate.of(2015, 12, 31);
    assertFalse(FinLib.isMarketHoliday(date));
  }
}
