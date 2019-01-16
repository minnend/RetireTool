package org.minnen.retiretool.tests;

import java.time.LocalDate;
import java.time.Month;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

@RunWith(Suite.class)
@SuiteClasses({ TestBond.class, TestCumulativeStats.class, TestFinLib.class, TestFixedPoint.class,
    TestInvestmentStats.class, TestKDE.class, TestLibrary.class, TestMixablePredictor.class, TestRankers.class,
    TestRegression.class, TestSequence.class, TestSequenceStore.class, TestSequenceStoreV1.class, TestSlippage.class,
    TestStockInfo.class, TestStump.class, TestTimeLib.class })
public class AllTests
{
  public static Sequence buildMonthlySequence(double[] data)
  {
    return buildMonthlySequence("test", data);
  }

  public static Sequence buildMonthlySequence(String name, double[] data)
  {
    Sequence seq = new Sequence(name);
    LocalDate date = LocalDate.of(2000, Month.JANUARY, 1);
    for (double x : data) {
      seq.addData(x, TimeLib.toMs(date));
      date = date.plusMonths(1);
    }
    return seq;
  }
}
