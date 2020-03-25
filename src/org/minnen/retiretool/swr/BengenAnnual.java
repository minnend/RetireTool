package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.simba.SimbaFund;
import org.minnen.retiretool.data.simba.SimbaIO;

public class BengenAnnual
{
  private static final Map<String, SimbaFund> data;
  public static final Sequence                stock;
  public static final Sequence                bonds;

  static {
    Map<String, SimbaFund> temp = null;
    try {
      temp = SimbaIO.loadSimbaData(new File(DataIO.getFinancePath(), "simba-2019b-real.csv"));
    } catch (IOException e) {
      e.printStackTrace();
    }
    data = temp;
    stock = data.get("TSM").annualReturns;
    String bondSymbol = "ITT"; // TBM
    bonds = data.get(bondSymbol).annualReturns;
    assert stock.matches(bonds);
  }

  public static void main(String[] args) throws IOException
  {
    // TODO implement me!
  }
}
