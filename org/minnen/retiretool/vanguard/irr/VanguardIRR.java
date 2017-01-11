package org.minnen.retiretool.vanguard.irr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class VanguardIRR
{
  public static String dollars2double(String s)
  {
    return s.replaceAll("[$,]", "");
  }

  public static List<Transaction> loadTransactions(File file) throws IOException
  {
    List<Transaction> transactions = new ArrayList<>();
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line;
    double totalValue = 0.0;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) continue;

      if (line.startsWith("093926")) {
        String[] toks = line.trim().split(",");
        double value = Double.parseDouble(toks[toks.length - 1]);
        totalValue += value;
        continue;
      }

      String[] toks = line.trim().split("\t+");
      assert toks.length == 6;

      String[] dateToks = toks[0].split("/");
      assert dateToks.length == 3;
      int month = Integer.parseInt(dateToks[0]);
      int day = Integer.parseInt(dateToks[1]);
      int year = Integer.parseInt(dateToks[2]);

      Transaction transaction = new Transaction();
      transaction.date = TimeLib.toMs(year, month, day);
      transaction.description = toks[1];
      transaction.fund = toks[2];
      transaction.quantity = Double.parseDouble(toks[3]);
      transaction.price = Double.parseDouble(dollars2double(toks[4]));
      transaction.amount = Double.parseDouble(dollars2double(toks[5]));
      transactions.add(transaction);
    }
    in.close();

    transactions.sort(new Comparator<Transaction>()
    {
      @Override
      public int compare(Transaction a, Transaction b)
      {
        if (a.date < b.date) return -1;
        if (a.date > b.date) return 1;
        return 0;
      }
    });

    Transaction transaction = new Transaction();
    transaction.date = transactions.get(transactions.size() - 1).date + 1;
    transaction.description = "Liquidate";
    transaction.fund = "All";
    transaction.amount = totalValue;
    transactions.add(transaction);

    return transactions;
  }

  public static void main(String[] args) throws IOException
  {
    // Load transaction data.
    File file = new File("G:/research/finance", "vanguard-transactions-20131025-20161003.csv");
    List<Transaction> transactions = loadTransactions(file);
    System.out.printf("Transactions: %d\n", transactions.size());

    // Print all transaction types.
    Set<String> types = new HashSet<>();
    for (Transaction t : transactions) {
      types.add(t.description);
    }
    System.out.println("Transaction Types:");
    for (String s : types) {
      System.out.printf(" %s\n", s);
    }

    // Print all funds.
    Set<String> funds = new HashSet<>();
    for (Transaction t : transactions) {
      funds.add(t.fund);
    }
    System.out.println("Funds:");
    for (String s : funds) {
      System.out.printf(" %s\n", s);
    }

    // Simulate transactions and calculate IRR.
    for (Transaction t : transactions) {
      // System.out.printf("%s | %52s | %25s | %9.2f\n", TimeLib.formatDate2(t.date), t.description, t.fund, t.amount);
      if (t.description.equalsIgnoreCase("Plan Contribution") || t.description.equalsIgnoreCase("Conversion In")) {
        System.out.printf("%s  %s\n", FinLib.currencyFormatter.format(t.amount), TimeLib.formatYMD(t.date));
      } else if (t.description.equalsIgnoreCase("Fee")) {} else if (t.description
          .equalsIgnoreCase("Daily Accrual Dividends")) {} else if (t.description
          .equalsIgnoreCase("Dividends on Equity Investments")) {} else if (t.description
          .equalsIgnoreCase("Miscellaneous Pending Dividends")) {} else if (t.description
          .equalsIgnoreCase("Source to Source Pending Dividends")) {} else if (t.description
          .equalsIgnoreCase("Plan Initiated Transfer Pending Dividends (Closeout)")) {
        // Do nothing.
      } else if (t.description.equalsIgnoreCase("Liquidate")) {
        System.out.printf("-%s  %s\n", FinLib.currencyFormatter.format(t.amount), TimeLib.formatYMD(t.date));
      } else if (t.description.equalsIgnoreCase("Source to Source/Fund to Fund Transfer In")
          || t.description.equalsIgnoreCase("Source to Source/Fund to Fund Transfer Out")
          || t.description.equalsIgnoreCase("Fund to Fund In") || t.description.equalsIgnoreCase("Fund to Fund Out")
          || t.description.equalsIgnoreCase("Plan Initiated TransferIn")
          || t.description.equalsIgnoreCase("Plan Initiated TransferOut")) {
        // Do nothing.
      } else {
        String msg = String.format("Unknown Transaction: [%s]", t.description);
        throw new RuntimeException(msg);
      }
      // System.out.printf("$%s\n", FinLib.currencyFormatter.format(balance));
    }
  }
}
