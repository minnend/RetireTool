package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Bond
{
  private final Sequence bondData;
  public final double    par;
  public final double    coupon;
  public final double    annualFreq;
  public final int       startIndex, endIndex;

  public Bond(Sequence bondData, double par, double coupon, double annualFreq, int startIndex, int endIndex)
  {
    assert par > 0.0;
    assert coupon >= 0.0;
    assert annualFreq >= 0.0;
    assert endIndex > startIndex;

    this.bondData = bondData;
    this.par = par;
    this.coupon = coupon;
    this.annualFreq = annualFreq;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    assert Math.abs(par - price(startIndex)) < 0.01;
  }

  public Bond(Sequence bondData, double par, int startIndex)
  {
    this(bondData, par, par * bondData.get(startIndex, 0) / 100.0, 2, startIndex, startIndex + 120);
  }

  public boolean paysThisMonth(int index)
  {
    if (!isActive(index) || index == startIndex) {
      return false;
    }

    int diff = index - startIndex;
    int monthsBetweenPayments = getMonthsBetweenPayments();
    return (diff % monthsBetweenPayments) == 0;
  }

  public boolean isActive(int index)
  {
    return index >= startIndex && index <= endIndex;
  }

  public boolean isExpiring(int index)
  {
    return index == endIndex;
  }

  public double couponPayment()
  {
    return coupon / annualFreq;
  }

  public double paymentThisMonth(int index)
  {
    double couponPayment = (paysThisMonth(index) ? couponPayment() : 0.0);
    double parPayment = (index == endIndex ? par : 0.0);
    return couponPayment + parPayment;
  }

  public int getMonthsBetweenPayments()
  {
    return (int) Math.round(12.0 / annualFreq);
  }

  public int getNextPaymentMonth(int index)
  {
    for (int i = index; i <= endIndex; ++i) {
      if (paysThisMonth(i)) {
        return i;
      }
    }
    return -1;
  }

  public double price(int index)
  {
    if (!isActive(index) || index == endIndex) {
      return 0.0;
    }

    double interestRate = bondData.get(index, 0);

    // Calculations for number of payments left.
    int nextPaymentIndex = getNextPaymentMonth(index);
    assert nextPaymentIndex >= index;

    int monthsBetweenPayments = getMonthsBetweenPayments();
    int n = (int) Math.ceil((double) (endIndex - index) / monthsBetweenPayments);
    double years = n / annualFreq;

    // System.out.printf("| index=%d  interest=%.3f  nextPayment=%d  monthsBetween=%d  n=%d  years=%f\n", index,
    // interestRate, nextPaymentIndex, monthsBetweenPayments, n, years);

    // Calculations for fractional interest.
    double fractionalInterest = 0.0;
    int numMonthsToPayment = nextPaymentIndex - index;
    assert numMonthsToPayment >= 0 && numMonthsToPayment <= monthsBetweenPayments;
    if (numMonthsToPayment > 0) {
      fractionalInterest = 1.0 - (double) numMonthsToPayment / monthsBetweenPayments;
    }

    double price = calcPrice(coupon, interestRate, par, years, annualFreq, fractionalInterest);
    // System.out.printf("| monthsToPayment=%d  fractionalInterest=%f  price=%.2f\n", numMonthsToPayment,
    // fractionalInterest, price);
    assert !Double.isNaN(price);
    return price;
  }

  /**
   * Calculates the price of a bond.
   * 
   * @param coupon amount paid <code>annualFreq</code> times per year
   * @param interestRate current interest rate
   * @param parValue amount paid at maturity
   * @param years years until maturity
   * @param annualFreq number of times per year that the coupon is paid
   * @param fractionalInterest fraction of coupon payment period already passed [0..1)
   * @return current price of the bond
   */
  public static double calcPrice(double coupon, double interestRate, double parValue, double years, double annualFreq,
      double fractionalInterest)
  {
    if (annualFreq <= 0.0) {
      annualFreq = 1.0;
    }
    double couponPayment = coupon / annualFreq;
    double nf = years * annualFreq; // number of payments left
    double effIR = (interestRate / 100.0) / annualFreq; // effective interest rate per payment
    double x = Math.pow(1.0 + effIR, -nf);
    double couponPrice = couponPayment * (1.0 - x) / effIR;
    double maturityPrice = parValue * x;

    double accruedInterest = 0.0;
    if (fractionalInterest > 0.0) {
      double prevPrice = couponPrice + maturityPrice;
      double nextPrice = calcPrice(coupon, interestRate, parValue, years - 1.0 / annualFreq, annualFreq, 0.0)
          + couponPayment;
      double priceRatio = nextPrice / prevPrice;
      accruedInterest = prevPrice * (Math.pow(priceRatio, fractionalInterest) - 1.0);
      // System.out.printf("Previous Price: %.2f  Next Price: %.2f  ratio=%f\n", prevPrice, nextPrice, priceRatio);
    }

    // System.out.printf("[%.2f + %.2f + %.2f]\n", couponPrice, maturityPrice, accruedInterest);
    return couponPrice + maturityPrice + accruedInterest;
  }

  /**
   * Calculates bond ROI for the given range using the rebuy approach.
   * 
   * Each month, the existing bond is sold and a new one is purchased.
   * 
   * @param bondData interest rates for bonds
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence
   * @return sequence of ROIs
   */
  public static Sequence calcReturnsRebuy(Sequence bondData, int iStart, int iEnd)
  {
    if (iStart < 0 || iEnd < iStart || iEnd >= bondData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, bondData.size()));
    }

    double cash = 1.0; // start with one dollar
    Sequence seq = new Sequence("Bonds (Rebuy)");
    seq.addData(cash, bondData.getTimeMS(iStart));

    for (int i = iStart; i < iEnd; ++i) {
      // Buy bond at start of this month.
      Bond bond = new Bond(bondData, cash, i);
      // System.out.printf("Bought %d: cash=%f, price=%f\n", i, cash, bond.price(i));

      // Sell bond at end of the month (we use start of next month).
      cash = bond.price(i + 1);
      // System.out.printf("  Sell %d: price=%f\n", i+1, cash);

      // Add sequence data point for new month.
      seq.addData(cash, bondData.getTimeMS(i + 1));
    }

    return seq;
  }

  /**
   * Calculates bond ROI for the given range using the hold-to-maturity approach.
   * 
   * All bonds are held to maturity and coupon payments are used to buy more bonds.
   * 
   * @param bondData interest rates for bonds
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence
   * @return sequence of ROIs
   */
  public static Sequence calcReturnsHold(Sequence bondData, int iStart, int iEnd)
  {
    if (iStart < 0 || iEnd < iStart || iEnd >= bondData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, bondData.size()));
    }

    final double principal = 1000.0;
    final double bondQuantum = 10.0; // bonds can only be purchased in fixed increments
    double cash = principal;
    Sequence seq = new Sequence("Bonds (Hold)");
    seq.addData(cash, bondData.getTimeMS(iStart));
    List<Bond> bonds = new ArrayList<Bond>(); // TODO not an efficient data structure

    for (int i = iStart; i < iEnd; ++i) {
      // Collect from existing bonds.
      Iterator<Bond> it = bonds.iterator();
      while (it.hasNext()) {
        Bond bond = it.next();
        cash += bond.paymentThisMonth(i);
        assert bond.isActive(i);
        if (bond.isExpiring(i)) {
          it.remove();
        }
      }

      // Buy new bonds.
      double bondValue = bondQuantum * Math.floor(cash / bondQuantum);
      if (bondValue > 0.0) {
        bonds.add(new Bond(bondData, bondValue, i));
        cash -= bondValue;
      }

      // Add sequence data point for new month.
      double value = cash;
      for (Bond bond : bonds) {
        value += bond.price(i);
      }
      seq.addData(value, bondData.getTimeMS(i + 1));
    }
    return seq._div(principal);
  }
}
