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
  private static final Sequence               stock;
  private static final Sequence               bonds;

  static {
    Map<String, SimbaFund> temp = null;
    try {
      temp = SimbaIO.loadSimbaData(new File(DataIO.getFinancePath(), "simba-2019b-real.csv"));
    } catch (IOException e) {
      e.printStackTrace();
    }
    data = temp;
    stock = data.get("TSM").annualReturns;
    bonds = data.get("TBM").annualReturns;
    assert stock.matches(bonds);
  }

  public static void main(String[] args) throws IOException
  {}
}
