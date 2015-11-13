package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.TimeLib;

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
}
