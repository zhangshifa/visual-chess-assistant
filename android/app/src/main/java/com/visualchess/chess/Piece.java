package com.visualchess.chess;

/** 棋子常量与基础工具（红方为正、黑方为负）。 */
public final class Piece {
    public static final int EMPTY = 0;
    public static final int ROOK = 1;    // 车 / 車
    public static final int KNIGHT = 2;  // 马 / 馬
    public static final int CANNON = 3;  // 炮 / 砲
    public static final int BISHOP = 4;  // 相 / 象
    public static final int ADVISOR = 5; // 仕 / 士
    public static final int KING = 6;    // 帅 / 将
    public static final int PAWN = 7;    // 兵 / 卒

    public static int type(int p) { return Math.abs(p); }
    public static boolean isRed(int p) { return p > 0; }
    public static boolean isBlack(int p) { return p < 0; }
    public static boolean isEmpty(int p) { return p == EMPTY; }

    /** 通用字形（红黑共用简化字），用于棋盘绘制。 */
    public static String glyph(int p) {
        switch (type(p)) {
            case ROOK:    return "车";
            case KNIGHT:  return "马";
            case CANNON:  return "炮";
            case BISHOP:  return "相";
            case ADVISOR: return "士";
            case KING:    return "帅";
            case PAWN:    return "兵";
            default:      return "·";
        }
    }

    /** 完整名称（红黑区分）。 */
    public static String fullName(int p) {
        if (p == EMPTY) return "空";
        boolean red = isRed(p);
        switch (type(p)) {
            case ROOK:    return red ? "车" : "車";
            case KNIGHT:  return red ? "马" : "馬";
            case CANNON:  return red ? "炮" : "砲";
            case BISHOP:  return red ? "相" : "象";
            case ADVISOR: return red ? "仕" : "士";
            case KING:    return red ? "帅" : "将";
            case PAWN:    return red ? "兵" : "卒";
            default:      return "?";
        }
    }
}
