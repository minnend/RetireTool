package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.FixedPoint;

public class TestFixedPoint
{
  @Test
  public void testConvert()
  {
    long x;

    x = 101;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));

    x = 9321850;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));

    x = -101;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));

    x = 0;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));

    x = 1;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));

    x = -1;
    assertEquals(x, FixedPoint.toFixed(FixedPoint.toFloat(x)));
  }

  @Test
  public void testMul()
  {
    long x, y, fx, fy;

    x = 3;
    y = 4;
    fx = FixedPoint.toFixed(x);
    fy = FixedPoint.toFixed(y);
    assertEquals(FixedPoint.toFixed(x * y), FixedPoint.mul(fx, fy));

    x = -3;
    y = 4;
    fx = FixedPoint.toFixed(x);
    fy = FixedPoint.toFixed(y);
    assertEquals(FixedPoint.toFixed(x * y), FixedPoint.mul(fx, fy));

    x = 0;
    y = 1;
    fx = FixedPoint.toFixed(x);
    fy = FixedPoint.toFixed(y);
    assertEquals(FixedPoint.toFixed(x * y), FixedPoint.mul(fx, fy));

    x = 3029847;
    y = 430921;
    fx = FixedPoint.toFixed(x);
    fy = FixedPoint.toFixed(y);
    assertEquals(FixedPoint.toFixed(x * y), FixedPoint.mul(fx, fy));

    x = 3121312;
    y = -49898;
    fx = FixedPoint.toFixed(x);
    fy = FixedPoint.toFixed(y);
    assertEquals(FixedPoint.toFixed(x * y), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(2.5);
    fy = FixedPoint.toFixed(3.0);
    assertEquals(FixedPoint.toFixed(7.5), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(-0.1);
    fy = FixedPoint.toFixed(0.05);
    assertEquals(FixedPoint.toFixed(-0.005), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(0.02);
    fy = FixedPoint.toFixed(0.075);
    assertEquals(FixedPoint.toFixed(0.002), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(-0.02);
    fy = FixedPoint.toFixed(0.075);
    assertEquals(FixedPoint.toFixed(-0.002), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(0.02);
    fy = FixedPoint.toFixed(-0.075);
    assertEquals(FixedPoint.toFixed(-0.002), FixedPoint.mul(fx, fy));

    fx = FixedPoint.toFixed(-0.02);
    fy = FixedPoint.toFixed(-0.075);
    assertEquals(FixedPoint.toFixed(0.002), FixedPoint.mul(fx, fy));
  }

  @Test
  public void testDiv()
  {
    long fx, fy;

    fx = FixedPoint.toFixed(12);
    fy = FixedPoint.toFixed(4);
    assertEquals(FixedPoint.toFixed(3), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(3);
    fy = FixedPoint.toFixed(6);
    assertEquals(FixedPoint.toFixed(0.5), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(-12);
    fy = FixedPoint.toFixed(4);
    assertEquals(FixedPoint.toFixed(-3), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(-12);
    fy = FixedPoint.toFixed(-4);
    assertEquals(FixedPoint.toFixed(3), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(12);
    fy = FixedPoint.toFixed(-4);
    assertEquals(FixedPoint.toFixed(-3), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(0);
    fy = FixedPoint.toFixed(4);
    assertEquals(FixedPoint.toFixed(0), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(1000000);
    fy = FixedPoint.toFixed(250000);
    assertEquals(FixedPoint.toFixed(4), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(1);
    fy = FixedPoint.toFixed(10);
    assertEquals(FixedPoint.toFixed(0.1), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(1);
    fy = FixedPoint.toFixed(100);
    assertEquals(FixedPoint.toFixed(0.01), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(1);
    fy = FixedPoint.toFixed(1000);
    assertEquals(FixedPoint.toFixed(0.001), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(14);
    fy = FixedPoint.toFixed(10000);
    assertEquals(FixedPoint.toFixed(0.001), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(16);
    fy = FixedPoint.toFixed(10000);
    assertEquals(FixedPoint.toFixed(0.002), FixedPoint.div(fx, fy));

    fx = FixedPoint.toFixed(15);
    fy = FixedPoint.toFixed(10000);
    assertEquals(FixedPoint.toFixed(0.002), FixedPoint.div(fx, fy));
  }
}
