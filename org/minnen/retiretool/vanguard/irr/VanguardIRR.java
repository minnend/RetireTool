package org.minnen.retiretool.vanguard.irr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class VanguardIRR
{
  private static String dollars2double(String s)
  {
    return s.replaceAll("[$,]", "");
  }

  private static List<Holding> loadHoldings(File file) throws IOException
  {
    List<Holding> holdings = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;
        if (line.startsWith("Plan Number")) continue; // skip header

        String[] toks = line.trim().split(",");
        assert toks.length == 6;

        Holding holding = new Holding();
        holding.planNumber = Integer.parseInt(toks[0]);
        holding.planName = toks[1];
        holding.fund = toks[2];
        holding.shares = Double.parseDouble(toks[3]);
        String s = dollars2double(toks[4]);
        holding.price = s.isEmpty() ? 1.0 : Double.parseDouble(s);
        holding.totalValue = Double.parseDouble(dollars2double(toks[5]));
        holdings.add(holding);
      }
    }
    return holdings;
  }

  private static List<Transaction> loadTransactions(File file) throws IOException
  {
    List<Transaction> transactions = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

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
    }

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
    return transactions;
  }

  public static void main(String[] args) throws IOException
  {
    // Load current holdings.
    File file = new File(DataIO.financePath, "vanguard-holdings.csv");
    List<Holding> holdings = loadHoldings(file);
    System.out.printf("Holdings: %d\n", holdings.size());
    double balance = 0.0;
    for (Holding holding : holdings) {
      balance += holding.totalValue;
      // System.out.println(" " + holding);
    }
    System.out.printf("Total value: $%s\n", FinLib.currencyFormatter.format(balance));
    // System.out.println();

    // Load transaction data.
    file = new File(DataIO.financePath, "vanguard-transactions-web-20131025-20180731.txt");
    List<Transaction> transactions = loadTransactions(file);
    Transaction lastTransaction = transactions.get(transactions.size() - 1);
    System.out.printf("Transactions: %d\n", transactions.size());
    System.out.printf("Time range: [%s] -> [%s]\n", TimeLib.formatYMD(transactions.get(0).date),
        TimeLib.formatYMD(lastTransaction.date));
    // System.out.println();

    // Print all transaction types.
    // Map<String, Integer> types = new HashMap<>();
    // for (Transaction t : transactions) {
    // int count = types.getOrDefault(t.description, 0);
    // types.put(t.description, count + 1);
    // }
    // System.out.println("-- Transaction Types --");
    // for (Map.Entry<String, Integer> x : types.entrySet()) {
    // System.out.printf(" %54s: %d\n", x.getKey(), x.getValue());
    // }
    // System.out.println();

    // Print all funds.
    // Map<String, Integer> funds = new HashMap<>();
    // for (Transaction t : transactions) {
    // int count = funds.getOrDefault(t.fund, 0);
    // funds.put(t.fund, count + 1);
    // }
    // System.out.println("-- Funds --");
    // for (Map.Entry<String, Integer> x : funds.entrySet()) {
    // System.out.printf(" %26s: %d\n", x.getKey(), x.getValue());
    // }
    // System.out.println();

    // Collect contributions.
    long today = lastTransaction.date;
    double totalContributions = 0.0;
    List<Contribution> contributions = new ArrayList<>();
    for (Transaction t : transactions) {
      // System.out.printf("%s | %52s | %25s | %9.2f\n", TimeLib.formatDate2(t.date), t.description, t.fund, t.amount);
      if (t.description.equalsIgnoreCase("Plan Contribution") || t.description.equalsIgnoreCase("Conversion In")) {
        Contribution contrib = new Contribution(t.date, t.amount, today);
        totalContributions += contrib.amount;
        contributions.add(contrib);
      } else if (t.description.equalsIgnoreCase("Fee")) {
        // subtract
      } else if (t.description.equalsIgnoreCase("Daily Accrual Dividends")
          || t.description.equalsIgnoreCase("Dividends on Equity Investments")
          || t.description.equalsIgnoreCase("Miscellaneous Pending Dividends")
          || t.description.equalsIgnoreCase("Source to Source Pending Dividends")
          || t.description.equalsIgnoreCase("Plan Initiated Transfer Pending Dividends (Closeout)")) {
        // add dividend
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
    }
    System.out.printf("Contributions (%d): $%s\n", contributions.size(),
        FinLib.currencyFormatter.format(totalContributions));

    // Solve for IIR.
    double r = Contribution.solveIRR(contributions, balance);
    System.out.printf("IRR: %.3f%%\n", r);
  }
}
