package org.minnen.retiretool.tests;

import java.util.Calendar;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

@RunWith(Suite.class)
@SuiteClasses({ TestBond.class, TestInvestmentStats.class, TestLibrary.class, TestFinLib.class, TestSequence.class,
    TestSequenceStore.class, TestSequenceStoreV1.class, TestSlippage.class, TestFixedPoint.class, TestTimeLib.class })
public class AllTests
{
  public static Sequence buildMonthlySequence(double[] data)
  {
    return buildMonthlySequence("test", data);
  }

  public static Sequence buildMonthlySequence(String name, double[] data)
  {
    Sequence seq = new Sequence(name);
    Calendar cal = TimeLib.setTime(TimeLib.now(), 1, 1, 2000);
    for (double x : data) {
      seq.addData(x, cal.getTimeInMillis());
      cal.add(Calendar.MONTH, 1);
    }
    return seq;
  }
}
