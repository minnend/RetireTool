package org.minnen.retiretool;

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
}
