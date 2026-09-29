package com.visualchess.vision;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.visualchess.chess.Board;

/**
 * 棋盘检测：将摄像头帧中「引导框」(4 角，图像坐标) 透视校正为 9×10 网格，
 * 逐格裁剪后交给 PieceRecognizer 识别，输出 int[10][9] 局面。
 *
 * corners 顺序：[x0,y0(左上), x1,y1(右上), x2,y2(左下), x3,y3(右下)]
 */
public class BoardDetector {

    public static final int OUT_W = 270;
    public static final int OUT_H = 300;

    /** 用给定引导框识别整盘局面。 */
    public int[][] recognize(Bitmap frame, float[] corners, PieceRecognizer rec) {
        int[][] board = new int[Board.ROWS][Board.COLS];
        if (frame == null || corners == null) return board;
        Bitmap rect = rectify(frame, corners);
        int cellW = OUT_W / Board.COLS; // 9 列
        int cellH = OUT_H / Board.ROWS; // 10 行
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                int sx = c * cellW + (int) (cellW * 0.16f);
                int sy = r * cellH + (int) (cellH * 0.16f);
                int sw = (int) (cellW * 0.68f);
                int sh = (int) (cellH * 0.68f);
                if (sx + sw > OUT_W) sw = OUT_W - sx;
                if (sy + sh > OUT_H) sh = OUT_H - sy;
                Bitmap cell = Bitmap.createBitmap(rect, sx, sy, sw, sh);
                board[r][c] = rec.recognize(cell);
                cell.recycle();
            }
        }
        rect.recycle();
        return board;
    }

    /** 四点双线性校正：把四边形 corners 映射为 outW×outH 的矩形图。 */
    private Bitmap rectify(Bitmap src, float[] c) {
        int sw = src.getWidth(), sh = src.getHeight();
        int[] spx = new int[sw * sh];
        src.getPixels(spx, 0, sw, 0, 0, sw, sh);
        Bitmap out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888);
        int[] opx = new int[OUT_W * OUT_H];
        float x0 = c[0], y0 = c[1], x1 = c[2], y1 = c[3], x2 = c[4], y2 = c[5], x3 = c[6], y3 = c[7];
        for (int y = 0; y < OUT_H; y++) {
            float v = OUT_H > 1 ? (float) y / (OUT_H - 1) : 0f;
            for (int x = 0; x < OUT_W; x++) {
                float u = OUT_W > 1 ? (float) x / (OUT_W - 1) : 0f;
                float topx = lerp(x0, x1, u), topy = lerp(y0, y1, u);
                float botx = lerp(x2, x3, u), boty = lerp(y2, y3, u);
                float sx = lerp(topx, botx, v), sy = lerp(topy, boty, v);
                int ix = Math.round(sx), iy = Math.round(sy);
                int col;
                if (ix >= 0 && ix < sw && iy >= 0 && iy < sh) col = spx[iy * sw + ix];
                else col = Color.WHITE;
                opx[y * OUT_W + x] = col;
            }
        }
        out.setPixels(opx, 0, OUT_W, 0, 0, OUT_W, OUT_H);
        return out;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
