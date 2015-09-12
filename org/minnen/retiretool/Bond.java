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
  }

  /**
   * Create a 10-year bond that pays semi-annually.
   * 
   * @param bondData interest rate data
   * @param par face value of bond
   * @param startIndex index into bondData when the bond is purchased
   */
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
    if (coupon == 0.0 || annualFreq == 0.0) {
      return 0.0;
    } else {
      return coupon / annualFreq;
    }
  }

  public double paymentThisMonth(int index)
  {
    double couponPayment = (paysThisMonth(index) ? couponPayment() : 0.0);
    double parPayment = (index == endIndex ? par : 0.0);
    return couponPayment + parPayment;
  }

  /** @return number of months bond is active */
  public int duration()
  {
    return endIndex - startIndex;
  }

  public int getMonthsBetweenPayments()
  {
    if (annualFreq <= 0.0) {
      return duration();
    } else {
      return (int) Math.round(12.0 / annualFreq);
    }
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
    int numMonthsToPayment = nextPaymentIndex - index;

    int monthsBetweenPayments = getMonthsBetweenPayments();
    int n = (int) Math.ceil((double) (endIndex - index) / monthsBetweenPayments);
    double years = annualFreq <= 0.0 ? numMonthsToPayment / 12.0 : n / annualFreq;

    // System.out.printf("| index=%d  interest=%.3f  nextPayment=%d  monthsBetween=%d  n=%d  years=%f\n", index,
    // interestRate, nextPaymentIndex, monthsBetweenPayments, n, years);

    // Calculations for fractional interest.
    double fractionalInterest = 0.0;
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
   * @param coupon total interest payments for each year
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
    if (coupon > 0.0 && fractionalInterest > 0.0) {
      double curPrice = couponPrice + maturityPrice;
      double futurePrice = calcPrice(coupon, interestRate, parValue, years - 1.0 / annualFreq, annualFreq, 0.0)
          + couponPayment;
      double priceRatio = futurePrice / curPrice;
      accruedInterest = curPrice * (Math.pow(priceRatio, fractionalInterest) - 1.0);
      // System.out.printf("Previous Price: %.2f  Next Price: %.2f  ratio=%f\n", prevPrice, nextPrice, priceRatio);
    }

    // System.out.printf("[%.2f + %.2f + %.2f]\n", couponPrice, maturityPrice, accruedInterest);
    return couponPrice + maturityPrice + accruedInterest;
  }

  /**
   * Calculates bond ROI for the given range using the rebuy approach.
   * 
   * Each month, the existing bond is sold and a new one is purchased. Bonds are modeled as 10-year bonds that pay
   * semi-annually.
   * 
   * @param factory buys a particular kind of bond
   * @param bondData interest rates for bonds
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence (negative => count back from end of sequence)
   * @return sequence of ROIs
   */
  public static Sequence calcReturnsRebuy(BondFactory factory, Sequence bondData, int iStart, int iEnd)
  {
    if (iEnd < 0) {
      iEnd += bondData.length();
    }
    if (iStart < 0 || iEnd < iStart || iEnd >= bondData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, bondData.size()));
    }

    final double principal = 10000.0;
    double cash = principal;
    Sequence seq = new Sequence(factory.name() + " (Rebuy)");
    seq.addData(cash, bondData.getTimeMS(iStart));

    for (int i = iStart; i < iEnd; ++i) {
      // Buy bond at start of this month.
      BondFactory.Receipt receipt = factory.buy(bondData, cash, i);
      Bond bond = receipt.bond;
      cash = receipt.cash;
      // System.out.printf("Bought %d: cash=%f, price=%f\n", i, cash, bond.price(i));

      // Sell bond at end of the month (we use start of next month).
      cash += bond.price(i + 1);
      // System.out.printf("  Sell %d: price=%f\n", i + 1, cash);

      // Add sequence data point for new month.
      seq.addData(cash, bondData.getTimeMS(i + 1));
    }

    return seq._div(principal);
  }

  /**
   * Calculates bond ROI for the given range using the hold-to-maturity approach.
   * 
   * All bonds are held to maturity and coupon payments are used to buy more bonds.
   * 
   * @param factory buys a particular kind of bond
   * @param bondData interest rates for bonds
   * @param iStart start simulation at this index in the data sequence
   * @param iEnd end simulation at this index in the data sequence (negative => count back from end of sequence)
   * @return sequence of ROIs
   */
  public static Sequence calcReturnsHold(BondFactory factory, Sequence bondData, int iStart, int iEnd)
  {
    if (iEnd < 0) {
      iEnd += bondData.length();
    }
    if (iStart < 0 || iEnd < iStart || iEnd >= bondData.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, iEnd=%d, size=%d", iStart, iEnd, bondData.size()));
    }

    final double principal = 10000.0;
    double cash = principal;
    Sequence seq = new Sequence(factory.name() + " (Hold)");
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
      BondFactory.Receipt receipt = factory.buy(bondData, cash, i);
      cash = receipt.cash;
      if (receipt.bond != null) {
        bonds.add(receipt.bond);
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
