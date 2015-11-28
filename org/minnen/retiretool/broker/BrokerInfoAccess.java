package org.minnen.retiretool.broker;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;

/**
 * Provides limited access to information in a broker.
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

  public Sequence getPriceSeq(String name)
  {
    SequenceStore store = broker.store;
    if (store.hasMisc(name)) {
      return store.getMisc(name);
    } else if (store.hasName(name)) {
      return store.get(name);
    } else {
      throw new IllegalArgumentException("Can't find asset: " + name);
    }
  }
}
