package org.minnen.retiretool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;

public class TBills
{
  /**
   * Load data from CSV export of Shiller's SNP/CPI excel spreadsheet
   * 
   * @param fname name of file to load
   * @return true on success
   */
  public static Sequence loadData(String fname) throws IOException
  {
    File file = new File(fname);
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read t-bills file (%s)", fname));
    }
    System.out.printf("Loading Treasury Bills data file: [%s]\n", fname);

    BufferedReader in = new BufferedReader(new FileReader(fname));

    Sequence data = new Sequence("T-Bills");
    String line;
    while ((line = in.readLine()) != null) {
      String[] toks = line.trim().split("\\s+");
      if (toks == null || toks.length != 2) {
        continue;
      }

      String[] dateFields = toks[0].split("-");
      try {
        int year = Integer.parseInt(dateFields[0]);
        int month = Integer.parseInt(dateFields[1]);
        double rate = Double.parseDouble(toks[1]);

        Calendar cal = Library.now();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        data.addData(rate, cal.getTimeInMillis());
      } catch (NumberFormatException e) {
        continue;
      }
    }
    in.close();
    return data;
  }
}
