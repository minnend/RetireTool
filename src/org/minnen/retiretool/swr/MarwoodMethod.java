package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotLine;

public class MarwoodMethod
{
  // TODO Enforce 20% cap on DMSWR and Bengen SWR? If not enforced, salaries and final balance could be wrong.
  // TODO Walk-forward optimization for Bengen SWR -- how well does it generalize?
  // TODO allow for changing asset allocation over time? need to model taxes in this case?
  // TODO final balance with re-retire or without? Currently, retireTime==currentTime is without, others are with.

  /**
   * Run a DMSWR simulation and return information for each retirement period.
   * 
   * The dimensions of the returned sequence hold: (0) DMSWR, (1) virtual retirement months, (2) final balance, (3)
   * bengen salary, (4) DM salary.
   * 
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @pparam percentStock percent stock (vs. bonds) to hold (70 = 70%)
   * @return List of monthly info objects for each month that starts a retirement period
   */
  public static List<MonthlyInfo> findMarwoodSWR(int retirementYears, int lookbackYears, int percentStock)
      throws IOException
  {
    final int bengenSWR = BengenTable.getSWR(retirementYears, percentStock);
    final int lookbackMonths = lookbackYears * 12;
    final int iLastWithFullRetirement = SwrLib.lastIndex(retirementYears);
    final int iStartSim = lookbackMonths; // first data point with fully lookback history

    List<MonthlyInfo> results = new ArrayList<>();
    final double nestEgg = SwrLib.getNestEgg(iStartSim, lookbackYears, percentStock);
    double balance = nestEgg;
    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      final long retireTime = SwrLib.time(iRetire);

      // Find best "virtual" retirement year within the lookback period.
      int dmswr = 0;
      int bestIndex = -1;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iRetire - iLookback; // index of start of virtual retirement

        final int virtualYears = retirementYears + (int) Math.ceil(iLookback / 12.0);
        final int virtualSWR = BengenTable.getSWR(virtualYears, percentStock);

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        MonthlyInfo info = BengenMethod.run(iVirtualStart, iRetire + 1, virtualSWR / 100.0, percentStock,
            Inflation.Real, virtualSalaries);
        assert info.ok();

        assert iLookback + 1 == virtualSalaries.size();
        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iRetire;

        final int swr = SwrLib.percentToBasisPoints(virtualNow.percent());
        assert iLookback > 0 || swr == bengenSWR; // iLookback == 0 must match Bengen
        if (swr > dmswr) {
          dmswr = swr;
          bestIndex = iVirtualStart;
        }
      }
      assert dmswr > 0 && bestIndex >= 0; // must find something
      assert dmswr >= bengenSWR; // Bengen is lower bound on DMSWR

      List<MonthlyInfo> trajectory = new ArrayList<>();
      MonthlyInfo info = BengenMethod.runPeriod(iRetire, dmswr / 100.0, retirementYears, percentStock, Inflation.Real,
          nestEgg, trajectory);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == retirementYears * 12;
      final double finalBalance = info.finalBalance;

      final double bengenSalary = balance * bengenSWR / 10000.0;
      final double marwoodSalary = balance * dmswr / 10000.0;

      double crystalSalary = Double.NaN; // may not exist if the retirement period extends into the future
      if (iRetire <= iLastWithFullRetirement) {
        final int cbswr = BengenTable.get(retirementYears, percentStock, retireTime).swr;
        crystalSalary = balance * cbswr / 10000.0;
      }

      final MonthlyInfo firstMonth = trajectory.get(0);
      assert Library.almostEqual(firstMonth.startBalance, nestEgg, 1e-6);
      assert firstMonth.retireTime == retireTime;
      assert firstMonth.currentTime == retireTime;
      assert firstMonth.retirementMonth == 1;

      final int virtualRetirementMonths = iRetire - bestIndex;
      final double growth = SwrLib.growth(iRetire, percentStock); // growth due to market
      final double monthlyIncome = marwoodSalary / 12.0;
      final double endBalance = (nestEgg - monthlyIncome) * growth;
      info = new MonthlyInfo(retireTime, retireTime, 1, monthlyIncome, nestEgg, endBalance, dmswr,
          virtualRetirementMonths, bengenSalary, marwoodSalary, crystalSalary);
      info.finalBalance = finalBalance;
      results.add(info);

      // Simulate nest egg value so that we can report retirement salary in dollars.
      balance *= growth;
    }

    return results;
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static List<MonthlyInfo> reretire(long retireTime, int retirementYears, int lookbackYears, int percentStock,
      Inflation inflation, double nestEgg)
  {
    final int iRetire = SwrLib.indexForTime(retireTime);
    final int nRetirementMonths = retirementYears * 12;

    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 10000.0;
    double bengenSalary = bengenSWR * nestEgg;

    double cbswr = Double.NaN;
    if (iRetire <= SwrLib.lastIndex(retirementYears)) {
      BengenEntry bengen = BengenTable.get(retirementYears, percentStock, retireTime);
      cbswr = bengen.swr / 10000.0;
    }
    double crystalSalary = cbswr * nestEgg;

    double balance = nestEgg;
    double salary = 0;
    int virtualRetirementMonths = -1;

    List<MonthlyInfo> trajectory = new ArrayList<>();
    for (int i = iRetire; i < iRetire + nRetirementMonths && i < SwrLib.length(); ++i) {
      final long now = SwrLib.time(i);
      assert (i == iRetire && now == retireTime) || (i > iRetire && now > retireTime);

      // Look up SWR for a new retiree with a reduced retirement period.
      final int nMonthsRetired = i - iRetire;
      final int yearsLeft = (int) Math.ceil((nRetirementMonths - nMonthsRetired) / 12.0);
      assert yearsLeft > 0 && yearsLeft <= retirementYears;
      assert (i > iRetire || yearsLeft == retirementYears);

      // Jump to higher salary if re-retiring helps.
      final MarwoodEntry entry = MarwoodTable.get(now, yearsLeft, lookbackYears, percentStock);
      final double reSalary = Math.min(balance * entry.swr / 10000.0, balance * 0.2); // cap salary at 20% of balance
      if (reSalary > salary) {
        salary = reSalary;
        virtualRetirementMonths = entry.virtualRetirementMonths;
      } else {
        ++virtualRetirementMonths;
      }
      final double monthlyIncome = salary / 12.0;

      // Calculate effective SWR at retire date by backing out inflation.
      final double adjustedSalary = (inflation == Inflation.Real ? salary : salary * SwrLib.inflation(i, iRetire));
      final double effectiveSWR = adjustedSalary / nestEgg * 100.0;

      final double startBalance = balance;
      balance -= monthlyIncome; // withdrawal at beginning of month
      assert balance > 0; // true by construction
      balance *= SwrLib.growth(i, percentStock); // market affects remaining balance

      final int swrBasisPoints = SwrLib.percentToBasisPoints(effectiveSWR);
      MonthlyInfo info = new MonthlyInfo(retireTime, now, i - iRetire + 1, monthlyIncome, startBalance, balance,
          swrBasisPoints, virtualRetirementMonths, bengenSalary, salary, crystalSalary);
      trajectory.add(info);

      if (inflation == Inflation.Nominal) {
        final double k = SwrLib.inflation(i);
        salary *= k;
        bengenSalary *= k;
        crystalSalary *= k;
      }
    }

    // Fill in final balance with re-retire.
    final MarwoodEntry entry = MarwoodTable.get(retireTime, retirementYears, lookbackYears, percentStock);
    trajectory.get(0).finalBalance = entry.finalBalance; // store final balance *without* re-retire
    for (int i = 1; i < trajectory.size(); ++i) {
      trajectory.get(i).finalBalance = balance;
    }

    return trajectory;
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
    Sequence seqCrystalSalary = new Sequence("Crystal Ball Salary");
    Sequence seqMarwoodSalary = new Sequence(String.format("DMSWR Salary (%d, %d)", retirementYears, lookbackYears));

    for (int iRetire = iStartSim; iRetire < SwrLib.length(); ++iRetire) {
      // Find best "virtual" retirement year within the lookback period.
      MarwoodEntry dmswr = MarwoodTable.get(SwrLib.time(iRetire), retirementYears, lookbackYears, percentStock);
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
      seqCrystalSalary.addData(dmswr.crystalSalary, now);

      // final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
      // System.out.printf("%d <- %d [%s] <- [%s] swr: %d +%.3f%% | $%.2f\n", iRetire, bestIndex,
      // TimeLib.formatMonth(SwrLib.time(iRetire)), TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR, swrGain,
      // dmswr.finalBalance);

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }
    }

    System.out.printf("Mean SWR: %d  Max SWR: %d @ [%s] (index: %d)\n", sumSWR / (SwrLib.length() - iStartSim), maxSWR,
        TimeLib.formatMonth(SwrLib.time(maxIndex)), maxIndex);
    System.out.printf(" win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

    // The crystall ball SWR is the same as Bengen per retirement start date.
    Sequence seqCrystalSWR = BengenMethod.findSwrAcrossTime(retirementYears, percentStock);
    seqCrystalSWR.setName("Crystal Ball");
    int index = seqCrystalSWR.getIndexAtOrAfter(seqMarwoodSWR.getStartMS());
    seqCrystalSWR = seqCrystalSWR.subseq(index);

    final double bengenAsPercent = bengenSWR / 100.0;
    Sequence seqBengenSWR = new Sequence("Bengen SWR");
    for (FeatureVec v : seqMarwoodSWR) {
      seqBengenSWR.addData(bengenAsPercent, v.getTime());
    }

    String title = String.format("Safe Withdrawal Rates (%d%% stock / %d%% bonds)", percentStock, 100 - percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "marwood-swr.html"), title, "100%",
        "800px", ChartScaling.LINEAR, ChartTiming.MONTHLY, seqBengenSWR, seqCrystalSWR, seqMarwoodSWR);
    // config.addPlotLineY(new PlotLine(bengenSWR / 100.0, 3.0, "#272", "dash"));
    config.setColors(new String[] { "#272", "#7cb5ec", "#434348" });
    config.setLineWidth(3);
    config.setAxisLabelFontSize(20);
    config.setTickInterval(12, -1);
    config.setMinMaxY(3.0, 13.0);
    config.setTickFormatter("return this.value.split(' ')[1];", "return this.value + '%';");
    config.setLegendConfig(
        "align: 'left', verticalAlign: 'top', x: 90, y: 40, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 20, }, backgroundColor: '#fff', borderWidth: 1, padding: 16, shadow: true, symbolWidth: 32,");
    Chart.saveChart(config);

    config = Chart.saveLineChart(
        new File(DataIO.getOutputPath(), String.format("marwood-salary-%d-%d.html", retirementYears, lookbackYears)),
        "Retirement Salary ($1M nest egg in today\\'s dollars)", "100%", "800px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, seqMarwoodSalary, seqBengenSalary);
    config.setColors(new String[] { "#434348", "#272" });
    config.setLineWidth(3);
    config.setAxisLabelFontSize(20);
    config.setTickInterval(36, -1);
    config.setTickFormatter("return this.value.split(' ')[1];", "return '$' + this.value;");
    config.setLegendConfig(
        "align: 'left', verticalAlign: 'top', x: 120, y: 40, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 20, }, backgroundColor: '#fff', borderWidth: 1, padding: 16, shadow: true, symbolWidth: 32,");
    Chart.saveChart(config);
  }

  /** Simulate re-retiring to boost withdrawals after the original retirement date. */
  public static void createReRetireCharts(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    List<Sequence> trajectories = new ArrayList<>();
    final int lookbackMonths = lookbackYears * 12;
    for (int iMarwood = lookbackMonths; iMarwood < SwrLib.lastIndex(retirementYears); ++iMarwood) {
      final long retireTime = SwrLib.time(iMarwood);

      // Don't plot every starting time.
      LocalDate date = TimeLib.ms2date(retireTime);
      if (date.getYear() % 5 != 0 || date.getMonth() != Month.JANUARY) continue;

      // List<MonthlyInfo> trajectory = MarwoodTable.getTrajectory(retireTime, retirementYears, lookbackYears,
      // percentStock);
      List<MonthlyInfo> trajectory = reretire(retireTime, retirementYears, lookbackYears, percentStock, Inflation.Real,
          1e6);

      Sequence seq = new Sequence(TimeLib.ms2date(trajectory.get(0).retireTime).format(TimeLib.dtfY));
      for (MonthlyInfo info : trajectory) {
        seq.addData(info.swr / 100.0, info.currentTime);
      }
      trajectories.add(seq);
    }

    String filename = String.format("reretire-%d-%d-%d.html", retirementYears, lookbackYears, percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), filename),
        "DMSWR Trajectories with Re-Retiring", "100%", "800px", ChartScaling.LINEAR, ChartTiming.INDEX, trajectories);
    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 100.0;
    config.addPlotLineY(new PlotLine(bengenSWR, 2.0, "#777", "dash"));
    String[] labels = new String[trajectories.get(0).size()];
    for (int i = 0; i < labels.length; ++i) {
      final long ms = trajectories.get(0).getTimeMS(i) - trajectories.get(0).getStartMS();
      if (ms == 0) {
        labels[i] = "";
      } else {
        labels[i] = TimeLib.formatDuration(ms).replace(".0", "");
      }
    }
    config.setLabels(labels);
    config.setAxisLabelFontSize(20);
    config.setLineWidth(3);
    config.setTickInterval(12, -1);
    config.setMinMaxY(3.0, 15.0);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setMinorTickIntervalY(1.0);
    Chart.saveChart(config);
  }

  public static void main(String[] args) throws IOException
  {
    final int retirementYears = 30;
    final int lookbackYears = 20;
    final int percentStock = 75;

    SwrLib.setupWithDefaultFiles();
    System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
    System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

    createCharts(retirementYears, lookbackYears, percentStock);

    MarwoodTable.genReRetireTable(retirementYears, lookbackYears, percentStock);
    createReRetireCharts(retirementYears, lookbackYears, percentStock);
  }
}
