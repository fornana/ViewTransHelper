package com.nalan.widget.viewtranshelper;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Author： liyi
 * Date：    2017/3/4.
 */
/*
    outset指shape放大或者移动，不管怎么变换始终都将viewport包裹在内
    手指在viewport上时才能捕获相应的事件
 */
public class OutsetTransHelper implements ViewTransHelper.Callback {
    private Rect mCurrentShape,mViewport;
    private int mShapeWidth,mShapeHeight;
    private int mShapeWidthMax,mShapeHeightMax;

    private Matrix mMatrix;
    private float[] mTempDelta;
    private RectF mTempRectF;
    private float[] mTempValues;

    private boolean mHandleTouch;
    private ViewTransHelper mTransHelper;

    public OutsetTransHelper(View root){
        mMatrix = new Matrix();
        mViewport = new Rect();
        mCurrentShape = new Rect();

        mTempRectF = new RectF();
        mTempDelta = new float[2];
        mTempValues = new float[9];

        mTransHelper = new ViewTransHelper(root,this);
    }

    public void setOnTapListener(ViewTransHelper.OnTapListener listener){
        mTransHelper.setOnTapListener(listener);
    }

    /*
        viewport - 视口，显示的区域
        shapeWidth、shapeHeight - 需要变换的区域的初始大小，初始时它的(left,top)与viewport的(left,top)一致
        minWidth、minHeight - 最小为多少，不允许为0是因为可以缩放
     */
    public void setup(Rect viewport,int shapeWidth,int shapeHeight,int maxWidth,int maxHeight){
        if(maxWidth<viewport.width() || maxHeight<viewport.height()
                || viewport.width()==0 || viewport.height()==0 || shapeWidth==0 || shapeHeight==0
                || shapeWidth<viewport.width() || shapeHeight<viewport.height()
                ||shapeWidth>maxWidth || shapeHeight>maxHeight)
            throw new IllegalArgumentException("大小错误");

        mViewport.set(viewport);
        mShapeWidth = shapeWidth;
        mShapeHeight = shapeHeight;
        mShapeWidthMax = maxWidth;
        mShapeHeightMax = maxHeight;

        mCurrentShape.set(mViewport.left,mViewport.top,mViewport.left+shapeWidth,mViewport.top+shapeHeight);
        mMatrix.reset();
        mMatrix.postTranslate(mViewport.left,mViewport.top);
    }

    /*
        设置shape的大小，在setup以后调用它
     */
    public void setCurrentShape(Rect dst){
        adjustShape(mViewport,dst,mShapeWidthMax,mShapeHeightMax);
        matrix(mMatrix,mViewport,dst,mShapeWidth,mShapeHeight);
        mCurrentShape.set(dst);
    }

    public boolean onTouchEvent(MotionEvent ev){
        boolean ret;
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        final int action = ev.getActionMasked();

        switch (action){
            case MotionEvent.ACTION_DOWN:
                if(x>=mViewport.left && x<=mViewport.right && y>=mViewport.top && y<=mViewport.bottom){
                    mHandleTouch = mTransHelper.processTouchEvent(ev);
                    return mHandleTouch;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                if(mHandleTouch)
                    mTransHelper.processTouchEvent(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                ret = mHandleTouch;
                if(mHandleTouch)
                    mTransHelper.processTouchEvent(ev);
                mHandleTouch = false;
                return ret;

            case MotionEvent.ACTION_MOVE:
                ret = mHandleTouch;
                if(mHandleTouch)
                    mTransHelper.processTouchEvent(ev);
                return ret;
        }

        return false;
    }

    public Matrix getTransformMatrix(){
        return mMatrix;
    }

    public Rect getCurrentShape(){
        return mCurrentShape;
    }

    private boolean adjustTranslate(Rect viewport, Rect src, float[] delta,int widthMax,int heightMax){
        boolean overX = false;
        boolean overY = false;

        if(src.left+delta[0]<viewport.right-widthMax) {
            delta[0] = viewport.right-widthMax - src.left;
            overX = true;
        }

        if(src.left+delta[0]>viewport.left){
            delta[0] = viewport.left-src.left;
            overX = true;
        }

        if(src.top+delta[1]<viewport.bottom-heightMax) {
            delta[1] = viewport.bottom-heightMax - src.top;
            overY = true;
        }

        if(src.top+delta[1]>viewport.top){
            delta[1] = viewport.top-src.top;
            overY = true;
        }

        if(src.right+delta[0]>viewport.left+widthMax) {
            delta[0] = viewport.left+widthMax - src.right;
            overX = true;
        }

        if(src.right+delta[0]<viewport.right){
            delta[0] = viewport.right-src.right;
            overX = true;
        }

        if(src.bottom+delta[1]>viewport.top+heightMax) {
            delta[1] = viewport.top+heightMax - src.bottom;
            overY = true;
        }

        if(src.bottom+delta[1]<viewport.bottom){
            delta[1] = viewport.bottom-src.bottom;
            overY = true;
        }

        return overX && overY;
    }

    private void adjustScale(Rect viewport,Rect src,int maxWidth,int maxHeight,float[] delta){
        int dstWidth = (int) (src.width()*delta[0]);
        int dstHeight = (int) (src.height()*delta[1]);

        dstWidth = Math.max(viewport.width(),dstWidth);
        dstWidth = Math.min(dstWidth,maxWidth);

        dstHeight = Math.max(viewport.height(),dstHeight);
        dstHeight = Math.min(dstHeight,maxHeight);

        delta[0] = dstWidth*1f/src.width();
        delta[1] = dstHeight*1f/src.height();
    }

    private void adjustShape(Rect viewport, Rect dst, int maxWidth, int maxHeight){
        int minLeft = viewport.right-maxWidth;
        int minTop = viewport.bottom-maxHeight;
        int maxLeft = viewport.left;
        int maxTop = viewport.top;

        dst.left = Math.max(minLeft,dst.left);
        dst.left = Math.min(dst.left,maxLeft);
        dst.top = Math.max(minTop,dst.top);
        dst.top = Math.min(dst.top,maxTop);

        dst.right = Math.max(viewport.right,dst.right);
        dst.right = Math.min(dst.right,viewport.left+maxWidth);
        dst.bottom = Math.max(viewport.bottom,dst.bottom);
        dst.bottom = Math.min(dst.bottom,viewport.top+maxHeight);

        if(dst.width()>maxWidth)
            dst.right = dst.left+maxWidth;
        if(dst.height()>maxHeight)
            dst.bottom = dst.top+maxHeight;
    }

    //根据dst计算出matrix
    private void matrix(Matrix matrix,Rect viewport,Rect dst,int width,int height){
        float sx = dst.width()*1f/width;
        float sy = dst.height()*1f/height;

        float dx = dst.left-viewport.left;
        float dy = dst.top-viewport.top;

        matrix.reset();
        matrix.getValues(mTempValues);
        mTempValues[Matrix.MSCALE_X] = sx;
        mTempValues[Matrix.MSCALE_Y] = sy;
        mTempValues[Matrix.MTRANS_X] = dx;
        mTempValues[Matrix.MTRANS_Y] = dy;
        matrix.setValues(mTempValues);
    }

    private boolean inRect(Rect src,Rect dst){
        return src.left>=dst.left &&src.top>=dst.top && src.right<=dst.right && src.bottom<=dst.bottom;
    }

    @Override
    public boolean canDragHorizontal() {
        return mShapeWidthMax>mViewport.width();
    }

    @Override
    public boolean canDragVertical() {
        return mShapeHeightMax>mViewport.height();
    }

    @Override
    public boolean canScaleHorizontal() {
        return true;
    }

    @Override
    public boolean canScaleVertical() {
        return true;
    }

    @Override
    public float getScaleLevel() {
        return 1.2f;
    }

    @Override
    public void onScale(float sx, float sy, float px, float py) {
        postScale(sx,sy,px,py);
    }

    @Override
    public void onDrag(int dx, int dy) {
        postTranslate(dx,dy);
    }

    @Override
    public boolean onFling(int dx, int dy) {
        return postTranslate(dx, dy);
    }

    private boolean postTranslate(int dx,int dy){
        mTempDelta[0] = dx;
        mTempDelta[1] = dy;
        boolean over = adjustTranslate(mViewport,mCurrentShape,mTempDelta,mShapeWidthMax,mShapeHeightMax);
        dx = (int) mTempDelta[0];
        dy = (int) mTempDelta[1];

        mMatrix.postTranslate(dx,dy);
        mCurrentShape.offset(dx,dy);
        return over;
    }

    private void postScale(float sx,float sy,float px,float py){
        mTempDelta[0] = sx;
        mTempDelta[1] = sy;
        adjustScale(mViewport,mCurrentShape,mShapeWidthMax,mShapeHeightMax,mTempDelta);
        sx = mTempDelta[0];
        sy = mTempDelta[1];

        mMatrix.postScale(sx,sy,px,py);
        mTempRectF.set(mViewport.left,mViewport.top,mShapeWidth,mShapeHeight);
        mMatrix.mapRect(mTempRectF);

        mCurrentShape.set((int)mTempRectF.left,(int)mTempRectF.top,(int)mTempRectF.right,(int)mTempRectF.bottom);
        adjustShape(mViewport,mCurrentShape,mShapeWidthMax,mShapeHeightMax);

        mMatrix.getValues(mTempValues);
        mTempValues[Matrix.MTRANS_X] = mCurrentShape.left-mViewport.left;
        mTempValues[Matrix.MTRANS_Y] = mCurrentShape.top-mViewport.top;
        mMatrix.setValues(mTempValues);
    }

}
