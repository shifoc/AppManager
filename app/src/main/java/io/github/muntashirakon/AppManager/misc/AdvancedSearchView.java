// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.annotation.SuppressLint;
import android.app.SearchableInfo;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.TintTypedArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.rapidfuzz.RapidFuzz;
import io.github.muntashirakon.rapidfuzz.RapidFuzzCached;

public class AdvancedSearchView extends SearchView {
    @IntDef(flag = true, value = {
            SEARCH_TYPE_CONTAINS,
            SEARCH_TYPE_PREFIX,
            SEARCH_TYPE_SUFFIX,
            SEARCH_TYPE_REGEX,
            SEARCH_TYPE_FUZZY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchType {
    }

    /**
     * Search using {@link String#contains(CharSequence)}.
     */
    public static final int SEARCH_TYPE_CONTAINS = 1;
    /**
     * Search using {@link String#startsWith(String)}.
     */
    public static final int SEARCH_TYPE_PREFIX = 1 << 1;
    /**
     * Search using {@link String#endsWith(String)}.
     */
    public static final int SEARCH_TYPE_SUFFIX = 1 << 2;
    /**
     * Search using {@link String#matches(String)} or {@link java.util.regex.Pattern}.
     */
    public static final int SEARCH_TYPE_REGEX = 1 << 3;
    /**
     * Perform fuzzy search.
     */
    public static final int SEARCH_TYPE_FUZZY = 1 << 4;

    @SearchType
    private int mType = SEARCH_TYPE_CONTAINS;
    @SearchType
    private int mEnabledTypes = SEARCH_TYPE_CONTAINS | SEARCH_TYPE_PREFIX | SEARCH_TYPE_SUFFIX | SEARCH_TYPE_REGEX | SEARCH_TYPE_FUZZY;
    private CharSequence mQueryHint;
    private final ImageView mSearchTypeSelectionButton;
    private final SearchAutoComplete mSearchSrcTextView;
    private final Drawable mSearchHintIcon;
    @Nullable
    private OnQueryTextListener mOnQueryTextListener;
    @Nullable
    private OnClickListener mOnSearchIconClickListener;
    private final OnClickListener mOnSearchIconClickListenerSuper;
    @Nullable
    private OnFocusChangeListener mOnQueryTextFocusChangeListener;
    private final OnFocusChangeListener mOnQueryTextFocusChangeListenerSuper;
    private final SearchView.OnQueryTextListener mOnQueryTextListenerSuper = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            if (mOnQueryTextListener != null) {
                return mOnQueryTextListener.onQueryTextSubmit(query, mType);
            }
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (mOnQueryTextListener != null) {
                return mOnQueryTextListener.onQueryTextChange(newText, mType);
            }
            return false;
        }
    };
    private final View.OnClickListener onClickSearchIcon = v -> {
        PopupMenu popupMenu = new PopupMenu(getContext(), v);
        popupMenu.inflate(R.menu.view_advanced_search_type_selections);
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_search_type_contains) {
                mType = SEARCH_TYPE_CONTAINS;
            } else if (id == R.id.action_search_type_prefix) {
                mType = SEARCH_TYPE_PREFIX;
            } else if (id == R.id.action_search_type_suffix) {
                mType = SEARCH_TYPE_SUFFIX;
            } else if (id == R.id.action_search_type_regex) {
                mType = SEARCH_TYPE_REGEX;
            } else if (id == R.id.action_search_type_fuzzy) {
                mType = SEARCH_TYPE_FUZZY;
            }
            if (mOnQueryTextListener != null) {
                mOnQueryTextListener.onQueryTextChange(getQuery().toString(), mType);
            }
            updateQueryHint();
            return true;
        });
        Menu menu = popupMenu.getMenu();
        if ((mEnabledTypes & SEARCH_TYPE_CONTAINS) == 0) {
            menu.findItem(R.id.action_search_type_contains).setVisible(false);
        }
        if ((mEnabledTypes & SEARCH_TYPE_PREFIX) == 0) {
            menu.findItem(R.id.action_search_type_prefix).setVisible(false);
        }
        if ((mEnabledTypes & SEARCH_TYPE_SUFFIX) == 0) {
            menu.findItem(R.id.action_search_type_suffix).setVisible(false);
        }
        if ((mEnabledTypes & SEARCH_TYPE_REGEX) == 0) {
            menu.findItem(R.id.action_search_type_regex).setVisible(false);
        }
        if ((mEnabledTypes & SEARCH_TYPE_FUZZY) == 0) {
            menu.findItem(R.id.action_search_type_fuzzy).setVisible(false);
        }
        popupMenu.show();
    };

    public AdvancedSearchView(@NonNull Context context) {
        this(context, null);
    }

    public AdvancedSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.searchViewStyle);
    }

    @SuppressLint("RestrictedApi")
    public AdvancedSearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSearchSrcTextView = findViewById(R.id.search_src_text);
        mSearchTypeSelectionButton = findViewById(R.id.search_mag_icon);
        mSearchTypeSelectionButton.setImageResource(R.drawable.ic_filter_menu_outline);
        mSearchTypeSelectionButton.setBackgroundResource(R.drawable.item_transparent);
        mSearchTypeSelectionButton.setOnClickListener(onClickSearchIcon);
        final TintTypedArray a = TintTypedArray.obtainStyledAttributes(context,
                attrs, R.styleable.SearchView, defStyleAttr, 0);
        mQueryHint = a.getText(R.styleable.SearchView_queryHint);
        mSearchHintIcon = a.getDrawable(R.styleable.SearchView_searchHintIcon);
        a.recycle();
        setIconified(isIconified());
        updateQueryHint();
        mOnQueryTextFocusChangeListenerSuper = (v, hasFocus) -> {
            v.postDelayed(() -> {
                // This has to be like this because the {@link SearchAutoComplete#onFocusChanged(boolean, int, Rect)}
                // has an issue.
                // FIXME: 29/11/21 Override SearchAutoComplete and create a new search layout from the original to
                //  include the overridden class
                if (!isIconified()) {
                    mSearchTypeSelectionButton.setVisibility(VISIBLE);
                }
            }, 1);
            if (mOnQueryTextFocusChangeListener != null) {
                mOnQueryTextFocusChangeListener.onFocusChange(v, hasFocus);
            }
        };
        mOnSearchIconClickListenerSuper = v -> {
            mSearchTypeSelectionButton.setVisibility(VISIBLE);
            if (mOnSearchIconClickListener != null) {
                mOnSearchIconClickListener.onClick(v);
            }
        };
        mSearchSrcTextView.setOnFocusChangeListener(mOnQueryTextFocusChangeListenerSuper);
        super.setOnSearchClickListener(mOnSearchIconClickListenerSuper);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        if (!isIconified()) mSearchTypeSelectionButton.setVisibility(VISIBLE);
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        boolean result = super.requestFocus(direction, previouslyFocusedRect);
        if (result && !isIconified()) mSearchTypeSelectionButton.setVisibility(VISIBLE);
        return result;
    }

    @Override
    public void setOnQueryTextFocusChangeListener(OnFocusChangeListener listener) {
        mOnQueryTextFocusChangeListener = listener;
    }

    public void setOnQueryTextListener(@Nullable OnQueryTextListener listener) {
        if (listener == null) return;
        mOnQueryTextListener = listener;
        super.setOnQueryTextListener(mOnQueryTextListenerSuper);
    }

    /**
     * @deprecated This method is ignored. Use {@link #setOnQueryTextListener(OnQueryTextListener)} instead.
     */
    @Override
    public final void setOnQueryTextListener(SearchView.OnQueryTextListener listener) {
        throw new UnsupportedOperationException("Wrong function. Use the other function by the same name.");
    }

    @Override
    public void setOnSearchClickListener(OnClickListener listener) {
        mOnSearchIconClickListener = listener;
    }

    @Override
    public void setIconifiedByDefault(boolean iconified) {
        super.setIconifiedByDefault(iconified);
        updateQueryHint();
    }

    @Override
    public void setSearchableInfo(SearchableInfo searchable) {
        super.setSearchableInfo(searchable);
        if (!isIconified()) mSearchTypeSelectionButton.setVisibility(VISIBLE);
    }

    @Override
    public void setSubmitButtonEnabled(boolean enabled) {
        super.setSubmitButtonEnabled(enabled);
        if (!isIconified()) mSearchTypeSelectionButton.setVisibility(VISIBLE);
    }

    @Override
    public void setQueryHint(@Nullable CharSequence hint) {
        super.setQueryHint(hint);
        mQueryHint = hint;
    }

    public void setEnabledTypes(@SearchType int enabledTypes) {
        this.mEnabledTypes = enabledTypes;
        if (this.mEnabledTypes == 0) {
            mEnabledTypes = SEARCH_TYPE_CONTAINS;
        }
    }

    public void addEnabledTypes(@SearchType int enabledTypes) {
        this.mEnabledTypes |= enabledTypes;
    }

    public void removeEnabledTypes(@SearchType int enabledTypes) {
        this.mEnabledTypes &= ~enabledTypes;
        if (this.mEnabledTypes == 0) {
            mEnabledTypes = SEARCH_TYPE_CONTAINS;
        }
    }

    public static boolean matches(String query, String text, @SearchType int type) {
        switch (type) {
            case SEARCH_TYPE_CONTAINS:
                return text.contains(query);
            case SEARCH_TYPE_PREFIX:
                return text.startsWith(query);
            case SEARCH_TYPE_SUFFIX:
                return text.endsWith(query);
            case SEARCH_TYPE_REGEX:
                return text.matches(query);
            case SEARCH_TYPE_FUZZY:
                return RapidFuzz.weightedRatio(query, text, 50.0) > 0;
        }
        return false;
    }

    public interface ChoiceGenerator<T> {
        @Nullable
        String getChoice(T object);
    }

    public interface ChoicesGenerator<T> {
        List<String> getChoices(T object);
    }

    public static <T> List<T> matches(String query, Collection<T> choices, ChoiceGenerator<T> generator, @SearchType int type) {
        if (choices == null) return null;
        if (choices.size() == 0) return Collections.emptyList();
        List<T> results = new ArrayList<>(choices.size());
        if (type == SEARCH_TYPE_FUZZY) {
            List<RapidFuzzCached.Result<T>> fuzzyResults = RapidFuzzCached.extractAll(query, choices, generator::getChoice, 50.);
            Collections.sort(fuzzyResults, (o1, o2) -> -Double.compare(o1.getScore(), o2.getScore()));
            for (RapidFuzzCached.Result<T> fuzzyResult : fuzzyResults) {
                results.add(fuzzyResult.getObject());
            }
            return results;
        }
        if (type == SEARCH_TYPE_REGEX) {
            Pattern p;
            try {
                p = Pattern.compile(query);
                for (T choice : choices) {
                    String text = generator.getChoice(choice);
                    if (text == null) continue;
                    if (p.matcher(text).find()) {
                        results.add(choice);
                    }
                }
            } catch (PatternSyntaxException ignore) {
            }
            return results;
        }
        // Rests are typical
        for (T choice : choices) {
            String text = generator.getChoice(choice);
            if (text == null) continue;
            if (matches(query, text, type)) {
                results.add(choice);
            }
        }
        return results;
    }

    public static <T> List<T> matches(String query, Collection<T> choices, ChoicesGenerator<T> generator, @SearchType int type) {
        if (choices == null) return null;
        if (choices.size() == 0) return Collections.emptyList();
        List<T> results = new ArrayList<>(choices.size());
        if (type == SEARCH_TYPE_FUZZY) {
            List<RapidFuzzCached.Result<T>> fuzzyResults = RapidFuzzCached.extractAll(query, choices, generator::getChoices, 50.);
            Collections.sort(fuzzyResults, (o1, o2) -> -Double.compare(o1.getScore(), o2.getScore()));
            for (RapidFuzzCached.Result<T> fuzzyResult : fuzzyResults) {
                results.add(fuzzyResult.getObject());
            }
            return results;
        }
        if (type == SEARCH_TYPE_REGEX) {
            Pattern p;
            try {
                p = Pattern.compile(query);
                for (T choice : choices) {
                    List<String> texts = generator.getChoices(choice);
                    for (String text : texts) {
                        if (text == null) continue;
                        if (p.matcher(text).find()) {
                            results.add(choice);
                            break; // Only a single match is enough
                        }
                    }
                }
            } catch (PatternSyntaxException ignore) {
            }
            return results;
        }
        // Rests are typical
        for (T choice : choices) {
            List<String> texts = generator.getChoices(choice);
            for (String text : texts) {
                if (text == null) continue;
                if (matches(query, text, type)) {
                    results.add(choice);
                    break; // Only a single match is enough
                }
            }
        }
        return results;
    }

    private void updateQueryHint() {
        CharSequence hintText = mQueryHint + " (" + getQueryHint(mType) + ")";
        if (!isIconfiedByDefault() && mSearchHintIcon != null) {
            // Search icon isn't displayed when it is iconified by default.
            final int textSize = (int) (mSearchSrcTextView.getTextSize() * 1.25);
            mSearchHintIcon.setBounds(0, 0, textSize, textSize);

            final SpannableStringBuilder ssb = new SpannableStringBuilder("   ");
            ssb.setSpan(new ImageSpan(mSearchHintIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(hintText);
            super.setQueryHint(ssb);
            return;
        }
        super.setQueryHint(hintText);
    }

    @NonNull
    private CharSequence getQueryHint(@SearchType int type) {
        switch (type) {
            default:
            case SEARCH_TYPE_CONTAINS:
                return getContext().getString(R.string.search_type_contains);
            case SEARCH_TYPE_FUZZY:
                return getContext().getString(R.string.search_type_fuzzy);
            case SEARCH_TYPE_PREFIX:
                return getContext().getString(R.string.search_type_prefix);
            case SEARCH_TYPE_REGEX:
                return getContext().getString(R.string.search_type_regular_expressions);
            case SEARCH_TYPE_SUFFIX:
                return getContext().getString(R.string.search_type_suffix);
        }
    }

    public interface OnQueryTextListener {
        boolean onQueryTextChange(String newText, @SearchType int type);

        boolean onQueryTextSubmit(String query, @SearchType int type);
    }
}