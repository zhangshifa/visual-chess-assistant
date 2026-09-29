package com.visualchess.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.visualchess.chess.Board;
import com.visualchess.chess.Engine;
import com.visualchess.chess.Move;
import com.visualchess.chess.Piece;
import com.visualchess.ui.BoardOverlayView;
import com.visualchess.vision.BoardDetector;
import com.visualchess.vision.PieceRecognizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SEARCH_DEPTH = 4;
    private static final int SIM_DEPTH = 3;
    private static final String TAG = "VisualChess";

    private TextureView cameraView;
    private BoardOverlayView overlay;
    private TextView tvStatus, tvEval, tvMoves;
    private Button btnManual;

    private final Engine engine = new Engine();
    private final PieceRecognizer recognizer = new PieceRecognizer();
    private final BoardDetector detector = new BoardDetector();

    private int[][] board;
    private int sideToMove = 1;            // 1=红先
    private final List<Move> moveList = new ArrayList<>();
    private boolean manualMode = false;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private String cameraId;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.cameraView);
        overlay = findViewById(R.id.overlay);
        tvStatus = findViewById(R.id.tvStatus);
        tvEval = findViewById(R.id.tvEval);
        tvMoves = findViewById(R.id.tvMoves);

        board = new Board().grid; // 初始局面

        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (!manualMode) return false;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    int[] cell = overlay.pointToCell(e.getX(), e.getY());
                    if (cell != null) cyclePiece(cell[0], cell[1]);
                    return true;
                }
                return false;
            }
        });

        bindButton(R.id.btnDetect, new View.OnClickListener() { public void onClick(View v) { doRecognize(); } });
        bindButton(R.id.btnSwitchSide, new View.OnClickListener() { public void onClick(View v) { switchSide(); } });
        bindButton(R.id.btnAIMove, new View.OnClickListener() { public void onClick(View v) { doAIMove(); } });
        bindButton(R.id.btnSimulate, new View.OnClickListener() { public void onClick(View v) { doSimulate(); } });
        bindButton(R.id.btnAnalyze, new View.OnClickListener() { public void onClick(View v) { doAnalyze(); } });
        bindButton(R.id.btnReset, new View.OnClickListener() { public void onClick(View v) { doReset(); } });
        btnManual = findViewById(R.id.btnManual);
        btnManual.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                manualMode = !manualMode;
                btnManual.setText(manualMode ? "完成摆子" : "手动摆子");
                status(manualMode ? "手动摆子：点击格子循环切换棋子（红→黑→空）" : "已退出手动摆子");
            }
        });

        cameraView.setSurfaceTextureListener(surfaceListener);

        bgThread = new HandlerThread("camera-bg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void bindButton(int id, View.OnClickListener l) {
        findViewById(id).setOnClickListener(l);
    }

    // ---------------- 相机 ----------------

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { openCamera(); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { setupCorners(); }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return false; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
    };

    private void openCamera() {
        CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (cm == null) return;
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { cameraId = id; break; }
            }
            if (cameraId == null) cameraId = cm.getCameraIdList()[0];
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                return;
            }
            cm.openCamera(cameraId, stateCallback, bgHandler);
        } catch (CameraAccessException e) {
            status("相机打开失败：" + e.getMessage());
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(@NonNull CameraDevice cam) { cameraDevice = cam; createPreview(); }
        @Override public void onDisconnected(@NonNull CameraDevice cam) { cam.close(); cameraDevice = null; }
        @Override public void onError(@NonNull CameraDevice cam, int error) { cam.close(); cameraDevice = null; }
    };

    private void createPreview() {
        SurfaceTexture st = cameraView.getSurfaceTexture();
        if (st == null || cameraDevice == null) return;
        st.setDefaultBufferSize(1280, 720);
        Surface surface = new Surface(st);
        try {
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(@NonNull CameraCaptureSession s) {
                    captureSession = s;
                    setupCorners();
                    try { s.setRepeatingRequest(previewBuilder.build(), null, bgHandler); }
                    catch (CameraAccessException e) { Log.e(TAG, "preview", e); }
                }
                @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {}
            }, bgHandler);
        } catch (CameraAccessException e) {
            status("预览创建失败：" + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) openCamera();
        else if (req == 1) Toast.makeText(this, "需要相机权限才能识别棋盘", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraView.isAvailable()) openCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (captureSession != null) { captureSession.close(); captureSession = null; }
        if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        bgThread.quitSafely();
    }

    private void setupCorners() {
        int W = overlay.getWidth(), H = overlay.getHeight();
        if (W <= 0 || H <= 0) return;
        float cw = Math.min(W * 0.92f, H * 0.92f * 9f / 10f);
        float ch = cw * 10f / 9f;
        float left = (W - cw) / 2f, top = (H - ch) / 2f;
        overlay.setCorners(new float[]{left, top, left + cw, top, left, top + ch, left + cw, top + ch});
    }

    // ---------------- 业务功能 ----------------

    private void runBg(Runnable r) { executor.execute(r); }
    private void runUi(Runnable r) { runOnUiThread(r); }

    private void doRecognize() {
        runBg(new Runnable() {
            @Override public void run() {
                final Bitmap fb = cameraView.getBitmap();
                if (fb == null) { runUi(new Runnable() { public void run() { status("无法获取画面，请确认相机已开启"); } }); return; }
                float[] sc = scaleCorners(fb);
                int[][] b = detector.recognize(fb, sc, recognizer);
                fb.recycle();
                board = b; sideToMove = 1; moveList.clear();
                runUi(new Runnable() {
                    public void run() {
                        overlay.setBoard(board);
                        overlay.setLastMove(null);
                        overlay.setSuggestion(null);
                        updateEval();
                        tvMoves.setText("已识别局面。可「分析局面」「AI 走子」或「模拟对局」。\n");
                        status("识别完成（若有误，可「手动摆子」修正）");
                    }
                });
            }
        });
    }

    private float[] scaleCorners(Bitmap fb) {
        float[] c = overlay.getCorners();
        float sx = fb.getWidth() / (float) overlay.getWidth();
        float sy = fb.getHeight() / (float) overlay.getHeight();
        return new float[]{c[0] * sx, c[1] * sy, c[2] * sx, c[3] * sy, c[4] * sx, c[5] * sy, c[6] * sx, c[7] * sy};
    }

    private void doAIMove() {
        runBg(new Runnable() {
            public void run() {
                final Engine.SearchResult r = engine.analyze(board, sideToMove, SEARCH_DEPTH);
                if (r.noMoves) { runUi(new Runnable() { public void run() { status(r.isCheckmate ? "将死，无着法" : "困毙/无合法着法"); } }); return; }
                final Move m = r.move;
                board = Engine.clone(board); Engine.make(board, m);
                final int score = r.score;
                runUi(new Runnable() {
                    public void run() {
                        overlay.setBoard(board);
                        overlay.setLastMove(m);
                        overlay.setSuggestion(null);
                        moveList.add(m);
                        appendMove(m);
                        sideToMove = -sideToMove;
                        updateEval();
                        status("AI 着法：" + m.toNotation() + "  评分 " + score);
                    }
                });
            }
        });
    }

    private void doAnalyze() {
        runBg(new Runnable() {
            public void run() {
                final Engine.SearchResult r = engine.analyze(board, sideToMove, SEARCH_DEPTH);
                if (r.noMoves) { runUi(new Runnable() { public void run() { status(r.isCheckmate ? "将死，无着法" : "无合法着法"); } }); return; }
                final StringBuilder sb = new StringBuilder("分析：最佳着法 ").append(r.move.toNotation()).append("  评分 ").append(r.score).append("\n主要变例：");
                for (Move m : r.pv) sb.append(" ").append(m.toNotation());
                runUi(new Runnable() {
                    public void run() {
                        overlay.setSuggestion(r.move);
                        status(sb.toString());
                    }
                });
            }
        });
    }

    private void doSimulate() {
        runBg(new Runnable() {
            public void run() {
                int cur = sideToMove;
                int steps = 0;
                while (steps < 40) {
                    Engine.SearchResult r = engine.analyze(board, cur, SIM_DEPTH);
                    if (r.noMoves) { runUi(new Runnable() { public void run() { status(r.isCheckmate ? "模拟结束：将死" : "模拟结束：无着法"); } }); break; }
                    final Move m = r.move;
                    board = Engine.clone(board); Engine.make(board, m);
                    cur = -cur; steps++;
                    runUi(new Runnable() {
                        public void run() {
                            overlay.setBoard(board);
                            overlay.setLastMove(m);
                            moveList.add(m);
                            appendMove(m);
                        }
                    });
                    if (r.isCheckmate) { runUi(new Runnable() { public void run() { status("模拟结束：将死，共 " + steps + " 步"); } }); break; }
                    try { Thread.sleep(350); } catch (InterruptedException e) { break; }
                }
                sideToMove = cur;
                runUi(new Runnable() { public void run() { updateEval(); status("模拟对局结束，共 " + steps + " 步"); } });
            }
        });
    }

    private void switchSide() {
        int[][] nb = new int[Board.ROWS][Board.COLS];
        for (int r = 0; r < Board.ROWS; r++)
            for (int c = 0; c < Board.COLS; c++)
                nb[r][c] = board[Board.ROWS - 1 - r][Board.COLS - 1 - c];
        board = nb;
        sideToMove = -sideToMove;
        overlay.setBoard(board);
        overlay.setLastMove(null);
        overlay.setSuggestion(null);
        updateEval();
        status("已切换视角（红/黑翻转）");
    }

    private void doReset() {
        board = new Board().grid;
        sideToMove = 1;
        moveList.clear();
        overlay.setBoard(board);
        overlay.setLastMove(null);
        overlay.setSuggestion(null);
        tvMoves.setText("着法记录：（点击「识别棋盘」从摄像头读取局面；或「手动摆子」自行摆放）");
        updateEval();
        status("已重置为初始局面");
    }

    private void cyclePiece(int r, int c) {
        final int[] CYCLE = {0, Piece.ROOK, Piece.KNIGHT, Piece.CANNON, Piece.BISHOP, Piece.ADVISOR, Piece.KING, Piece.PAWN,
                -Piece.ROOK, -Piece.KNIGHT, -Piece.CANNON, -Piece.BISHOP, -Piece.ADVISOR, -Piece.KING, -Piece.PAWN};
        int cur = board[r][c];
        int idx = 0;
        for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == cur) { idx = i; break; }
        board[r][c] = CYCLE[(idx + 1) % CYCLE.length];
        overlay.setBoard(board);
        updateEval();
    }

    // ---------------- UI 辅助 ----------------

    private void status(String s) { if (tvStatus != null) tvStatus.setText(s); }

    private void updateEval() {
        int sc = engine.eval(board, sideToMove);
        tvEval.setText("评分 " + sc + (sideToMove > 0 ? " · 红走" : " · 黑走"));
    }

    private void appendMove(Move m) {
        String txt = tvMoves.getText().toString();
        tvMoves.setText(txt + "\n" + moveList.size() + ". " + m.toNotation());
    }
}
