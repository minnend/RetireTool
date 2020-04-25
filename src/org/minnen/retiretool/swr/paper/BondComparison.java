package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;

import org.minnen.retiretool.Bond;
import org.minnen.retiretool.Bond.DivOrPow;
import org.minnen.retiretool.BondFactory;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Shiller;
import org.minnen.retiretool.data.simba.SimbaFund;
import org.minnen.retiretool.data.simba.SimbaIO;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

/**
 * Generate graph showing bond growth curves for different methods of computing growth from interest rates.
 */
public class BondComparison
{
  public static double calcBondPrice(double par, double coupon, double annualInterestRate, int years,
      int couponsPerYear)
  {
    annualInterestRate /= couponsPerYear;
    final int nPayments = couponsPerYear * years;
    double price = 0;
    for (int i = 1; i <= nPayments; ++i) {
      price += FinLib.getPresentValue(coupon, annualInterestRate, i);
    }
    price += FinLib.getPresentValue(par, annualInterestRate, nPayments);
    return price;
  }

  public static void explore()
  {
    final double par = 1000.0;
    final int years = 10;
    final int couponsPerYear = 2;
    final double ytm = 10.0;

    // double price = par / Math.pow(FinLib.ret2mul(ytm / couponsPerYear), years * couponsPerYear);
    // double foo = Bond.calcPrice(0, ytm, par, years, couponsPerYear, 0);
    // double bar = calcBondPrice(par, 0, ytm, years, couponsPerYear);
    // System.out.printf("price: $%.2f $%.2f $%.2f\n", price, foo, bar);

    double foo = Bond.calcPrice(100.0, 10.0, 1000.0, 10.0, 2, 0.0);
    double bar = Bond.calcPrice(100.0, 10.0, 1000.0, 10.0, 2, 1.0 / 6.0);
    System.out.printf("price: $%.2f $%.2f -> %f\n", foo, bar, FinLib.mul2ret(bar / foo));

    foo = Bond.calcPrice(83.95, 10.0, 1000.0, 10.0, 2, 0.0);
    bar = Bond.calcPrice(83.95, 10.0, 1000.0, 10.0, 2, 1.0 / 6.0);
    System.out.printf("price: $%.2f $%.2f -> %f\n", foo, bar, FinLib.mul2ret(bar / foo));

    foo = Bond.calcPrice(7.90, 10.0, 1000.0, 10.0, 2, 0.0);
    bar = Bond.calcPrice(7.90, 10.0, 1000.0, 10.0, 2, 1.0 / 6.0);
    System.out.printf("price: $%.2f $%.2f -> %f\n", foo, bar, FinLib.mul2ret(bar / foo));

    double pv = FinLib.getPresentValue(1000, 10, 10);
    foo = Bond.calcPriceZeroCoupon(10, 1000, 10);
    bar = Bond.calcPriceZeroCoupon(10, 1000, 10 - 1.0 / 12.0);
    double x = Bond.calcPrice(0, 10, 1000, 10, 0, 1.0 / 12.0);
    double accruedInterest = (1000 - pv) / 120.0;
    System.out.printf("zero: (%.2f) $%.2f $%.2f -> %f (%f, %f)\n", pv, foo, bar, FinLib.mul2ret(bar / foo),
        pv + accruedInterest, FinLib.mul2ret((pv + accruedInterest) / foo));

    System.exit(0);
  }

  public static void main(String[] args) throws IOException
  {
    Shiller.downloadData();

    Sequence shillerData = Shiller.loadAll(Shiller.getPathCSV(), false);
    Sequence bondData = shillerData.extractDimAsSeq(Shiller.GS10).setName("GS10");

    // explore();

    Sequence bondsYTM = Bond.calcBondReturnsYTM(bondData);
    Sequence bondsRebuy = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, 0, -1);
    Sequence bondsNaiveDiv = Bond.calcReturnsNaiveInterest(BondFactory.note10Year, bondData, 0, -1,
        DivOrPow.DivideBy12);
    Sequence bondsNaivePow = Bond.calcReturnsNaiveInterest(BondFactory.note10Year, bondData, 0, -1,
        DivOrPow.TwelfthRoot);

    Map<String, SimbaFund> simba = SimbaIO.loadSimbaData(Inflation.Nominal);
    SimbaFund simbaReturns = simba.get("ITT");
    Sequence simbaBonds = new Sequence(String.format("%s (%s)", simbaReturns.name, simbaReturns.symbol));
    double x = 1.0;
    LocalDate date = TimeLib.ms2date(TimeLib.toMs(simbaReturns.startYear, Month.JANUARY, 1));
    simbaBonds.addData(x, date);
    for (FeatureVec v : simbaReturns.annualReturns) {
      x *= v.get(0);
      date = date.plusYears(1);
      simbaBonds.addData(x, date);
    }
    System.out.println(simbaBonds);

    bondsYTM.setName("10-Year Constant Maturity (YTM)");
    bondsNaiveDiv.setName("10-Year Constant Maturity (Naive Interest)");
    
    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "bond-comparison.html"),
        "Bond Fund Growth Comparison", "100%", "800px", ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, bondsNaiveDiv,
        bondsYTM);
    // bondsRebuy, bondsHold, bondsNaiveDiv, bondsNaivePow, simbaBonds);
    config.setAxisLabelFontSize(28);
    config.setLineWidth(5);
    config.setAnimation(false);
    config.setTickInterval(72, -1);
    config.setTickFormatter("return this.value.split(' ')[1];", null);
    config.setMinMaxY(1, 1024);
    config.setTitleConfig("margin: -20, y: 20, style: { fontSize: 36 }");
    config.setLegendConfig(
        "align: 'left', verticalAlign: 'top', x: 100, y: 60, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 24, }, backgroundColor: '#fff', borderWidth: 1, padding: 10, shadow: true, symbolWidth: 32,");

    Chart.saveChart(config);
  }
}
