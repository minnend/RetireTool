package org.minnen.retiretool.broker;

import org.minnen.retiretool.Slippage;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

/**
 * Factory class for creating simulations.
 */
public class SimFactory
{
  public final SequenceStore store;
  public final Sequence      guideSeq;
  public final Slippage      slippage;
  public final int           maxDelay;
  public final PriceModel    priceModel;

  public SimFactory(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, PriceModel priceModel)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.maxDelay = maxDelay;
    this.priceModel = priceModel;
  }

  public Simulation build()
  {
    return new Simulation(store, guideSeq.dup(), slippage, maxDelay, priceModel);
  }
}
