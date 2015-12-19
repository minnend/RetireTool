package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.time.Month;

import org.junit.Test;
import org.minnen.retiretool.util.TimeLib;

public class TestTimeLib
{
  @Test
  public void testMonthsBetween()
  {
    long t1, t2;

    t1 = TimeLib.toMs(2015, Month.DECEMBER, 20);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 20);
    assertEquals(0, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2015, Month.DECEMBER, 1);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 31);
    assertEquals(0, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2015, Month.NOVEMBER, 20);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 20);
    assertEquals(1, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2015, Month.NOVEMBER, 20);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 19);
    assertEquals(0, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2015, Month.NOVEMBER, 30);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 1);
    assertEquals(0, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2015, Month.NOVEMBER, 1);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 30);
    assertEquals(1, TimeLib.monthsBetween(t1, t2));

    t1 = TimeLib.toMs(2013, Month.NOVEMBER, 1);
    t2 = TimeLib.toMs(2015, Month.DECEMBER, 30);
    assertEquals(25, TimeLib.monthsBetween(t1, t2));
  }

  @Test
  public void testIsSameMonth()
  {
    LocalDate d1, d2;

    d1 = LocalDate.of(2015, Month.DECEMBER, 20);
    d2 = LocalDate.of(2015, Month.DECEMBER, 20);
    assertTrue(TimeLib.isSameMonth(d1, d2));

    d1 = LocalDate.of(2015, Month.DECEMBER, 20);
    d2 = LocalDate.of(2015, Month.NOVEMBER, 20);
    assertFalse(TimeLib.isSameMonth(d1, d2));

    d1 = LocalDate.of(2015, Month.DECEMBER, 1);
    d2 = LocalDate.of(2015, Month.DECEMBER, 31);
    assertTrue(TimeLib.isSameMonth(d1, d2));

    d1 = LocalDate.of(2015, Month.DECEMBER, 1);
    d2 = LocalDate.of(2016, Month.DECEMBER, 31);
    assertFalse(TimeLib.isSameMonth(d1, d2));

    d1 = LocalDate.of(2015, Month.FEBRUARY, 28);
    d2 = LocalDate.of(2016, Month.FEBRUARY, 28);
    assertFalse(TimeLib.isSameMonth(d1, d2));

    d1 = LocalDate.of(2016, Month.FEBRUARY, 28);
    d2 = LocalDate.of(2016, Month.FEBRUARY, 29);
    assertTrue(TimeLib.isSameMonth(d1, d2));
  }

  @Test
  public void testToPreviousBusinessDay()
  {
    LocalDate date;

    date = TimeLib.toPreviousBusinessDay(LocalDate.of(2015, Month.NOVEMBER, 1));
    assertEquals(2015, date.getYear());
    assertEquals(Month.OCTOBER, date.getMonth());
    assertEquals(30, date.getDayOfMonth());

    date = TimeLib.toPreviousBusinessDay(LocalDate.of(2015, Month.NOVEMBER, 30));
    assertEquals(2015, date.getYear());
    assertEquals(Month.NOVEMBER, date.getMonth());
    assertEquals(27, date.getDayOfMonth());

    date = TimeLib.toPreviousBusinessDay(LocalDate.of(2015, Month.OCTOBER, 30));
    assertEquals(2015, date.getYear());
    assertEquals(Month.OCTOBER, date.getMonth());
    assertEquals(29, date.getDayOfMonth());

    date = TimeLib.toPreviousBusinessDay(LocalDate.of(2016, Month.JANUARY, 1));
    assertEquals(2015, date.getYear());
    assertEquals(Month.DECEMBER, date.getMonth());
    assertEquals(31, date.getDayOfMonth());

    date = TimeLib.toPreviousBusinessDay(LocalDate.of(2016, Month.MARCH, 1));
    assertEquals(2016, date.getYear());
    assertEquals(Month.FEBRUARY, date.getMonth());
    assertEquals(29, date.getDayOfMonth());
  }

  @Test
  public void testToNextBusinessDay()
  {
    LocalDate date;

    date = TimeLib.toNextBusinessDay(LocalDate.of(2015, Month.NOVEMBER, 1));
    assertEquals(2015, date.getYear());
    assertEquals(Month.NOVEMBER, date.getMonth());
    assertEquals(2, date.getDayOfMonth());

    date = TimeLib.toNextBusinessDay(LocalDate.of(2015, Month.NOVEMBER, 30));
    assertEquals(2015, date.getYear());
    assertEquals(Month.DECEMBER, date.getMonth());
    assertEquals(1, date.getDayOfMonth());

    date = TimeLib.toNextBusinessDay(LocalDate.of(2015, Month.OCTOBER, 31));
    assertEquals(2015, date.getYear());
    assertEquals(Month.NOVEMBER, date.getMonth());
    assertEquals(2, date.getDayOfMonth());

    date = TimeLib.toNextBusinessDay(LocalDate.of(2016, Month.JANUARY, 1));
    assertEquals(2016, date.getYear());
    assertEquals(Month.JANUARY, date.getMonth());
    assertEquals(4, date.getDayOfMonth());

    date = TimeLib.toNextBusinessDay(LocalDate.of(2016, Month.FEBRUARY, 26));
    assertEquals(2016, date.getYear());
    assertEquals(Month.FEBRUARY, date.getMonth());
    assertEquals(29, date.getDayOfMonth());
  }

  @Test
  public void testToLastBusinessDayOfMonth()
  {
    LocalDate d1, d2;

    d1 = LocalDate.of(2015, Month.DECEMBER, 20);
    d2 = TimeLib.toLastBusinessDayOfMonth(d1);
    assertTrue(TimeLib.isSameMonth(d1, d2));
    assertEquals(2015, d2.getYear());
    assertEquals(Month.DECEMBER, d2.getMonth());
    assertEquals(31, d2.getDayOfMonth());

    d1 = LocalDate.of(2016, Month.FEBRUARY, 1);
    d2 = TimeLib.toLastBusinessDayOfMonth(d1);
    assertTrue(TimeLib.isSameMonth(d1, d2));
    assertEquals(2016, d2.getYear());
    assertEquals(Month.FEBRUARY, d2.getMonth());
    assertEquals(29, d2.getDayOfMonth());

    d1 = LocalDate.of(2015, Month.OCTOBER, 1);
    d2 = TimeLib.toLastBusinessDayOfMonth(d1);
    assertTrue(TimeLib.isSameMonth(d1, d2));
    assertEquals(2015, d2.getYear());
    assertEquals(Month.OCTOBER, d2.getMonth());
    assertEquals(30, d2.getDayOfMonth());
  }

  @Test
  public void testClosestBusinessDay()
  {
    LocalDate d1, d2;

    d1 = LocalDate.of(2015, Month.DECEMBER, 21);
    d2 = TimeLib.getClosestBusinessDay(d1, true);
    assertEquals(2015, d2.getYear());
    assertEquals(Month.DECEMBER, d2.getMonth());
    assertEquals(21, d2.getDayOfMonth());

    d1 = LocalDate.of(2015, Month.DECEMBER, 20);
    d2 = TimeLib.getClosestBusinessDay(d1, true);
    assertEquals(2015, d2.getYear());
    assertEquals(Month.DECEMBER, d2.getMonth());
    assertEquals(21, d2.getDayOfMonth());

    d1 = LocalDate.of(2015, Month.OCTOBER, 31);
    d2 = TimeLib.getClosestBusinessDay(d1, true);
    assertEquals(2015, d2.getYear());
    assertEquals(Month.OCTOBER, d2.getMonth());
    assertEquals(30, d2.getDayOfMonth());

    d1 = LocalDate.of(2015, Month.AUGUST, 1);
    d2 = TimeLib.getClosestBusinessDay(d1, true);
    assertEquals(2015, d2.getYear());
    assertEquals(Month.JULY, d2.getMonth());
    assertEquals(31, d2.getDayOfMonth());

    d1 = LocalDate.of(2015, Month.AUGUST, 1);
    d2 = TimeLib.getClosestBusinessDay(d1, false);
    assertEquals(2015, d2.getYear());
    assertEquals(Month.AUGUST, d2.getMonth());
    assertEquals(3, d2.getDayOfMonth());
  }
}
