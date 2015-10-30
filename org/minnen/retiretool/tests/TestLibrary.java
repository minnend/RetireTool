package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.Library;

public class TestLibrary
{
  @Test
  public void testSum()
  {
    int[] a = new int[] { 1, 2, 3, 4, 5 };

    assertEquals(15, Library.sum(a));
    assertEquals(14, Library.sum(a, 1, -1));
    assertEquals(7, Library.sum(a, 2, -2));
    assertEquals(9, Library.sum(a, 3, 4));
    assertEquals(4, Library.sum(a, 3, 3));
    assertEquals(0, Library.sum(a, 5, 1));
    assertEquals(5, Library.sum(a, -1, -1));
  }

  @Test
  public void testCopy()
  {
    // Sizes match.
    double from[] = new double[] { 1, 2, 3, 4 };
    double to[] = new double[from.length];
    Library.copy(from, to);
    assertArrayEquals(from, to, 0.0);

    // Destination is smaller.
    double expected[] = new double[] { 1, 2 };
    to = new double[2];
    Library.copy(from, to);
    assertArrayEquals(expected, to, 0.0);

    // Destination is larger.
    expected = new double[] { 1, 2, 3, 4, 0, 0 };
    to = new double[6];
    Library.copy(from, to);
    assertArrayEquals(expected, to, 0.0);

    // Destination is empty.
    expected = new double[0];
    to = new double[0];
    Library.copy(from, to);
    assertArrayEquals(expected, to, 0.0);

    // Source is empty.
    expected = new double[] { 0, 0, 0 };
    from = new double[0];
    to = new double[3];
    Library.copy(from, to);
    assertArrayEquals(expected, to, 0.0);
  }

  @Test
  public void testSort()
  {
    // Sort ascending.
    double[] a = new double[] { 5, -3, 2, 0.01, 1e6 };
    double[] expected = new double[] { -3, 0.01, 2, 5, 1e6 };
    Library.sort(a, true);
    assertArrayEquals(expected, a, 0.0);

    // Sort descending.
    a = new double[] { 5, -3, 2, 0.01, 1e6 };
    expected = new double[] { 1e6, 5, 2, 0.01, -3 };
    Library.sort(a, false);
    assertArrayEquals(expected, a, 0.0);
  }

  @Test
  public void testMean()
  {
    double[] a = new double[] { 1, 2, 3, 4, 5, 6 };
    assertEquals(3.5, Library.mean(a), 1e-6);

    a = new double[] { 600, 470, 170, 430, 300 };
    assertEquals(394, Library.mean(a), 1e-6);
  }

  @Test
  public void testVariance()
  {
    double[] a = new double[] { 1, 2, 3, 4, 5, 6 };
    assertEquals(3.5, Library.variance(a), 1e-6);
    assertEquals(1.87082869339, Library.stdev(a), 1e-6);

    a = new double[] { 600, 470, 170, 430, 300 };
    assertEquals(27130, Library.variance(a), 1e-6);
    assertEquals(164.711869639, Library.stdev(a), 1e-6);
  }

  @Test
  public void testCovariance()
  {
    double[] a = new double[] { 1, 2, 3 };
    double[] b = new double[] { 4, 1, -2 };
    assertEquals(-3, Library.covariance(a, b), 1e-6);
  }

  @Test
  public void testCorrelation()
  {
    double[] a = new double[] { 1, 2, 3 };
    double[] b = new double[] { 4, 1, -2 };
    assertEquals(-1, Library.correlation(a, b), 1e-6);
  }
}
