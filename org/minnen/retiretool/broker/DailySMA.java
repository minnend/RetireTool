package org.minnen.retiretool.broker;

import java.util.Random;

public class DailySMA
{
  private final Account  account;
  private final String   riskyName;
  private final String   safeName;

  private final double   margin       = 0.5 / 100.0;
  private final int      nLookback    = 10;
  private final double[] monthlyMeans = new double[12];

  private double         currentValue;

  private static Random  rng          = new Random(); // TODO

  public DailySMA(Account account, String riskyName, String safeName)
  {
    this.account = account;
    this.riskyName = riskyName;
    this.safeName = safeName;
  }

  public void init(TimeInfo timeInfo)
  {

  }

  public void step(TimeInfo timeInfo)
  {

  }

  public boolean predict()
  {
    // TODO
    return rng.nextBoolean();
  }
}
