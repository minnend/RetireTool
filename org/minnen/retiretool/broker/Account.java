package org.minnen.retiretool.broker;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.broker.transactions.Transaction;
import org.minnen.retiretool.broker.transactions.TransactionBuy;
import org.minnen.retiretool.broker.transactions.TransactionDeposit;
import org.minnen.retiretool.broker.transactions.TransactionOpen;
import org.minnen.retiretool.broker.transactions.TransactionWithdraw;

public class Account
{
  public enum Type {
    Taxable, Traditional, Roth
  };

  public final Broker             broker;
  public final Type               type;
  private final List<Transaction> transactions = new ArrayList<>(); ;

  private double                  balance;

  public Account(Broker broker, Type type)
  {
    this.broker = broker;
    this.type = type;
    transactions.add(new TransactionOpen(this, broker.getTime()));
  }

  public double getBalance()
  {
    return balance;
  }

  private void apply(Transaction transaction)
  {
    if (transaction instanceof TransactionDeposit) {
      apply((TransactionDeposit) transaction);
    } else if (transaction instanceof TransactionWithdraw) {
      apply((TransactionWithdraw) transaction);
    } else if (transaction instanceof TransactionOpen) {
      // Nothing to do.
    } else if (transaction instanceof TransactionBuy) {
      apply((TransactionBuy) transaction);
    }
  }

  private void apply(TransactionDeposit deposit)
  {
    balance += deposit.amount;
    assert FinLib.equiv(balance, deposit.postBalance);
  }

  private void apply(TransactionWithdraw withdraw)
  {
    balance -= withdraw.amount;
    assert FinLib.equiv(balance, withdraw.postBalance);
  }

  private void apply(TransactionBuy buy)
  {
    balance -= buy.getValue();
    assert FinLib.equiv(balance, buy.postBalance);
  }

  public void deposit(double amount)
  {
    assert amount > 0.0;
    TransactionDeposit deposit = new TransactionDeposit(this, broker.getTime(), amount);
    transactions.add(deposit);
    apply(deposit);
  }

  public void withdraw(double amount)
  {
    assert amount > 0.0;
    TransactionWithdraw withdraw = new TransactionWithdraw(this, broker.getTime(), amount);
    transactions.add(withdraw);
    apply(withdraw);
  }

  public void buyShares(String name, int nShares)
  {
    double price = broker.getPrice(name, broker.getTime());
    double value = price * nShares;
    assert value <= balance;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares);
    transactions.add(buy);
    apply(buy);
  }

  public void buyValue(String name, double value)
  {
    assert value <= balance;
    double price = broker.getPrice(name, broker.getTime());
    int nShares = (int) Math.floor(value / price);

    assert price * nShares <= balance;
    TransactionBuy buy = new TransactionBuy(this, broker.getTime(), name, nShares);
    transactions.add(buy);
    apply(buy);
  }

  public void sellShares(String name, int nShares)
  {

  }

  public void sellValue(String name, double value)
  {

  }

  public void sellAll(String name)
  {

  }

  public void printTransactions()
  {
    for (Transaction transaction : transactions) {
      System.out.println(transaction);
    }
  }
}
