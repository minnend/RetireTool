package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.time.DayOfWeek;
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

  @Test
  public void testGetNthDayOfWeek()
  {
    LocalDate date;

    // Thanksgiving = Fourth Thursday of November.
    date = TimeLib.getNthDayOfWeek(2015, Month.NOVEMBER, 4, DayOfWeek.THURSDAY);
    assertEquals(LocalDate.of(2015, 11, 26), date);

    date = TimeLib.getNthDayOfWeek(2016, Month.NOVEMBER, 4, DayOfWeek.THURSDAY);
    assertEquals(LocalDate.of(2016, 11, 24), date);

    date = TimeLib.getNthDayOfWeek(1990, Month.NOVEMBER, 4, DayOfWeek.THURSDAY);
    assertEquals(LocalDate.of(1990, 11, 22), date);

    date = TimeLib.getNthDayOfWeek(1900, Month.NOVEMBER, 4, DayOfWeek.THURSDAY);
    assertEquals(LocalDate.of(1900, 11, 22), date);

    // Memorial Day = Last Monday of May.
    date = TimeLib.getNthDayOfWeek(2015, Month.MAY, -1, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2015, 5, 25), date);

    date = TimeLib.getNthDayOfWeek(2014, Month.MAY, -1, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2014, 5, 26), date);

    date = TimeLib.getNthDayOfWeek(2016, Month.MAY, -1, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2016, 5, 30), date);

    // MLK Day = Third Monday of January.
    date = TimeLib.getNthDayOfWeek(2016, Month.JANUARY, 3, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2016, 1, 18), date);

    date = TimeLib.getNthDayOfWeek(2015, Month.JANUARY, 3, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2015, 1, 19), date);

    date = TimeLib.getNthDayOfWeek(2014, Month.JANUARY, 3, DayOfWeek.MONDAY);
    assertEquals(LocalDate.of(2014, 1, 20), date);
  }

  @Test
  public void testGetEaster()
  {
    // https://en.wikipedia.org/wiki/List_of_dates_for_Easter

    int year = 2015;
    LocalDate date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 5), date);

    year = 2016;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 3, 27), date);

    year = 2014;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 20), date);

    year = 2013;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 3, 31), date);

    year = 2012;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 8), date);

    year = 2011;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 24), date);

    year = 2000;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 23), date);

    year = 1995;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 4, 16), date);

    year = 2035;
    date = TimeLib.getEaster(year);
    assertEquals(LocalDate.of(year, 3, 25), date);
  }
}
