package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.data.Sequence;

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
    assertEquals(1.2, FinLib.getReturn(cumulativeReturns, 0, 1), 1e-6);
    assertEquals(1.2, FinLib.getReturn(cumulativeReturns, 1, 2), 1e-6);
    assertEquals(0.9, FinLib.getReturn(cumulativeReturns, 2, 3), 1e-6);
    assertEquals(1.44, FinLib.getReturn(cumulativeReturns, 0, 2), 1e-6);
    assertEquals(1.08, FinLib.getReturn(cumulativeReturns, 1, 3), 1e-6);
    assertEquals(1.296, FinLib.getReturn(cumulativeReturns, 0, 3), 1e-6);
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
}