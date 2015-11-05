package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.Library;

public class Broker
{
  private long                time     = Library.TIME_ERROR;
  private final List<Account> accounts = new ArrayList<>();

  public void setTime(long time)
  {
    this.time = time;
  }

  public long getTime()
  {
    return time;
  }

  public Account openAccount(double startingBalance, Account.Type accountType)
  {
    assert startingBalance >= 0.0;

    Account account = new Account(this, accountType);
    accounts.add(account);
    account.deposit(startingBalance);
    return account;
  }

  public double getPrice(String name, long time)
  {
    return 0.0; // TODO
  }
}
