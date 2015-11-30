package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.util.Calendar;

import org.junit.Test;
import org.minnen.retiretool.util.TimeLib;

public class TestTimeLib
{
  @Test
  public void testMonthsBetween()
  {
    long a, b;

    a = TimeLib.getTime(1, 1, 1995);
    b = TimeLib.getTime(1, 1, 1995);
    assertEquals(0, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(1, 1, 1995);
    b = TimeLib.getTime(22, 1, 1995);
    assertEquals(0, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(1, 1, 1995);
    b = TimeLib.getTime(1, 2, 1995);
    assertEquals(1, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(10, 12, 1994);
    b = TimeLib.getTime(20, 1, 1995);
    assertEquals(1, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(1, 1, 1995);
    b = TimeLib.getTime(1, 1, 1996);
    assertEquals(12, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(31, 1, 1994);
    b = TimeLib.getTime(1, 1, 1995);
    assertEquals(12, TimeLib.monthsBetween(a, b));

    a = TimeLib.getTime(1, 1, 1995);
    b = TimeLib.getTime(1, 2, 2000);
    assertEquals(61, TimeLib.monthsBetween(a, b));
  }

  @Test
  public void testToNextBusinessDay()
  {
    long time;
    Calendar cal;

    time = TimeLib.toNextBusinessDay(TimeLib.getTime(1, 11, 2015));
    cal = TimeLib.ms2cal(time);
    assertEquals(2015, cal.get(Calendar.YEAR));
    assertEquals(10, cal.get(Calendar.MONTH));
    assertEquals(2, cal.get(Calendar.DAY_OF_MONTH));

    time = TimeLib.toNextBusinessDay(TimeLib.getTime(30, 11, 2015));
    cal = TimeLib.ms2cal(time);
    assertEquals(2015, cal.get(Calendar.YEAR));
    assertEquals(11, cal.get(Calendar.MONTH));
    assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));

    time = TimeLib.toNextBusinessDay(TimeLib.getTime(31, 10, 2015));
    cal = TimeLib.ms2cal(time);
    assertEquals(2015, cal.get(Calendar.YEAR));
    assertEquals(10, cal.get(Calendar.MONTH));
    assertEquals(2, cal.get(Calendar.DAY_OF_MONTH));

    time = TimeLib.toNextBusinessDay(TimeLib.getTime(1, 1, 2016));
    cal = TimeLib.ms2cal(time);
    assertEquals(2016, cal.get(Calendar.YEAR));
    assertEquals(0, cal.get(Calendar.MONTH));
    assertEquals(4, cal.get(Calendar.DAY_OF_MONTH));

    time = TimeLib.toNextBusinessDay(TimeLib.getTime(26, 2, 2016));
    cal = TimeLib.ms2cal(time);
    assertEquals(2016, cal.get(Calendar.YEAR));
    assertEquals(1, cal.get(Calendar.MONTH));
    assertEquals(29, cal.get(Calendar.DAY_OF_MONTH));
  }
}
