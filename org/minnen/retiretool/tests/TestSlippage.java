package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.Slippage;

public class TestSlippage
{
  public static final double eps = 1e-6;

  @Test
  public void testNoSlippage()
  {
    Slippage slippage = new Slippage(0.0, 0.0);

    double price = 100.0;
    assertEquals(price, slippage.applyToBuy(price), eps);
    assertEquals(price, slippage.applyToSell(price), eps);

    price = 10.0;
    assertEquals(price, slippage.applyToBuy(price), eps);
    assertEquals(price, slippage.applyToSell(price), eps);

    price = 0.0;
    assertEquals(price, slippage.applyToBuy(price), eps);
    assertEquals(price, slippage.applyToSell(price), eps);
  }

  @Test
  public void testFixedOnly()
  {
    Slippage slippage = new Slippage(1.0, 0.0);

    assertEquals(101.0, slippage.applyToBuy(100.0), eps);
    assertEquals(99.0, slippage.applyToSell(100.0), eps);
  }

  @Test
  public void testFractionOnly()
  {
    Slippage slippage = new Slippage(0.0, 10.0);

    assertEquals(110.0, slippage.applyToBuy(100.0), eps);
    assertEquals(90.0, slippage.applyToSell(100.0), eps);
  }

  @Test
  public void testFixedAndFraction()
  {
    Slippage slippage1 = new Slippage(1.0, 10.0);
    assertEquals(111.0, slippage1.applyToBuy(100.0), eps);
    assertEquals(89.0, slippage1.applyToSell(100.0), eps);

    Slippage slippage2 = new Slippage(0.01, 0.1);
    assertEquals(10.02, slippage2.applyToBuy(10.0), eps);
    assertEquals(9.98, slippage2.applyToSell(10.0), eps);
  }
}
