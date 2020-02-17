package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.Bond;
import org.minnen.retiretool.Bond.DivOrPow;
import org.minnen.retiretool.BondFactory;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Shiller;
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

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "bond-comparison.html"), "Bond Growth Calculation Comparison",
        "100%", "800px", ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, bondsRebuy, bondsHold, bondsNaiveDiv,
        bondsNaivePow);
  }
}
