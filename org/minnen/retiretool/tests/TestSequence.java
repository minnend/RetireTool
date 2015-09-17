package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.data.Sequence;

public class TestSequence
{
  final double eps = 1e-8;

  @Test
  public void testMinMax()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });
    assertFalse(seq.isEmpty());
    assertEquals(4, seq.size());
    assertEquals(4, seq.length());
    assertEquals(1, seq.getNumDims());
    assertFalse(seq.isLocked());
    assertEquals("test", seq.getName());
    assertEquals(1, seq.getMin().get(0), eps);
    assertEquals(4, seq.getMax().get(0), eps);
  }

  @Test
  public void testSubseq()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });

    Sequence subseq = seq.subseq(2);
    assertEquals(2, subseq.length());
    assertArrayEquals(new double[] { 4, 3 }, subseq.extractDim(0), eps);

    subseq = seq.subseq(1, 2);
    assertEquals(2, subseq.length());
    assertArrayEquals(new double[] { 1, 4 }, subseq.extractDim(0), eps);

    seq.lock(1, 3);
    subseq = seq.subseq(1, 2);
    assertEquals(2, subseq.length());
    assertArrayEquals(new double[] { 4, 3 }, subseq.extractDim(0), eps);
  }

  @Test
  public void testExtractDim()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });

    double[] a = seq.extractDim(0);
    assertEquals(4, a.length);
    assertArrayEquals(new double[] { 2, 1, 4, 3 }, a, eps);

    a = seq.extractDim(0, 2);
    assertEquals(2, a.length);
    assertArrayEquals(new double[] { 4, 3 }, a, eps);

    a = seq.extractDim(0, 1, 2);
    assertEquals(2, a.length);
    assertArrayEquals(new double[] { 1, 4 }, a, eps);

    seq.lock(1, 3);
    a = seq.extractDim(0, 1, 2);
    assertEquals(2, a.length);
    assertArrayEquals(new double[] { 4, 3 }, a, eps);
  }

  @Test
  public void testLock()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });
    assertFalse(seq.isLocked());
    seq.lock(0, 3);
    assertTrue(seq.isLocked());
    assertEquals(4, seq.length());
    assertEquals(1, seq.get(1, 0), eps);
    assertEquals(3, seq.get(3, 0), eps);

    seq.lock(1, 2);
    assertEquals(2, seq.length());
    assertEquals(1, seq.get(0, 0), eps);
    assertEquals(4, seq.get(1, 0), eps);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testBeforeLockViolation()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });
    assertFalse(seq.isLocked());
    seq.lock(1, 2);
    assertTrue(seq.isLocked());
    assertEquals(2, seq.length());
    seq.get(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testAfterLockViolation()
  {
    Sequence seq = new Sequence("test", new double[] { 2, 1, 4, 3 });
    assertFalse(seq.isLocked());
    seq.lock(0, 2);
    assertTrue(seq.isLocked());
    assertEquals(3, seq.length());
    seq.get(3);
  }
}
