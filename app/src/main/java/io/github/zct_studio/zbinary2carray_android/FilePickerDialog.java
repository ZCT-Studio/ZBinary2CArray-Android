package io.github.zct_studio.zbinary2carray_android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import android.content.res.ColorStateList;
import java.io.File;
import java.util.*;

/**
 * Custom file/folder picker — zero Android SAF dependency.
 * <p>
 * File mode:   tap file = pick, tap folder = enter.
 * Folder mode: tap folder = enter, bottom "Select" button picks current folder.
 */
public class FilePickerDialog extends Dialog {

    public interface OnPathPickedListener { void onPathPicked(String path); }

    private final boolean mFolderMode;
    private final String   mInitialPath;
    private final OnPathPickedListener mListener;

    private EditText    mPathEdit;
    private LinearLayout mFileList;
    private ScrollView  mScrollView;
    private String      mCurrentPath;
    private TextView    mTitle;
    private MaterialButton mSelectFolderBtn;
    private FrameLayout mRootFrame;

    public FilePickerDialog(Context context, boolean folderMode,
                            String initialPath,
                            OnPathPickedListener listener) {
        super(context);
        mFolderMode  = folderMode;
        mInitialPath = initialPath;
        mListener    = listener;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(createLayout());
        Window w = getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawableResource(android.R.color.transparent);
            // Allow drawing behind system bars
            WindowCompat.setDecorFitsSystemWindows(w, false);
            // Ensure dialog receives window insets
            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            w.addFlags(flags);
        }
    }

    // ── Layout ────────────────────────────────────────────────────

    private View createLayout() {
        mRootFrame = new FrameLayout(getContext());
        mRootFrame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRootFrame.setBackgroundColor(MaterialColors.getColor(getContext(),
                android.R.attr.colorBackground, android.graphics.Color.WHITE));

        // Apply WindowInsets so content clears status/nav bars
        ViewCompat.setOnApplyWindowInsetsListener(mRootFrame, (v, insets) -> {
            int left   = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            int top    = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int right  = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            mRootFrame.setPadding(left, top, right, bottom);
            return insets;
        });

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(2), dp(8), dp(2));

        // ── Top app bar ──
        LinearLayout topBar = buildTopBar();
        root.addView(topBar);

        // ── Path bar ──
        LinearLayout pathRow = buildPathRow();
        root.addView(pathRow);

        // ── New folder button (folder mode only) ──
        if (mFolderMode) {
            MaterialButton nfBtn = new MaterialButton(getContext());
            nfBtn.setText("＋ 新建文件夹");
            nfBtn.setAllCaps(false);
            nfBtn.setTextSize(14);
            nfBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
            nfBtn.setCornerRadius(dp(8));
            nfBtn.setStrokeWidth(dp(1));
            nfBtn.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorOutline, 0x33000000)));
            nfBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            nfBtn.setTextColor(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorPrimary, 0xFF0078D4));
            nfBtn.setOnClickListener(v -> showNewFolderDialog());
            LinearLayout.LayoutParams nfLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nfLp.setMargins(0, dp(8), 0, 0);
            nfBtn.setLayoutParams(nfLp);
            root.addView(nfBtn);
        }

        // ── File list ──
        mScrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.setMargins(0, dp(8), 0, dp(8));
        mScrollView.setLayoutParams(svLp);

        LinearLayout listCard = new LinearLayout(getContext());
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setBackground(createListBg());
        listCard.setPadding(0, dp(2), 0, dp(2));

        mFileList = new LinearLayout(getContext());
        mFileList.setOrientation(LinearLayout.VERTICAL);
        listCard.addView(mFileList);
        mScrollView.addView(listCard);
        root.addView(mScrollView);

        // ── Bottom action bar ──
        LinearLayout bottomBar = new LinearLayout(getContext());
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.END);
        bottomBar.setPadding(0, dp(8), 0, dp(4));

        MaterialButton cancelBtn = new MaterialButton(getContext());
        cancelBtn.setText("取消");
        cancelBtn.setAllCaps(false);
        cancelBtn.setTextSize(14);
        cancelBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        cancelBtn.setCornerRadius(dp(8));
        cancelBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        cancelBtn.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
        cancelBtn.setOnClickListener(v -> dismiss());

        bottomBar.addView(cancelBtn);

        if (mFolderMode) {
            mSelectFolderBtn = new MaterialButton(getContext());
            mSelectFolderBtn.setText("选择此文件夹");
            mSelectFolderBtn.setAllCaps(false);
            mSelectFolderBtn.setTextSize(14);
            mSelectFolderBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
            mSelectFolderBtn.setCornerRadius(dp(8));
            mSelectFolderBtn.setBackgroundColor(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorPrimary, 0xFF0078D4));
            mSelectFolderBtn.setTextColor(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorOnPrimary, android.graphics.Color.WHITE));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMargins(dp(8), 0, 0, 0);
            mSelectFolderBtn.setLayoutParams(slp);
            mSelectFolderBtn.setOnClickListener(v -> {
                mListener.onPathPicked(mCurrentPath);
                dismiss();
            });
            bottomBar.addView(mSelectFolderBtn);
        } else {
            TextView hint = new TextView(getContext());
            hint.setText("点击列表中的文件即可选择");
            hint.setTextSize(13);
            hint.setTextColor(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            hint.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            hintLp.setMargins(dp(8), 0, 0, 0);
            hint.setLayoutParams(hintLp);
            bottomBar.addView(hint);
        }
        root.addView(bottomBar);

        mRootFrame.addView(root);

        navigateTo(mInitialPath != null ? mInitialPath
                : Environment.getExternalStorageDirectory().getAbsolutePath());
        return mRootFrame;
    }

    private LinearLayout buildTopBar() {
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(8), 0, dp(8));

        // Back / close button
        MaterialButton backBtn = new MaterialButton(getContext());
        backBtn.setText("\u2190"); // ←
        backBtn.setAllCaps(false);
        backBtn.setTextSize(18);
        backBtn.setMinWidth(0); backBtn.setMinimumWidth(0);
        backBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        backBtn.setCornerRadius(dp(20));
        backBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        backBtn.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        backBtn.setOnClickListener(v -> {
            File p = new File(mCurrentPath).getParentFile();
            if (p != null) navigateTo(p.getAbsolutePath());
            else dismiss();
        });
        top.addView(backBtn);

        mTitle = new TextView(getContext());
        mTitle.setText(mFolderMode ? "选择输出文件夹" : "选择输入文件");
        mTitle.setTextSize(20);
        mTitle.setTypeface(null, Typeface.BOLD);
        mTitle.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMargins(dp(8), 0, 0, 0);
        mTitle.setLayoutParams(tLp);
        top.addView(mTitle);

        return top;
    }

    private LinearLayout buildPathRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Up button
        MaterialButton upBtn = new MaterialButton(getContext());
        upBtn.setText("←");
        upBtn.setAllCaps(false);
        upBtn.setTextSize(14);
        upBtn.setMinWidth(0); upBtn.setMinimumWidth(0);
        upBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        upBtn.setCornerRadius(dp(8));
        upBtn.setStrokeWidth(dp(1));
        upBtn.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOutline, 0x33000000)));
        upBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        upBtn.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        upBtn.setOnClickListener(v -> navigateUp());
        row.addView(upBtn);

        mPathEdit = new EditText(getContext());
        mPathEdit.setSingleLine(true);
        mPathEdit.setTextSize(13);
        mPathEdit.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        mPathEdit.setHintTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000));
        mPathEdit.setHint("存储路径");
        mPathEdit.setBackground(createEditBg());
        mPathEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams peLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        peLp.setMargins(dp(8), 0, 0, 0);
        mPathEdit.setLayoutParams(peLp);
        mPathEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                File f = new File(s.toString());
                if (f.isDirectory() && !f.getAbsolutePath().equals(mCurrentPath)) {
                    mCurrentPath = f.getAbsolutePath();
                    refreshList();
                }
            }
        });
        row.addView(mPathEdit);
        return row;
    }

    // ── Navigation ────────────────────────────────────────────────

    private void navigateTo(String path) {
        File f = new File(path);
        if (!f.isDirectory()) f = Environment.getExternalStorageDirectory();
        mCurrentPath = f.getAbsolutePath();
        mPathEdit.setText(mCurrentPath);
        mPathEdit.setSelection(mCurrentPath.length());
        refreshList();
    }

    private void navigateUp() {
        File p = new File(mCurrentPath).getParentFile();
        if (p != null) navigateTo(p.getAbsolutePath());
    }

    private void refreshList() {
        mFileList.removeAllViews();
        File[] children = new File(mCurrentPath).listFiles();
        if (children == null || children.length == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("此文件夹为空，或暂时无法访问");
            empty.setTextSize(14);
            empty.setTextColor(MaterialColors.getColor(getContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000));
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            mFileList.addView(empty);
            return;
        }
        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File child : children) mFileList.addView(createRow(child));
    }

    private View createRow(File file) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        String icon = file.isDirectory() ? "📁" : "📄";
        TextView tv = new TextView(getContext());
        tv.setText(icon + "  " + file.getName());
        tv.setTextSize(15);
        tv.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        row.addView(tv);

        // Trailing arrow/indicator
        TextView trail = new TextView(getContext());
        trail.setText(file.isDirectory() ? "\u203A" : ""); // ›
        trail.setTextSize(18);
        trail.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000));
        row.addView(trail);

        if (file.isDirectory()) {
            row.setOnClickListener(v -> navigateTo(file.getAbsolutePath()));
        } else {
            // File mode: tap file = pick
            row.setOnClickListener(v -> {
                mListener.onPathPicked(file.getAbsolutePath());
                dismiss();
            });
        }
        return row;
    }

    private void showNewFolderDialog() {
        final EditText et = new EditText(getContext());
        et.setHint("文件夹名称");
        et.setTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        et.setHintTextColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000));
        et.setBackground(createEditBg());
        et.setPadding(dp(12), dp(10), dp(12), dp(10));

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("新建文件夹")
                .setView(et)
                .setPositiveButton("创建", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) {
                        File nd = new File(mCurrentPath, n);
                        if (nd.mkdirs()) refreshList();
                        else Toast.makeText(getContext(), "创建失败，请检查文件夹名称或存储权限", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    // ── Styling ───────────────────────────────────────────────────

    private android.graphics.drawable.Drawable createEditBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorSurfaceVariant, 0x1A000000));
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOutline, 0x26000000));
        return gd;
    }

    private android.graphics.drawable.Drawable createListBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE));
        gd.setCornerRadius(dp(10));
        gd.setStroke(dp(1), MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOutlineVariant, 0x26000000));
        return gd;
    }

    private int dp(int px) {
        return (int)(px * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}



