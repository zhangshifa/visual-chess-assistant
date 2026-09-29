package com.visualchess.vision;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.visualchess.chess.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * 棋子识别：用渲染的汉字模板（红/黑两色共 14 种）对单元格位图做归一化互相关匹配。
 * 同时根据颜色（红/黑）与暗像素占比判定是否有子。
 */
public class PieceRecognizer {

    private static final int TPL = 48;

    private static class Tpl {
        int type;
        int sign;
        float[] data;
    }

    private final List<Tpl> templates = new ArrayList<>();

    public PieceRecognizer() {
        buildTemplates();
    }

    private void buildTemplates() {
        String[] glyphs = {"车", "马", "炮", "相", "士", "帅", "兵"};
        int[] types = {Piece.ROOK, Piece.KNIGHT, Piece.CANNON, Piece.BISHOP, Piece.ADVISOR, Piece.KING, Piece.PAWN};
        for (int i = 0; i < types.length; i++) {
            for (int sign : new int[]{1, -1}) {
                int color = sign > 0 ? Color.rgb(200, 40, 40) : Color.rgb(30, 30, 30);
                Bitmap bmp = Bitmap.createBitmap(TPL, TPL, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                c.drawColor(Color.WHITE);
                Paint p = new Paint();
                p.setColor(color);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(TPL * 0.8f);
                p.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                float y = TPL / 2f - (p.ascent() + p.descent()) / 2f;
                c.drawText(glyphs[i], TPL / 2f, y, p);
                templates.add(makeTpl(types[i], sign, bmp));
                bmp.recycle();
            }
        }
    }

    private Tpl makeTpl(int type, int sign, Bitmap bmp) {
        Tpl t = new Tpl();
        t.type = type;
        t.sign = sign;
        int[] px = new int[TPL * TPL];
        bmp.getPixels(px, 0, TPL, 0,0, TPL, TPL);
        float[] g = new float[TPL * TPL];
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            g[i] = (Color.red(v) * 0.299f + Color.green(v) * 0.587f + Color.blue(v) * 0.114f) / 255f;
        }
        t.data = g;
        return t;
    }

    /** 识别单个单元格位图，返回 0（无子）或 ±type。 */
    public int recognize(Bitmap cell) {
        if (cell == null) return 0;
        int w = cell.getWidth(), h = cell.getHeight();
        if (w < 4 || h < 4) return 0;
        int[] px = new int[w * h];
        cell.getPixels(px, 0, w, 0, 0, w, h);
        // 中心区域统计：暗像素占比 + 颜色
        int x0 = w / 4, y0 = h / 4, x1 = 3 * w / 4, y1 = 3 * h / 4;
        int dark = 0, tot = 0;
        long rSum = 0, gSum = 0, bSum = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int v = px[y * w + x];
                rSum += Color.red(v); gSum += Color.green(v); bSum += Color.blue(v);
                float gg = (Color.red(v) * 0.299f + Color.green(v) * 0.587f + Color.blue(v) * 0.114f) / 255f;
                if (gg < 0.6f) dark++;
                tot++;
            }
        }
        if (tot == 0) return 0;
        float darkRatio = (float) dark / tot;
        if (darkRatio < 0.22f) return 0; // 无明显棋子
        float rr = rSum / (float) tot, gg2 = gSum / (float) tot, bb = bSum / (float) tot;
        int sign = (rr > bb + 15f && rr > gg2) ? 1 : -1;
        // 缩放至模板尺寸后匹配
        Bitmap scaled = Bitmap.createScaledBitmap(cell, TPL, TPL, true);
        int[] spx = new int[TPL * TPL];
        scaled.getPixels(spx, 0, TPL, 0, 0, TPL, TPL);
        float[] sg = new float[TPL * TPL];
        for (int i = 0; i < spx.length; i++) {
            int v = spx[i];
            sg[i] = (Color.red(v) * 0.299f + Color.green(v) * 0.587f + Color.blue(v) * 0.114f) / 255f;
        }
        scaled.recycle();
        float bestNcc = -2f;
        int bestType = 0;
        for (Tpl t : templates) {
            if (t.sign != sign) continue;
            float ncc = ncc(sg, t.data);
            if (ncc > bestNcc) { bestNcc = ncc; bestType = t.type; }
        }
        if (bestType == 0) return 0;
        return sign * bestType;
    }

    private static float ncc(float[] a, float[] b) {
        int n = a.length;
        float ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
        ma /= n; mb /= n;
        float num = 0, da = 0, db = 0;
        for (int i = 0; i < n; i++) {
            float x = a[i] - ma, y = b[i] - mb;
            num += x * y; da += x * x; db += y * y;
        }
        float den = (float) Math.sqrt(da * db);
        return den < 1e-6f ? -1f : num / den;
    }
}
