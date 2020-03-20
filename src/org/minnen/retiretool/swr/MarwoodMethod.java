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
  // TODO Calculate DMSWR for all retirement lengths for each month.
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
      int bestSWR = 0;
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
        if (swr > bestSWR) {
          bestSWR = swr;
          bestIndex = iVirtualStart;
        }
      }
      assert bestSWR > 0 && bestIndex >= 0; // must find something

      List<MonthlyInfo> trajectory = new ArrayList<>();
      MonthlyInfo info = BengenMethod.runPeriod(iRetire, bestSWR / 100.0, retirementYears, percentStock, Inflation.Real,
          trajectory);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == retirementYears * 12;

      final double bengenSalary = balance * bengenSWR / 10000.0;
      final double marwoodSalary = balance * bestSWR / 10000.0;

      double crystalSalary = Double.NaN; // may not exist if the retirement period extends into the future
      if (iRetire <= SwrLib.lastIndex(retirementYears)) {
        final double cbswr = BengenTable.get(retirementYears, percentStock, retireTime).swr / 10000.0;
        crystalSalary = cbswr * balance;
      }

      final MonthlyInfo firstMonth = trajectory.get(0);
      assert Library.almostEqual(firstMonth.startBalance, nestEgg, 1e-6);
      assert firstMonth.retireTime == retireTime;
      assert firstMonth.currentTime == retireTime;
      assert firstMonth.retirementMonth == 1;

      final int virtualRetirementMonths = iRetire - bestIndex;
      info = new MonthlyInfo(retireTime, retireTime, 1, firstMonth.monthlyIncome, nestEgg, firstMonth.endBalance,
          bestSWR, virtualRetirementMonths, bengenSalary, marwoodSalary, crystalSalary);
      results.add(info);

      // Simulate nest egg value so that we can report retirement salary in dollars.
      balance *= SwrLib.growth(iRetire, percentStock); // growth due to market
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

    final double cbswr = BengenTable.get(retirementYears, percentStock, retireTime).swr / 10000.0;
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
      final double reSalary = Math.min(balance * entry.dmswr / 10000.0, balance * 0.2); // cap salary at 20% of balance
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
      final int bestSWR = dmswr.dmswr;
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
        seq.addData(info.dmswr / 100.0, info.currentTime);
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

  public static void createCarolTable(int lookbackYears, int percentStock)
  {
    final int[] years = new int[] { 1950, 1951, 1952, 1953, 1954, 1955, 1956, 1957, 1958, 1959, 1960, 1965, 1970, 1975,
        1980, 1985, 1990, 1995, 2000, 2005 };
    Set<Integer> yearsToPrint = new HashSet<>();
    for (int year : years) {
      yearsToPrint.add(year);
    }

    final double nestEgg = 1e6;
    final int retirementYears = 35;
    final int finalYear = years[0] + retirementYears - 1;
    final long timeStart = TimeLib.toMs(years[0], Month.JANUARY, 1);
    final long timeEnd = TimeLib.toMs(years[0] + retirementYears - 1, Month.DECEMBER, 1);
    final int iStart = SwrLib.indexForTime(timeStart);
    final int iEnd = SwrLib.indexForTime(timeEnd);

    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 100.0;
    List<MonthlyInfo> bengenTrajectory = new ArrayList<>();
    BengenMethod.run(iStart, iEnd + 1, bengenSWR, percentStock, Inflation.Nominal, bengenTrajectory);
    assert bengenTrajectory.size() == (iEnd - iStart + 1);

    List<MonthlyInfo> dmTrajectory = MarwoodMethod.reretire(timeStart, retirementYears, lookbackYears, percentStock,
        Inflation.Nominal, nestEgg);
    assert dmTrajectory.size() == (iEnd - iStart + 1);

    for (MonthlyInfo bengenInfo : bengenTrajectory) {
      final int index = bengenInfo.index;
      final LocalDate date = TimeLib.ms2date(bengenInfo.currentTime);

      final boolean isNewYear = (date.getMonth() == Month.JANUARY && yearsToPrint.contains(date.getYear()));
      final boolean isLastMonth = (date.getMonth() == Month.DECEMBER && date.getYear() == finalYear);
      if (!isNewYear && !isLastMonth) continue;
      final int year = (isLastMonth ? finalYear + 1 : date.getYear());

      MonthlyInfo dmInfo = dmTrajectory.get(index - iStart);
      assert (dmInfo.retireTime == bengenInfo.retireTime) && (dmInfo.currentTime == bengenInfo.currentTime);
      assert dmInfo.index == index;
      assert Library.almostEqual(bengenInfo.bengenSalary, dmInfo.bengenSalary, 1e-6);

      double growth = SwrLib.growth(index, index + 12, 100); // market returns for the year
      growth *= SwrLib.inflation(index + 12, index); // adjust market returns for inflation
      final int yearsLeft = Math.max(0, retirementYears - (year - years[0]));

      final double inflation = SwrLib.inflation(dmInfo.index, iStart); // inflation multiplier back to retirement date
      final double dmSalaryReal = dmInfo.monthlyIncome * 12 * inflation;
      final double bengenSalaryReal = bengenInfo.monthlyIncome * 12 * inflation;

      final double bengenBalance = (isLastMonth ? bengenInfo.endBalance : bengenInfo.startBalance) * inflation;
      final double dmBalance = (isLastMonth ? dmInfo.endBalance : dmInfo.startBalance) * inflation;

      final long lookbackDate = SwrLib.time(dmInfo.index - dmInfo.virtualRetirementMonths);
      final double dmswr = dmInfo.dmswr / 100.0;

      final String sBengenBalance = String.format("\\$%s", FinLib.dollarFormatter.format(bengenBalance));
      final String sDmswrBalance = String.format("\\$%s", FinLib.dollarFormatter.format(dmBalance));
      final String sBengenSalaryReal = String.format("\\$%s", FinLib.dollarFormatter.format(bengenSalaryReal));
      final String sDmswrSalaryReal = String.format("\\$%s", FinLib.dollarFormatter.format(dmSalaryReal));

      System.out.printf("%d  & %2d  & %5.1f\\%%  & %12s  & %12s  & %12s  & %12s  & %5.2f\\%%  &  %9s  \\\\\n", year,
          yearsLeft, FinLib.mul2ret(growth), sBengenSalaryReal, sDmswrSalaryReal, sBengenBalance, sDmswrBalance, dmswr,
          TimeLib.formatMonth(lookbackDate));
    }
  }

  public static void main(String[] args) throws IOException
  {
    final int retirementYears = 30;
    final int lookbackYears = 20;
    final int percentStock = 75;

    final boolean buildCarolTable = true;

    if (buildCarolTable) {
      SwrLib.setupWithDefaultFiles(Inflation.Nominal);
      createCarolTable(lookbackYears, percentStock);
    } else {
      SwrLib.setupWithDefaultFiles();
      System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
      System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

      createCharts(retirementYears, lookbackYears, percentStock);

      MarwoodTable.genReRetireTable(retirementYears, lookbackYears, percentStock);
      createReRetireCharts(retirementYears, lookbackYears, percentStock);
    }
  }
}
