package org.minnen.retiretool.vanguard;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VanguardFund
{
  public String symbol;
  public String description;
  public String fundID;
  public double expenseRatio;

  public static enum FundSet {
    All, Google401k
  }

  public VanguardFund(String symbol, String description, String fundID, double expenseRatio)
  {
    this.symbol = symbol;
    this.description = description;
    this.fundID = fundID;
    this.expenseRatio = expenseRatio;
  }

  public static String[] getFundsForSearch()
  {
    return new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VISVX", "VGENX", "VGHCX", "VGPMX", "VBIIX", "VBISX",
        "VWEHX", "VEIEX", "VEURX", "VINEX" };
  }

  public static List<VanguardFund> getFunds(FundSet fundSet)
  {
    List<VanguardFund> funds = new ArrayList<>();

    funds.add(new VanguardFund("VFINX", "S&P 500", "0085", 0.16)); // VIIIX
    funds.add(new VanguardFund("VEXMX", "US Extended Market", "0085", 0.16)); // VEMPX
    funds.add(new VanguardFund("VTSMX", "US Stock Market", "0085", 0.16)); // Replacement for VIIIX + VEMPX
    funds.add(new VanguardFund("VBMFX", "US Bond Market", "0084", 0.16));
    funds.add(new VanguardFund("VGSIX", "REITs", "0123", 0.26));
    funds.add(new VanguardFund("VGTSX", "International Stock", "0113", 0.19));

    funds.add(new VanguardFund("VWINX", "Wellesley Income", "0027", 0.23));
    funds.add(new VanguardFund("VFISX", "Short-Term Treasuries", "0032", 0.20));
    funds.add(new VanguardFund("CRISX", "CRM Small Cap Value", null, 0.90));
    funds.add(new VanguardFund("DODGX", "Dodge & Cox Stock", null, 0.52));

    if (fundSet == FundSet.All) {
      funds.add(new VanguardFund("VIPSX", "Inflation-Protected Securities", "0119", 0.20)); // June 2000
      funds.add(new VanguardFund("VISGX", "Small-Cap Growth", "0861", 0.20));

      funds.add(new VanguardFund("VISVX", "Small-Cap Value", "0860", 0.20));
      funds.add(new VanguardFund("VGENX", "Energy", "0051", 0.37));
      funds.add(new VanguardFund("VGHCX", "Health Care", "0052", 0.36));
      funds.add(new VanguardFund("VGPMX", "Precious Metals & Mining", "0053", 0.35));
      funds.add(new VanguardFund("VBIIX", "Intermediate Bonds", "0314", 0.16));
      funds.add(new VanguardFund("VBISX", "Short-Term Bonds", null, 0.16));
      funds.add(new VanguardFund("VWEHX", "High-Yield Bonds", null, 0.23));
      funds.add(new VanguardFund("VWNFX", "Windsor II", null, 0.34));

      funds.add(new VanguardFund("VEIEX", "Emerging Markets", null, 0.32));
      funds.add(new VanguardFund("VEURX", "European Stock", null, 0.26));
      funds.add(new VanguardFund("VINEX", "International Explorer", null, 0.42));
      funds.add(new VanguardFund("VTRIX", "International Value", null, 0.46));
      funds.add(new VanguardFund("VWIGX", "International Growth", null, 0.46));

      funds.add(new VanguardFund("VITAX", "Information Technology", null, 0.10)); // Feb 2004
      funds.add(new VanguardFund("GLD", "Gold", null, 0.40)); // Nov 2004
      funds.add(new VanguardFund("USAGX", "Gold", null, 0.40)); // Nov 2004
      // funds.add(new VanguardFund("VICSX", "Intermediate Corporate Bonds", "1946", 0.07)); // Nov 2009

      // funds.add(new VanguardFund("^VIX", "VIX", null, 0.0));      
      // funds.add("VHGEX", new VanguardFund("VHGEX", "Global Equity", "0129", 0.57)); // US = 40%
    }

    funds.sort(new Comparator<VanguardFund>()
    {
      @Override
      public int compare(VanguardFund a, VanguardFund b)
      {
        return a.symbol.compareTo(b.symbol);
      }
    });

    return funds;
  }

  public static Map<String, VanguardFund> getFundMap(FundSet fundSet)
  {
    Map<String, VanguardFund> fundMap = new HashMap<>();
    for (VanguardFund fund : getFunds(fundSet)) {
      fundMap.put(fund.symbol, fund);
    }
    return fundMap;
  }

  public static String[] getFundNames(FundSet fundSet)
  {
    List<VanguardFund> funds = getFunds(fundSet);
    String[] names = new String[funds.size()];
    for (int i = 0; i < names.length; ++i) {
      names[i] = funds.get(i).symbol;
    }
    return names;
  }
}
