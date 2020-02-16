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
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotLine;

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
    final int iLastWithFullRetirement = SwrLib.lastIndex(years);
    final int iFirstWithHistory = lookbackMonths;
    final int iStartSim = iFirstWithHistory;
    final int percentStock = BengenMethod.lookUpPercentStock(years);

    int maxSWR = 0; // what was the best SWR of all time?
    int maxIndex = -1; // index when best SWR was found
    int nWin = 0, nFail = 0; // win = better than Bengen SWR
    Sequence seqMarwoodSWR = new Sequence("Marwood SWR");
    Sequence seqRealBalance = new Sequence("Final Balance (real)");
    Sequence seqYearsBack = new Sequence("Virtual Retirement Years");
    Sequence seqBengenSalary = new Sequence("Bengen Salary");
    Sequence seqMarwoodSalary = new Sequence("Marwood Salary");

    // Set initial nest egg so that final nest egg is $1M. Last data point is `-2` since we need one more month of data
    // to simulate a one month retirement.
    double nestEgg = 1e6 * SwrLib.growth(-2, iStartSim, percentStock);

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      // Find best "virtual" retirement year within the lookback period.
      int bestSWR = 0;
      int bestIndex = -1;
      MonthlyInfo bestInfo = null;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iRetire - iLookback; // index of start of virtual retirement

        final int virtualYears = years + (int) Math.ceil(iLookback / 12.0);
        final int virtualPercentStock = BengenMethod.lookUpPercentStock(virtualYears);
        final int swr = BengenMethod.lookUpSWR(virtualYears);

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        final int simYears = (int) Math.floor(iLookback / 12.0) + 1; // run long enough to include current month
        MonthlyInfo info = SwrLib.runPeriod(iVirtualStart, swr / 100.0, simYears, virtualPercentStock, virtualSalaries);
        assert info.ok();

        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iRetire;

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
        maxIndex = iRetire;
      }

      MonthlyInfo info = SwrLib.runPeriod(iRetire, bestSWR / 100.0, years, percentStock, null);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == years * 12;

      final long now = SwrLib.time(iRetire);
      seqMarwoodSWR.addData(bestSWR / 100.0, now);
      final double realBalance = info.balance * SwrLib.inflation(info.index, iRetire);
      seqRealBalance.addData(realBalance, now);
      seqYearsBack.addData((iRetire - bestIndex) / 12.0, now);

      final double realNestEgg = nestEgg * SwrLib.inflation(iRetire, -2);
      final double bengenSalary = realNestEgg * bengenSWR / 10000.0;
      final double marwoodSalary = realNestEgg * bestSWR / 10000.0;
      seqMarwoodSalary.addData(marwoodSalary, now);
      seqBengenSalary.addData(bengenSalary, now);

      // if (bestIndex != iRetire) { // only print info if we found something better than Bengen
      final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
      System.out.printf("%d -> %d  [%s] -> [%s] swr: %d +%.3f%% | $%.2f ($%.2f) %s  ($%.2f, $%.2f)\n", iRetire,
          bestIndex, TimeLib.formatMonth(SwrLib.time(iRetire)), TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR,
          swrGain, realBalance, info.balance, bestInfo, marwoodSalary, bengenSalary);
      // }

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }

      // Simulate nest egg value so that we can report retirement salary in dollars.
      // TODO make monthly contributions over time?
      nestEgg *= SwrLib.growth(iRetire, percentStock); // growth due to market
    }

    System.out.printf("Max SWR: %d [%s]: %d\n", maxIndex, TimeLib.formatMonth(SwrLib.time(maxIndex)), maxSWR);
    System.out.printf("win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-swr.html"), "Marwood-Minnen SWR",
        "100%", "800px", ChartScaling.LINEAR, ChartTiming.MONTHLY, seqMarwoodSWR, seqYearsBack);
    config.addPlotLineY(new PlotLine(bengenSWR / 100.0, 2.0, "#777", "dash"));
    Chart.saveChart(config);

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-salary.html"),
        "Retirement Salary ($1M nest egg in today\\'s dollars)", "100%", "800px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, seqMarwoodSalary, seqBengenSalary);
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();
    findMarwoodSWR(30, 10); // TODO support more than the [30..40] range
  }
}
