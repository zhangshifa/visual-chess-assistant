package com.visualchess.chess;

import java.util.Arrays;

/** 中国象棋棋盘：10 行 × 9 列。row 0 为黑方底线，row 9 为红方底线。 */
public class Board {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    public int[][] grid = new int[ROWS][COLS];

    public Board() { reset(); }

    public void reset() {
        int[][] init = {
            {-Piece.ROOK, -Piece.KNIGHT, -Piece.BISHOP, -Piece.ADVISOR, -Piece.KING, -Piece.ADVISOR, -Piece.BISHOP, -Piece.KNIGHT, -Piece.ROOK},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, -Piece.CANNON, 0, 0, 0, 0, 0, -Piece.CANNON, 0},
            {-Piece.PAWN, 0, -Piece.PAWN, 0, -Piece.PAWN, 0, -Piece.PAWN, 0, -Piece.PAWN},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {Piece.PAWN, 0, Piece.PAWN, 0, Piece.PAWN, 0, Piece.PAWN, 0, Piece.PAWN},
            {0, Piece.CANNON, 0, 0, 0, 0, 0, Piece.CANNON, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.ADVISOR, Piece.KING, Piece.ADVISOR, Piece.BISHOP, Piece.KNIGHT, Piece.ROOK}
        };
        for (int r = 0; r < ROWS; r++) grid[r] = Arrays.copyOf(init[r], COLS);
    }

    public Board clone() {
        Board b = new Board();
        for (int r = 0; r < ROWS; r++) b.grid[r] = Arrays.copyOf(grid[r], COLS);
        return b;
    }

    public int get(int r, int c) { return grid[r][c]; }
    public void set(int r, int c, int v) { grid[r][c] = v; }

    public static boolean inBounds(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS; }

    /** 红方（side>0）向上走，过河即 row<=4；黑方（side<0）向下走，过河即 row>=5。 */
    public static boolean crossedRiver(int side, int r) {
        return side > 0 ? r <= 4 : r >= 5;
    }

    /** 将/帅所在宫格范围。 */
    public static boolean inPalace(int side, int r, int c) {
        if (c < 3 || c > 5) return false;
        return side > 0 ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }
}
