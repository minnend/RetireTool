package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.minnen.retiretool.Bond;

public class TestBond
{

  @Test
  public void testCalcPrice()
  {
    // source: https://faculty.unlv.edu/msullivan/Chapter%207.pdf
    double tol = 0.0051; // site gives answer to nearest penny
    double p = Bond.calcPrice(98.75, 7, 1000, 10, 1, 0);
    assertEquals(p, 1201.93, tol);

    p = Bond.calcPrice(98.75, 9, 1000, 10, 1, 0);
    assertEquals(p, 1056.15, tol);

    p = Bond.calcPrice(98.75, 11, 1000, 10, 1, 0);
    assertEquals(p, 933.75, tol);

    p = Bond.calcPrice(145, 11, 1000, 16, 2, 0);
    assertEquals(p, 1260.82, tol);

    p = Bond.calcPrice(0, 9, 1000, 30, 0, 0);
    assertEquals(p, 75.37, tol);

    // source: http://users.wfu.edu/palmitar/Law&Valuation/chapter%204/4-2-2.htm
    p = Bond.calcPrice(86, 8, 1000, 10, 1, 0);
    assertEquals(p, 1040.26, tol);

    p = Bond.calcPrice(86, 8, 1000, 10, 2, 0);
    assertEquals(p, 1040.77, tol);

    // source: http://www.economics-finance.org/jefe/fin/Secrestpaper.pdf
    p = Bond.calcPrice(120, 8, 1000, 2, 2, 72.0 / 182.0);
    assertEquals(p, 1089.37, tol);

    p = Bond.calcPrice(120, 8, 1000, 2, 2, 0);
    assertEquals(p, 1072.60, tol);

    // source: http://accountingexplained.com/financial/lt-liabilities/bond-price
    tol = 0.51; // site only gives quote to nearest dollar
    p = Bond.calcPrice(8000, 10, 100000, 10, 1, 0);
    assertEquals(p, 87711, tol);

    p = Bond.calcPrice(9000, 8, 100000, 10, 2, 0);
    assertEquals(p, 106795, tol);
  }
}
