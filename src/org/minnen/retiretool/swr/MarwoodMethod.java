package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Histogram;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotLine;

public class MarwoodMethod
{
  // TODO Calculate DMSWR for all retirement lengths for each month.
  // TODO Walk-forward optimization for Bengen SWR -- how well does it generalize?

  /**
   * Run a Marwood-Minnen SWR simulation and print results.
   * 
   * @param nRetireYears duration of retirement
   * @param nLookbackYears number of previous years to check for a better "virtual retirement" time
   * @return Sequence holding Marwood-Minnen SWR for each retirement date
   */
  public static Sequence findMarwoodSWR(int nRetireYears, int nLookbackYears) throws IOException
  {
    final int bengenSWR = BengenMethod.lookUpSWR(nRetireYears);
    final int lookbackMonths = nLookbackYears * 12;
    final int iLastWithFullRetirement = SwrLib.lastIndex(nRetireYears);
    final int iFirstWithHistory = lookbackMonths;
    final int iStartSim = iFirstWithHistory;
    final int percentStock = BengenMethod.lookUpPercentStock(nRetireYears);

    int maxSWR = 0; // what was the best SWR of all time?
    int maxIndex = -1; // index when best SWR was found
    int sumSWR = 0;
    int nWin = 0, nFail = 0; // win = better than Bengen SWR
    Sequence seqMarwoodSWR = new Sequence(String.format("DMSWR (%d, %d)", nRetireYears, nLookbackYears));
    Sequence seqBalance = new Sequence("Final Balance");
    Sequence seqYearsBack = new Sequence("Virtual Retirement Years");
    Sequence seqBengenSalary = new Sequence("Bengen Salary");
    Sequence seqMarwoodSalary = new Sequence(String.format("Marwood Salary (%d, %d)", nRetireYears, nLookbackYears));

    // Set initial nest egg so that final nest egg is $1M. Last data point is `-2` since we need one more month of data
    // to simulate a one month retirement.
    double nestEgg = 1e6 * SwrLib.growth(-2, iStartSim, percentStock);

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      // Find best "virtual" retirement year within the lookback period.
      int bestSWR = 0;
      int bestIndex = -1;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iRetire - iLookback; // index of start of virtual retirement

        final int virtualYears = nRetireYears + (int) Math.ceil(iLookback / 12.0);
        final int virtualPercentStock = BengenMethod.lookUpPercentStock(virtualYears);
        final int swr = BengenMethod.lookUpSWR(virtualYears);

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        final int simYears = (int) Math.floor(iLookback / 12.0) + 1; // run long enough to include current month
        // TODO update API to run to a specific index.
        MonthlyInfo info = SwrLib.runPeriod(iVirtualStart, swr / 100.0, simYears, virtualPercentStock, virtualSalaries);
        assert info.ok();

        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iRetire;

        final double floatSWR = virtualNow.percent() * 100;
        final int impliedSWR = (int) Math.floor(floatSWR);
        assert iLookback > 0 || impliedSWR == bengenSWR; // iLookback == 0 must match Bengen
        if (impliedSWR > bestSWR) {
          bestSWR = impliedSWR;
          bestIndex = iVirtualStart;
        }
      }

      if (bestSWR > maxSWR) {
        maxSWR = bestSWR;
        maxIndex = iRetire;
      }
      sumSWR += bestSWR;

      MonthlyInfo info = SwrLib.runPeriod(iRetire, bestSWR / 100.0, nRetireYears, percentStock, null);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == nRetireYears * 12;

      final long now = SwrLib.time(iRetire);
      seqMarwoodSWR.addData(bestSWR / 100.0, now);
      seqBalance.addData(info.balance, now);
      seqYearsBack.addData((iRetire - bestIndex) / 12.0, now);

      final double bengenSalary = nestEgg * bengenSWR / 10000.0;
      final double marwoodSalary = nestEgg * bestSWR / 10000.0;
      seqMarwoodSalary.addData(marwoodSalary, now);
      seqBengenSalary.addData(bengenSalary, now);

      final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
      System.out.printf("%d <- %d [%s] <- [%s] swr: %d +%.3f%% | $%.2f\n", iRetire, bestIndex,
          TimeLib.formatMonth(SwrLib.time(iRetire)), TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR, swrGain,
          info.balance);

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }

      // Simulate nest egg value so that we can report retirement salary in dollars.
      nestEgg *= SwrLib.growth(iRetire, percentStock); // growth due to market
    }

    System.out.printf("Mean SWR: %d\n", sumSWR / (SwrLib.length() - iStartSim));
    System.out.printf("Max SWR: %d @ [%s] (index: %d)\n", maxSWR, TimeLib.formatMonth(SwrLib.time(maxIndex)), maxIndex);
    System.out.printf("win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

    Sequence seqMaxSWR = BengenMethod.findSwrSequence(nRetireYears, 70);
    int index = seqMaxSWR.getIndexAtOrAfter(seqMarwoodSWR.getStartMS());
    seqMaxSWR = seqMaxSWR.subseq(index);

    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-swr.html"), "Marwood-Minnen SWR",
        "100%", "800px", ChartScaling.LINEAR, ChartTiming.MONTHLY, seqMarwoodSWR, seqYearsBack, seqMaxSWR);
    config.addPlotLineY(new PlotLine(bengenSWR / 100.0, 2.0, "#777", "dash"));
    Chart.saveChart(config);

    Chart.saveLineChart(
        new File(DataIO.getOutputPath(), String.format("marwood-salary-%d-%d.html", nRetireYears, nLookbackYears)),
        "Retirement Salary ($1M nest egg in today\\'s dollars)", "100%", "800px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, seqMarwoodSalary, seqBengenSalary);

    return seqMarwoodSWR;
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static void reretire(Sequence seqMarwoodSWR, int years, int lookbackYears) throws IOException
  {
    Sequence allEffectiveSWRs = new Sequence(String.format("Re-Retire Effective SWRs (%d, %d)", years, lookbackYears));
    List<Sequence> swrTrajectories = new ArrayList<>();

    final double nestEgg = 1e6; // retire with one million dollars
    final int nRetirementMonths = years * 12;
    // TODO if we adjust asset allocation, do we need to model taxes?
    // we already rebalance monthly without worrying about capital gains.
    final int percentStock = BengenMethod.lookUpPercentStock(years);

    for (int iMarwood = 0; iMarwood < seqMarwoodSWR.size(); ++iMarwood) {
      final long timeRetire = seqMarwoodSWR.getTimeMS(iMarwood);
      final int iRetire = SwrLib.indexForTime(seqMarwoodSWR.getTimeMS(iMarwood));
      assert timeRetire == SwrLib.time(iRetire);

      double balance = nestEgg;
      double salary = 0;

      Sequence swrTrajectory = new Sequence(String.format("[%s]", TimeLib.formatMonth(timeRetire)));
      for (int i = iRetire; i < iRetire + nRetirementMonths && i < SwrLib.length(); ++i) {
        final long now = SwrLib.time(i);
        assert (i == iRetire && now == timeRetire) || (i > iRetire && now > timeRetire);

        final int nMonthsRetired = i - iRetire;
        final int yearsLeft = (int) Math.ceil((nRetirementMonths - nMonthsRetired) / 12.0);
        assert yearsLeft > 0 && yearsLeft <= years;

        // TODO need dmswr for reduced retirement length => calculate DMSWR for all durations for each month.
        final double bengenSWR = BengenMethod.lookUpSWR(yearsLeft) / 100.0;
        final double swr = Math.max(seqMarwoodSWR.get(iMarwood + nMonthsRetired, 0), bengenSWR);
        double reSalary = balance * swr / 1200.0;
        if (reSalary > salary) {
          if (i > iRetire) System.out.printf("Raise! $%.2f -> $%.2f\n", salary, reSalary);
          salary = reSalary;
        }

        // Calculate effective SWR at retire date by backing out market growth.
        final double effectiveSWR = salary / nestEgg * 1200.0;
        swrTrajectory.addData(effectiveSWR, now);
        allEffectiveSWRs.addData(effectiveSWR, now);

        System.out.printf("%d.%d (%d)  (%.1f, %d)  [%s] -> [%s]: swr=%.2f%% (%.2f)  salary=$%.2f  balance=$%.2f\n",
            iMarwood, nMonthsRetired, i, nMonthsRetired / 12.0, yearsLeft, TimeLib.formatMonth(timeRetire),
            TimeLib.formatMonth(now), swr, effectiveSWR, salary, balance);

        balance -= salary; // withdrawal at beginning of month
        assert balance > 0; // true by construction
        assert BengenMethod.lookUpPercentStock(yearsLeft) == percentStock; // else model taxes on allocation change
        balance *= SwrLib.growth(i, percentStock); // market affects remaining balance
      }

      LocalDate date = TimeLib.ms2date(timeRetire);
      if (date.getYear() % 5 == 0 && date.getMonth() == Month.JANUARY) {
        swrTrajectories.add(swrTrajectory);
      }
    }

    // Calculate histogram over dm-swr and save as a bar chart.
    Sequence histBasic = Histogram.computeHistogram(seqMarwoodSWR, 0.05, 4.0, 0);
    Sequence histWithReRetire = Histogram.computeHistogram(allEffectiveSWRs, 0.05, 4.0, 0);
    assert Math.abs(Library.sum(histBasic.extractDim(2)) - 1.0) < 1e-6;
    assert Math.abs(Library.sum(histWithReRetire.extractDim(2)) - 1.0) < 1e-6;
    assert allEffectiveSWRs.getMin().get(0) >= seqMarwoodSWR.getMin().get(0);
    assert Math.abs(allEffectiveSWRs.get(0, 0) - seqMarwoodSWR.get(0, 0)) <= 1e-6;

    histWithReRetire = histWithReRetire.subseq(0, histBasic.size());

    String[] labels = new String[histBasic.size()];
    for (int i = 0; i < labels.length; ++i) {
      FeatureVec v = histBasic.get(i);
      labels[i] = String.format("%.2f", v.get(0));
    }
    FeatureVec mul = FeatureVec.ones(histBasic.getNumDims()).set(2, 100.0); // convert fraction to percent
    histBasic._mul(mul);
    histWithReRetire._mul(mul);

    Chart.saveChart(new File(DataIO.getOutputPath(), "dmswr-histogram.html"), ChartConfig.Type.Bar,
        "Marwood-Minnen SWR Histogram", labels, null, "100%", "800px", 0.0, Double.NaN, 0.05, ChartScaling.LINEAR,
        ChartTiming.INDEX, 2, histBasic, histWithReRetire);

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "reretire.html"), "SWR Trajectories (With Re-Retiring)",
        "100%", "800px", ChartScaling.LINEAR, ChartTiming.INDEX, swrTrajectories);
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();

    final int years = 30;
    final int lookbackYears = 10;
    Sequence seqMarwoodSWR = findMarwoodSWR(years, lookbackYears);
    System.out.println(seqMarwoodSWR);

    // reretire(seqMarwoodSWR, years, lookbackYears);
  }
}
