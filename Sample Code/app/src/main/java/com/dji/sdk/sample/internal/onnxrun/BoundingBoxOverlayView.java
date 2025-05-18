package com.dji.sdk.sample.internal.onnxrun;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxOverlayView extends View {


    private List<RectF> boxes = new ArrayList<>();
    private Paint boxPaint;

    public BoundingBoxOverlayView(Context context) {
        super(context);
        init();
    }

    // 👇 ЭТО ОБЯЗАТЕЛЬНО ДЛЯ ИНФЛЕЙТА ИЗ XML
    public BoundingBoxOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStrokeWidth(4);
        boxPaint.setStyle(Paint.Style.STROKE);
    }

    public void updateBoxes(List<RectF> newBoxes) {
        boxes = newBoxes;
        postInvalidate(); // перерисовать
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (RectF rect : boxes) {
            canvas.drawRect(rect, boxPaint);
        }
    }
}
