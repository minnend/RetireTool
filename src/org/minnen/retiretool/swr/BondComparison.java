package org.minnen.retiretool.swr;

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
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

/**
 * Generate graph showing bond growth curves for different methods of computing growth from interest rates.
 */
public class BondComparison
{
  public static void main(String[] args) throws IOException
  {
    Shiller.downloadData();

    Sequence shillerData = Shiller.loadAll(Shiller.getPathCSV(), false);
    Sequence bondData = shillerData.extractDimAsSeq(Shiller.GS10).setName("GS10");

    Sequence bondsRebuy = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    Sequence bondsHold = Bond.calcReturnsHold(BondFactory.note10Year, bondData, 0, -1);
    Sequence bondsNaiveDiv = Bond.calcReturnsNaiveInterest(BondFactory.note10Year, bondData, 0, -1,
        DivOrPow.DivideBy12);
    Sequence bondsNaivePow = Bond.calcReturnsNaiveInterest(BondFactory.note10Year, bondData, 0, -1,
        DivOrPow.TwelfthRoot);

    Map<String, SimbaFund> simba = SimbaIO.loadSimbaData(Inflation.Nominal);
    SimbaFund simbaReturns = simba.get("TBM");
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

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "bond-comparison.html"), "Bond Growth Calculation Comparison",
        "100%", "800px", ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, bondsRebuy, bondsHold, bondsNaiveDiv,
        bondsNaivePow, simbaBonds);
  }
}
