package fr.masciulli.drinks.fragment;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.util.Timer;
import java.util.TimerTask;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import fr.masciulli.drinks.R;
import fr.masciulli.drinks.data.DrinksProvider;
import fr.masciulli.drinks.model.Drink;
import fr.masciulli.drinks.util.AnimUtils;
import fr.masciulli.drinks.view.BlurTransformation;
import fr.masciulli.drinks.view.ObservableScrollView;
import fr.masciulli.drinks.view.ScrollViewListener;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class DrinkDetailFragment extends Fragment implements ScrollViewListener, Callback<Drink>, BackPressedListener {

    private static final String STATE_DRINK = "drink";
    private static final long ANIM_IMAGE_ENTER_DURATION = 500;
    private static final long ANIM_IMAGE_EXIT_DURATION = 500;
    private static final long ANIM_TEXT_ENTER_DURATION = 500;
    private static final long ANIM_TEXT_EXIT_DURATION = 300;

    private static final TimeInterpolator sDecelerator = new DecelerateInterpolator();

    private ImageView mImageView;
    private ImageView mBlurredImageView;
    private TextView mHistoryView;
    private ObservableScrollView mScrollView;
    private TextView mIngredientsView;
    private TextView mInstructionsView;
    private ProgressBar mProgressBar;
    private Button mWikipediaButton;

    private MenuItem mRetryAction;

    private int mImageViewHeight;

    private int mDrinkId;
    private Transformation mTransformation;

    private Drink mDrink;
    private int mTopDelta;
    private int mPreviousItemHeight;
    private Drawable mBackground;
    private int mPreviousOrientation;
    private int mPreviousItemTop;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_drink_detail, container, false);

        setHasOptionsMenu(true);

        mImageView = (ImageView) root.findViewById(R.id.image);
        mBlurredImageView = (ImageView) root.findViewById(R.id.image_blurred);
        mHistoryView = (TextView) root.findViewById(R.id.history);
        mIngredientsView = (TextView) root.findViewById(R.id.ingredients);
        mInstructionsView = (TextView) root.findViewById(R.id.instructions);
        mScrollView = (ObservableScrollView) root.findViewById(R.id.scroll);
        mProgressBar = (ProgressBar) root.findViewById(R.id.progressbar);
        mWikipediaButton = (Button) root.findViewById(R.id.wikipedia);

        Intent intent = getActivity().getIntent();
        mDrinkId = intent.getIntExtra("drink_id", 1);
        String name = intent.getStringExtra("drink_name");
        String imageUrl = intent.getStringExtra("drink_imageurl");

        // Data needed for animations
        mPreviousItemHeight = intent.getIntExtra("height", 0);
        mPreviousItemTop = intent.getIntExtra("top", 0);
        mPreviousOrientation = intent.getIntExtra("orientation", 0);

        mBackground = root.getBackground();

        getActivity().setTitle(name);
        Picasso.with(getActivity()).load(imageUrl).into(mImageView);

        mTransformation = new BlurTransformation(getActivity(), getResources().getInteger(R.integer.blur_radius));
        Picasso.with(getActivity()).load(imageUrl).transform(mTransformation).into(mBlurredImageView);

        mImageViewHeight = (int) getResources().getDimension(R.dimen.drink_detail_recipe_margin);
        mScrollView.setScrollViewListener(this);
        mWikipediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrink == null) return;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mDrink.wikipedia)));
            }
        });

        if (savedInstanceState != null) {
            Drink drink = savedInstanceState.getParcelable(STATE_DRINK);
            if (drink != null) {
                success(drink, null);
            } else {
                refresh();
            }
        } else {
            ViewTreeObserver observer = mImageView.getViewTreeObserver();
            if (observer != null) {
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {

                    @Override
                    public boolean onPreDraw() {
                        mImageView.getViewTreeObserver().removeOnPreDrawListener(this);

                        int[] screenLocation = new int[2];
                        mImageView.getLocationOnScreen(screenLocation);
                        mTopDelta = mPreviousItemTop - screenLocation[1];

                        runEnterAnimation();

                        return true;
                    }
                });
            }
        }

        return root;
    }

    private void runEnterAnimation() {

        Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        };
        mImageView.setTranslationY(mTopDelta);

        ViewPropertyAnimator animator = mImageView.animate().setDuration(ANIM_IMAGE_ENTER_DURATION).
                translationX(0).translationY(0).
                setInterpolator(sDecelerator);

        if (VERSION.SDK_INT >= 16) {
            animator.withEndAction(refreshRunnable);
        } else {
            AnimUtils.scheduleEndAction(refreshRunnable, ANIM_IMAGE_ENTER_DURATION);
        }

        ObjectAnimator bgAnim = ObjectAnimator.ofInt(mBackground, "alpha", 0, 255);
        bgAnim.setDuration(ANIM_IMAGE_ENTER_DURATION);
        bgAnim.start();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mDrink != null) {
            outState.putParcelable(STATE_DRINK, mDrink);
        }
    }

    @Override
    public void onScrollChanged(ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
        float alpha = 2 * (float) y / (float) mImageViewHeight;
        if (alpha > 1) {
            alpha = 1;
        }
        mBlurredImageView.setAlpha(alpha);

        mImageView.setTop((0 - y) / 2);
        mImageView.setBottom(mImageViewHeight - y);
        mBlurredImageView.setTop((0 - y) / 2);
        mBlurredImageView.setBottom(mImageViewHeight - y);
    }

    @Override
    public void success(Drink drink, Response response) {
        Log.d(getTag(), "Drink detail loading has succeeded");

        mDrink = drink;

        if (getActivity() == null) return;

        mProgressBar.setVisibility(View.GONE);

        getActivity().setTitle(drink.name);

        Picasso.with(getActivity()).load(drink.imageUrl).into(mImageView);
        Picasso.with(getActivity()).load(drink.imageUrl).transform(mTransformation).into(mBlurredImageView);

        mHistoryView.setText(drink.history);

        String htmlString = "";
        int i = 0;
        for (String ingredient : drink.ingredients) {
            if (++i == drink.ingredients.size()) {
                htmlString += "&#8226; " + ingredient;
            } else {
                htmlString += "&#8226; " + ingredient + "<br>";
            }
        }
        mIngredientsView.setText(Html.fromHtml(htmlString));

        mInstructionsView.setText(drink.instructions);
        mWikipediaButton.setText(String.format(getString(R.string.drink_detail_wikipedia), drink.name));

        ViewTreeObserver observer = mScrollView.getViewTreeObserver();
        if (observer != null) {
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {

                @Override
                public boolean onPreDraw() {
                    mScrollView.getViewTreeObserver().removeOnPreDrawListener(this);
                    mScrollView.setAlpha(0);
                    mScrollView.animate().setDuration(ANIM_TEXT_ENTER_DURATION).
                            alpha(1).
                            setInterpolator(sDecelerator);

                    return true;
                }
            });
        }
        mScrollView.setVisibility(View.VISIBLE);
    }

    @Override
    public void failure(RetrofitError error) {

        Response resp = error.getResponse();
        String message;
        if (resp != null) {
            message = "response status : " + resp.getStatus();
        } else {
            message = "no response";
        }
        Log.e(getTag(), "Drink detail loading has failed : " + message);

        if (getActivity() == null) return;

        mProgressBar.setVisibility(View.GONE);
        if (mRetryAction != null) mRetryAction.setVisible(true);
        if (error.isNetworkError()) {
            Crouton.makeText(getActivity(), getString(R.string.network_error), Style.ALERT).show();
        } else {
            Crouton.makeText(getActivity(), R.string.drink_detail_loading_failed, Style.ALERT).show();
        }

    }

    private void refresh() {
        mProgressBar.setVisibility(View.VISIBLE);
        if (mRetryAction != null) mRetryAction.setVisible(false);
        DrinksProvider.getDrink(mDrinkId, this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.drink_detail, menu);
        mRetryAction = menu.findItem(R.id.retry);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.retry:
                refresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        // Configure the end action (finishing activity)
        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        };

        // Configure the image exit animation in a runnable

        final Runnable imageAnim = new Runnable() {
            @Override
            public void run() {

                mBlurredImageView.setAlpha(0f);

                int[] screenLocation = new int[2];
                mImageView.getLocationOnScreen(screenLocation);
                mTopDelta = mPreviousItemTop - screenLocation[1];
                ViewPropertyAnimator imageViewAnimator = mImageView.animate().setDuration(ANIM_IMAGE_EXIT_DURATION).
                        translationX(0).translationY(mTopDelta).
                        setInterpolator(sDecelerator);

                if (VERSION.SDK_INT >= 16) {
                    imageViewAnimator.withEndAction(finish);
                } else {
                    AnimUtils.scheduleEndAction(finish, ANIM_IMAGE_EXIT_DURATION);
                }

                ObjectAnimator bgAnim = ObjectAnimator.ofInt(mBackground, "alpha", 255, 0);
                bgAnim.setDuration(ANIM_IMAGE_EXIT_DURATION);
                bgAnim.start();
            }
        };


        if (mScrollView != null) {
            ViewPropertyAnimator animator = mScrollView.animate().setDuration(ANIM_TEXT_EXIT_DURATION).
                    alpha(0).
                    setInterpolator(sDecelerator);

            if (VERSION.SDK_INT >= 16) {
                animator.withEndAction(imageAnim);
            } else {
                AnimUtils.scheduleEndAction(imageAnim, ANIM_TEXT_EXIT_DURATION);
            }
        } else {
            // scrollView null, let's run the image animation right away
            imageAnim.run();
        }
    }
}
