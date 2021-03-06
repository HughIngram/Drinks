package fr.masciulli.drinks.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.picasso.Picasso;
import fr.masciulli.drinks.R;
import fr.masciulli.drinks.model.Drink;
import fr.masciulli.drinks.model.Liquor;
import fr.masciulli.drinks.ui.adapter.holder.RelatedHeaderViewHolder;
import fr.masciulli.drinks.ui.adapter.holder.TileViewHolder;

import java.util.ArrayList;
import java.util.List;

public class LiquorRelatedAdapter extends RecyclerView.Adapter {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_DRINK = 1;

    private Liquor liquor;
    private List<Drink> drinks = new ArrayList<>();

    private ItemClickListener<Liquor> wikipediaClickListener;
    private ItemClickListener<Drink> drinkClickListener;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View root;
        switch (viewType) {
            case TYPE_HEADER:
                root = inflater.inflate(R.layout.item_liquor_detail_header, parent, false);
                return new RelatedHeaderViewHolder(root);
            case TYPE_DRINK:
                root = inflater.inflate(R.layout.item_liquor_detail_drink, parent, false);
                return new TileViewHolder(root);
            default:
                throw new IllegalArgumentException("Unknown view type");
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case TYPE_HEADER:
                bindHeaderHolder((RelatedHeaderViewHolder) holder);
                return;
            case TYPE_DRINK:
                bindDrinkHolder((TileViewHolder) holder, position);
                return;
            default:
                throw new IllegalArgumentException("Unknown view type");
        }
    }

    private void bindHeaderHolder(final RelatedHeaderViewHolder holder) {
        Context context = holder.itemView.getContext();

        holder.getHistoryView().setText(liquor.getHistory());
        holder.getWikipediaButton()
                .setText(context.getString(R.string.wikipedia, liquor.getName()));
        holder.getRelatedDrinksTitle()
                .setText(context.getString(R.string.related_drinks, liquor.getName()));
        if (wikipediaClickListener != null) {
            holder.getWikipediaButton().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    wikipediaClickListener.onItemClick(holder.getAdapterPosition(), liquor);
                }
            });
        }
    }

    private void bindDrinkHolder(final TileViewHolder holder, final int position) {
        Context context = holder.itemView.getContext();

        final Drink drink = drinks.get(position - 1);
        holder.getNameView().setText(drink.getName());
        if (drinkClickListener != null) {
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drinkClickListener.onItemClick(holder.getAdapterPosition(), drink);
                }
            });
        }
        Picasso.with(context).load(drink.getImageUrl())
                .fit()
                .centerCrop()
                .into(holder.getImageView());
    }

    @Override
    public int getItemCount() {
        return drinks.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_DRINK;
    }

    public void setLiquor(Liquor liquor) {
        this.liquor = liquor;
        notifyItemChanged(0);
    }

    public void setRelatedDrinks(List<Drink> drinks) {
        this.drinks.clear();
        this.drinks.addAll(drinks);
        notifyItemRangeChanged(1, drinks.size());
    }

    public void setWikipediaClickListener(ItemClickListener<Liquor> listener) {
        wikipediaClickListener = listener;
    }

    public void setDrinkClickListener(ItemClickListener<Drink> listener) {
        drinkClickListener = listener;
    }
}
