package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class MarwoodMethod
{
  /**
   * Run a Marwood-Minnen SWR simulation and print results.
   * 
   * @param years duration of retirement
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   */
  public static void findMarwoodSWR(int years, int lookbackYears) throws IOException
  {
    final int bengenSWR = BengenMethod.lookUpSWR(years);
    final int lookbackMonths = lookbackYears * 12;
    final int lastIndex = SwrLib.lastIndex(years);
    final int percentStock = BengenMethod.lookUpPercentStock(years);

    int maxSWR = 0; // what was the best SWR of all time?
    int maxIndex = -1; // index when best SWR was found
    int nWin = 0, nFail = 0; // win = better than Bengen SWR
    Sequence seqMarwoodSWR = new Sequence("Marwood SWR");
    Sequence seqRealBalance = new Sequence("Final Balance (real)");

    for (int iStart = lookbackMonths; iStart <= lastIndex; ++iStart) {
      // Find best "virtual" retirement year within the lookback period.
      int bestSWR = 0;
      int bestIndex = -1;
      MonthlyInfo bestInfo = null;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iStart - iLookback; // index of start of virtual retirement

        final int virtualYears = years + (int) Math.ceil(iLookback / 12.0);
        final int virtualPercentStock = BengenMethod.lookUpPercentStock(virtualYears);
        final int swr = BengenMethod.lookUpSWR(virtualYears);

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        final int simYears = (int) Math.floor(iLookback / 12.0) + 1; // run long enough to include current month
        MonthlyInfo info = SwrLib.runPeriod(iVirtualStart, swr / 100.0, simYears, virtualPercentStock, virtualSalaries);
        assert info.ok();

        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iStart;

        final int impliedSWR = (int) Math.floor(virtualNow.percent() * 100);
        assert iLookback > 0 || impliedSWR == bengenSWR; // iLookback == 0 must match Bengen
        if (impliedSWR > bestSWR) {
          bestSWR = impliedSWR;
          bestIndex = iVirtualStart;
          bestInfo = virtualNow;
        }

        // System.out.printf(" %d [%s] vy=%d [%d] r: %7.4f [%d, %d] balance: $%.2f\n", iVirtualStart,
        // TimeLib.formatMonth(SwrLib.time(iVirtualStart)), virtualYears, swr, FinLib.mul2ret(r), impliedSWR, bestSWR,
        // currentBalance);
      }

      if (bestSWR > maxSWR) {
        maxSWR = bestSWR;
        maxIndex = iStart;
      }

      MonthlyInfo info = SwrLib.runPeriod(iStart, bestSWR / 100.0, years, percentStock, null);
      assert info.ok(); // safe by construction, but still verify

      seqMarwoodSWR.addData(bestSWR / 100.0, SwrLib.time(iStart));
      final double realBalance = info.balance * SwrLib.inflation(info.index, iStart);
      seqRealBalance.addData(realBalance, SwrLib.time(iStart));

      if (bestIndex != iStart) { // only print info if we found something better than Bengen
        final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
        System.out.printf("%d [%s] -> %d [%s] swr: %d +%.3f%% |$%.2f ($%.2f)| inflation: %f  %s\n", iStart,
            TimeLib.formatMonth(SwrLib.time(iStart)), bestIndex, TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR,
            swrGain, realBalance, info.balance, SwrLib.inflation(iStart, bestIndex), bestInfo);
      }

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }
    }

    System.out.printf("Max SWR: %d [%s]: %d\n", maxIndex, TimeLib.formatMonth(SwrLib.time(maxIndex)), maxSWR);
    System.out.printf("win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-swr.html"), "Marwood-Minnen SWR", "100%", "800px",
        ChartScaling.LINEAR, ChartTiming.MONTHLY, seqMarwoodSWR);
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();
    findMarwoodSWR(30, 10); // TODO support more than the [30..40] range
  }
}
