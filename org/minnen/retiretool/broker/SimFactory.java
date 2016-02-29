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
  public final double        startingBalance;
  public final double        monthlyDeposit;
  public final PriceModel    valueModel;
  public final PriceModel    quoteModel;

  public SimFactory(SequenceStore store, Sequence guideSeq, Slippage slippage, int maxDelay, double startingBalance,
      double monthlyDeposit, PriceModel valueModel, PriceModel quoteModel)
  {
    this.store = store;
    this.guideSeq = guideSeq;
    this.slippage = slippage;
    this.maxDelay = maxDelay;
    this.startingBalance = startingBalance;
    this.monthlyDeposit = monthlyDeposit;
    this.valueModel = valueModel;
    this.quoteModel = quoteModel;
  }

  public Simulation build()
  {
    return new Simulation(store, guideSeq.dup(), slippage, maxDelay, startingBalance, monthlyDeposit, valueModel,
        quoteModel);
  }
}
