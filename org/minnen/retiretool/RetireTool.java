package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.minnen.retiretool.Shiller.Inflation;

public class RetireTool
{

  public static void main(String[] args) throws IOException
  {
    if (args.length != 2) {
      System.err.println("Usage: java ~.ShillerSnp <shiller-data-file> <t-bill-file>");
      System.exit(1);
    }
    Bond.testPricing();

    Shiller shiller = new Shiller();    
    shiller.loadData(args[0]);

    Sequence tbills = TBills.loadData(args[1]);
    
    System.out.printf("T-Bills: [%s] -> [%s]\n", Library.formatDate(tbills.getStartMS()), Library.formatDate(tbills.getEndMS()));

    // System.out.printf("Bond Price: $%.2f\n", Shiller.calcBondPrice(120, 8, 1000, 2.0, 2, 72.0/182.0));
    // System.exit(1);

    // shiller.printReturnLikelihoods();
    // shiller.printWithdrawalLikelihoods(30, 0.1);
    // shiller.genReturnChart(Inflation.Ignore, new File("g:/test.html"));
    // int[] years = new int[] { 1, 2, 5, 10, 15, 20, 30, 40, 50 };
    // for (int i = 0; i < years.length; ++i) {
    // shiller.genReturnComparison(years[i] * 12, Inflation.Ignore, new File("g:/test.html"));
    // }

    // shiller.genReturnComparison(12*30, Inflation.Ignore, new File("g:/test.html"));
    shiller.genReturnChart(Inflation.Ignore, new File("g:/cumulative-returns.html"));
    shiller.genSMASweepChart(Inflation.Ignore, new File("g:/sma-sweep.html"));
    shiller.genMomentumSweepChart(Inflation.Ignore, new File("g:/momentum-sweep.html"));
    System.exit(0);

    int retireAge = 65;
    int ssAge = 70;
    double expectedSocSecFraction = 0.7; // assume we'll only get a fraction of current SS estimate
    double expectedMonthlySS = Shiller.SOC_SEC_AT70 * expectedSocSecFraction;
    int nYears = 105 - retireAge;
    double expenseRatio = 0.1;
    double likelihood = 0.99;
    double taxRate = 0.30;
    double desiredMonthlyCash = 6000.00;
    double desiredRunwayYears = 1.0;
    double salary = Shiller.calcAnnualSalary(desiredMonthlyCash, taxRate);
    System.out.printf("Salary: %s\n", Shiller.currencyFormatter.format(salary));
    List<Integer> failures = shiller.calcSavingsTarget(salary, likelihood, nYears, expenseRatio, retireAge, ssAge,
        expectedMonthlySS, desiredRunwayYears);
    // if (!failures.isEmpty()) {
    // System.out.println("Failures:");
    // for (int i : failures) {
    // System.out.printf(" [%s] -> [%s]\n", Library.formatDate(shiller.data.getTimeMS(i)),
    // Library.formatDate(shiller.data.getTimeMS(i + nYears * 12)));
    // }
    // }

    // calcSavings(230000.0, 5500.0, 53000.0/12, 7.0, 40);

    System.exit(0);
  }
}
