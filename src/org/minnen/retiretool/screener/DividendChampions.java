package org.minnen.retiretool.screener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.util.Writer;

/**
 * Class to load data from a Dividend Champions excel file from Drip Investing.
 * 
 * Site: http://www.dripinvesting.org/Tools/Tools.asp<br/>
 * Direct Link: https://bitly.com/USDividendChampions
 */
public class DividendChampions
{
  public static File getPathExcel()
  {
    return new File(DataIO.getFinancePath(), "U.S.DividendChampions.xlsx");
  }

  public static File getPathCSV()
  {
    return new File(DataIO.getFinancePath(), "U.S.DividendChampions.csv");
  }

  private static double getSafeNumeric(Cell cell, FormulaEvaluator evaluator)
  {
    if (cell == null) return Double.NaN;
    cell = evaluator.evaluateInCell(cell);

    CellType type = cell.getCellTypeEnum();
    if (type == CellType.STRING) {
      if (cell.getStringCellValue().equals("n/a")) return Double.NaN;
      else throw new RuntimeException("Unexpected numeric cell: " + cell.getStringCellValue());
    } else if (type == CellType.NUMERIC) {
      return cell.getNumericCellValue();
    } else {
      throw new RuntimeException("Unexpected numeric cell: " + cell);
    }
  }

  public static List<StockInfo> loadData() throws IOException
  {
    File excel = getPathExcel();
    File csv = getPathCSV();
    if (excel.lastModified() > csv.lastModified()) {
      List<StockInfo> stocks = loadDataFromExcel();
      saveCSV(stocks);
      return stocks;
    } else {
      return loadCSV();
    }
  }

  public static List<StockInfo> loadDataFromExcel() throws IOException
  {
    List<StockInfo> stocks = new ArrayList<>();
    FileInputStream file = new FileInputStream(getPathExcel());
    try (Workbook workbook = new XSSFWorkbook(file)) {
      FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
      Sheet sheet = workbook.getSheet("All CCC");
      int firstCompanyIndex = -1; // index of row with first company on list
      int iSymbol = -1;
      int iSector = -1;
      int iIndustry = -1;
      int iNumYears = -1;
      int iYield = -1;
      int iDividend = -1;
      int iNumPayments = -1;
      int iPayout = -1;
      int iMarketCap = -1;
      for (Row row : sheet) {
        Cell cell = row.getCell(0);
        if (firstCompanyIndex < 0) { // have not verified column labels yet
          String s = cell.getStringCellValue().trim();
          if (s.equals("Name")) {
            int firstLabelIndex = row.getRowNum() - 1;
            if (firstLabelIndex < 0) throw new RuntimeException("Expected two label rows.");
            int secondLabelIndex = firstLabelIndex + 1;
            firstCompanyIndex = secondLabelIndex + 1;
            Row firstLabelRow = sheet.getRow(firstLabelIndex);
            if (!firstLabelRow.getCell(0).getStringCellValue().trim().equals("Company")) {
              throw new RuntimeException("Expected \"Company Name\" in first column.");
            }

            // Find other fields by label.
            for (int i = 1; i <= row.getLastCellNum(); ++i) {
              cell = row.getCell(i);
              if (cell == null || cell.getCellTypeEnum() != CellType.STRING) continue;
              String label2 = cell.getStringCellValue().trim();

              cell = firstLabelRow.getCell(i);
              String label1 = "";
              if (cell != null && cell.getCellTypeEnum() == CellType.STRING) {
                label1 = cell.getStringCellValue().trim();
              }

              if (label2.equals("Symbol")) iSymbol = i;
              else if (label2.equals("Sector")) iSector = i;
              else if (label2.equals("Industry")) iIndustry = i;
              else if (label1.equals("No.") && label2.equals("Yrs")) iNumYears = i;
              else if (label1.equals("Div.") && label2.equals("Yield")) iYield = i;
              else if (label1.equals("Current") && label2.equals("Dividend")) iDividend = i;
              else if (label1.equals("Payouts/") && label2.equals("Year")) iNumPayments = i;
              else if (label1.equals("EPS%") && label2.equals("Payout")) iPayout = i;
              else if (label1.equals("MktCap") && label2.equals("($Mil)")) iMarketCap = i;
            }
          }
        } else {
          if (cell == null || cell.getCellTypeEnum() != CellType.STRING) break;

          String name = cell.getStringCellValue().trim();
          assert !name.contains(",");
          String symbol = row.getCell(iSymbol).getStringCellValue().trim().toUpperCase();

          StockInfo stock = new StockInfo(name, symbol);
          stock.sector = row.getCell(iSector).getStringCellValue().trim();
          stock.industry = row.getCell(iIndustry).getStringCellValue().trim();

          stock.nYearsDivIncrease = (int) Math.round(row.getCell(iNumYears).getNumericCellValue());
          stock.dividendYield = evaluator.evaluateInCell(row.getCell(iYield)).getNumericCellValue();
          stock.dividend = row.getCell(iDividend).getNumericCellValue();
          stock.nDivPaymentsPerYear = (int) Math.round(row.getCell(iNumPayments).getNumericCellValue());
          stock.annualDividend = stock.dividend * stock.nDivPaymentsPerYear;

          cell = row.getCell(iPayout);
          stock.epsPayout = getSafeNumeric(cell, evaluator);
          cell = row.getCell(iMarketCap);
          stock.marketCap = getSafeNumeric(cell, evaluator);
          System.out.println(stock);
          stocks.add(stock);
        }
      }
    }

    return stocks;

  }

  public static void saveCSV(List<StockInfo> stocks) throws IOException
  {
    try (Writer writer = new Writer(getPathCSV())) {
      for (StockInfo stock : stocks) {
        writer.writeln(stock.serializeToString());
      }
    }
  }

  public static List<StockInfo> loadCSV() throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(getPathCSV()))) {
      List<StockInfo> stocks = new ArrayList<>();
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        line = line.trim();
        StockInfo stock = StockInfo.fromString(line);
        if (stock == null) throw new IOException("Bad StockInfo line: " + line);
        stocks.add(stock);
      }
      return stocks;
    }
  }
}
