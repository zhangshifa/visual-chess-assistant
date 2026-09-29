package com.visualchess.chess;

/** 一步棋：从 (fr,fc) 到 (tr,tc)，moved 为移动棋子（含符号），captured 为被吃子（0 表示无）。 */
public class Move {
    public final int fr, fc, tr, tc;
    public final int moved;
    public final int captured;

    public Move(int fr, int fc, int tr, int tc, int moved, int captured) {
        this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
        this.moved = moved; this.captured = captured;
    }

    public boolean isCapture() { return captured != 0; }

    /** 中文记谱（如「炮二平五」的简化版：起点->终点 + 子）。 */
    public String toNotation() {
        return Piece.fullName(moved) + " (" + (fc + 1) + "," + (fr + 1) + ")→(" + (tc + 1) + "," + (tr + 1) + ")";
    }

    @Override public String toString() {
        return toNotation();
    }
}
