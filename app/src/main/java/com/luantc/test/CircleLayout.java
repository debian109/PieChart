package com.luantc.test;

/**
 * Created by luantruong on 6/30/16.
 */

import java.util.HashSet;
import java.util.Set;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.Region.Op;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.luantc.test.animation.ChartAnimator;
import com.luantc.test.animation.Easing;
import com.luantc.test.animation.EasingFunction;

public class CircleLayout extends ViewGroup {

    public static final int LAYOUT_NORMAL = 1;
    public static final int LAYOUT_PIE = 2;

    private int mLayoutMode = LAYOUT_NORMAL;

    private Drawable mInnerCircle;

    private float mAngleOffset;
    private float mAngleRange;

    private float mDividerWidth;
    private int mInnerRadius;

    private Paint mDividerPaint;
    private Paint mCirclePaint;

    private RectF mBounds = new RectF();

    private Bitmap mDst;
    private Bitmap mSrc;
    private Canvas mSrcCanvas;
    private Canvas mDstCanvas;
    private Xfermode mXfer;
    private Paint mXferPaint;

    private View mMotionTarget;

    private Bitmap mDrawingCache;
    private Canvas mCachedCanvas;
    private Set<View> mDirtyViews = new HashSet<View>();
    private boolean mCached = false;
    ChartAnimator mAnimator;
    private boolean isAnimationOnly = false;

    private static final float SWEEP_INC = 0.5f;

    /**
     * holds the raw version of the current rotation angle of the chart
     */
    private float mRawRotationAngle = 270f;

    private float mPadding = 7.5f;
    Context mContext;

    public CircleLayout(Context context) {
        this(context, null);
        mContext = context;
        init();
    }

    @SuppressLint("NewApi")
    public CircleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CircleLayout, 0, 0);

        try {
            int dividerColor = a.getColor(R.styleable.CircleLayout_sliceDivider, android.R.color.darker_gray);
            mInnerCircle = a.getDrawable(R.styleable.CircleLayout_innerCircle);

            if (mInnerCircle instanceof ColorDrawable) {
                int innerColor = a.getColor(R.styleable.CircleLayout_innerCircle, android.R.color.white);
                mCirclePaint.setColor(innerColor);
            }

            mDividerPaint.setColor(dividerColor);

            mAngleOffset = a.getFloat(R.styleable.CircleLayout_angleOffset, 0f);
            mAngleRange = a.getFloat(R.styleable.CircleLayout_angleRange, 360f);
            mDividerWidth = a.getDimensionPixelSize(R.styleable.CircleLayout_dividerWidth, 1);
            mInnerRadius = a.getDimensionPixelSize(R.styleable.CircleLayout_innerRadius, 80);

            mLayoutMode = a.getColor(R.styleable.CircleLayout_layoutMode, LAYOUT_NORMAL);
        } finally {
            a.recycle();
        }

        mDividerPaint.setStrokeWidth(mDividerWidth);

        mXfer = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
        mXferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        //Turn off hardware acceleration if possible
        if (Build.VERSION.SDK_INT >= 11) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
    }

    private void init() {
        if (Build.VERSION.SDK_INT < 11)
            mAnimator = new ChartAnimator();
        else
            mAnimator = new ChartAnimator(new ValueAnimator.AnimatorUpdateListener() {

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    postInvalidate();
                }
            });
    }

    public void setAnimationOnly(boolean isAnimationOnly) {
        this.isAnimationOnly = isAnimationOnly;
    }

    /**
     * ################ ################ ################ ################
     * ANIMATIONS ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     */
    /** CODE BELOW FOR PROVIDING EASING FUNCTIONS */

    /**
     * Animates the drawing / rendering of the chart on both x- and y-axis with
     * the specified animation time. If animate(...) is called, no further
     * calling of invalidate() is necessary to refresh the chart. ANIMATIONS
     * ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillisX
     * @param durationMillisY
     * @param easingX         a custom easing function to be used on the animation phase
     * @param easingY         a custom easing function to be used on the animation phase
     */
    public void animateXY(int durationMillisX, int durationMillisY, EasingFunction easingX,
                          EasingFunction easingY) {
        mAnimator.animateXY(durationMillisX, durationMillisY, easingX, easingY);
    }

    /**
     * Animates the rendering of the chart on the x-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     * @param easing         a custom easing function to be used on the animation phase
     */
    public void animateX(int durationMillis, EasingFunction easing) {
        mAnimator.animateX(durationMillis, easing);
    }

    /**
     * Animates the rendering of the chart on the y-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     * @param easing         a custom easing function to be used on the animation phase
     */
    public void animateY(int durationMillis, EasingFunction easing) {
        mAnimator.animateY(durationMillis, easing);
    }

    /**
     * ################ ################ ################ ################
     * ANIMATIONS ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     */
    /** CODE BELOW FOR PREDEFINED EASING OPTIONS */

    /**
     * Animates the drawing / rendering of the chart on both x- and y-axis with
     * the specified animation time. If animate(...) is called, no further
     * calling of invalidate() is necessary to refresh the chart. ANIMATIONS
     * ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillisX
     * @param durationMillisY
     * @param easingX         a predefined easing option
     * @param easingY         a predefined easing option
     */
    public void animateXY(int durationMillisX, int durationMillisY, Easing.EasingOption easingX,
                          Easing.EasingOption easingY) {
        mAnimator.animateXY(durationMillisX, durationMillisY, easingX, easingY);
    }

    /**
     * Animates the rendering of the chart on the x-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     * @param easing         a predefined easing option
     */
    public void animateX(int durationMillis, Easing.EasingOption easing) {
        mAnimator.animateX(durationMillis, easing);
    }

    /**
     * Animates the rendering of the chart on the y-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     * @param easing         a predefined easing option
     */
    public void animateY(int durationMillis, Easing.EasingOption easing) {
        mAnimator.animateY(durationMillis, easing);
    }

    /**
     * ################ ################ ################ ################
     * ANIMATIONS ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     */
    /** CODE BELOW FOR ANIMATIONS WITHOUT EASING */

    /**
     * Animates the rendering of the chart on the x-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     */
    public void animateX(int durationMillis) {
        mAnimator.animateX(durationMillis);
    }

    /**
     * Animates the rendering of the chart on the y-axis with the specified
     * animation time. If animate(...) is called, no further calling of
     * invalidate() is necessary to refresh the chart. ANIMATIONS ONLY WORK FOR
     * API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillis
     */
    public void animateY(int durationMillis) {
        mAnimator.animateY(durationMillis);
    }

    /**
     * Animates the drawing / rendering of the chart on both x- and y-axis with
     * the specified animation time. If animate(...) is called, no further
     * calling of invalidate() is necessary to refresh the chart. ANIMATIONS
     * ONLY WORK FOR API LEVEL 11 (Android 3.0.x) AND HIGHER.
     *
     * @param durationMillisX
     * @param durationMillisY
     */
    public void animateXY(int durationMillisX, int durationMillisY) {
        mAnimator.animateXY(durationMillisX, durationMillisY);
    }

    public void setLayoutMode(int mode) {
        mLayoutMode = mode;
        requestLayout();
        invalidate();
    }

    public int getLayoutMode() {
        return mLayoutMode;
    }

    public int getRadius() {
        final int width = getWidth();
        final int height = getHeight();

        final float minDimen = width > height ? height : width;

        float radius = (minDimen - mInnerRadius) / 2f;

        return (int) radius;
    }

    public void getCenter(PointF p) {
        p.set(getWidth() / 2f, getHeight() / 2);
    }

    public void setAngleOffset(float offset) {
        mAngleOffset = offset;
        requestLayout();
        invalidate();
    }

    public float getAngleOffset() {
        return mAngleOffset;
    }

    public void setInnerRadius(int radius) {
        mInnerRadius = radius;
        requestLayout();
        invalidate();
    }

    public int getInnerRadius() {
        return mInnerRadius;
    }

    public void setInnerCircle(Drawable d) {
        mInnerCircle = d;
        requestLayout();
        invalidate();
    }

    public void setInnerCircle(int res) {
        mInnerCircle = getContext().getResources().getDrawable(res);
        requestLayout();
        invalidate();
    }

    public void setInnerCircleColor(int color) {
        mInnerCircle = new ColorDrawable(color);
        requestLayout();
        invalidate();
    }

    public Drawable getInnerCircle() {
        return mInnerCircle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int count = getChildCount();

        int maxHeight = 0;
        int maxWidth = 0;

        // Find rightmost and bottommost child
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                //maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
                //maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
            }
        }

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        int width = resolveSize(maxWidth, widthMeasureSpec);
        int height = resolveSize(maxHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);

        if (mSrc != null && (mSrc.getWidth() != width || mSrc.getHeight() != height)) {
            /*mDst.recycle();
            mSrc.recycle();
            mDrawingCache.recycle();*/

            mDst = null;
            mSrc = null;
            mDrawingCache = null;
        }

        if (mSrc == null) {
            mSrc = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mDst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mDrawingCache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            mSrcCanvas = new Canvas(mSrc);
            mDstCanvas = new Canvas(mDst);
            mCachedCanvas = new Canvas(mDrawingCache);

            // Handle when drawborder dont
            mSrc.eraseColor(Color.TRANSPARENT);
            mDst.eraseColor(Color.TRANSPARENT);
            mDrawingCache.eraseColor(Color.TRANSPARENT);
        }
    }

    public LayoutParams layoutParams(View child) {
        return (LayoutParams) child.getLayoutParams();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int childs = getChildCount();

        float totalWeight = 0f;

        for (int i = 0; i < childs; i++) {
            final View child = getChildAt(i);

            LayoutParams lp = layoutParams(child);

            totalWeight += lp.weight;
        }

        final int width = getWidth();
        final int height = getHeight();

        final float minDimen = width > height ? height : width;
        final float radius = (minDimen - mInnerRadius) / 2f;

        mBounds.set(width / 2 - minDimen / 2 + mPadding, height / 2 - minDimen / 2 + mPadding, width / 2 + minDimen / 2 - mPadding, height / 2 + minDimen / 2 - mPadding);

        float startAngle = mAngleOffset;

        for (int i = 0; i < childs; i++) {
            final View child = getChildAt(i);

            final LayoutParams lp = layoutParams(child);

            //final float angle = mAngleRange /totalWeight * lp.weight;
            float angle = 0;
            if (i == (childs - 1)) {

            }

            ViewModel model = (ViewModel) child.getTag();
            angle = mAngleRange * (model.getPercentage() / 100);
            final float centerAngle = startAngle + angle / 2f;
            final int x;
            final int y;

            if (childs > 1) {
                x = (int) (radius * Math.cos(Math.toRadians(centerAngle))) + width / 2;
                y = (int) (radius * Math.sin(Math.toRadians(centerAngle))) + height / 2;
            } else {
                x = width / 2;
                y = height / 2;
            }

            final int halfChildWidth = child.getMeasuredWidth() / 2;
            final int halfChildHeight = child.getMeasuredHeight() / 2;

            final int left = lp.width != LayoutParams.FILL_PARENT ? x - halfChildWidth : 0;
            final int top = lp.height != LayoutParams.FILL_PARENT ? y - halfChildHeight : 0;
            final int right = lp.width != LayoutParams.FILL_PARENT ? x + halfChildWidth : width;
            final int bottom = lp.height != LayoutParams.FILL_PARENT ? y + halfChildHeight : height;

            child.layout(left, top, right, bottom);

            if (left != child.getLeft() || top != child.getTop()
                    || right != child.getRight() || bottom != child.getBottom()
                    || lp.startAngle != startAngle
                    || lp.endAngle != startAngle + angle) {
                mCached = false;
            }

            lp.startAngle = startAngle;

            startAngle += angle;

            lp.endAngle = startAngle;
        }

        invalidate();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        LayoutParams lp = new LayoutParams(p.width, p.height);

        if (p instanceof LinearLayout.LayoutParams) {
            lp.weight = ((LinearLayout.LayoutParams) p).weight;
        }

        return lp;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mLayoutMode == LAYOUT_NORMAL) {
            return super.dispatchTouchEvent(ev);
        }

        final int action = ev.getAction();
        final float x = ev.getX() - getWidth() / 2f;
        final float y = ev.getY() - getHeight() / 2f;

        if (action == MotionEvent.ACTION_DOWN) {

            if (mMotionTarget != null) {
                MotionEvent cancelEvent = MotionEvent.obtain(ev);
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL);

                cancelEvent.offsetLocation(-mMotionTarget.getLeft(), -mMotionTarget.getTop());

                mMotionTarget.dispatchTouchEvent(cancelEvent);
                cancelEvent.recycle();

                mMotionTarget = null;
            }

            final float radius = (float) Math.sqrt(x * x + y * y);

            if (radius < mInnerRadius || radius > getWidth() / 2f || radius > getHeight() / 2f) {
                return false;
            }

            float angle = (float) Math.toDegrees(Math.atan2(y, x));

            if (angle < 0) angle += mAngleRange;

            final int childs = getChildCount();

            for (int i = 0; i < childs; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = layoutParams(child);

                float startAngle = lp.startAngle % mAngleRange;
                float endAngle = lp.endAngle % mAngleRange;
                float touchAngle = angle;

                if (startAngle > endAngle) {
                    if (touchAngle < startAngle && touchAngle < endAngle) {
                        touchAngle += mAngleRange;
                    }

                    endAngle += mAngleRange;
                }

                if (startAngle <= touchAngle && endAngle >= touchAngle) {
                    ev.offsetLocation(-child.getLeft(), -child.getTop());

                    boolean dispatched = child.dispatchTouchEvent(ev);

                    if (dispatched) {
                        mMotionTarget = child;

                        return true;
                    } else {
                        ev.setLocation(0f, 0f);

                        return onTouchEvent(ev);
                    }
                }
            }
        } else if (mMotionTarget != null) {
            ev.offsetLocation(-mMotionTarget.getLeft(), -mMotionTarget.getTop());

            mMotionTarget.dispatchTouchEvent(ev);

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mMotionTarget = null;
            }
        }

        return onTouchEvent(ev);
    }

    private void drawChild(Canvas canvas, View child, LayoutParams lp, boolean animation) {

        mSrcCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        mDstCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        mSrcCanvas.save();

        int childLeft = child.getLeft();
        int childTop = child.getTop();
        int childRight = child.getRight();
        int childBottom = child.getBottom();
        mSrcCanvas.clipRect(childLeft, childTop, childRight, childBottom, Op.REPLACE);
        mSrcCanvas.translate(childLeft, childTop);

        child.draw(mSrcCanvas);

        mSrcCanvas.restore();

        mXferPaint.setXfermode(null);
        mXferPaint.setColor(Color.BLACK);

        float sweepAngle = (lp.endAngle - lp.startAngle) % 360;

        if (isAnimationOnly) {

            float mStart = lp.startAngle;
            float mSweep = 0;
            mDstCanvas.drawArc(mBounds, mStart, mSweep, true, mXferPaint);

            mXferPaint.setXfermode(mXfer);
            mDstCanvas.drawBitmap(mSrc, 0f, 0f, mXferPaint);

            canvas.drawBitmap(mDst, 0f, 0f, null);

            while (mSweep < lp.endAngle){

                mDstCanvas.drawArc(mBounds, mStart, mSweep, true, mXferPaint);

                mSweep += SWEEP_INC;



                invalidate();

            }

            if (animation) isAnimationOnly = false;

        } else {
            mDstCanvas.drawArc(mBounds, lp.startAngle, sweepAngle, true, mXferPaint);
            mXferPaint.setXfermode(mXfer);
            mDstCanvas.drawBitmap(mSrc, 0f, 0f, mXferPaint);
            ViewModel model = (ViewModel) child.getTag();
            if (model.isNeedHighlight()) {
                Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
                border.setXfermode(null);
                border.setAntiAlias(true);
                border.setDither(true);
                border.setColor(Color.WHITE);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeJoin(Paint.Join.ROUND);
                border.setStrokeCap(Paint.Cap.ROUND);
                border.setStrokeWidth(5f); // set stroke width
                mDstCanvas.drawArc(mBounds, lp.startAngle, sweepAngle, true, border);
            }
            canvas.drawBitmap(mDst, 0f, 0f, null);
        }
    }

    private void drawDividers(Canvas canvas, float halfWidth, float halfHeight, float radius) {
        final int childs = getChildCount();

        if (childs < 2) {
            return;
        }

        for (int i = 0; i < childs; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = layoutParams(child);

            canvas.drawLine(halfWidth, halfHeight,
                    radius * (float) Math.cos(Math.toRadians(lp.startAngle)) + halfWidth,
                    radius * (float) Math.sin(Math.toRadians(lp.startAngle)) + halfHeight,
                    mDividerPaint);

            if (i == childs - 1) {
                canvas.drawLine(halfWidth, halfHeight,
                        radius * (float) Math.cos(Math.toRadians(lp.endAngle)) + halfWidth,
                        radius * (float) Math.sin(Math.toRadians(lp.endAngle)) + halfHeight,
                        mDividerPaint);
            }
        }
    }

    private void drawInnerCircle(Canvas canvas, float halfWidth, float halfHeight) {
        if (mInnerCircle != null) {
            if (!(mInnerCircle instanceof ColorDrawable)) {
                mInnerCircle.setBounds(
                        (int) halfWidth - mInnerRadius,
                        (int) halfHeight - mInnerRadius,
                        (int) halfWidth + mInnerRadius,
                        (int) halfHeight + mInnerRadius);

                mInnerCircle.draw(canvas);
            } else {
                canvas.drawCircle(halfWidth, halfHeight, mInnerRadius, mCirclePaint);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mLayoutMode == LAYOUT_NORMAL) {
            super.dispatchDraw(canvas);
            return;
        }

        if (mSrc == null || mDst == null || mSrc.isRecycled() || mDst.isRecycled()) {
            return;
        }

        final int childs = getChildCount();

        final float halfWidth = getWidth() / 2f;
        final float halfHeight = getHeight() / 2f;

        final float radius = halfWidth > halfHeight ? halfHeight : halfWidth;

        /*if (mCached && mDrawingCache != null && !mDrawingCache.isRecycled() && mDirtyViews.size() < childs / 2) {
            canvas.drawBitmap(mDrawingCache, 0f, 0f, null);

            redrawDirty(canvas);

            drawDividers(canvas, halfWidth, halfHeight, radius);

            drawInnerCircle(canvas, halfWidth, halfHeight);

            return;
        } else {
            mCached = false;
        }*/


        /*Canvas sCanvas = null;

        if (mCachedCanvas != null) {
            sCanvas = canvas;
            canvas = mCachedCanvas;
        }*/

        Drawable bkg = getBackground();
        if (bkg != null) {
            bkg.draw(canvas);
        }

        for (int i = 0; i < childs; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = layoutParams(child);

            if (i == childs - 1)
                drawChild(canvas, child, lp, true);
            else
                drawChild(canvas, child, lp, false);

        }

        drawDividers(canvas, halfWidth, halfHeight, radius);

        drawInnerCircle(canvas, halfWidth, halfHeight);

        /*if (mCachedCanvas != null) {
            sCanvas.drawBitmap(mDrawingCache, 0f, 0f, null);
            mDirtyViews.clear();
            mCached = true;
        }*/
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {

        public float startAngle;
        public float endAngle;

        public float weight = 1f;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }
}