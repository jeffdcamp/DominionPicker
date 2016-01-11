package ca.marklauman.dominionpicker.rules;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import ca.marklauman.dominionpicker.R;
import ca.marklauman.dominionpicker.cardadapters.imagefactories.CoinFactory;
import ca.marklauman.dominionpicker.database.LoaderId;
import ca.marklauman.dominionpicker.database.Provider;
import ca.marklauman.dominionpicker.database.TableCard;
import ca.marklauman.dominionpicker.settings.Prefs;
import ca.marklauman.tools.Utils;
import ca.marklauman.tools.preferences.Preference.PreferenceListener;
import ca.marklauman.tools.preferences.SmallNumberPreference;

/** Adapter used to display the simple rules list.
 *  This adapter directly handles the setting of the {@link Prefs#FILT_CURSE} and
 *  {@link Prefs#FILT_SET} values as well. */
class RulesAdapter extends ArrayAdapter<View>
                   implements OnItemClickListener, PreferenceListener,
                              LoaderManager.LoaderCallbacks<Cursor> {
    /** Keys updated by this adapter. */
    private static final String[] PREF_KEYS = {Prefs.LIMIT_SUPPLY, Prefs.FILT_SET, Prefs.FILT_CURSE};

    /** ListView paired to this adapter; */
    private final ListView listView;
    /** View used to show that this list is still loading */
    private final View footerView;
    /** Preferred list item height */
    private final int prefHeight;
    /** 8dp in px */
    private final int dp8;
    /** Coin factory used to make the coin drawables */
    private final CoinFactory coins;
    /** LoaderManager of the this Adapter's activity */
    private final LoaderManager lm;

    /** SharedPreference object of this context */
    private final SharedPreferences prefs;
    /** The current value of the filt_set preference */
    private final HashSet<Integer> filt_set;
    /** The current value of the filt_cost preference */
    private final HashSet<Integer> filt_cost;

    /** The views available for this adapter */
    final private ArrayList<View> views;
    /** Position of the expansion filters */
    private final Range posExp = new Range();
    /** Position of the promo filters in this adapter */
    private final Range posPromo = new Range();
    /** Position of the potion filter */
    private int posPotion = -1;
    /** Position of the cost filters in this adapter */
    private final Range posCost = new Range();
    /** Position of the filt_curse checkbox */
    private int posCurse = -1;

    public RulesAdapter(Context context, ListView list) {
        super(context, R.layout.fragment_rules);
        listView = list;
        footerView = View.inflate(context, R.layout.list_item_loading, null);
        listView.addFooterView(footerView);
        lm = ((AppCompatActivity)context).getSupportLoaderManager();
        coins = new CoinFactory(context.getResources());

        // Load the preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        filt_set = getArrayPref(Prefs.FILT_SET);
        filt_cost = getArrayPref(Prefs.FILT_COST);

        // Get dimensions needed for view construction.
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        TypedValue tv = new TypedValue();
        context.getTheme()
                .resolveAttribute(android.R.attr.listPreferredItemHeight, tv, true);
        prefHeight = (int)(tv.getDimension(metrics)+0.5);
        dp8 = (int)(8 * metrics.density + 0.5f);

        views = new ArrayList<>(20);
        rebuild();
    }

    /** Read a preference containing an array of integers */
    private HashSet<Integer> getArrayPref(String key) {
        String pref_raw = prefs.getString(key, "");
        String[] pref_arr = (0==pref_raw.length()) ? new String[0]
                                                   : pref_raw.split(",");
        HashSet<Integer> res = new HashSet<>(pref_arr.length);
        for(String s : pref_arr)
            res.add(Integer.parseInt(s));
        return res;
    }

    public void rebuild() {
        // Start to build the list of views
        views.clear();
        views.add(newNumPref(getContext(), 0, Prefs.LIMIT_SUPPLY, R.string.limit_supply, prefHeight));

        // Add an extra 8dp to the top list item
        views.get(0).setPadding(0, dp8, 0, 0);

        // Start loading the card sets
        lm.restartLoader(LoaderId.RULES_EXP, null, this);
        notifyDataSetChanged();
    }



    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return views.get(position);
    }

    @Override
    public int getCount() {
        return views.size();
    }

    protected SmallNumberPreference newNumPref(Context c, int id, String key,
                                               int textRes, int height) {
        SmallNumberPreference res = new SmallNumberPreference(c);
        res.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
                                                         height));
        res.setText(textRes);
        res.setKey(key);
        res.setListener(this, id);
        return res;
    }

    protected View newSeparator(Context c, int textRes) {
        View res = View.inflate(c, R.layout.list_item_seperator, null);
        TextView txt = (TextView)res.findViewById(android.R.id.text1);
        txt.setText(textRes);
        return res;
    }

    protected CheckedTextView newChecked(Context c, String text, int imgRes) {
        CheckedTextView res = (CheckedTextView) View.inflate(c, R.layout.rule_checkbox, null);
        res.setText(text);
        if (imgRes != 0)
            Utils.setDrawables(res, imgRes, 0, 0, 0);
        return res;
    }


    protected CheckedTextView newChecked(Context c, String text, Drawable drawable) {
        CheckedTextView res = (CheckedTextView) View.inflate(c, R.layout.rule_checkbox, null);
        res.setText(text);
        if (drawable != null)
            Utils.setDrawables(res, drawable, null, null, null);
        return res;
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch(id) {
            case LoaderId.RULES_EXP:
                return new CursorLoader(getContext(), Provider.URI_CARD_SET,
                        new String[]{TableCard._SET_ID, TableCard._SET_NAME, TableCard._PROMO},
                        Prefs.filt_lang, null, TableCard._PROMO+", "+Prefs.sort_set);
            case LoaderId.RULES_COST:
                return new CursorLoader(getContext(), Provider.URI_CARD_DATA_U,
                        new String[]{TableCard._COST_VAL},
                        null, null, TableCard._COST_VAL);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Context context = getContext();
        CheckedTextView view;
        switch(loader.getId()) {
            case LoaderId.RULES_EXP:
                // Get the expansion icons
                int[] icons = Utils.getResourceArray(context, R.array.card_set_icons);

                // Add the expansions
                views.add(newSeparator(context, R.string.rules_expansions));
                posExp.start = views.size();
                int _id = data.getColumnIndex(TableCard._SET_ID);
                int _name = data.getColumnIndex(TableCard._SET_NAME);
                int _promo = data.getColumnIndex(TableCard._PROMO);
                data.moveToPosition(-1);
                while(data.moveToNext() && data.getInt(_promo)==0) {
                    int id = data.getInt(_id);
                    view = newChecked(context, data.getString(_name), icons[id]);
                    if(filt_set.contains(id)) view.toggle();
                    view.setTag(id);
                    views.add(view);
                }
                posExp.end = views.size()-1;

                // Add the promo sets
                views.add(newSeparator(context, R.string.rules_promo));
                posPromo.start = views.size();
                do {
                    int id = data.getInt(_id);
                    view = newChecked(context, data.getString(_name), icons[id]);
                    if(filt_set.contains(id)) view.toggle();
                    view.setTag(id);
                    views.add(view);
                } while(data.moveToNext());
                posPromo.end = views.size()-1;

                // Start loading the coin costs
                lm.initLoader(LoaderId.RULES_COST, null, this);
                break;


            case LoaderId.RULES_COST:
                views.add(newSeparator(context, R.string.rules_cost));

                // Add the potion checkbox
                posPotion = views.size();
                view = newChecked(context, context.getString(R.string.potion),
                                     R.drawable.ic_dom_potion);
                if(prefs.getBoolean(Prefs.FILT_POTION, true))
                    view.toggle();
                views.add(view);


                Resources res = context.getResources();
                posCost.start = views.size();
                int coinSize = res.getDimensionPixelSize(R.dimen.vp_size_small);
                int _cost = data.getColumnIndex(TableCard._COST_VAL);
                data.moveToPosition(-1);
                while (data.moveToNext()){
                    int cost = data.getInt(_cost);
                    view = newChecked(context,
                            res.getQuantityString(R.plurals.format_coin,
                                    cost, "" + cost),
                            coins.getDrawable("" + cost, coinSize));
                    if(!filt_cost.contains(cost))
                        view.toggle();
                    view.setTag(cost);
                    views.add(view);
                }
                posCost.end = views.size()-1;

                // Add the curse filter
                views.add(newSeparator(context, R.string.rules_other));
                posCurse = views.size();
                view = newChecked(context, context.getString(R.string.rules_curse),
                        R.drawable.ic_dom_curse);
                if(prefs.getBoolean(Prefs.FILT_CURSE, true)) view.toggle();
                views.add(view);

                // Remove the loading icon - we're done that.
                if(listView != null)
                    listView.removeFooterView(footerView);
                break;

        }
        notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {}


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // If its the curse giver's checkbox
        if(position == posCurse)
            toggleCheckbox((CheckedTextView)view, Prefs.FILT_CURSE);

        // If its the potion filter checkbox
        else if(position == posPotion)
            toggleCheckbox((CheckedTextView)view, Prefs.FILT_POTION);

        // If its a card set checkbox
        else if((posExp.start <= position && position <= posExp.end)
                || (posPromo.start <= position && position <= posPromo.end))
            toggleCheckbox((CheckedTextView)view, Prefs.FILT_SET, filt_set, false);

        // If its a cost checkbox
        else if(posCost.start <= position && position <= posCost.end)
            toggleCheckbox((CheckedTextView)view, Prefs.FILT_COST, filt_cost, true);
    }


    private void toggleCheckbox(CheckedTextView check, String key) {
        check.toggle();
        prefs.edit().putBoolean(key, check.isChecked()).commit();
        Prefs.notifyChange(getContext(), key);
        notifyDataSetChanged();
    }

    private void toggleCheckbox(CheckedTextView check, String key,
                                HashSet<Integer> value, boolean invertSel) {
        check.toggle();
        int set_id = (Integer)check.getTag();
        // invertSel XOR check.isChecked()
        if(invertSel ^ check.isChecked()) value.add(set_id);
        else value.remove(set_id);
        prefs.edit().putString(key, Utils.join(",", value)).commit();
        Prefs.notifyChange(getContext(), key);
        notifyDataSetChanged();
    }


    @Override
    public void preferenceChanged(int id) {
        Prefs.notifyChange(getContext(), PREF_KEYS[id]);
    }

    protected static class Range {
        int start = -1;
        int end = -1;
    }
}