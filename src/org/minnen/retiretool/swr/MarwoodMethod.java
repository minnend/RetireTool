package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MarwoodTable;
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
  // TODO allow for changing asset allocation over time? need to model taxes in this case?
  // TODO final balance with re-retire or without? Currently, retireTime==currentTime is without, others are with.

  /**
   * Run a Marwood-Minnen SWR simulation and print results.
   * 
   * The dimensions of the returned sequence hold: (0) DMSWR, (1) virtual retirement months, (2) final balance, (3)
   * bengen salary, (4) DM salary.
   * 
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @pparam percentStock percent stock (vs. bonds) to hold (70 = 70%)
   * @return Sequence holding Marwood-Minnen SWR for each retirement date
   */
  public static Sequence findMarwoodSWR(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int bengenSWR = BengenTable.getSWR(retirementYears, percentStock);
    final int lookbackMonths = lookbackYears * 12;
    final int iLastWithFullRetirement = SwrLib.lastIndex(retirementYears);
    final int iStartSim = lookbackMonths; // first data point with fully lookback history

    Sequence seqMarwoodSWR = new Sequence(String.format("DMSWR (%d, %d)", retirementYears, lookbackYears));

    double nestEgg = SwrLib.getNestEgg(iStartSim, lookbackYears, percentStock);
    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      final long retireTime = SwrLib.time(iRetire);

      // Find best "virtual" retirement year within the lookback period.
      int bestSWR = 0;
      int bestIndex = -1;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iRetire - iLookback; // index of start of virtual retirement

        final int virtualYears = retirementYears + (int) Math.ceil(iLookback / 12.0);
        final int virtualSWR = BengenTable.getSWR(virtualYears, percentStock);

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        MonthlyInfo info = SwrLib.run(iVirtualStart, iRetire + 1, virtualSWR / 100.0, percentStock, virtualSalaries);
        assert info.ok();

        assert iLookback + 1 == virtualSalaries.size();
        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iRetire;

        final int swr = SwrLib.percentToBasisPoints(virtualNow.percent());
        assert iLookback > 0 || swr == bengenSWR; // iLookback == 0 must match Bengen
        if (swr > bestSWR) {
          bestSWR = swr;
          bestIndex = iVirtualStart;
        }
      }
      assert bestSWR > 0 && bestIndex >= 0; // must find something

      MonthlyInfo info = SwrLib.runPeriod(iRetire, bestSWR / 100.0, retirementYears, percentStock, null);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == retirementYears * 12;

      final double bengenSalary = nestEgg * bengenSWR / 10000.0;
      final double marwoodSalary = nestEgg * bestSWR / 10000.0;

      double crystalSalary = Double.NaN; // may not exist if the retirement period extends into the future
      if (iRetire <= SwrLib.lastIndex(retirementYears)) {
        final double cbswr = BengenTable.get(retirementYears, percentStock, retireTime).swr / 10000.0;
        crystalSalary = cbswr * nestEgg;
      }

      final int virtualRetirementMonths = iRetire - bestIndex;
      FeatureVec v = MarwoodEntry.buildVector(retireTime, retireTime, bestSWR, virtualRetirementMonths, info.balance,
          bengenSalary, marwoodSalary, crystalSalary);
      seqMarwoodSWR.addData(v, retireTime);

      // Simulate nest egg value so that we can report retirement salary in dollars.
      nestEgg *= SwrLib.growth(iRetire, percentStock); // growth due to market
    }

    return seqMarwoodSWR;
  }

  /**
   * Run a Marwood-Minnen SWR simulation and print results.
   * 
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @pparam percentStock percent stock (vs. bonds) to hold (70 = 70%)
   */
  public static void createCharts(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int bengenSWR = BengenTable.getSWR(retirementYears, percentStock);
    final int lookbackMonths = lookbackYears * 12;
    final int iFirstWithHistory = lookbackMonths;
    final int iStartSim = iFirstWithHistory;

    int maxSWR = 0; // what was the best SWR of all time?
    int maxIndex = -1; // index when best SWR was found
    int sumSWR = 0;
    int nWin = 0, nFail = 0; // win = better than Bengen SWR
    Sequence seqMarwoodSWR = new Sequence(String.format("DMSWR (%d, %d)", retirementYears, lookbackYears));
    Sequence seqBalance = new Sequence("Final Balance");
    Sequence seqYearsBack = new Sequence("Virtual Retirement Years");
    Sequence seqBengenSalary = new Sequence("Bengen Salary");
    Sequence seqMarwoodSalary = new Sequence(String.format("DMSWR Salary (%d, %d)", retirementYears, lookbackYears));

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      // Find best "virtual" retirement year within the lookback period.
      MarwoodEntry dmswr = MarwoodTable.get(retirementYears, lookbackYears, percentStock, SwrLib.time(iRetire));
      final int bestSWR = dmswr.swr;
      final int bestIndex = iRetire - dmswr.virtualRetirementMonths;
      assert bestIndex >= 0;

      if (bestSWR > maxSWR) {
        maxSWR = bestSWR;
        maxIndex = iRetire;
      }
      sumSWR += bestSWR;

      final long now = SwrLib.time(iRetire);
      seqMarwoodSWR.addData(bestSWR / 100.0, now);
      seqBalance.addData(dmswr.finalBalance, now);
      seqYearsBack.addData(dmswr.virtualRetirementMonths / 12.0, now);
      seqMarwoodSalary.addData(dmswr.marwoodSalary, now);
      seqBengenSalary.addData(dmswr.bengenSalary, now);

      final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
      System.out.printf("%d <- %d [%s] <- [%s] swr: %d +%.3f%% | $%.2f\n", iRetire, bestIndex,
          TimeLib.formatMonth(SwrLib.time(iRetire)), TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR, swrGain,
          dmswr.finalBalance);

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }
    }

    System.out.printf("Mean SWR: %d\n", sumSWR / (SwrLib.length() - iStartSim));
    System.out.printf("Max SWR: %d @ [%s] (index: %d)\n", maxSWR, TimeLib.formatMonth(SwrLib.time(maxIndex)), maxIndex);
    System.out.printf("win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

    Sequence seqMaxSWR = BengenMethod.findSwrSequence(retirementYears, percentStock);
    seqMaxSWR.setName("Crystal Ball");
    int index = seqMaxSWR.getIndexAtOrAfter(seqMarwoodSWR.getStartMS());
    seqMaxSWR = seqMaxSWR.subseq(index);

    String title = String.format("Safe Withdrawal Rates (%d%% equities / %d%% bonds)", percentStock,
        100 - percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-swr.html"), title, "100%",
        "800px", ChartScaling.LINEAR, ChartTiming.MONTHLY, seqMarwoodSWR, seqMaxSWR);
    config.addPlotLineY(new PlotLine(bengenSWR / 100.0, 3.0, "#272", "dash"));
    config.setColors(new String[] { "#434348", "#7cb5ec" });
    config.setLineWidth(3);
    config.setAxisLabelFontSize(20);
    config.setTickInterval(12, -1);
    config.setTickFormatter("return this.value.split(' ')[1];", "return this.value + '%';");
    Chart.saveChart(config);

    config = Chart.saveLineChart(
        new File(DataIO.getOutputPath(), String.format("marwood-salary-%d-%d.html", retirementYears, lookbackYears)),
        "Retirement Salary ($1M nest egg in today\\'s dollars)", "100%", "800px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, seqMarwoodSalary, seqBengenSalary);
    config.setColors(new String[] { "#434348", "#7cb5ec" });
    config.setLineWidth(3);
    Chart.saveChart(config);
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static void reretireWithCharts(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    Sequence allEffectiveSWRs = new Sequence(
        String.format("Re-Retire Effective SWRs (%d, %d)", retirementYears, lookbackYears));
    List<Sequence> swrTrajectories = new ArrayList<>();

    final double nestEgg = 1e6; // retire with one million dollars
    final int nRetirementMonths = retirementYears * 12;

    Sequence seqMarwoodSWR = MarwoodTable.getSeq(retirementYears, lookbackYears, percentStock);
    seqMarwoodSWR = seqMarwoodSWR.extractDimAsSeq(0)._div(100.0); // convert basis points to percentage

    for (int iMarwood = 0; iMarwood < seqMarwoodSWR.size(); ++iMarwood) {
      final long timeRetire = seqMarwoodSWR.getTimeMS(iMarwood);
      final int iRetire = SwrLib.indexForTime(seqMarwoodSWR.getTimeMS(iMarwood));
      assert timeRetire == SwrLib.time(iRetire);

      double balance = nestEgg;
      double salary = 0;

      Sequence swrTrajectory = new Sequence(String.format("%d", TimeLib.ms2date(timeRetire).getYear()));
      for (int i = iRetire; i < iRetire + nRetirementMonths && i < SwrLib.length(); ++i) {
        final long now = SwrLib.time(i);
        assert (i == iRetire && now == timeRetire) || (i > iRetire && now > timeRetire);

        final int nMonthsRetired = i - iRetire;
        final int yearsLeft = (int) Math.ceil((nRetirementMonths - nMonthsRetired) / 12.0);
        assert yearsLeft > 0 && yearsLeft <= retirementYears;

        final double swr = MarwoodTable.getSWR(yearsLeft, lookbackYears, percentStock) / 100.0;
        final double dmswr = Math.max(seqMarwoodSWR.get(iMarwood + nMonthsRetired, 0), swr);

        double reSalary = balance * dmswr / 1200.0;
        if (reSalary > salary) {
          // if (i > iRetire) System.out.printf("Raise! $%.2f -> $%.2f\n", salary, reSalary);
          salary = reSalary;
        }

        // Calculate effective SWR at retire date by backing out market growth.
        final double effectiveSWR = salary / nestEgg * 1200.0;
        swrTrajectory.addData(Math.min(effectiveSWR, 20.0), now); // cap SWR at 20%.
        allEffectiveSWRs.addData(effectiveSWR, now);

        // System.out.printf("%d.%d (%d) (%.1f, %d) [%s] -> [%s]: swr=%.2f%% (%.2f) salary=$%.2f balance=$%.2f\n",
        // iMarwood, nMonthsRetired, i, nMonthsRetired / 12.0, yearsLeft, TimeLib.formatMonth(timeRetire),
        // TimeLib.formatMonth(now), dmswr, effectiveSWR, salary, balance);

        balance -= salary; // withdrawal at beginning of month
        assert balance > 0; // true by construction
        balance *= SwrLib.growth(i, percentStock); // market affects remaining balance
      }

      LocalDate date = TimeLib.ms2date(timeRetire);
      if (date.getYear() % 5 == 0 && date.getMonth() == Month.JANUARY) {
        swrTrajectories.add(swrTrajectory);
      }
    }

    // Calculate histogram over DMSWR and save as a bar chart.
    Sequence histBasic = Histogram.computeHistogram(seqMarwoodSWR, 0.05, 4.0, 0);
    Sequence histWithReRetire = Histogram.computeHistogram(allEffectiveSWRs, 0.05, 4.0, 0);
    assert Math.abs(Library.sum(histBasic.extractDim(2)) - 1.0) < 1e-6;
    assert Math.abs(Library.sum(histWithReRetire.extractDim(2)) - 1.0) < 1e-6;
    assert allEffectiveSWRs.getMin().get(0) > seqMarwoodSWR.getMin().get(0) - 1e-6;
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

    Chart.saveChart(new File(DataIO.getOutputPath(), "dmswr-histogram.html"), ChartConfig.Type.Bar, "DMSWR Histogram",
        labels, null, "100%", "800px", 0.0, Double.NaN, 0.05, ChartScaling.LINEAR, ChartTiming.INDEX, 2, histBasic,
        histWithReRetire);

    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "reretire.html"),
        "SWR Trajectories (With Re-Retiring)", "100%", "800px", ChartScaling.LINEAR, ChartTiming.INDEX,
        swrTrajectories);
    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 100.0;
    config.addPlotLineY(new PlotLine(bengenSWR, 2.0, "#777", "dash"));
    labels = new String[swrTrajectories.get(0).size()];
    DecimalFormat df = new DecimalFormat();
    for (int i = 0; i < labels.length; ++i) {
      labels[i] = df.format(i / 12.0);
    }
    config.setLabels(labels);
    // config.setLineWidth(3);
    Chart.saveChart(config);
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static Sequence reretire(long retireTime, int retirementYears, int lookbackYears, int percentStock)
      throws IOException
  {
    final int iRetire = SwrLib.indexForTime(retireTime);
    final int nRetirementMonths = retirementYears * 12;
    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 10000.0;

    final double nestEgg = SwrLib.getNestEgg(iRetire, lookbackYears, percentStock);
    double balance = nestEgg;
    double salary = 0;

    final MarwoodEntry entry = MarwoodTable.get(retirementYears, lookbackYears, percentStock, retireTime);

    String name = String.format("Re-Retire (%d, %d, %d%% stock)", retirementYears, lookbackYears, percentStock);
    Sequence trajectory = new Sequence(name);
    for (int i = iRetire; i < iRetire + nRetirementMonths && i < SwrLib.length(); ++i) {
      final long now = SwrLib.time(i);
      assert (i == iRetire && now == retireTime) || (i > iRetire && now > retireTime);

      // Look up SWR for a new retiree with a reduced retirement period.
      final int nMonthsRetired = i - iRetire;
      final int yearsLeft = (int) Math.ceil((nRetirementMonths - nMonthsRetired) / 12.0);
      assert yearsLeft > 0 && yearsLeft <= retirementYears;
      final double reRetireSWR = MarwoodTable.get(yearsLeft, lookbackYears, percentStock, now).swr / 100.0;

      // Jump to higher salary if re-retiring helps.
      double reSalary = balance * reRetireSWR / 100.0;
      if (reSalary > salary) salary = reSalary;

      // Calculate effective SWR at retire date by backing out market growth.
      double effectiveSWR = salary / nestEgg * 100.0;

      // TODO how best to cap SWR? Need to adjust salary and do the same in findMarwoodSWR() above.
      // effectiveSWR = Math.min(effectiveSWR, 20.0); // cap SWR at 20%

      final double bengenSalary = bengenSWR * balance;
      double crystalSalary = Double.NaN; // may not exist if the retirement period extends into the future
      if (i <= SwrLib.lastIndex(retirementYears)) {
        final double cbswr = BengenTable.get(retirementYears, percentStock, retireTime).swr / 10000.0;
        crystalSalary = cbswr * balance;
      }

      double finalBalance = Double.NaN;
      if (retireTime == now) {
        assert i == iRetire;
        finalBalance = entry.finalBalance; // store final balance *without* re-retire
      } else {
        assert i > iRetire && now > retireTime;
        // Note: will fill in correct value (final balance *with* re-retire) later.
      }
      final int swrBasisPoints = SwrLib.percentToBasisPoints(effectiveSWR);
      FeatureVec v = MarwoodEntry.buildVector(retireTime, now, swrBasisPoints, entry.virtualRetirementMonths,
          finalBalance, bengenSalary, salary, crystalSalary);
      trajectory.addData(v, now);

      // System.out.printf("%d [%s] %d (%d left) [%s]: (%.2f, %.2f) ($%.2f, $%.2f) balance=$%.2f\n", iRetire,
      // TimeLib.formatYM(retireTime), i, yearsLeft, TimeLib.formatMonth(now), reRetireSWR, effectiveSWR, reSalary,
      // salary, balance);

      balance -= salary / 12.0; // withdrawal at beginning of month
      assert balance > 0; // true by construction
      balance *= SwrLib.growth(i, percentStock); // market affects remaining balance
    }

    // Fill in final balance with re-retire.
    for (int i = 1; i < trajectory.size(); ++i) {
      trajectory.set(i, MarwoodEntry.iFinalBalance, balance);
    }

    return trajectory;
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static void reretire(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int lookbackMonths = lookbackYears * 12;
    for (int i = lookbackMonths; i < SwrLib.lastIndex(retirementYears); ++i) {
      final long retireTime = SwrLib.time(i);
      Sequence trajectory = reretire(retireTime, retirementYears, lookbackYears, percentStock);
      System.out.println(trajectory);
    }
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setupWithDefaultFiles();

    System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
    System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

    final int years = 30;
    final int lookbackYears = 20;
    final int percentStock = 75;

    createCharts(years, lookbackYears, percentStock);
    // reretire(years, lookbackYears, percentStock);
  }
}
