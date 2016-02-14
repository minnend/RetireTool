package org.minnen.retiretool.broker;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.util.FinLib;

/**
 * Provides limited access to information in a broker.
 * 
 * Pass this object to trading systems so that they can interact with the broker without "accidentally" modifying the
 * broker or accessing restricted information.
 */
public class BrokerInfoAccess
{
  private final Broker broker;

  public BrokerInfoAccess(Broker broker)
  {
    this.broker = broker;
  }

  public long getTime()
  {
    return broker.getTime();
  }

  public TimeInfo getTimeInfo()
  {
    return broker.getTimeInfo();
  }

  public long getPrice(String name)
  {
    return broker.getPrice(name, getTime());
  }

  public long getPrice(String name, long time)
  {
    assert time <= getTime();
    return broker.getPrice(name, time);
  }

  public boolean hasSeq(String name)
  {
    SequenceStore store = broker.store;
    return store.hasName(name);
  }

  public int getID(String name)
  {
    SequenceStore store = broker.store;
    return store.getIndex(name);
  }

  public Sequence getSeq(String name)
  {
    SequenceStore store = broker.store;
    return store.tryGet(name);
  }

  public Sequence getSeq(int id)
  {
    SequenceStore store = broker.store;
    return store.tryGet(id);
  }
}
