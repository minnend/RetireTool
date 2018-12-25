package org.minnen.retiretool.broker;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

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

  public int getID(String name)
  {
    SequenceStore store = broker.store;
    return store.getIndex(name);
  }

  /** @return name of the sequence with the given ID. */
  public String getName(int id)
  {
    return broker.store.tryGet(id).getName();
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
