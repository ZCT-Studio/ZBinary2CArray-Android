package io.github.zct_studio.zbinary2carray_android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import android.content.res.ColorStateList;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {

    private String  mInputPath    = "";
    private String  mOutputDir    = "";
    private boolean mIsTabletLayout;
    private String  mSettingsPath;
    private boolean mBuilt = false;
    private boolean mWaitingForStoragePermission = false;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Spinner    mLangSpinner;

    private EditText   mInputPathEdit;
    private Button     mBrowseInputBtn;
    private EditText   mOutputDirEdit;
    private Button     mBrowseDirBtn;
    private EditText   mOutputStemEdit;
    private TextView   mFilenameInvalidHint;

    private Button     mConvertBtn;
    private ProgressBar mProgressBar;
    private TextView   mStatusText;

    private MaterialCardView mResultCard;
    private TextView   mResultTitle, mResultMessage;
    private Button     mOpenOutputBtn;

    private RadioGroup mTypeRadio;
    private Switch     mModeSwitch;
    private TextView   mModeExplain;
    private Switch     mIncGuardSwitch, mTidySwitch;
    private LinearLayout mStorageWrap, mConstWrap;
    private EditText   mNumsPerLineEdit;
    private Switch     mAnnotToolSwitch, mAnnotRunnerSwitch;
    private EditText   mAnnotToolNameEdit, mAnnotRunnerNameEdit;

    private TextView   mTitleView;
    private LinearLayout mLeftCol, mRightCol;
    private FrameLayout mAppBarSpacer;

    private ActivityResultLauncher<String> mPermLauncher;


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarAppearance();

        mSettingsPath = getFilesDir().getAbsolutePath() + "/settings.json";

        mPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (!granted) showPermDialog(); else afterPermGranted(); });

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        String sysLang = detectSystemLanguage();
        boolean firstRun = loadSettings(sysLang);
        loadLocales();
        NativeBridge.nativeSetLanguage(getStoredLang());

        if (firstRun) {
            showFirstRunLangDialog();
        } else {
            checkPermAndBuild();
        }
    }

    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mIsTabletLayout = isWide();
        if (mBuilt) rebuildUI();
    }

    @Override protected void onResume() {
        super.onResume();
        applySystemBarAppearance();
        if (mWaitingForStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager()) {
            mWaitingForStoragePermission = false;
            if (!mBuilt) afterPermGranted();
        }
    }

    private String detectSystemLanguage() {
        Locale loc = Locale.getDefault();
        String tag = loc.toLanguageTag();
        if (tag.startsWith("zh-Hant") || tag.equals("zh-TW") || tag.equals("zh-HK"))
            return "zh-TW";
        if (tag.startsWith("zh")) return "zh-CN";
        return "en-US";
    }

    private String getStoredLang() {
        try {
            String json = NativeBridge.nativeLoadSettings(mSettingsPath);
            return new org.json.JSONObject(json).optString("language", detectSystemLanguage());
        } catch (Exception e) { return detectSystemLanguage(); }
    }

    private boolean loadSettings(String sysLang) {
        try {
            String json = NativeBridge.nativeLoadSettings(mSettingsPath);
            org.json.JSONObject jo = new org.json.JSONObject(json);
            boolean first = jo.optBoolean("first_run", true);
            String lang = jo.optString("language", sysLang);
            NativeBridge.nativeSetLanguage(lang);
            return first;
        } catch (Exception e) {
            NativeBridge.nativeSetLanguage(sysLang);
            return true;
        }
    }

    private void loadLocales() {
        for (String tag : new String[]{"en-US", "zh-CN", "zh-TW"}) {
            try {
                InputStream is = getAssets().open("lang/" + tag + ".json");
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                NativeBridge.nativeLoadLanguage(tag, sb.toString());
            } catch (Exception e) { /* skip */ }
        }
    }

    private void checkPermAndBuild() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                mWaitingForStoragePermission = true;
                showPermDialog();
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                mPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }
        afterPermGranted();
    }

    private void afterPermGranted() {
        if (mBuilt) return;
        mWaitingForStoragePermission = false;
        mIsTabletLayout = isWide();
        buildUI();
        mBuilt = true;
        refreshAllText();
    }

    private void showPermDialog() {
        new AlertDialog.Builder(this)
            .setTitle(tr("perm.title"))
            .setMessage(tr("perm.message"))
            .setPositiveButton(tr("perm.settings"), (d, w) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                } else {
                    mPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            })
            .setNegativeButton(tr("common.cancel"), (d, w) -> finish())
            .setCancelable(false).show();
    }

    private void showFirstRunLangDialog() {
        final String[] tags  = {"en-US", "zh-CN", "zh-TW"};
        final String[] names = {"English", "\u7B80\u4F53\u4E2D\u6587", "\u7E41\u9AD4\u4E2D\u6587"};
        String cur = getStoredLang();
        int sel = 0;
        for (int i = 0; i < tags.length; i++) { if (tags[i].equals(cur)) { sel = i; break; } }

        new AlertDialog.Builder(this)
            .setTitle("\uD83C\uDF10 " + tr("first_run.language_title"))
            .setSingleChoiceItems(names, sel, null)
            .setPositiveButton(tr("common.ok"), (d, w) -> {
                int idx = ((AlertDialog) d).getListView().getCheckedItemPosition();
                if (idx >= 0 && idx < tags.length) {
                    NativeBridge.nativeSetLanguage(tags[idx]);
                    saveSettings(tags[idx]);
                    refreshAllText();
                }
                checkPermAndBuild();
            })
            .setCancelable(false).show();
    }

    private void saveSettings(String lang) {
        try {
            org.json.JSONObject jo = new org.json.JSONObject();
            jo.put("language", lang);
            jo.put("first_run", false);
            NativeBridge.nativeSaveSettings(mSettingsPath, jo.toString());
        } catch (Exception e) { /* ignore */ }
    }

    private void rebuildUI() {
        UiState state = captureUiState();
        ViewGroup root = findViewById(android.R.id.content);
        root.removeAllViews();
        mBuilt = false;
        buildUI();
        mBuilt = true;
        restoreUiState(state);
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground,
                android.graphics.Color.WHITE));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int left   = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            int top    = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int right  = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            sv.setPadding(left, top, right, bottom);
            return insets;
        });

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(20));

        LinearLayout top = buildTopBar();
        body.addView(top);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(mIsTabletLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cols.setLayoutParams(clp);

        mLeftCol = new LinearLayout(this); mLeftCol.setOrientation(LinearLayout.VERTICAL);
        mRightCol = new LinearLayout(this); mRightCol.setOrientation(LinearLayout.VERTICAL);
        if (mIsTabletLayout) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mLeftCol.setLayoutParams(llp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rlp.setMargins(dp(12), 0, 0, 0);
            mRightCol.setLayoutParams(rlp);
        } else {
            mLeftCol.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(12), 0, 0);
            mRightCol.setLayoutParams(rlp);
        }
        cols.addView(mLeftCol); cols.addView(mRightCol);
        body.addView(cols);

        addInputCard(mLeftCol);
        addOutputCard(mLeftCol);
        addConvertCard(mLeftCol);
        addOptionsCard(mRightCol);

        body.addView(buildFooter());

        sv.addView(body);
        root.addView(sv);
        setContentView(root);
    }

    private LinearLayout buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(16), dp(14), dp(16));
        top.setBackground(createHeroBg());
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.setMargins(0, 0, 0, dp(16));
        top.setLayoutParams(topLp);


        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        mTitleView = new TextView(this);
        mTitleView.setText("ZBinary2CArray");
        mTitleView.setTextSize(20);
        mTitleView.setTypeface(null, Typeface.BOLD);
        mTitleView.setTextColor(android.graphics.Color.WHITE);
        titleCol.addView(mTitleView);

        TextView subTitle = new TextView(this);
        subTitle.setText("- Android");
        subTitle.setTextSize(13);
        subTitle.setTextColor(0xD9FFFFFF);
        subTitle.setPadding(0, 0, 0, dp(2));
        titleCol.addView(subTitle);

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(tLp);
        titleCol.setClickable(true);
        titleCol.setFocusable(true);
        titleCol.setOnClickListener(v -> openExternalUrl("https://github.com/ZCT-Studio/ZBinary2CArray-Android"));
        top.addView(titleCol);

        TextView langIcon = new TextView(this);
        langIcon.setText("\uD83C\uDF10"); // 🌐
        langIcon.setTextSize(16);
        langIcon.setPadding(0, 0, dp(4), 0);
        top.addView(langIcon);

        mLangSpinner = new Spinner(this);
        String[] langTags = {"en-US", "zh-CN", "zh-TW"};
        ArrayAdapter<String> lad = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, langTags) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(android.graphics.Color.WHITE);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(MaterialColors.getColor(MainActivity.this,
                        com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK));
                view.setPadding(dp(16), dp(12), dp(24), dp(12));
                return view;
            }
        };
        mLangSpinner.setAdapter(lad);
        mLangSpinner.setPopupBackgroundDrawable(createSpinnerPopupBg());
        String cur = NativeBridge.nativeGetCurrentLanguage();
        for (int i = 0; i < langTags.length; i++)
            if (langTags[i].equals(cur)) { mLangSpinner.setSelection(i); break; }
        mLangSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (first) { first = false; return; }
                String tag = (String) p.getItemAtPosition(pos);
                NativeBridge.nativeSetLanguage(tag);
                saveSettings(tag);
                rebuildUI();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        top.addView(mLangSpinner);

        return top;
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE));
        c.setCardElevation(dp(1));
        c.setRadius(dp(18));
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x26000000)));
        c.setContentPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        c.setLayoutParams(lp);
        return c;
    }

    private void addInputCard(LinearLayout parent) {
        MaterialCardView c = card();
        LinearLayout body = cardContent(c);

        body.addView(secTitle("input.group"));

        mInputPathEdit = new EditText(this); mInputPathEdit.setEnabled(false);
        mInputPathEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mInputPathEdit.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mInputPathEdit.setHint(tr("input.placeholder"));
        mInputPathEdit.setBackground(createEditBg());
        mInputPathEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        mInputPathEdit.setTextSize(14);

        mBrowseInputBtn = materialOutlinedBtn(tr("input.browse"));
        mBrowseInputBtn.setOnClickListener(v -> pickFile());

        LinearLayout row = hrow();
        row.addView(wrapInFrame(mInputPathEdit, 1f));
        row.addView(mBrowseInputBtn);
        row.setPadding(0, dp(8), 0, 0);
        body.addView(row);

        parent.addView(c);
    }

    private void addOutputCard(LinearLayout parent) {
        MaterialCardView c = card();
        LinearLayout body = cardContent(c);

        body.addView(secTitle("output.group"));

        LinearLayout dr = hrow();
        dr.setGravity(Gravity.BOTTOM);
        LinearLayout dirWrap = new LinearLayout(this);
        dirWrap.setOrientation(LinearLayout.VERTICAL);
        dirWrap.addView(lbl("output.dir_label"));
        mOutputDirEdit = new EditText(this); mOutputDirEdit.setEnabled(false);
        mOutputDirEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mOutputDirEdit.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mOutputDirEdit.setHint(tr("output.dir_placeholder"));
        mOutputDirEdit.setBackground(createEditBg());
        mOutputDirEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        mOutputDirEdit.setTextSize(14);
        dirWrap.addView(mOutputDirEdit);
        LinearLayout.LayoutParams dirLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dirWrap.setLayoutParams(dirLp);
        dr.addView(dirWrap);

        mBrowseDirBtn = materialOutlinedBtn(tr("output.dir_browse"));
        mBrowseDirBtn.setText("...");
        mBrowseDirBtn.setOnClickListener(v -> pickFolder());
        ((LinearLayout.LayoutParams) dirWrap.getLayoutParams()).setMargins(0, 0, dp(8), 0);
        dr.addView(mBrowseDirBtn);
        body.addView(dr);

        body.addView(space(12));
        mOutputStemEdit = new EditText(this);
        mOutputStemEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mOutputStemEdit.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mOutputStemEdit.setHint(tr("output.filename_placeholder"));
        mOutputStemEdit.setBackground(createEditBg());
        mOutputStemEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        mOutputStemEdit.setTextSize(14);
        mOutputStemEdit.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { validateStem(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        LinearLayout stemWrap = new LinearLayout(this);
        stemWrap.setOrientation(LinearLayout.VERTICAL);
        stemWrap.addView(lbl("output.filename_label"));
        stemWrap.addView(mOutputStemEdit);
        body.addView(stemWrap);

        mFilenameInvalidHint = new TextView(this);
        mFilenameInvalidHint.setTextSize(11);
        mFilenameInvalidHint.setTextColor(MaterialColors.getColor(this, android.R.attr.colorError, 0xFFE81123));
        mFilenameInvalidHint.setVisibility(View.GONE);
        mFilenameInvalidHint.setText(tr("output.filename_invalid"));
        body.addView(mFilenameInvalidHint);

        parent.addView(c);
    }

    private void addConvertCard(LinearLayout parent) {
        MaterialCardView c = card();
        LinearLayout body = cardContent(c);

        mConvertBtn = materialFilledBtn(tr("convert.button"));
        mConvertBtn.setOnClickListener(v -> doConvert());

        mProgressBar = new ProgressBar(this);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pblp.setMargins(dp(16), 0, 0, 0);
        mProgressBar.setLayoutParams(pblp);

        LinearLayout row = hrow();
        row.addView(mConvertBtn);
        row.addView(mProgressBar);
        body.addView(row);

        mStatusText = new TextView(this);
        mStatusText.setTextSize(13);
        mStatusText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mStatusText.setVisibility(View.GONE);
        body.addView(mStatusText);

        parent.addView(c);
    }

    private void addResultCard(LinearLayout parent) {
        mResultCard = new MaterialCardView(this);
        mResultCard.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE));
        mResultCard.setCardElevation(dp(2));
        mResultCard.setRadius(dp(10));
        mResultCard.setStrokeWidth(dp(1));
        mResultCard.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant,
                0x26000000)));
        mResultCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(8));
        mResultCard.setLayoutParams(rlp);

        LinearLayout body = cardContent(mResultCard);

        mResultTitle = new TextView(this);
        mResultTitle.setTextSize(16); mResultTitle.setTypeface(null, Typeface.BOLD);
        body.addView(mResultTitle);

        mResultMessage = new TextView(this);
        mResultMessage.setTextSize(13);
        mResultMessage.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mResultMessage.setPadding(0, dp(6), 0, dp(12));
        body.addView(mResultMessage);

        mOpenOutputBtn = materialTonalBtn(tr("result.open_in_explorer"));
        mOpenOutputBtn.setVisibility(View.GONE);
        mOpenOutputBtn.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(mOutputDir))); } catch (Exception e) {}
        });
        body.addView(mOpenOutputBtn);
        parent.addView(mResultCard);
    }

    private void addOptionsCard(LinearLayout parent) {
        MaterialCardView c = card();
        LinearLayout body = cardContent(c);

        body.addView(secTitle("options.group"));

        body.addView(lbl("options.type_label"));
        mTypeRadio = new RadioGroup(this);
        mTypeRadio.setOrientation(LinearLayout.VERTICAL);
        mTypeRadio.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        String[] types = {"options.type_u8","options.type_u16","options.type_u32","options.type_u64"};
        for (int i = 0; i < types.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(tr(types[i]));
            rb.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                    android.graphics.Color.WHITE));
            rb.setMinHeight(dp(40)); rb.setMinimumHeight(dp(40));
            rb.setPadding(dp(4), 0, dp(12), 0);
            RadioGroup.LayoutParams typeLp = new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            typeLp.setMargins(0, 0, 0, dp(3));
            rb.setLayoutParams(typeLp);
            mTypeRadio.addView(rb);
        }
        ((RadioButton) mTypeRadio.getChildAt(0)).setChecked(true);
        body.addView(mTypeRadio);

        body.addView(space(8));
        LinearLayout modeWrap = new LinearLayout(this); modeWrap.setOrientation(LinearLayout.VERTICAL);
        mModeSwitch = new Switch(this);
        mModeSwitch.setChecked(true);
        LinearLayout modeRow = hrow(); modeRow.addView(mModeSwitch);
        TextView modeLabel = new TextView(this); modeLabel.setText(tr("options.mode_label"));
        modeLabel.setTextSize(14); modeLabel.setPadding(dp(8), 0, 0, 0);
        modeLabel.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE));
        modeRow.addView(modeLabel); modeWrap.addView(modeRow);
        mModeExplain = new TextView(this);
        mModeExplain.setTextSize(12);
        mModeExplain.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0x99000000));
        mModeExplain.setPadding(dp(48), 0, 0, 0);
        mModeSwitch.setOnCheckedChangeListener((btn, on) -> mModeExplain.setText(
                tr("options.current_mode") + tr(on ? "options.mode_header_only" : "options.mode_source")));
        mModeExplain.setText(tr("options.current_mode") + tr("options.mode_header_only"));
        modeWrap.addView(mModeExplain);
        body.addView(modeWrap);

        body.addView(space(4));
        LinearLayout swRow = hrow();
        mIncGuardSwitch = switchWithLabel(swRow, "options.inc_guard", true);
        mTidySwitch     = switchWithLabel(swRow, "options.tidy", true);
        body.addView(swRow);

        body.addView(space(8));
        LinearLayout comboRow = hrow();
        mStorageWrap = spinnerWrap("options.storage_label", new String[]{"none", "static", "inline"});
        comboRow.addView(mStorageWrap);
        comboRow.addView(spaceH(dp(12)));
        mConstWrap = spinnerWrap("options.const_label", new String[]{"none", "const", "constexpr"});
        comboRow.addView(mConstWrap);
        body.addView(comboRow);

        body.addView(space(8));
        LinearLayout nplWrap = new LinearLayout(this);
        nplWrap.setOrientation(LinearLayout.VERTICAL);
        nplWrap.addView(lbl("options.nums_per_line_label"));
        mNumsPerLineEdit = new EditText(this); mNumsPerLineEdit.setText("0");
        mNumsPerLineEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        mNumsPerLineEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mNumsPerLineEdit.setBackground(createEditBg());
        mNumsPerLineEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        nplWrap.addView(mNumsPerLineEdit);
        body.addView(nplWrap);

        body.addView(space(16));
        TextView annTitle = new TextView(this);
        annTitle.setText(tr("options.annotation_group"));
        annTitle.setTextSize(15); annTitle.setTypeface(null, Typeface.BOLD);
        annTitle.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        annTitle.setPadding(0, 0, 0, dp(8));
        body.addView(annTitle);

        LinearLayout toolRow = hrow();
        mAnnotToolSwitch = new Switch(this); mAnnotToolSwitch.setChecked(true);
        toolRow.addView(mAnnotToolSwitch);
        TextView tlbl = new TextView(this);
        tlbl.setText(tr("options.annot_tool"));
        tlbl.setTextSize(14);
        tlbl.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        tlbl.setPadding(dp(8), 0, 0, 0);
        toolRow.addView(tlbl);
        body.addView(toolRow);
        mAnnotToolNameEdit = new EditText(this);
        mAnnotToolNameEdit.setText("ZBinary2CArray-Android");
        mAnnotToolNameEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mAnnotToolNameEdit.setBackground(createEditBg());
        mAnnotToolNameEdit.setHint(tr("options.annot_toolname_placeholder"));
        mAnnotToolNameEdit.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mAnnotToolNameEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        mAnnotToolSwitch.setOnCheckedChangeListener((btn, on) ->
                mAnnotToolNameEdit.setEnabled(on));
        body.addView(mAnnotToolNameEdit);

        body.addView(space(8));
        LinearLayout runRow = hrow();
        mAnnotRunnerSwitch = new Switch(this); mAnnotRunnerSwitch.setChecked(true);
        runRow.addView(mAnnotRunnerSwitch);
        TextView rlbl = new TextView(this);
        rlbl.setText(tr("options.annot_runner"));
        rlbl.setTextSize(14);
        rlbl.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        rlbl.setPadding(dp(8), 0, 0, 0);
        runRow.addView(rlbl);
        body.addView(runRow);
        mAnnotRunnerNameEdit = new EditText(this);
        mAnnotRunnerNameEdit.setText(getDeviceModelName());
        mAnnotRunnerNameEdit.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        mAnnotRunnerNameEdit.setBackground(createEditBg());
        mAnnotRunnerNameEdit.setHint(tr("options.annot_runnername_placeholder"));
        mAnnotRunnerNameEdit.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        mAnnotRunnerNameEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        mAnnotRunnerSwitch.setOnCheckedChangeListener((btn, on) ->
                mAnnotRunnerNameEdit.setEnabled(on));
        body.addView(mAnnotRunnerNameEdit);

        parent.addView(c);
    }

    private LinearLayout cardContent(MaterialCardView c) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        c.addView(body);
        return body;
    }

    private FrameLayout wrapInFrame(View v, float weight) {
        FrameLayout fl = new FrameLayout(this);
        fl.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(0, 0, dp(8), 0);
        fl.setLayoutParams(lp);
        return fl;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView secTitle(String key) {
        TextView tv = new TextView(this);
        tv.setText(tr(key));
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        tv.setPadding(0, 0, 0, dp(10));
        return tv;
    }

    private TextView lbl(String key) {
        TextView tv = new TextView(this);
        tv.setText(tr(key)); tv.setTextSize(13);
        tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        tv.setPadding(0, 0, 0, dp(4));
        return tv;
    }

    private Button materialFilledBtn(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        b.setCornerRadius(dp(14));
        b.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                0xFF0078D4));
        b.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary,
                android.graphics.Color.WHITE));
        return b;
    }

    private Button materialOutlinedBtn(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setCornerRadius(dp(12));
        b.setStrokeWidth(dp(1));
        b.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0x33000000)));
        b.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        b.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                0xFF0078D4));
        return b;
    }

    private Button materialTonalBtn(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setCornerRadius(dp(6));
        b.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer,
                0x1A0078D4));
        b.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer,
                0xFF0078D4));
        return b;
    }

    private View buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(4), 0, dp(8));
        MaterialButton telegram = new MaterialButton(this);
        telegram.setText("Telegram");
        telegram.setAllCaps(false);
        telegram.setTextSize(14);
        telegram.setIconResource(R.drawable.ic_telegram);
        telegram.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary, 0xFF0078D4)));
        telegram.setIconPadding(dp(8));
        telegram.setPadding(dp(16), dp(8), dp(16), dp(8));
        telegram.setCornerRadius(dp(14));
        telegram.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        telegram.setTextColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary, 0xFF0078D4));
        telegram.setOnClickListener(v -> openExternalUrl("https://t.me/ZCT_Studio"));
        footer.addView(telegram);
        return footer;
    }

    private Switch switchWithLabel(LinearLayout parent, String key, boolean def) {
        LinearLayout w = new LinearLayout(this); w.setOrientation(LinearLayout.VERTICAL);
        w.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(this); s.setChecked(def);
        LinearLayout row = hrow();
        row.addView(s);
        TextView l = new TextView(this);
        l.setText(tr(key)); l.setTextSize(13);
        l.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.WHITE));
        l.setPadding(dp(8), 0, 0, 0);
        row.addView(l);
        w.addView(row);
        parent.addView(w);
        return s;
    }

    private LinearLayout spinnerWrap(String key, String[] items) {
        LinearLayout w = new LinearLayout(this); w.setOrientation(LinearLayout.VERTICAL);
        w.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView l = new TextView(this);
        l.setText(tr(key)); l.setTextSize(12);
        l.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                0x99000000));
        l.setPadding(0, 0, 0, dp(4));
        w.addView(l);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        w.addView(sp);
        return w;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
        return v;
    }

    private android.graphics.drawable.Drawable createHeroBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF155EEF, 0xFF4F46E5});
        gd.setCornerRadius(dp(22));
        return gd;
    }

    private android.graphics.drawable.Drawable createSpinnerPopupBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE));
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant, 0x26000000));
        return gd;
    }

    private String getDeviceModelName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        return model.isEmpty() ? "Android" : model;
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, tr("errors.open_link_failed"), Toast.LENGTH_SHORT).show();
        }
    }

    private View spaceH(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 0));
        return v;
    }

    private void pickFile() {
        String init = mInputPath.isEmpty()
                ? Environment.getExternalStorageDirectory().getAbsolutePath() : mInputPath;
        new FilePickerDialog(this, false, init, path -> {
            mInputPath = path; mInputPathEdit.setText(path);
            if (mOutputStemEdit.getText().toString().isEmpty()) {
                String stem = new File(path).getName();
                int dot = stem.lastIndexOf('.');
                if (dot > 0) stem = stem.substring(0, dot);
                mOutputStemEdit.setText(stem);
            }
        }).show();
    }

    private void pickFolder() {
        String init = mOutputDir.isEmpty()
                ? Environment.getExternalStorageDirectory().getAbsolutePath() : mOutputDir;
        new FilePickerDialog(this, true, init, path -> {
            mOutputDir = path; mOutputDirEdit.setText(path);
        }).show();
    }

    private void doConvert() {
        String in  = mInputPathEdit.getText().toString().trim();
        String out = mOutputDirEdit.getText().toString().trim();
        String stem = mOutputStemEdit.getText().toString().trim();

        if (in.isEmpty())  { showRes(false, tr("errors.no_input")); return; }
        if (out.isEmpty()) { showRes(false, tr("errors.no_output_dir")); return; }
        if (stem.isEmpty()){ showRes(false, tr("errors.no_filename")); return; }
        if (!validateStem()){ showRes(false, tr("errors.invalid_filename")); return; }

        String cfg = buildCfgJson();
        setBusy(true);

        mExecutor.execute(() -> {
            String s = NativeBridge.nativeConvert(in, out, stem, cfg);
            mHandler.post(() -> {
                setBusy(false);
                try {
                    org.json.JSONObject jo = new org.json.JSONObject(s);
                    boolean ok = jo.optBoolean("ok");
                    if (ok && jo.has("outputDir"))
                        mOutputDir = jo.optString("outputDir");
                    showRes(ok, jo.optString("message"));
                } catch (Exception e) {
                    showRes(false, tr("errors.unexpected") + ": " + e.getMessage());
                }
            });
        });
    }

    private boolean validateStem() {
        String s = mOutputStemEdit.getText().toString();
        boolean ok = !s.isEmpty();
        for (char c : s.toCharArray()) {
            if (c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0) ok = false;
        }
        if (!s.isEmpty() && (s.endsWith(".") || s.endsWith(" "))) ok = false;
        mFilenameInvalidHint.setVisibility(ok ? View.GONE : View.VISIBLE);
        return ok;
    }

    private String buildCfgJson() {
        try {
            org.json.JSONObject cfg = new org.json.JSONObject();
            int ti = -1;
            RadioButton checked = mTypeRadio.findViewById(mTypeRadio.getCheckedRadioButtonId());
            if (checked != null) ti = mTypeRadio.indexOfChild(checked);
            if (ti < 0) ti = 0;
            cfg.put("type", ti);
            cfg.put("headerOnly", mModeSwitch.isChecked());
            cfg.put("incGuard", mIncGuardSwitch.isChecked());
            cfg.put("moreTidy", mTidySwitch.isChecked());
            cfg.put("storage",
                    ((Spinner)((LinearLayout)mStorageWrap).getChildAt(1)).getSelectedItemPosition());
            cfg.put("constSpec",
                    ((Spinner)((LinearLayout)mConstWrap).getChildAt(1)).getSelectedItemPosition());
            try { cfg.put("numsPerLine",
                    Integer.parseInt(mNumsPerLineEdit.getText().toString().trim()));
            } catch (NumberFormatException e) { cfg.put("numsPerLine", 0); }
            org.json.JSONObject ann = new org.json.JSONObject();
            ann.put("tool", mAnnotToolSwitch.isChecked());
            ann.put("runner", mAnnotRunnerSwitch.isChecked());
            ann.put("toolName", mAnnotToolNameEdit.getText().toString().trim());
            ann.put("runnerName", mAnnotRunnerNameEdit.getText().toString().trim());
            cfg.put("annotation", ann);
            return cfg.toString();
        } catch (Exception e) { return "{}"; }
    }

    private void setBusy(boolean busy) {
        mConvertBtn.setEnabled(!busy);
        mConvertBtn.setAlpha(busy ? 0.6f : 1.0f);
        mProgressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        mStatusText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) mStatusText.setText(tr("convert.in_progress"));
    }

    private void showRes(boolean ok, String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle((ok ? "✓ " : "× ") + tr(ok ? "result.title_success" : "result.title_error"))
                .setMessage(msg);
        if (ok) {
            dialog.setPositiveButton(tr("result.open_in_explorer"), (d, w) -> openOutputDirectory());
            dialog.setNegativeButton(tr("common.cancel"), null);
        } else {
            dialog.setPositiveButton(tr("common.ok"), null);
        }
        dialog.show();
    }

    private void openOutputDirectory() {
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            String path = new File(mOutputDir).getCanonicalPath();
            if (!path.equals(root) && !path.startsWith(root + File.separator)) throw new IOException("outside shared storage");
            String relative = path.equals(root) ? "" : path.substring(root.length() + 1);
            Uri folderUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:" + relative);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent treeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri);
                startActivity(treeIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, tr("errors.open_output_failed"), Toast.LENGTH_SHORT).show();
        }
    }

    private static final class UiState {
        String inputPath, outputDir, outputStem, numsPerLine, toolName, runnerName;
        int typeIndex;
        boolean headerOnly, includeGuard, tidy, annotateTool, annotateRunner;
    }

    private UiState captureUiState() {
        UiState state = new UiState();
        if (!mBuilt) return state;
        state.inputPath = mInputPathEdit.getText().toString();
        state.outputDir = mOutputDirEdit.getText().toString();
        state.outputStem = mOutputStemEdit.getText().toString();
        state.numsPerLine = mNumsPerLineEdit.getText().toString();
        state.toolName = mAnnotToolNameEdit.getText().toString();
        state.runnerName = mAnnotRunnerNameEdit.getText().toString();
        View selected = mTypeRadio.findViewById(mTypeRadio.getCheckedRadioButtonId());
        state.typeIndex = Math.max(0, mTypeRadio.indexOfChild(selected));
        state.headerOnly = mModeSwitch.isChecked();
        state.includeGuard = mIncGuardSwitch.isChecked();
        state.tidy = mTidySwitch.isChecked();
        state.annotateTool = mAnnotToolSwitch.isChecked();
        state.annotateRunner = mAnnotRunnerSwitch.isChecked();
        return state;
    }

    private void restoreUiState(UiState state) {
        if (state == null || !mBuilt) return;
        mInputPath = state.inputPath;
        mOutputDir = state.outputDir;
        mInputPathEdit.setText(state.inputPath);
        mOutputDirEdit.setText(state.outputDir);
        mOutputStemEdit.setText(state.outputStem);
        mNumsPerLineEdit.setText(state.numsPerLine);
        mAnnotToolNameEdit.setText(state.toolName);
        mAnnotRunnerNameEdit.setText(state.runnerName);
        if (state.typeIndex < mTypeRadio.getChildCount())
            mTypeRadio.check(mTypeRadio.getChildAt(state.typeIndex).getId());
        mModeSwitch.setChecked(state.headerOnly);
        mIncGuardSwitch.setChecked(state.includeGuard);
        mTidySwitch.setChecked(state.tidy);
        mAnnotToolSwitch.setChecked(state.annotateTool);
        mAnnotRunnerSwitch.setChecked(state.annotateRunner);
    }

    private void refreshAllText() {
        if (!mBuilt) return;
        mTitleView.setText("ZBinary2CArray");

        mBrowseInputBtn.setText("...");
        mInputPathEdit.setHint(tr("input.placeholder"));

        mBrowseDirBtn.setText("...");
        mOutputDirEdit.setHint(tr("output.dir_placeholder"));
        mOutputStemEdit.setHint(tr("output.filename_placeholder"));

        mConvertBtn.setText(tr("convert.button"));
        if (mStatusText.getVisibility() == View.VISIBLE)
            mStatusText.setText(tr("convert.in_progress"));
    }

    private String tr(String key) {
        return NativeBridge.nativeTranslate(key);
    }

    private void applySystemBarAppearance() {
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        boolean lightBars = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    private android.graphics.drawable.Drawable createEditBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant,
                0x1A000000));
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline,
                0x26000000));
        return gd;
    }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean isWide() {
        Configuration cfg = getResources().getConfiguration();
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
                || (cfg.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                   >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }
}



