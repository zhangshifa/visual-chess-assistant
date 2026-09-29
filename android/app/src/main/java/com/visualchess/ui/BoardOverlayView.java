package com.visualchess.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.visualchess.chess.Board;
import com.visualchess.chess.Move;
import com.visualchess.chess.Piece;

/** 在摄像头预览之上绘制：引导框 / 9×10 网格 / 识别到的棋子 / 上一步箭头 / AI 建议。 */
public class BoardOverlayView extends View {

    private Paint gridPaint, redPaint, blackPaint, arrowPaint, sugPaint;
    private float[] corners = null;          // [x0,y0,x1,y1,x2,y2,x3,y3]
    private int[][] board = null;
    private Move lastMove = null;
    private Move suggestion = null;

    public BoardOverlayView(Context ctx) { super(ctx); init(); }
    public BoardOverlayView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#FFD54F"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setAlpha(200);

        redPaint = new Paint();
        redPaint.setColor(Color.parseColor("#E53935"));
        redPaint.setTextAlign(Paint.Align.CENTER);
        redPaint.setTextSize(34f);
        redPaint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));

        blackPaint = new Paint();
        blackPaint.setColor(Color.parseColor("#212121"));
        blackPaint.setTextAlign(Paint.Align.CENTER);
        blackPaint.setTextSize(34f);
        blackPaint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));

        arrowPaint = new Paint();
        arrowPaint.setColor(Color.parseColor("#43A047"));
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(6f);

        sugPaint = new Paint();
        sugPaint.setColor(Color.parseColor("#1E88E5"));
        sugPaint.setStyle(Paint.Style.STROKE);
        sugPaint.setStrokeWidth(5f);
        sugPaint.setAlpha(220);
    }

    public void setCorners(float[] c) { this.corners = c; invalidate(); }
    public float[] getCorners() { return corners; }
    public void setBoard(int[][] b) { this.board = b; invalidate(); }
    public void setLastMove(Move m) { this.lastMove = m; invalidate(); }
    public void setSuggestion(Move m) { this.suggestion = m; invalidate(); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private float[] cellCenter(int r, int c) {
        float u = (c + 0.5f) / Board.COLS;
        float v = (r + 0.5f) / Board.ROWS;
        float topx = lerp(corners[0], corners[2], u), topy = lerp(corners[1], corners[3], u);
        float botx = lerp(corners[4], corners[6], u), boty = lerp(corners[5], corners[7], u);
        return new float[]{ lerp(topx, botx, v), lerp(topy, boty, v) };
    }

    /** 将视图坐标反算为棋盘格 (r,c)。仅当 corners 为轴对齐矩形时准确（本应用即如此）。 */
    public int[] pointToCell(float x, float y) {
        if (corners == null) return null;
        float left = corners[0], right = corners[2], top = corners[1], bottom = corners[5];
        float u = (x - left) / (right - left);
        float v = (y - top) / (bottom - top);
        int c = (int) (u * Board.COLS);
        int r = (int) (v * Board.ROWS);
        if (r < 0 || r >= Board.ROWS || c < 0 || c >= Board.COLS) return null;
        return new int[]{ r, c };
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (corners == null) return;

        Path path = new Path();
        path.moveTo(corners[0], corners[1]);
        path.lineTo(corners[2], corners[3]);
        path.lineTo(corners[6], corners[7]);
        path.lineTo(corners[4], corners[5]);
        path.close();
        canvas.drawPath(path, gridPaint);

        // 网格：10 条竖线、11 条横线
        for (int j = 0; j <= Board.COLS; j++) {
            float u = (float) j / Board.COLS;
            float tx = lerp(corners[0], corners[2], u), ty = lerp(corners[1], corners[3], u);
            float bx = lerp(corners[4], corners[6], u), by = lerp(corners[5], corners[7], u);
            canvas.drawLine(tx, ty, bx, by, gridPaint);
        }
        for (int i = 0; i <= Board.ROWS; i++) {
            float v = (float) i / Board.ROWS;
            float lx = lerp(corners[0], corners[4], v), ly = lerp(corners[1], corners[5], v);
            float rx = lerp(corners[2], corners[6], v), ry = lerp(corners[3], corners[7], v);
            canvas.drawLine(lx, ly, rx, ry, gridPaint);
        }

        // 棋子
        if (board != null) {
            Paint bg = new Paint();
            bg.setStyle(Paint.Style.FILL);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    int p = board[r][c];
                    if (p == 0) continue;
                    float[] ctr = cellCenter(r, c);
                    bg.setColor(Piece.isRed(p) ? Color.argb(70, 229, 57, 53) : Color.argb(70, 33, 33, 33));
                    canvas.drawCircle(ctr[0], ctr[1], 26f, bg);
                    Paint tp = Piece.isRed(p) ? redPaint : blackPaint;
                    canvas.drawText(Piece.glyph(p), ctr[0], ctr[1] - (tp.ascent() + tp.descent()) / 2f, tp);
                }
            }
        }

        // 建议着法：高亮起止格
        if (suggestion != null) {
            drawCellBox(canvas, suggestion.fr, suggestion.fc);
            drawCellBox(canvas, suggestion.tr, suggestion.tc);
        }

        // 上一步箭头
        if (lastMove != null) {
            drawArrow(canvas, cellCenter(lastMove.fr, lastMove.fc), cellCenter(lastMove.tr, lastMove.tc));
        }
    }

    private void drawCellBox(Canvas canvas, int r, int c) {
        float u0 = (float) c / Board.COLS, u1 = (float) (c + 1) / Board.COLS;
        float v0 = (float) r / Board.ROWS, v1 = (float) (r + 1) / Board.ROWS;
        float x0 = lerp(lerp(corners[0], corners[2], u0), lerp(corners[4], corners[6], u0), v0);
        float y0 = lerp(lerp(corners[1], corners[3], u0), lerp(corners[5], corners[7], u0), v0);
        float x1 = lerp(lerp(corners[0], corners[2], u1), lerp(corners[4], corners[6], u1), v1);
        float y1 = lerp(lerp(corners[1], corners[3], u1), lerp(corners[5], corners[7], u1), v1);
        canvas.drawRect(x0, y0, x1, y1, sugPaint);
    }

    private void drawArrow(Canvas canvas, float[] a, float[] b) {
        canvas.drawLine(a[0], a[1], b[0], b[1], arrowPaint);
        double ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
        float len = 18f;
        float ax = (float) (b[0] - len * Math.cos(ang - 0.4));
        float ay = (float) (b[1] - len * Math.sin(ang - 0.4));
        float bx = (float) (b[0] - len * Math.cos(ang + 0.4));
        float by = (float) (b[1] - len * Math.sin(ang + 0.4));
        canvas.drawLine(b[0], b[1], ax, ay, arrowPaint);
        canvas.drawLine(b[0], b[1], bx, by, arrowPaint);
    }
}
