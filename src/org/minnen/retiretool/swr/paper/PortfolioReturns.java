package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.TimeLib;

public class PortfolioReturns
{
  private static void saveReturns(String filename, Inflation inflation) throws IOException
  {
    SwrLib.setup(null, null, inflation);

    File file = new File(DataIO.getFinancePath(), filename);
    try (Writer writer = new Writer(file)) {
      writer.writef("# Monthly Total Returns (%s)\n", inflation == Inflation.Nominal ? "nominal" : "real");
      writer.writeln("# Each column gives the montly return (1.04 => 4%) for a different stock/bond allocation.");
      writer.write("# Fields: date, stock allocations (percent): [");
      for (int i = 0; i < SwrLib.percentStockList.length; ++i) {
        writer.writef("%d%s", SwrLib.percentStockList[i], i == SwrLib.percentStockList.length - 1 ? "" : ",");
      }
      writer.writeln("], inflation.");
      for (int i = 0; i < SwrLib.length(); ++i) {
        writer.writef("%s", TimeLib.formatYM(SwrLib.time(i)));
        for (int percentStock : SwrLib.percentStockList) {
          writer.writef(",%f", SwrLib.growth(i, percentStock));
        }
        writer.writef(",%f\n", SwrLib.inflation(i));
      }
    }
  }

  public static void main(String[] args) throws IOException
  {
    saveReturns("portfolio-returns-nominal.csv", Inflation.Nominal);
    saveReturns("portfolio-returns-real.csv", Inflation.Real);
  }
}
