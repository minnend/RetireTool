package org.minnen.retiretool.swr;

import java.io.IOException;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;

public class MarwoodMethod
{
  // TODO Enforce 20% cap on DMSWR and Bengen SWR? If not enforced, salaries and final balance could be wrong.
  // TODO allow for changing asset allocation over time? need to model taxes in this case?
  // TODO final balance with re-retire or without? Currently, retireTime==currentTime is without, others are with.

  /**
   * Run a DMSWR simulation and return information for each retirement period.
   * 
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @pparam percentStock percent stock (vs. bonds) to hold (70 = 70%)
   * @return List of monthly info objects for each month that starts a retirement period
   */
  public static List<MonthlyInfo> findDMSWR(int retirementYears, int lookbackYears, int percentStock) throws IOException
  {
    final int lookbackMonths = lookbackYears * 12;
    final int iStartSim = lookbackMonths; // first data point with fully lookback history
    final int iEndSim = SwrLib.length() - 1;
    return findDMSWR(iStartSim, iEndSim, retirementYears, lookbackYears, percentStock);
  }

  /**
   * Run a DMSWR simulation and return information for each retirement period.
   * 
   * @param iStartSim first index of simulation
   * @param iEndSim last index of simulation (inclusive)
   * @param retirementYears duration of retirement in years
   * @param lookbackYears number of previous years to check for a better "virtual retirement" time
   * @param percentStock percent stock (vs. bonds) to hold (70 = 70%)
   * @return List of monthly info objects for each month that starts a retirement period
   */
  public static List<MonthlyInfo> findDMSWR(int iStartSim, int iEndSim, int retirementYears, int lookbackYears,
      int percentStock) throws IOException
  {
    final int bengenSWR = BengenTable.getSWR(retirementYears, percentStock);
    final int lookbackMonths = lookbackYears * 12;
    final int iLastWithFullRetirement = SwrLib.lastIndex(retirementYears);

    List<MonthlyInfo> results = new ArrayList<>();
    for (int iRetire = iStartSim; iRetire <= iEndSim; ++iRetire) {
      final long retireTime = SwrLib.time(iRetire);
      // TODO arg to select nest egg calculation + real or nominal dollars.
      final double nestEgg = SwrLib.getNestEgg(iRetire, iStartSim, lookbackYears, percentStock, Inflation.Nominal);
      // final double nestEgg = SwrLib.getNestEgg(iRetire, lookbackYears, percentStock);

      // Find best "virtual" retirement year within the lookback period.
      int dmswr = 0;
      int bestVirtualIndex = -1;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iRetire - iLookback; // index of start of virtual retirement
        final int virtualYears = retirementYears + (int) Math.ceil(iLookback / 12.0);
        final double virtualSWR = BengenTable.getSWR(virtualYears, percentStock) / 100.0;

        // Run simulation for virtual retirement period.
        List<MonthlyInfo> virtualTrajectory = new ArrayList<MonthlyInfo>();
        MonthlyInfo info = BengenMethod.run(iVirtualStart, iRetire + 1, virtualSWR, percentStock, Inflation.Real,
            virtualTrajectory);
        assert info.ok();

        assert iLookback + 1 == virtualTrajectory.size();
        MonthlyInfo virtualNow = virtualTrajectory.get(iLookback);
        assert virtualNow.index == iRetire;

        final int swr = SwrLib.percentToBasisPoints(virtualNow.percent());
        assert iLookback > 0 || swr == bengenSWR; // iLookback == 0 must match Bengen
        if (swr > dmswr) {
          dmswr = swr;
          bestVirtualIndex = iVirtualStart;
        }
      }
      assert dmswr > 0 && bestVirtualIndex >= 0; // must find something
      assert dmswr >= bengenSWR; // Bengen is lower bound on DMSWR

      List<MonthlyInfo> trajectory = new ArrayList<>();
      MonthlyInfo info = BengenMethod.runForDuration(iRetire, retirementYears, dmswr / 100.0, percentStock,
          Inflation.Real, nestEgg, trajectory);
      assert info.ok(); // safe by construction, but still verify
      assert iRetire > iLastWithFullRetirement || info.retirementMonth == retirementYears * 12;
      final double finalBalance = info.finalBalance;

      final double bengenSalary = nestEgg * bengenSWR / 10000.0;
      final double marwoodSalary = nestEgg * dmswr / 10000.0;

      double crystalSalary = Double.NaN; // may not exist if the retirement period extends into the future
      if (iRetire <= iLastWithFullRetirement) {
        final int cbswr = BengenTable.get(retireTime, retirementYears, percentStock).swr;
        crystalSalary = nestEgg * cbswr / 10000.0;
      }

      final MonthlyInfo firstMonth = trajectory.get(0);
      assert Library.almostEqual(firstMonth.startBalance, nestEgg, 1e-6);
      assert firstMonth.retireTime == retireTime;
      assert firstMonth.currentTime == retireTime;
      assert firstMonth.retirementMonth == 1;

      final int virtualRetirementMonths = iRetire - bestVirtualIndex;
      final double growth = SwrLib.growth(iRetire, percentStock); // growth due to market
      final double monthlyIncome = marwoodSalary / 12.0;
      final double endBalance = (nestEgg - monthlyIncome) * growth;
      info = new MonthlyInfo(retireTime, retireTime, 1, monthlyIncome, nestEgg, endBalance, dmswr,
          virtualRetirementMonths, bengenSalary, marwoodSalary, crystalSalary);
      info.finalBalance = finalBalance;
      results.add(info);
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
      BengenEntry bengen = BengenTable.get(retireTime, retirementYears, percentStock);
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

  public static void main(String[] args) throws IOException
  {
    SwrLib.setupWithDefaultFiles();
    System.out.printf("DMSWR entries: %d\n", MarwoodTable.marwoodMap.size());
    System.out.printf("DMSWR sequences: %d\n", MarwoodTable.marwoodSequences.size());

    // Section 4 "Analysis" example.
    long retireTime = TimeLib.toMs(1986, Month.FEBRUARY, 1);
    MarwoodEntry entry = MarwoodTable.get(retireTime, 30, 20, 75);
    int iVirtualRetirement = SwrLib.indexForTime(retireTime) - entry.virtualRetirementMonths;
    System.out.printf("%s  %d => %s", entry, entry.virtualRetirementMonths,
        TimeLib.formatMonth(SwrLib.time(iVirtualRetirement)));
  }
}
