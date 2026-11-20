package com.focusforge.quest.quest;

/**
 * The game economy. Rewards are a pure function of difficulty and are decided
 * here — never taken from AI output — so a manipulated prompt cannot mint coins.
 */
public enum Difficulty {
    //        coins  xp  hp
    MICRO(5, 10, 2),
    EASY(10, 25, 5),
    MEDIUM(20, 50, 10),
    HARD(35, 80, 15);

    private final int coins;
    private final int xp;
    private final int hpRestore;

    Difficulty(int coins, int xp, int hpRestore) {
        this.coins = coins;
        this.xp = xp;
        this.hpRestore = hpRestore;
    }

    public int coins() {
        return coins;
    }

    public int xp() {
        return xp;
    }

    public int hpRestore() {
        return hpRestore;
    }
}
