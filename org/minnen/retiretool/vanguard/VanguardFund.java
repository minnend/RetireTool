package org.minnen.retiretool.vanguard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VanguardFund
{
  public static final List<VanguardFund>        funds   = new ArrayList<>();
  public static final Map<String, VanguardFund> fundMap = new HashMap<>();

  public final String                           symbol;
  public final String                           description;
  public final String                           fundID;
  public final double                           expenseRatio;

  static {
    funds.add(new VanguardFund("VFINX", "S&P 500", "0085", 0.04)); // 1976, VFIAX / VIIIX @ 0.14, 0.04, 0.02
    funds.add(new VanguardFund("VEXMX", "US Extended Market", "0085", 0.08)); // 1987, VEXAX / VEMPX @ 0.21, 0.08, 0.05
    funds.add(new VanguardFund("VTSMX", "US Stock Market", "0085", 0.04)); // 1992 @ 0.15, 0.04, = VIIIX + VEMPX
    funds.add(new VanguardFund("VBMFX", "US Bond Market", "0084", 0.05)); // 1986, VBTLX @ 0.15, 0.05
    funds.add(new VanguardFund("VGSIX", "REITs", "0123", 0.26)); // 1996, VGSLX @ 0.26, 0.12
    funds.add(new VanguardFund("VGTSX", "International Stock", "0113", 0.19)); // 1996, VTIAX @ 0.18, 0.11

    funds.add(new VanguardFund("VWINX", "Wellesley Income", "0027", 0.23)); // 1980
    funds.add(new VanguardFund("VFISX", "Short-Term Treasuries", "0032", 0.10)); // 1991, VFIRX @ 0.20, 0.10
    funds.add(new VanguardFund("CRISX", "CRM Small Cap Value", null, 0.90)); // 1997
    funds.add(new VanguardFund("DODGX", "Dodge & Cox Stock", null, 0.52));

    funds.add(new VanguardFund("VIPSX", "Inflation-Protected Securities", "0119", 0.20)); // June 2000
    funds.add(new VanguardFund("VISGX", "Small-Cap Growth", "0861", 0.20)); // 1998

    funds.add(new VanguardFund("VISVX", "Small-Cap Value", "0860", 0.20)); // 1998
    funds.add(new VanguardFund("NAESX", "Small-Cap", "0048", 0.06)); // 1980, VSMAX @ 0.18, 0.06
    funds.add(new VanguardFund("VGENX", "Energy", "0051", 0.37)); // 1984
    funds.add(new VanguardFund("VGHCX", "Health Care", "0052", 0.36)); // 1984
    funds.add(new VanguardFund("VGPMX", "Precious Metals & Mining", "0053", 0.35)); // 1984
    funds.add(new VanguardFund("VBIIX", "Intermediate Bonds", "0314", 0.16)); // 1994
    funds.add(new VanguardFund("VBISX", "Short-Term Bonds", null, 0.16)); // 1994
    funds.add(new VanguardFund("VWEHX", "High-Yield Bonds", "0029", 0.13)); // 1980, VWEAX @ 0.23, 0.13
    funds.add(new VanguardFund("VWNFX", "Windsor II", null, 0.34));

    funds.add(new VanguardFund("VEIEX", "Emerging Markets", null, 0.32)); // 1994
    funds.add(new VanguardFund("VEURX", "European Stock", null, 0.26));
    funds.add(new VanguardFund("VINEX", "International Explorer", null, 0.42));
    funds.add(new VanguardFund("VTRIX", "International Value", null, 0.46));
    funds.add(new VanguardFund("VWIGX", "International Growth", null, 0.46));
    funds.add(new VanguardFund("VPACX", "Pacific Stock", "0072", 0.10)); // 1990, VPADX @ 0.26, 0.10
    funds.add(new VanguardFund("VEURX", "European Stock", "0079", 0.10)); // 1990, VEUSX @ 0.26, 0.10

    // funds.add(new VanguardFund("VITAX", "Information Technology", null, 0.10)); // Feb 2004
    // funds.add(new VanguardFund("GLD", "Gold", null, 0.40)); // Nov 2004
    // funds.add(new VanguardFund("USAGX", "Gold", null, 0.40)); // Aug 1984
    // funds.add(new VanguardFund("VICSX", "Intermediate Corporate Bonds", "1946", 0.07)); // Nov 2009

    // Sort funds by symbol.
    funds.sort(new Comparator<VanguardFund>()
    {
      @Override
      public int compare(VanguardFund a, VanguardFund b)
      {
        return a.symbol.compareTo(b.symbol);
      }
    });

    // Build map from symbol to fund object.
    for (VanguardFund fund : funds) {
      fundMap.put(fund.symbol, fund);
    }
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

  public static String[] getFundsIn401k()
  {
    // Institutional Funds: VEMPX, VIIIX, VTPSX, VBMPX, VGSNX
    // Return investor symbol since that's what's it in the EOD data.
    return new String[] { "VEXMX", "VFINX", "VGTSX", "VBMFX", "VGSIX" };
  }

  /** @return list of vanguard funds with data back to at least 1990 */
  public static String[] getOldFunds()
  {
    return new String[] { //
        "VFINX", // 1976 S&P 500
        "NAESX", // 1980 Small-cap
        "VGENX", // 1984 Energy
        //"VGHCX", // 1984 Health care
        "VGPMX", // 1984 Precious metals
        "VBMFX", // 1986 US Bonds
        "VEXMX", // 1987 US Extended
        "VPACX", // 1990 Pacific stock
        "VEURX", // 1990 European stock
    };
  }

  public static String[] getAllFunds()
  {
    String[] names = new String[funds.size()];
    for (int i = 0; i < names.length; ++i) {
      names[i] = funds.get(i).symbol;
    }
    return names;
  }
}
