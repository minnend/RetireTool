package org.minnen.retiretool.broker;

public class Receipt
{
  public final String name;
  public final double longGain;
  public final double shortGain;
  public final double longLoss;
  public final double shortLoss;

  public Receipt(String name, double longGain, double shortGain, double longLoss, double shortLoss)
  {
    this.name = name;
    this.longGain = longGain;
    this.shortGain = shortGain;
    this.longLoss = longLoss;
    this.shortLoss = shortLoss;
  }
}
