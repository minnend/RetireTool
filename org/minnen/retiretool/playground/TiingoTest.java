package org.minnen.retiretool.playground;

import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.tiingo.Tiingo;

public class TiingoTest
{
  public static void main(String[] args)
  {
    System.out.printf("Auth Key: %s\n", Tiingo.auth);
    String s = TiingoIO.downloadDailyData("vtsmx");
    System.out.println(s.substring(0, 500));
  }
}
