package org.minnen.retiretool.simba;

public class WinStats
{
  public int nWin, nTie, nLose, nLongestWinStreak, nLongestLoseStreak;

  public WinStats()
  {
    this(0, 0, 0, 0, 0);
  }

  public WinStats(int nWin, int nTie, int nLose, int nLongestWinStreak, int nLongestLoseStreak)
  {
    this.nWin = nWin;
    this.nTie = nTie;
    this.nLose = nLose;
    this.nLongestWinStreak = nLongestWinStreak;
    this.nLongestLoseStreak = nLongestLoseStreak;
  }

  public WinStats add(WinStats stats)
  {
    this.nWin += stats.nWin;
    this.nTie += stats.nTie;
    this.nLose += stats.nLose;
    this.nLongestWinStreak = Math.max(this.nLongestWinStreak, stats.nLongestWinStreak);
    this.nLongestLoseStreak = Math.max(this.nLongestLoseStreak, stats.nLongestLoseStreak);
    return this;
  }

  @Override
  public String toString()
  {
    return String.format("[%d, %d, %d]", nWin, nTie, nLose);
  }

}
