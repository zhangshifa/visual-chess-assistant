package com.visualchess.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 中国象棋引擎：合法走法生成（含全部特殊规则）、alpha-beta 搜索、静态评估。
 * side：>0 表示红方走子，<0 表示黑方走子。
 */
public class Engine {

    private static final int[] VALUE = {0, 600, 270, 285, 120, 120, 100000, 30};
    private static final int INF = 1_000_000;

    // ---------------- 走法生成 ----------------

    /** 生成伪合法走法（未校验是否送将）。 */
    public List<Move> generatePseudo(int[][] b, int side) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                if (Piece.isRed(p) != (side > 0)) continue;
                addMoves(b, r, c, p, side, moves);
            }
        }
        return moves;
    }

    private void addMoves(int[][] b, int r, int c, int p, int side, List<Move> moves) {
        switch (Piece.type(p)) {
            case Piece.ROOK:    genRook(b, r, c, p, moves); break;
            case Piece.KNIGHT:  genKnight(b, r, c, p, moves); break;
            case Piece.CANNON:  genCannon(b, r, c, p, moves); break;
            case Piece.BISHOP:  genBishop(b, r, c, p, side, moves); break;
            case Piece.ADVISOR: genAdvisor(b, r, c, p, side, moves); break;
            case Piece.KING:    genKing(b, r, c, p, side, moves); break;
            case Piece.PAWN:    genPawn(b, r, c, p, side, moves); break;
        }
    }

    private void genRook(int[][] b, int r, int c, int p, List<Move> moves) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (Board.inBounds(nr, nc)) {
                int q = b[nr][nc];
                if (q == 0) {
                    moves.add(new Move(r, c, nr, nc, p, 0));
                } else {
                    if (Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    private void genKnight(int[][] b, int r, int c, int p, List<Move> moves) {
        int[][] jumps = {{-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}};
        for (int[] j : jumps) {
            int nr = r + j[0], nc = c + j[1];
            if (!Board.inBounds(nr, nc)) continue;
            // 蹩马腿：横向跳时检查纵向邻格，纵向跳时检查横向邻格
            int lr = (Math.abs(j[0]) == 2) ? r + j[0] / 2 : r;
            int lc = (Math.abs(j[1]) == 2) ? c + j[1] / 2 : c;
            if (b[lr][lc] != 0) continue;
            int q = b[nr][nc];
            if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
        }
    }

    private void genCannon(int[][] b, int r, int c, int p, List<Move> moves) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            boolean screen = false;
            while (Board.inBounds(nr, nc)) {
                int q = b[nr][nc];
                if (!screen) {
                    if (q == 0) {
                        moves.add(new Move(r, c, nr, nc, p, 0));
                    } else {
                        screen = true; // 翻山：越过第一个棋子后才能吃子
                    }
                } else {
                    if (q != 0) {
                        if (Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
                        break;
                    }
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    private void genBishop(int[][] b, int r, int c, int p, int side, List<Move> moves) {
        int[][] diag = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] d : diag) {
            int nr = r + d[0], nc = c + d[1];
            if (!Board.inBounds(nr, nc)) continue;
            // 不可过河
            if (side > 0 && nr < 5) continue;
            if (side < 0 && nr > 4) continue;
            // 塞象眼
            if (b[r + d[0] / 2][c + d[1] / 2] != 0) continue;
            int q = b[nr][nc];
            if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
        }
    }

    private void genAdvisor(int[][] b, int r, int c, int p, int side, List<Move> moves) {
        int[][] diag = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] d : diag) {
            int nr = r + d[0], nc = c + d[1];
            if (!Board.inPalace(side, nr, nc)) continue;
            int q = b[nr][nc];
            if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
        }
    }

    private void genKing(int[][] b, int r, int c, int p, int side, List<Move> moves) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (!Board.inPalace(side, nr, nc)) continue;
            int q = b[nr][nc];
            if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, nc, p, q));
        }
        // 飞将（白脸将）：同列且中间无子时，可直接「吃」掉对方将/帅
        int step = (side > 0) ? -1 : 1;
        int nr = r + step;
        while (Board.inBounds(nr, c)) {
            int q = b[nr][c];
            if (q != 0) {
                if (Piece.type(q) == Piece.KING && Piece.isRed(q) != (side > 0))
                    moves.add(new Move(r, c, nr, c, p, q));
                break;
            }
            nr += step;
        }
    }

    private void genPawn(int[][] b, int r, int c, int p, int side, List<Move> moves) {
        int fwd = (side > 0) ? -1 : 1;
        int nr = r + fwd;
        if (Board.inBounds(nr, c)) {
            int q = b[nr][c];
            if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, nr, c, p, q));
        }
        // 过河后可横走
        if (Board.crossedRiver(side, r)) {
            for (int dc : new int[]{-1, 1}) {
                int nc = c + dc;
                if (Board.inBounds(r, nc)) {
                    int q = b[r][nc];
                    if (q == 0 || Piece.isRed(q) != Piece.isRed(p)) moves.add(new Move(r, c, r, nc, p, q));
                }
            }
        }
    }

    // ---------------- 攻防判定 ----------------

    /** 红帅与黑将是否照面（同列且中间无子）。 */
    public boolean kingsFacing(int[][] b) {
        int kc = -1;
        for (int c = 0; c < Board.COLS; c++) {
            Integer red = null, black = null;
            for (int r = 0; r < Board.ROWS; r++) {
                int p = b[r][c];
                if (Piece.type(p) == Piece.KING) {
                    if (Piece.isRed(p)) red = r; else black = r;
                }
            }
            if (red != null && black != null) {
                int lo = Math.min(red, black), hi = Math.max(red, black);
                boolean clear = true;
                for (int r = lo + 1; r < hi; r++) if (b[r][c] != 0) { clear = false; break; }
                if (clear) return true;
            }
        }
        return false;
    }

    /** 判断 (r,c) 是否被 bySide 一方攻击。 */
    public boolean squareAttacked(int[][] b, int r, int c, int bySide) {
        for (int rr = 0; rr < Board.ROWS; rr++) {
            for (int cc = 0; cc < Board.COLS; cc++) {
                int p = b[rr][cc];
                if (p == 0) continue;
                if (Piece.isRed(p) != (bySide > 0)) continue;
                if (pieceAttacks(b, rr, cc, p, r, c)) return true;
            }
        }
        return false;
    }

    /** 位于 (r,c)、类型为 p 的棋子能否攻击到 (tr,tc)。 */
    public boolean pieceAttacks(int[][] b, int r, int c, int p, int tr, int tc) {
        int t = Piece.type(p);
        int dr = tr - r, dc = tc - c;
        switch (t) {
            case Piece.ROOK: {
                if (r != tr && c != tc) return false;
                return betweenCount(b, r, c, tr, tc) == 0;
            }
            case Piece.KNIGHT: {
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                int lr = (Math.abs(dr) == 2) ? r + dr / 2 : r;
                int lc = (Math.abs(dc) == 2) ? c + dc / 2 : c;
                return b[lr][lc] == 0;
            }
            case Piece.CANNON: {
                if (r != tr && c != tc) return false;
                return betweenCount(b, r, c, tr, tc) == 1 && b[tr][tc] != 0;
            }
            case Piece.BISHOP: {
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                int side = Piece.isRed(p) ? 1 : -1;
                if (side > 0 && tr < 5) return false;
                if (side < 0 && tr > 4) return false;
                return b[r + dr / 2][c + dc / 2] == 0;
            }
            case Piece.ADVISOR: {
                if (Math.abs(dr) != 1 || Math.abs(dc) != 1) return false;
                return Board.inPalace(Piece.isRed(p) ? 1 : -1, tr, tc);
            }
            case Piece.KING: {
                if (Math.abs(dr) + Math.abs(dc) != 1) return false;
                return Board.inPalace(Piece.isRed(p) ? 1 : -1, tr, tc);
            }
            case Piece.PAWN: {
                int side = Piece.isRed(p) ? 1 : -1;
                int fwd = (side > 0) ? -1 : 1;
                if (tr == r + fwd && tc == c) return true;
                if (Board.crossedRiver(side, r) && tr == r && Math.abs(tc - c) == 1) return true;
                return false;
            }
        }
        return false;
    }

    private int betweenCount(int[][] b, int r, int c, int tr, int tc) {
        int cnt = 0;
        if (r == tr) {
            int step = (tc > c) ? 1 : -1;
            for (int cc = c + step; cc != tc; cc += step) if (b[r][cc] != 0) cnt++;
        } else if (c == tc) {
            int step = (tr > r) ? 1 : -1;
            for (int rr = r + step; rr != tr; rr += step) if (b[rr][c] != 0) cnt++;
        }
        return cnt;
    }

    /** side 一方是否处于被将军状态（含飞将）。 */
    public boolean isInCheck(int[][] b, int side) {
        int[] k = findKing(b, side);
        if (k == null) return false;
        int opp = -side;
        if (squareAttacked(b, k[0], k[1], opp)) return true;
        return kingsFacing(b);
    }

    private int[] findKing(int[][] b, int side) {
        for (int r = 0; r < Board.ROWS; r++)
            for (int c = 0; c < Board.COLS; c++) {
                int p = b[r][c];
                if (Piece.type(p) == Piece.KING && (Piece.isRed(p) == (side > 0))) return new int[]{r, c};
            }
        return null;
    }

    /** 生成全部合法走法（过滤送将 / 飞将）。 */
    public List<Move> legalMoves(int[][] b, int side) {
        List<Move> pseudo = generatePseudo(b, side);
        List<Move> legal = new ArrayList<>();
        for (Move m : pseudo) {
            int[][] nb = clone(b);
            make(nb, m);
            if (!isInCheck(nb, side)) legal.add(m);
        }
        return legal;
    }

    // ---------------- 评估与搜索 ----------------

    /** 评估局面，返回 side 一方视角的分值（正值有利）。 */
    public int eval(int[][] b, int side) {
        int score = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                int t = Piece.type(p);
                int v = VALUE[t];
                if (t == Piece.PAWN) {
                    v += (p > 0) ? (9 - r) * 8 : r * 8; // 兵/卒 越靠前越值钱
                } else {
                    v += (4 - Math.abs(c - 4)) * 2;       // 轻微中心化
                }
                score += (p > 0) ? v : -v;
            }
        }
        return side > 0 ? score : -score;
    }

    private int negamax(int[][] b, int side, int depth, int alpha, int beta) {
        if (depth == 0) return eval(b, side);
        List<Move> moves = legalMoves(b, side);
        if (moves.isEmpty()) {
            return isInCheck(b, side) ? -INF + (100 - depth) : 0; // 被将死为负，困毙为和
        }
        orderMoves(moves);
        int best = -INF;
        for (Move m : moves) {
            int[][] nb = clone(b);
            make(nb, m);
            int val = -negamax(nb, -side, depth - 1, -beta, -alpha);
            if (val > best) best = val;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    private void orderMoves(List<Move> moves) {
        Collections.sort(moves, new Comparator<Move>() {
            public int compare(Move a, Move b) {
                int ka = a.captured != 0 ? 1000 + VALUE[Piece.type(a.captured)] - VALUE[Piece.type(a.moved)] / 10 : 0;
                int kb = b.captured != 0 ? 1000 + VALUE[Piece.type(b.captured)] - VALUE[Piece.type(b.moved)] / 10 : 0;
                return kb - ka;
            }
        });
    }

    public static class SearchResult {
        public Move move;
        public int score;
        public List<Move> pv = new ArrayList<>();
        public boolean noMoves;
        public boolean isCheckmate;
    }

    /** 在 depth 层内为 side 寻找最佳着法，并返回评分与主要变例。 */
    public SearchResult analyze(int[][] b, int side, int depth) {
        SearchResult res = new SearchResult();
        List<Move> moves = legalMoves(b, side);
        if (moves.isEmpty()) {
            res.noMoves = true;
            res.isCheckmate = isInCheck(b, side);
            res.score = res.isCheckmate ? -INF : 0;
            return res;
        }
        orderMoves(moves);
        int alpha = -INF, beta = INF, best = -INF;
        Move bestM = null;
        for (Move m : moves) {
            int[][] nb = clone(b);
            make(nb, m);
            int val = -negamax(nb, -side, depth - 1, -beta, -alpha);
            if (val > best) { best = val; bestM = m; }
            if (best > alpha) alpha = best;
        }
        res.move = bestM;
        res.score = best;
        // 主要变例（最多 3 步）
        int[][] b1 = clone(b);
        make(b1, bestM);
        res.pv.add(bestM);
        Move r1 = bestMove(b1, -side, Math.max(1, depth - 1));
        if (r1 != null) {
            res.pv.add(r1);
            int[][] b2 = clone(b1);
            make(b2, r1);
            Move r2 = bestMove(b2, side, Math.max(1, depth - 2));
            if (r2 != null) res.pv.add(r2);
        }
        return res;
    }

    /** 返回 side 的最佳着法（无合法着法返回 null）。 */
    public Move bestMove(int[][] b, int side, int depth) {
        SearchResult r = analyze(b, side, depth);
        return r.noMoves ? null : r.move;
    }

    // ---------------- 工具 ----------------

    public static int[][] clone(int[][] b) {
        int[][] nb = new int[Board.ROWS][Board.COLS];
        for (int r = 0; r < Board.ROWS; r++) System.arraycopy(b[r], 0, nb[r], 0, Board.COLS);
        return nb;
    }

    public static void make(int[][] b, Move m) {
        b[m.tr][m.tc] = m.moved;
        b[m.fr][m.fc] = 0;
    }
}
