package com.nalan.widget.viewtranshelper;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.Scroller;

/**
 * Author： liyi
 * Date：    2017/2/28.
 */
//支持tap、double tap放大、drag、fling、scale
public class ViewTransHelper {
    private static final int STATE_IDLE = 0;

    private static final int STATE_DRAGGING = 1;

    private static final int STATE_SETTLING = 2;

    private static final int STATE_SCALING = 3;

    private int mTouchState;

    private View mRootView;
    private Callback mCallback;

    //与single drag pointer相关
    private int mTouchSlop;
    private int mActivePointerId;

    private VelocityTracker mVelocityTracker;
    private float mMinVelocity,mMaxVelocity;

    private float mLastMotionX,mLastMotionY;

    private ScrollRunnable mScrollRunnable;

    //与multi scale相关
    private float mSpanSlop;
    private float mInitialSpan,mMinSpan;

    private float mLastSpanX,mLastSpanY;

    private float mInstantFocusX,mInstantFocusY;
    private float mInstantSpanX,mInstantSpanY;

    //tap、double tap
    private GestureDetector mGestureDetector;
    private OnTapListener mOnTapListener;

    public ViewTransHelper(@NonNull View rootView,@NonNull Callback callback){
        Context context = rootView.getContext();
        mRootView = rootView;
        mCallback = callback;

        mTouchState = STATE_IDLE;

        mScrollRunnable = new ScrollRunnable(context);

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaxVelocity = configuration.getScaledMaximumFlingVelocity();

        mSpanSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 2;
        final Resources res = context.getResources();
        mTouchMinMajor = res.getDimensionPixelSize(R.dimen.config_minScalingTouchMajor);
        mMinSpan = res.getDimensionPixelSize(R.dimen.config_minScalingSpan);

        mGestureDetector = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                doScale(mCallback.getScaleLevel(),mCallback.getScaleLevel(),e.getX(),e.getY());
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if(mOnTapListener!=null)
                    return mOnTapListener.onTap(e.getX(),e.getY());
                else
                    return super.onSingleTapUp(e);
            }
        });
    }

    public void setOnTapListener(OnTapListener listener){
        mOnTapListener = listener;
    }

    private boolean doFling(int dx,int dy){
        boolean ret = mCallback.onFling(dx,dy);
        ViewCompat.postInvalidateOnAnimation(mRootView);
        return ret;
    }

    private void doDrag(int dx, int dy){
        mCallback.onDrag(dx,dy);
        ViewCompat.postInvalidateOnAnimation(mRootView);
    }

    private void doScale(float sx, float sy, float px, float py){
        mCallback.onScale(sx,sy,px,py);
        ViewCompat.postInvalidateOnAnimation(mRootView);
    }

    public boolean processTouchEvent(MotionEvent ev){
        mGestureDetector.onTouchEvent(ev);

        final int action = ev.getActionMasked();
        final boolean scaleEnableX = mCallback.canScaleHorizontal();
        final boolean scaleEnableY = mCallback.canScaleVertical();

        //up、cancel时结束scale；drag等处理
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
            if (mTouchState==STATE_SCALING ) {
                mTouchState = STATE_IDLE;
                mInitialSpan = 0;
                mLastSpanX = 0;
                mLastSpanY = 0;
            }

            clearTouchHistory();

            if(mTouchState ==STATE_DRAGGING && action==MotionEvent.ACTION_UP){
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                int xVel = (int) velocityTracker.getXVelocity(mActivePointerId);
                int yVel = (int) velocityTracker.getYVelocity(mActivePointerId);
                if(Math.abs(xVel)>mMinVelocity || Math.abs(yVel)>mMinVelocity)
                    mScrollRunnable.startFling(xVel,yVel);
            }

            if (mVelocityTracker != null) {
                mActivePointerId = -1;
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            return true;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        if(scaleEnableX || scaleEnableY)
            addTouchHistory(ev);

        switch (action){
            case MotionEvent.ACTION_DOWN:{
                final float x = ev.getX();
                final float y = ev.getY();
                mActivePointerId = ev.getPointerId(0);

                mLastMotionX = x;
                mLastMotionY = y;
                //scale数据的reset
                mInitialSpan = 0;

                if(mTouchState ==STATE_SETTLING){
                    mScrollRunnable.abortAnimation();
                    mTouchState = STATE_DRAGGING;
                    final ViewParent parent = mRootView.getParent();
                    if (parent != null)
                        parent.requestDisallowInterceptTouchEvent(true);
                }else
                    mTouchState = STATE_IDLE;
                break;
            }

            //刷新参数
            case MotionEvent.ACTION_POINTER_DOWN:
            //如果只剩一个，就reset scale,否则需要计算
            case MotionEvent.ACTION_POINTER_UP: {
                final int actionIndex = ev.getActionIndex();
                final int actionId = ev.getPointerId(actionIndex);

                if(!scaleEnableX && !scaleEnableY){
                    if(action==MotionEvent.ACTION_POINTER_DOWN){
                        mActivePointerId = actionId;
                        mLastMotionX = ev.getX(actionIndex);
                        mLastMotionY = ev.getY(actionIndex);
                    }else{
                        if (actionId == mActivePointerId) {
                            final int newIndex = actionIndex == 0 ? 1 : 0;
                            mActivePointerId = ev.getPointerId(newIndex);
                            mLastMotionX = ev.getX(newIndex);
                            mLastMotionY = ev.getY(newIndex);
                        }
                    }
                }else{
                    final int count = ev.getPointerCount();
                    final int div = action == MotionEvent.ACTION_POINTER_UP ? count - 1 : count;

                    if (div == 1) {//一定是pointer up
                        final int newIndex = actionIndex == 0 ? 1 : 0;
                        mActivePointerId = ev.getPointerId(newIndex);
                        mLastMotionX = ev.getX(newIndex);
                        mLastMotionY = ev.getY(newIndex);

                        mInitialSpan = 0;
                        mLastSpanX = 0;
                        mLastSpanY = 0;
                        mTouchState = STATE_IDLE;
                    }else {//scaleEnableX || scaleEnableY
                        refreshInstantScaleInfo(ev);
                        float instantSpan = (float) Math.sqrt(mInstantSpanX*mInstantSpanX+mInstantSpanY*mInstantSpanY);
                        mInitialSpan = instantSpan;
                        mLastSpanX = mInstantSpanX;
                        mLastSpanY = mInstantSpanY;
                        if(!(instantSpan>mMinSpan && mTouchState==STATE_SCALING))
                            mTouchState = STATE_IDLE;
                    }
                }
                break;
            }

            //是drag还是scale，都需要先计算相应的值
            case MotionEvent.ACTION_MOVE: {
                final int count = ev.getPointerCount();
                //ScaleGestureDetector的处理是如果span值过小，即各个pointers距离focus pointer的平均距离非常小，
                //就停止scale。但是，如果此时手指再散开呢？从流畅性来讲，应该是不需要判断就进入scale状态
                //所以，这里不做这种处理。只是在ACTION_POINTER_DOWN、ACTION_POINTER_UP时考虑
                if(count>1 && (scaleEnableX || scaleEnableY)){
                    refreshInstantScaleInfo(ev);

                    float instantSpanSquare = mInstantSpanX*mInstantSpanX+mInstantSpanY*mInstantSpanY;
                    float minSpanSquare = mMinSpan*mMinSpan;
                    float initialSpanSquare = mInitialSpan*mInitialSpan;
                    float spanSlopSquare = mSpanSlop*mSpanSlop;

                    if (mTouchState!=STATE_SCALING && instantSpanSquare >=  minSpanSquare && Math.abs(instantSpanSquare - initialSpanSquare) > spanSlopSquare) {
                        mTouchState = STATE_SCALING;
                        mLastSpanX = mInstantSpanX;
                        mLastSpanY  = mInstantSpanY;
                    }else if (mTouchState==STATE_SCALING) {
                        doScale(mInstantSpanX/mLastSpanX,mInstantSpanY/mLastSpanY,mInstantFocusX,mInstantFocusY);

                        mLastSpanX = mInstantSpanX;
                        mLastSpanY  = mInstantSpanY;
                    }
                }else{
                    int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    float x = ev.getX(pointerIndex);
                    float y = ev.getY(pointerIndex);
                    int dx = (int) (x-mLastMotionX);
                    int dy = (int) (y-mLastMotionY);
                    if(mTouchState!=STATE_DRAGGING){
                        if(checkTouchSlop(dx,dy))
                            mTouchState = STATE_DRAGGING;
                    }else {
                        doDrag(dx, dy);
                        mLastMotionX = x;
                        mLastMotionY = y;
                    }
                }
                break;
            }
        }

        return true;
    }

    private boolean checkTouchSlop(int dx, int dy) {
        boolean dragEnableX = mCallback.canDragHorizontal();
        boolean dragEnableY = mCallback.canDragVertical();

        if (dragEnableX && dragEnableY)
            return dx * dx + dy * dy > mTouchSlop * mTouchSlop;
        else if (dragEnableX)
            return Math.abs(dx) > mTouchSlop;
        else if (dragEnableY)
            return Math.abs(dy) > mTouchSlop;
        return false;
    }

    private final static long TOUCH_STABILIZE_TIME = 128;
    private float mTouchMinMajor;
    private int mTouchHistoryDirection;
    private float mTouchUpper,mTouchLower;
    private float mTouchHistoryLastAccepted;
    private long mTouchHistoryLastAcceptedTime;

    private void addTouchHistory(MotionEvent ev) {
        final long currentTime = SystemClock.uptimeMillis();
        final int count = ev.getPointerCount();
        boolean accept = currentTime - mTouchHistoryLastAcceptedTime >= TOUCH_STABILIZE_TIME;
        float total = 0;
        int sampleCount = 0;
        for (int i = 0; i < count; i++) {
            final boolean hasLastAccepted = !Float.isNaN(mTouchHistoryLastAccepted);
            final int historySize = ev.getHistorySize();
            final int pointerSampleCount = historySize + 1;
            for (int h = 0; h < pointerSampleCount; h++) {
                float major;
                if (h < historySize) {
                    major = ev.getHistoricalTouchMajor(i, h);
                } else {
                    major = ev.getTouchMajor(i);
                }
                if (major < mTouchMinMajor) major = mTouchMinMajor;
                total += major;

                if (Float.isNaN(mTouchUpper) || major > mTouchUpper) {
                    mTouchUpper = major;
                }
                if (Float.isNaN(mTouchLower) || major < mTouchLower) {
                    mTouchLower = major;
                }

                if (hasLastAccepted) {
                    final int directionSig = (int) Math.signum(major - mTouchHistoryLastAccepted);
                    if (directionSig != mTouchHistoryDirection ||
                            (directionSig == 0 && mTouchHistoryDirection == 0)) {
                        mTouchHistoryDirection = directionSig;
                        mTouchHistoryLastAcceptedTime = h < historySize ? ev.getHistoricalEventTime(h)
                                : ev.getEventTime();
                        accept = false;
                    }
                }
            }
            sampleCount += pointerSampleCount;
        }

        final float avg = total / sampleCount;

        if (accept) {
            float newAccepted = (mTouchUpper + mTouchLower + avg) / 3;
            mTouchUpper = (mTouchUpper + newAccepted) / 2;
            mTouchLower = (mTouchLower + newAccepted) / 2;
            mTouchHistoryLastAccepted = newAccepted;
            mTouchHistoryDirection = 0;
            mTouchHistoryLastAcceptedTime = ev.getEventTime();
        }
    }

    /**
     * Clear all touch history tracking. Useful in ACTION_CANCEL or ACTION_UP.
     * @see #addTouchHistory(MotionEvent)
     */
    private void clearTouchHistory() {
        mTouchUpper = Float.NaN;
        mTouchLower = Float.NaN;
        mTouchHistoryLastAccepted = Float.NaN;
        mTouchHistoryDirection = 0;
        mTouchHistoryLastAcceptedTime = 0;
    }

    private void refreshInstantScaleInfo(MotionEvent ev){
        final int action = ev.getActionMasked();
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? ev.getActionIndex() : -1;

        float sumX = 0, sumY = 0;
        final int count = ev.getPointerCount();
        final int div = pointerUp ? count - 1 : count;

        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            sumX += ev.getX(i);
            sumY += ev.getY(i);
        }

        mInstantFocusX = sumX / div;
        mInstantFocusY = sumY / div;

        float devSumX = 0, devSumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;

            final float touchSize = mTouchHistoryLastAccepted / 2;
            devSumX += Math.abs(ev.getX(i) - mInstantFocusX) + touchSize;
            devSumY += Math.abs(ev.getY(i) - mInstantFocusY) + touchSize;
        }
        final float devX = devSumX / div;
        final float devY = devSumY / div;

        mInstantSpanX = devX * 2;
        mInstantSpanY = devY * 2;
    }

    private class ScrollRunnable implements Runnable{
        private Scroller mScroller;
        private int mLastX,mLastY;

        ScrollRunnable(Context context){
            mScroller = new Scroller(context);
        }

        void startFling(int xVel,int yVel){
            mTouchState = STATE_SETTLING;
            mLastX = 0;
            mLastY = 0;
            mScroller.fling(0,0,xVel,yVel,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);
            ViewCompat.postOnAnimation(mRootView,this);
        }

        void abortAnimation(){
            if(!mScroller.isFinished())
                mScroller.abortAnimation();
        }

        @Override
        public void run() {
            if(mScroller.computeScrollOffset()) {
                int curX = mScroller.getCurrX();
                int curY = mScroller.getCurrY();
                boolean over = doFling(curX-mLastX,curY-mLastY);
                if(over) {
                    mScroller.abortAnimation();
                    mTouchState = STATE_IDLE;
                }
                mLastX = curX;
                mLastY = curY;
                ViewCompat.postOnAnimation(mRootView,this);
            }else
                mTouchState = STATE_IDLE;
        }
    }

    public interface Callback{

        boolean canDragHorizontal();

        boolean canDragVertical();

        boolean canScaleHorizontal();

        boolean canScaleVertical();

        //指double tap时，放大的系数，例如1.2f
        float getScaleLevel();

        void onScale(float sx, float sy, float px, float py);

        void onDrag(int dx, int dy);

        boolean onFling(int dx, int dy);
    }

    public interface OnTapListener{
        boolean onTap(float x, float y);
    }

}
