package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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

    final MarwoodEntry entry = MarwoodTable.get(retireTime, retirementYears, lookbackYears, percentStock);

    String name = String.format("Re-Retire (%s, %d, %d, %d%% stock)", TimeLib.formatYM(retireTime), retirementYears,
        lookbackYears, percentStock);
    Sequence trajectory = new Sequence(name);
    for (int i = iRetire; i < iRetire + nRetirementMonths && i < SwrLib.length(); ++i) {
      final long now = SwrLib.time(i);
      assert (i == iRetire && now == retireTime) || (i > iRetire && now > retireTime);

      // Look up SWR for a new retiree with a reduced retirement period.
      final int nMonthsRetired = i - iRetire;
      final int yearsLeft = (int) Math.ceil((nRetirementMonths - nMonthsRetired) / 12.0);
      assert yearsLeft > 0 && yearsLeft <= retirementYears;
      final double reRetireSWR = MarwoodTable.get(now, yearsLeft, lookbackYears, percentStock).swr / 100.0;

      // Jump to higher salary if re-retiring helps.
      double reSalary = balance * reRetireSWR / 100.0;
      if (reSalary > salary) salary = reSalary;

      // Calculate effective SWR at retire date by backing out market growth.
      final double effectiveSWR = Math.min(salary / nestEgg * 100.0, 20.0); // max 20%

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
    Sequence seqCrystalSWR = BengenMethod.findSwrSequence(retirementYears, percentStock);
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
            + "fontSize: 20, }, backgroundColor: '#fff', borderWidth: 1, padding: 16, shadow: true, symbolWidth: 32,\n");
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
            + "fontSize: 20, }, backgroundColor: '#fff', borderWidth: 1, padding: 16, shadow: true, symbolWidth: 32,\n");
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

      Sequence trajectory = MarwoodTable.getTrajectory(retireTime, retirementYears, lookbackYears, percentStock);
      if (trajectory == null) {
        System.out.printf("%s, %d, %d, %d\n", TimeLib.formatYM(retireTime), retirementYears, lookbackYears,
            percentStock);
      }
      assert trajectory != null;
      // swrTrajectory.setName(TimeLib.formatYM(swrTrajectory.getStartMS()));
      trajectory.setName(TimeLib.ms2date(trajectory.getStartMS()).format(TimeLib.dtfY));
      trajectories.add(trajectory);
    }

    String filename = String.format("reretire-%d-%d-%d.html", retirementYears, lookbackYears, percentStock);
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), filename),
        "DMSWR Trajectories with Re-Retiring", "100%", "800px", ChartScaling.LINEAR, ChartTiming.INDEX, trajectories);
    final double bengenSWR = BengenTable.getSWR(retirementYears, percentStock) / 100.0;
    config.addPlotLineY(new PlotLine(bengenSWR, 2.0, "#777", "dash"));
    String[] labels = new String[trajectories.get(0).size()];
    DecimalFormat df = new DecimalFormat();
    for (int i = 0; i < labels.length; ++i) {
      // labels[i] = df.format(i / 12.0);
      final long ms = trajectories.get(0).getTimeMS(i) - trajectories.get(0).getStartMS();
      if (ms == 0) {
        labels[i] = "";
      } else {
        labels[i] = TimeLib.formatDuration(ms).replace(".0", "");
      }
    }
    config.setLabels(labels);
    config.setAxisLabelFontSize(20);
    config.setTickInterval(12, -1);
    config.setMinMaxY(3.0, 20.0);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setMinorTickIntervalY(1.0);
    Chart.saveChart(config);
  }

  public static void createCarolTable(int percentStock)
  {
    final long timeStart = TimeLib.toMs(1970, Month.JANUARY, 1);
    final long timeEnd = TimeLib.toMs(1970 + 35, Month.JANUARY, 1);
    final int iStart = SwrLib.indexForTime(timeStart);
    final int iEnd = SwrLib.indexForTime(timeEnd);

    final int[] years = new int[] { 1970, 1971, 1972, 1973, 1974, 1975, 1980, 1985, 1990, 1995, 2000, 2005 };
    Set<Integer> yearsToPrint = new HashSet<>();
    for (int year : years) {
      yearsToPrint.add(year);
    }

    // Note: pre-computed salaries are adjusted for inflation so must be avoided here.
    double balance = 1e6;
    double dmswr = MarwoodTable.getSWR(35, 20, percentStock) / 10000.0;
    double monthlyIncome = balance * dmswr / 12.0;

    for (int index = iStart; index <= iEnd; ++index) {
      final long now = SwrLib.time(index);
      LocalDate date = TimeLib.ms2date(now);
      if (date.getMonth() == Month.JANUARY && yearsToPrint.contains(date.getYear())) {
        final double growth = FinLib.mul2ret(SwrLib.growth(index, index + 12, 100));
        final double actualWR = monthlyIncome / balance * 1200.0;
        final int yearsLeft = Math.max(1, 35 - (date.getYear() - 1970));
        final MarwoodEntry entry = MarwoodTable.get(now, yearsLeft, 20, percentStock);
        dmswr = Math.min(entry.swr / 100.0, 20.0); // cap dmswr at 20%
        final long lookbackDate = SwrLib.time(index - entry.virtualRetirementMonths);
        final String sBalance = String.format("\\$%s", FinLib.dollarFormatter.format(balance));
        final String sSalaryNominal = String.format("\\$%s", FinLib.dollarFormatter.format(monthlyIncome * 12));
        final String sSalaryReal = String.format("\\$%s",
            FinLib.dollarFormatter.format(monthlyIncome * 12 * SwrLib.inflation(index, iStart)));
        System.out.printf("%d  & %2d  & %12s  & %10s  & %5.1f\\%%  & %5.2f\\%%  & %5.2f\\%%  &  %9s  \\\\\n",
            date.getYear(), yearsLeft, sBalance, sSalaryNominal, growth, actualWR, dmswr,
            TimeLib.formatMonth(lookbackDate));
      }

      balance -= monthlyIncome;
      balance *= SwrLib.growth(index, percentStock);
      monthlyIncome *= SwrLib.inflation(index, index + 1);
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
      createCarolTable(percentStock);
    } else {
      SwrLib.setupWithDefaultFiles();
      System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
      System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

      // createCharts(retirementYears, lookbackYears, percentStock);
      //
      // MarwoodTable.genReRetireTable(retirementYears, lookbackYears, percentStock);
      // createReRetireCharts(retirementYears, lookbackYears, percentStock);
    }
  }
}
