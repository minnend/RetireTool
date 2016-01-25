package org.minnen.retiretool.broker;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Random;

/**
 * Provides various methods for calculating trade price from an OLHC vector.
 */
public class PriceModel
{
  public final static PriceModel zeroModel      = new PriceModel(Type.FixedIndex, false, 0, Double.NaN);
  public final static PriceModel closeModel     = new PriceModel(Type.Close, false);
  public final static PriceModel adjCloseModel  = new PriceModel(Type.AdjClose, false);
  public final static PriceModel openModel      = new PriceModel(Type.Open, false);
  public final static PriceModel uniformOCModel = new PriceModel(Type.UniformOC, true);
  public final static PriceModel uniformHLModel = new PriceModel(Type.UniformHL, true);

  public enum Type {
    FixedIndex, Close, AdjClose, Open, UniformOC, UniformHL, OpenSlip, CloseSlip
  }

  public final Type    type;
  public final int     iFixed;
  public final double  slipFraction;
  public final boolean bAdjustPrices;
  public final Random  rng = new Random();

  public PriceModel(Type type, boolean bAdjustPrices)
  {
    this(type, bAdjustPrices, -1, Double.NaN);
  }

  public PriceModel(Type type, boolean bAdjustPrices, int iFixed, double slipFraction)
  {
    assert type != Type.FixedIndex || iFixed >= 0;
    assert (type != Type.OpenSlip && type != Type.CloseSlip) || !Double.isNaN(slipFraction);

    this.type = type;
    this.bAdjustPrices = bAdjustPrices;
    this.iFixed = iFixed;
    this.slipFraction = slipFraction;

    // Never adjust prices if using AdjClose.
    assert type != Type.AdjClose || !bAdjustPrices;
  }

  public double getPrice(FeatureVec priceData)
  {
    double price = getRawPrice(priceData);
    if (bAdjustPrices) {
      price = adjustPrice(price, priceData);
    }
    return price;
  }

  private double getRawPrice(FeatureVec priceData)
  {
    switch (type) {
    case FixedIndex:
      return priceData.get(iFixed);
    case Close:
      return priceData.get(FinLib.Close);
    case AdjClose:
      return priceData.get(FinLib.AdjClose);
    case Open:
      return priceData.get(FinLib.Open);
    case UniformOC:
      return uniformPrice(FinLib.Open, FinLib.Close, priceData);
    case UniformHL:
      return uniformPrice(FinLib.Low, FinLib.High, priceData);
    case OpenSlip:
      return priceData.get(FinLib.Open) * (1.0 - slipFraction) + uniformPrice(FinLib.Low, FinLib.High, priceData)
          * slipFraction;
    case CloseSlip:
      return priceData.get(FinLib.Close) * (1.0 - slipFraction) + uniformPrice(FinLib.Low, FinLib.High, priceData)
          * slipFraction;
    default:
      throw new IllegalArgumentException("Unsupported PriceModel type: " + type);
    }
  }

  private double uniformPrice(int index1, int index2, FeatureVec priceData)
  {
    double price1 = priceData.get(index1);
    double price2 = priceData.get(index2);
    return price1 + (price2 - price1) * rng.nextDouble(true, true);
  }

  public static double adjustPrice(double price, FeatureVec priceData)
  {
    return priceData.get(FinLib.AdjClose) / priceData.get(FinLib.Close) * price;
  }
}
