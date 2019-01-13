package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.util.Fixed;

public class TestFixedPoint
{
  @Test
  public void testConvert()
  {
    long x;

    x = 101;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));

    x = 9321850;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));

    x = -101;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));

    x = 0;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));

    x = 1;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));

    x = -1;
    assertEquals(x, Fixed.toFixed(Fixed.toFloat(x)));
  }

  @Test
  public void testMul()
  {
    long x, y, fx, fy;

    x = 3;
    y = 4;
    fx = Fixed.toFixed(x);
    fy = Fixed.toFixed(y);
    assertEquals(Fixed.toFixed(x * y), Fixed.mul(fx, fy));

    x = -3;
    y = 4;
    fx = Fixed.toFixed(x);
    fy = Fixed.toFixed(y);
    assertEquals(Fixed.toFixed(x * y), Fixed.mul(fx, fy));

    x = 0;
    y = 1;
    fx = Fixed.toFixed(x);
    fy = Fixed.toFixed(y);
    assertEquals(Fixed.toFixed(x * y), Fixed.mul(fx, fy));

    x = 30297;
    y = 4309;
    fx = Fixed.toFixed(x);
    fy = Fixed.toFixed(y);
    assertEquals(Fixed.toFixed(x * y), Fixed.mul(fx, fy));

    x = 31213;
    y = -4989;
    fx = Fixed.toFixed(x);
    fy = Fixed.toFixed(y);
    assertEquals(Fixed.toFixed(x * y), Fixed.mul(fx, fy));

    fx = Fixed.toFixed(2.5);
    fy = Fixed.toFixed(3.0);
    assertEquals(Fixed.toFixed(7.5), Fixed.mul(fx, fy));

    fx = Fixed.toFixed(-0.1);
    fy = Fixed.toFixed(0.05);
    assertEquals(Fixed.toFixed(-0.005), Fixed.mul(fx, fy));

    // Note: the following cases test rounding, which depends on Fixed.SCALE.
    fx = Fixed.toFixed(0.0002);
    fy = Fixed.toFixed(0.075);
    assertEquals(Fixed.toFixed(0.00002), Fixed.mul(fx, fy));

    fx = Fixed.toFixed(-0.0002);
    fy = Fixed.toFixed(0.075);
    assertEquals(Fixed.toFixed(-0.00002), Fixed.mul(fx, fy));

    fx = Fixed.toFixed(0.0002);
    fy = Fixed.toFixed(-0.075);
    assertEquals(Fixed.toFixed(-0.00002), Fixed.mul(fx, fy));

    fx = Fixed.toFixed(-0.0002);
    fy = Fixed.toFixed(-0.075);
    assertEquals(Fixed.toFixed(0.00002), Fixed.mul(fx, fy));
  }

  @Test
  public void testDivRound()
  {
    long fx, fy;

    fx = Fixed.toFixed(12);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(3), Fixed.div(fx, fy));

    fx = Fixed.toFixed(3);
    fy = Fixed.toFixed(6);
    assertEquals(Fixed.toFixed(0.5), Fixed.div(fx, fy));

    fx = Fixed.toFixed(-12);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(-3), Fixed.div(fx, fy));

    fx = Fixed.toFixed(-12);
    fy = Fixed.toFixed(-4);
    assertEquals(Fixed.toFixed(3), Fixed.div(fx, fy));

    fx = Fixed.toFixed(12);
    fy = Fixed.toFixed(-4);
    assertEquals(Fixed.toFixed(-3), Fixed.div(fx, fy));

    fx = Fixed.toFixed(0);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(0), Fixed.div(fx, fy));

    fx = Fixed.toFixed(1000000);
    fy = Fixed.toFixed(250000);
    assertEquals(Fixed.toFixed(4), Fixed.div(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(10);
    assertEquals(Fixed.toFixed(0.1), Fixed.div(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(100);
    assertEquals(Fixed.toFixed(0.01), Fixed.div(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(1000);
    assertEquals(Fixed.toFixed(0.001), Fixed.div(fx, fy));

    // Note: the following cases test rounding, which depends on Fixed.SCALE.
    fx = Fixed.toFixed(14);
    fy = Fixed.toFixed(1000000);
    assertEquals(Fixed.toFixed(0.00001), Fixed.div(fx, fy));

    fx = Fixed.toFixed(16);
    fy = Fixed.toFixed(1000000);
    assertEquals(Fixed.toFixed(0.00002), Fixed.div(fx, fy));

    fx = Fixed.toFixed(15);
    fy = Fixed.toFixed(1000000);
    assertEquals(Fixed.toFixed(0.00002), Fixed.div(fx, fy));

    fx = Fixed.toFixed(-16);
    fy = Fixed.toFixed(1000000);
    assertEquals(Fixed.toFixed(-0.00002), Fixed.div(fx, fy));

    fx = Fixed.toFixed(15);
    fy = Fixed.toFixed(-1000000);
    assertEquals(Fixed.toFixed(-0.00002), Fixed.div(fx, fy));
  }

  @Test
  public void testDivTrunc()
  {
    long fx, fy;

    fx = Fixed.toFixed(12);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(3), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(3);
    fy = Fixed.toFixed(6);
    assertEquals(Fixed.toFixed(0.5), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(-12);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(-3), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(-12);
    fy = Fixed.toFixed(-4);
    assertEquals(Fixed.toFixed(3), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(12);
    fy = Fixed.toFixed(-4);
    assertEquals(Fixed.toFixed(-3), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(0);
    fy = Fixed.toFixed(4);
    assertEquals(Fixed.toFixed(0), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(1000000);
    fy = Fixed.toFixed(250000);
    assertEquals(Fixed.toFixed(4), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(10);
    assertEquals(Fixed.toFixed(0.1), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(100);
    assertEquals(Fixed.toFixed(0.01), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(1);
    fy = Fixed.toFixed(1000);
    assertEquals(Fixed.toFixed(0.001), Fixed.divTrunc(fx, fy));

    // Note: the following cases depend on Fixed.SCALE.
    fx = Fixed.toFixed(14);
    fy = Fixed.toFixed(Fixed.SCALE * 10);
    assertEquals(Fixed.toFixed(0.00001), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(16);
    fy = Fixed.toFixed(Fixed.SCALE * 10);
    assertEquals(Fixed.toFixed(0.00001), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(15);
    fy = Fixed.toFixed(Fixed.SCALE * 10);
    assertEquals(Fixed.toFixed(0.00001), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(-16);
    fy = Fixed.toFixed(Fixed.SCALE * 10);
    assertEquals(Fixed.toFixed(-0.00001), Fixed.divTrunc(fx, fy));

    fx = Fixed.toFixed(15);
    fy = Fixed.toFixed(-Fixed.SCALE * 10);
    assertEquals(Fixed.toFixed(-0.00001), Fixed.divTrunc(fx, fy));
  }

  @Test
  public void testTruncate()
  {
    long x, y;

    x = Fixed.toFixed(100);
    y = Fixed.toFixed(100);
    assertEquals(y, Fixed.truncate(x, Fixed.HUNDREDTH));

    x = Fixed.toFixed(0.1234);
    y = Fixed.toFixed(0.12);
    assertEquals(y, Fixed.truncate(x, Fixed.HUNDREDTH));

    x = Fixed.toFixed(0.1289);
    y = Fixed.toFixed(0.12);
    assertEquals(y, Fixed.truncate(x, Fixed.HUNDREDTH));
  }

  @Test
  public void testRound()
  {
    long x, y;

    x = Fixed.toFixed(100);
    y = Fixed.toFixed(100);
    assertEquals(y, Fixed.round(x, Fixed.HUNDREDTH));

    x = Fixed.toFixed(0.1234);
    y = Fixed.toFixed(0.12);
    assertEquals(y, Fixed.round(x, Fixed.HUNDREDTH));

    x = Fixed.toFixed(0.1289);
    y = Fixed.toFixed(0.13);
    assertEquals(y, Fixed.round(x, Fixed.HUNDREDTH));
  }
}
