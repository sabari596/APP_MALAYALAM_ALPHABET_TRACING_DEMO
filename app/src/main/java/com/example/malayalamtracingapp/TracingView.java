package com.example.malayalamtracingapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TracingView extends View {

    private static final String TAG = "TracingView";
    private Paint templatePaint;
    private Paint highlightPaint;
    private Paint tracePaint;
    private Paint feedbackPaint;
    private Paint debugPaint;


    private Path currentTracePath;
    private List<Path> completedTracePaths;
    private List<Path> originalTemplateStrokes;
    private List<Path> scaledTemplateStrokes;

    private int currentStrokeIndex = 0;

    // Last touch coordinates
    private float lastX, lastY;
    private static final float TOUCH_TOLERANCE = 4;


    private boolean isTracingCorrect = true;

    private static final float FEEDBACK_DISTANCE_THRESHOLD = 50;
    private static final float COMPLETENESS_THRESHOLD = 0.98f;


    private int viewWidth, viewHeight;

    public TracingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TracingView(Context context) {
        super(context);
        init();
    }

    /**
     * Initializes paints, paths, and other drawing resources.
     */
    private void init() {

        templatePaint = new Paint();
        templatePaint.setColor(Color.BLACK);
        templatePaint.setStyle(Paint.Style.STROKE);
        templatePaint.setStrokeJoin(Paint.Join.ROUND);
        templatePaint.setStrokeCap(Paint.Cap.ROUND);
        templatePaint.setStrokeWidth(10);
        templatePaint.setAntiAlias(true);

        templatePaint.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0));



        highlightPaint = new Paint();
        highlightPaint.setColor(Color.parseColor("#FF6F00"));
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeJoin(Paint.Join.ROUND);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setStrokeWidth(12);
        highlightPaint.setAntiAlias(true);

        highlightPaint.setPathEffect(new DashPathEffect(new float[]{20, 10}, 0));



        tracePaint = new Paint();
        tracePaint.setColor(Color.BLUE);
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeWidth(25);
        tracePaint.setAntiAlias(true);


        feedbackPaint = new Paint();
        feedbackPaint.setStyle(Paint.Style.STROKE);
        feedbackPaint.setStrokeJoin(Paint.Join.ROUND);
        feedbackPaint.setStrokeCap(Paint.Cap.ROUND);
        feedbackPaint.setStrokeWidth(25);
        feedbackPaint.setAntiAlias(true);


        debugPaint = new Paint();
        debugPaint.setColor(Color.RED);
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(5);


        currentTracePath = new Path();
        completedTracePaths = new ArrayList<>();
        originalTemplateStrokes = new ArrayList<>();
        scaledTemplateStrokes = new ArrayList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        Log.d(TAG, "onSizeChanged: View dimensions - Width: " + viewWidth + ", Height: " + viewHeight);

        if (!originalTemplateStrokes.isEmpty()) {
            scalePathsToView();
        }
    }


    public void setSvgPaths(List<String> svgPathDataList) {
        originalTemplateStrokes.clear();
        for (String svgPathData : svgPathDataList) {
            Path path = parseSvgPath(svgPathData);
            if (!path.isEmpty()) {
                originalTemplateStrokes.add(path);
            } else {
                Log.w(TAG, "setSvgPaths: Skipping empty or invalid path from SVG data: " + svgPathData);
            }
        }

        if (viewWidth > 0 && viewHeight > 0) {
            scalePathsToView();
        }
        resetTracing();
        invalidate();
    }


    private void scalePathsToView() {
        if (originalTemplateStrokes.isEmpty() || viewWidth == 0 || viewHeight == 0) {
            Log.w(TAG, "scalePathsToView: Original template strokes are empty or view dimensions are zero. Cannot scale.");
            scaledTemplateStrokes.clear();
            return;
        }

        Log.d(TAG, "scalePathsToView: Number of originalTemplateStrokes: " + originalTemplateStrokes.size());


        RectF combinedBounds = new RectF();
        boolean firstPath = true;
        for (Path path : originalTemplateStrokes) {
            RectF pathBounds = new RectF();
            path.computeBounds(pathBounds, true);
            if (firstPath) {
                combinedBounds.set(pathBounds);
                firstPath = false;
            } else {
                combinedBounds.union(pathBounds);
            }
            Log.d(TAG, "scalePathsToView: Individual path bounds in loop: " + pathBounds.toShortString());
        }


        float maxStrokeWidth = Math.max(templatePaint.getStrokeWidth(), highlightPaint.getStrokeWidth());
        maxStrokeWidth = Math.max(maxStrokeWidth, tracePaint.getStrokeWidth());

        float extraPadding = 50f;
        float padding = maxStrokeWidth / 2f + extraPadding;

        combinedBounds.left -= padding;
        combinedBounds.top -= padding;
        combinedBounds.right += padding;
        combinedBounds.bottom += padding;


        float pathWidth = combinedBounds.width();
        float pathHeight = combinedBounds.height();

        Log.d(TAG, "scalePathsToView: Combined original path bounds (with padding) - Left: " + combinedBounds.left + ", Top: " + combinedBounds.top +
                ", Right: " + combinedBounds.right + ", Bottom: " + combinedBounds.bottom +
                ", Width: " + pathWidth + ", Height: " + pathHeight);

        if (pathWidth == 0 || pathHeight == 0) {
            Log.e(TAG, "scalePathsToView: Combined path width or height is zero, cannot scale. This might indicate an issue with SVG parsing or empty paths.");
            scaledTemplateStrokes.clear();
            return;
        }


        float targetViewWidth = viewWidth * 0.8f;
        float targetViewHeight = viewHeight * 0.8f;

        float scaleX = targetViewWidth / pathWidth;
        float scaleY = targetViewHeight / pathHeight;
        float scale = Math.min(scaleX, scaleY);


        float translateX = (viewWidth - (pathWidth * scale)) / 2f - combinedBounds.left * scale;
        float translateY = (viewHeight - (pathHeight * scale)) / 2f - combinedBounds.top * scale;

        Log.d(TAG, "scalePathsToView: Calculated scale: " + scale +
                ", translateX: " + translateX + ", translateY: " + translateY);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);

        scaledTemplateStrokes.clear();
        for (Path originalPath : originalTemplateStrokes) {
            Path scaledPath = new Path();
            originalPath.transform(matrix, scaledPath);
            scaledTemplateStrokes.add(scaledPath);
        }

        RectF finalScaledBounds = new RectF();
        firstPath = true;
        for (Path scaledPath : scaledTemplateStrokes) {
            RectF pathBounds = new RectF();
            scaledPath.computeBounds(pathBounds, true);
            if (firstPath) {
                finalScaledBounds.set(pathBounds);
                firstPath = false;
            } else {
                finalScaledBounds.union(pathBounds);
            }
        }
        Log.d(TAG, "scalePathsToView: Final scaled combined bounds after transformation: " + finalScaledBounds.toShortString());
    }


    public void resetTracing() {
        currentTracePath.reset();
        completedTracePaths.clear();
        currentStrokeIndex = 0;
        isTracingCorrect = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        canvas.drawRect(0, 0, getWidth(), getHeight(), debugPaint);
        Log.d(TAG, "onDraw: Debug rectangle drawn. View width: " + getWidth() + ", height: " + getHeight());


        if (!scaledTemplateStrokes.isEmpty()) {
            for (int i = 0; i < scaledTemplateStrokes.size(); i++) {
                Path templateStroke = scaledTemplateStrokes.get(i);
                if (i == currentStrokeIndex) {
                    canvas.drawPath(templateStroke, highlightPaint); // Highlight current stroke
                } else {
                    canvas.drawPath(templateStroke, templatePaint); // Draw other strokes (completed or not yet active)
                }
            }
            Log.d(TAG, "onDraw: Scaled template strokes drawn. Current stroke index: " + currentStrokeIndex);
        } else {
            Log.w(TAG, "onDraw: Scaled template strokes list is empty, not drawing template.");
        }


        for (Path path : completedTracePaths) {
            canvas.drawPath(path, tracePaint);
        }


        feedbackPaint.setColor(isTracingCorrect ? Color.GREEN : Color.RED);
        canvas.drawPath(currentTracePath, feedbackPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent: Event received, action: " + event.getAction() + " (0=DOWN, 1=UP, 2=MOVE)"); // Added log for every touch event
        if (scaledTemplateStrokes.isEmpty() || currentStrokeIndex >= scaledTemplateStrokes.size()) {

            Log.d(TAG, "onTouchEvent: No character loaded or all strokes completed. Returning false.");
            return false;
        }

        float x = event.getX();
        float y = event.getY();


        Path currentTemplateStroke = scaledTemplateStrokes.get(currentStrokeIndex);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:

                currentTracePath.moveTo(x, y);
                lastX = x;
                lastY = y;

                isTracingCorrect = isPointNearTemplate(x, y, currentTemplateStroke);
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(x - lastX);
                float dy = Math.abs(y - lastY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    currentTracePath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;

                    if (!isPointNearTemplate(x, y, currentTemplateStroke)) {
                        isTracingCorrect = false;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                Log.d(TAG, "onTouchEvent: ACTION_UP detected.");
                currentTracePath.lineTo(x, y);


                boolean strokeOverallCorrect = checkStrokeCorrectness(currentTracePath, currentTemplateStroke);

                if (strokeOverallCorrect) {
                    completedTracePaths.add(new Path(currentTracePath)); // Add a copy of the correct path
                    currentStrokeIndex++;

                    if (currentStrokeIndex >= scaledTemplateStrokes.size()) {

                        Toast.makeText(getContext(), "Character Complete! Well Done!", Toast.LENGTH_LONG).show();

                    } else {

                        Toast.makeText(getContext(), "Stroke " + (currentStrokeIndex) + " Correct! Now trace the next part.", Toast.LENGTH_SHORT).show();
                    }
                } else {

                    Toast.makeText(getContext(), "Stroke " + (currentStrokeIndex + 1) + " Incorrect! Try again.", Toast.LENGTH_SHORT).show();

                }

                currentTracePath.reset();
                isTracingCorrect = true;
                break;
        }

        invalidate();
        Log.d(TAG, "onTouchEvent: Event processed, returning true."); // Added log for end of onTouchEvent
        return true;
    }


    private Path parseSvgPath(String svgPathData) {
        Path path = new Path();
        if (svgPathData == null || svgPathData.trim().isEmpty()) {
            Log.e(TAG, "parseSvgPath: Received null or empty SVG path data.");
            return path;
        }


        Pattern tokenPattern = Pattern.compile("([A-Za-z])|([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)");
        Matcher tokenMatcher = tokenPattern.matcher(svgPathData);

        List<String> tokens = new ArrayList<>();
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group());
        }
        Log.d(TAG, "parseSvgPath: Tokenized SVG data: " + tokens); // Added logging for tokens

        float currentX = 0, currentY = 0;
        float lastControlX = 0, lastControlY = 0;
        String currentCommand = "";
        int tokenIndex = 0;

        try {
            while (tokenIndex < tokens.size()) {
                String token = tokens.get(tokenIndex);


                if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                    currentCommand = token;
                    tokenIndex++;
                    Log.d(TAG, "parseSvgPath: Identified command: " + currentCommand); // Added logging for commands
                } else {

                    if (currentCommand.isEmpty()) {
                        Log.e(TAG, "parseSvgPath: Found argument '" + token + "' before any command.");
                        path.reset();
                        return path;
                    }

                }


                switch (currentCommand) {
                    case "M":
                    case "m":
                        float x = Float.parseFloat(tokens.get(tokenIndex++));
                        float y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("m")) { // Relative move
                            currentX += x;
                            currentY += y;
                        } else {
                            currentX = x;
                            currentY = y;
                        }
                        path.moveTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + "," + y + " -> current: " + currentX + "," + currentY); // Added logging for coordinates

                        if (currentCommand.equals("M")) currentCommand = "L";
                        else if (currentCommand.equals("m")) currentCommand = "l";
                        break;

                    case "L":
                    case "l":
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("l")) { // Relative line
                            currentX += x;
                            currentY += y;
                        } else {
                            currentX = x;
                            currentY = y;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + "," + y + " -> current: " + currentX + "," + currentY); // Added logging for coordinates
                        break;

                    case "H":
                    case "h":
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("h")) { // Relative horizontal
                            currentX += x;
                        } else {
                            currentX = x;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + " -> current: " + currentX + "," + currentY); // Added logging for coordinates
                        break;

                    case "V":
                    case "v":
                        y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("v")) { // Relative vertical
                            currentY += y;
                        } else {
                            currentY = y;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + y + " -> current: " + currentX + "," + currentY); // Added logging for coordinates
                        break;

                    case "C":
                    case "c":
                        float c1x = Float.parseFloat(tokens.get(tokenIndex++));
                        float c1y = Float.parseFloat(tokens.get(tokenIndex++));
                        float c2x = Float.parseFloat(tokens.get(tokenIndex++));
                        float c2y = Float.parseFloat(tokens.get(tokenIndex++));
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("c")) { // Relative cubic
                            path.cubicTo(currentX + c1x, currentY + c1y,
                                    currentX + c2x, currentY + c2y,
                                    currentX + x, currentY + y);
                            lastControlX = currentX + c2x;
                            lastControlY = currentY + c2y;
                            currentX += x;
                            currentY += y;
                        } else {
                            path.cubicTo(c1x, c1y, c2x, c2y, x, y);
                            lastControlX = c2x;
                            lastControlY = c2y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + c1x + "," + c1y + " " + c2x + "," + c2y + " " + x + "," + y + " -> current: " + currentX + "," + currentY); // Added logging for coordinates
                        break;

                    case "S":
                    case "s":
                        c1x = currentX + (currentX - lastControlX); // Reflect previous control point
                        c1y = currentY + (currentY - lastControlY); // Reflect previous control point

                        c2x = Float.parseFloat(tokens.get(tokenIndex++));
                        c2y = Float.parseFloat(tokens.get(tokenIndex++));
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("s")) {
                            path.cubicTo(c1x, c1y,
                                    currentX + c2x, currentY + c2y,
                                    currentX + x, currentY + y);
                            lastControlX = currentX + c2x;
                            lastControlY = currentY + c2y;
                            currentX += x;
                            currentY += y;
                        } else {
                            path.cubicTo(c1x, c1y, c2x, c2y, x, y);
                            lastControlX = c2x;
                            lastControlY = c2y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (shorthand) -> current: " + currentX + "," + currentY); // Added logging
                        break;

                    case "Q":
                    case "q":
                        c1x = Float.parseFloat(tokens.get(tokenIndex++));
                        c1y = Float.parseFloat(tokens.get(tokenIndex++));
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("q")) { // Relative quadratic
                            path.quadTo(currentX + c1x, currentY + c1y,
                                    currentX + x, currentY + y);
                            lastControlX = currentX + c1x;
                            lastControlY = currentY + c1y;
                            currentX += x;
                            currentY += y;
                        } else {
                            path.quadTo(c1x, c1y, x, y);
                            lastControlX = c1x;
                            lastControlY = c1y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + c1x + "," + c1y + " " + x + "," + y + " -> current: " + currentX + "," + currentY); // Added logging
                        break;

                    case "T":
                    case "t":
                        c1x = currentX + (currentX - lastControlX); // Reflect previous control point
                        c1y = currentY + (currentY - lastControlY); // Reflect previous control point

                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("t")) { // Relative shorthand quadratic
                            path.quadTo(c1x, c1y,
                                    currentX + x, currentY + y);
                            currentX += x;
                            currentY += y;
                        } else { // Absolute shorthand quadratic
                            path.quadTo(c1x, c1y, x, y);
                            currentX = x;
                            currentY = y;
                        }
                        lastControlX = c1x;
                        lastControlY = c1y;
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (shorthand) -> current: " + currentX + "," + currentY); // Added logging
                        break;

                    case "A": // elliptical arc (absolute)
                    case "a": // elliptical arc (relative)

                        Float.parseFloat(tokens.get(tokenIndex++)); // rx
                        Float.parseFloat(tokens.get(tokenIndex++)); // ry
                        Float.parseFloat(tokens.get(tokenIndex++)); // xAxisRotation
                        Integer.parseInt(tokens.get(tokenIndex++)); // largeArcFlag
                        Integer.parseInt(tokens.get(tokenIndex++)); // sweepFlag
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("a")) { // Relative arc
                            currentX += x;
                            currentY += y;
                        } else { // Absolute arc
                            currentX = x;
                            currentY = y;
                        }
                        path.lineTo(currentX, currentY); // Fallback to line for simplicity
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (arc) to " + currentX + "," + currentY + " (simplified to line)"); // Added logging
                        break;

                    case "Z": // closepath
                    case "z": // closepath
                        path.close();
                        Log.d(TAG, "parseSvgPath: Z (close path)"); // Added logging
                        break;

                    default:
                        Log.e(TAG, "parseSvgPath: Unknown or unhandled SVG path command: '" + currentCommand + "'");
                        path.reset();
                        return path;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "parseSvgPath: Missing argument for command '" + currentCommand + "' at token index " + tokenIndex + ". Error: " + e.getMessage());
            Log.e(TAG, "parseSvgPath: Full error trace: ", e);
            path.reset(); // Clear path if arguments are missing
        } catch (NumberFormatException e) {
            Log.e(TAG, "parseSvgPath: Invalid number format for argument of command '" + currentCommand + "' at token index " + (tokenIndex > 0 ? tokenIndex - 1 : 0) + ". Token: '" + (tokenIndex > 0 && tokenIndex <= tokens.size() ? tokens.get(tokenIndex - 1) : "N/A") + "'. Error: " + e.getMessage());
            Log.e(TAG, "parseSvgPath: Full error trace: ", e);
            path.reset(); // Clear path if number format is invalid
        } catch (Exception e) {
            Log.e(TAG, "parseSvgPath: General error during parsing. " + e.getMessage());
            Log.e(TAG, "parseSvgPath: Full error trace: ", e);
            path.reset(); // Clear path for any other unexpected errors
        } finally {
            Log.d(TAG, "parseSvgPath: Finished parsing. Path isEmpty: " + path.isEmpty());
            RectF finalBounds = new RectF();
            path.computeBounds(finalBounds, true);
            Log.d(TAG, "parseSvgPath: Final path bounds after parsing: " + finalBounds.toShortString());
        }
        return path;
    }

    private boolean isPointNearTemplate(float px, float py, Path templatePath) {
        if (templatePath == null || templatePath.isEmpty()) {
            Log.d(TAG, "isPointNearTemplate: Template path is null or empty. Returning false.");
            return false;
        }

        PathMeasure pm = new PathMeasure(templatePath, false);
        float[] coords = new float[2];
        float minDistanceSq = Float.MAX_VALUE;

        do {
            float length = pm.getLength();
            float step = 10f;
            for (float i = 0; i < length; i += step) {
                pm.getPosTan(i, coords, null);
                float dx = px - coords[0];
                float dy = py - coords[1];
                float distSq = dx * dx + dy * dy;
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                }
            }
        } while (pm.nextContour());

        boolean isNear = minDistanceSq <= (FEEDBACK_DISTANCE_THRESHOLD * FEEDBACK_DISTANCE_THRESHOLD);

        return isNear;
    }


    private boolean checkStrokeCorrectness(Path userStrokePath, Path templateStrokePath) {
        Log.d(TAG, "checkStrokeCorrectness: Starting correctness check.");
        if (userStrokePath == null || userStrokePath.isEmpty() || templateStrokePath == null || templateStrokePath.isEmpty()) {
            Log.d(TAG, "checkStrokeCorrectness: Input paths are empty or null. Returning false.");
            return false;
        }

        PathMeasure userPm = new PathMeasure(userStrokePath, false);
        float[] userCoords = new float[2];
        float userLength = userPm.getLength();

        PathMeasure templatePm = new PathMeasure(templateStrokePath, false);
        float templateLength = templatePm.getLength();

        Log.d(TAG, "checkStrokeCorrectness: User stroke length: " + userLength + ", Template stroke length: " + templateLength);


        if (templateLength > 0) {
            float completenessRatio = userLength / templateLength;
            Log.d(TAG, "checkStrokeCorrectness: Completeness Ratio: " + completenessRatio + " (Threshold: " + COMPLETENESS_THRESHOLD + ")");
            if (completenessRatio < COMPLETENESS_THRESHOLD) {
                Log.d(TAG, "checkStrokeCorrectness: Stroke too short. Completeness: " + (completenessRatio * 100) + "%");
                return false; // Not enough of the template was traced
            }
        } else {
            Log.w(TAG, "checkStrokeCorrectness: Template stroke length is zero, skipping completeness check.");

        }



        float sampleStep = 20f;

        if (userLength < sampleStep) {

            userPm.getPosTan(0, userCoords, null);
            boolean startNear = isPointNearTemplate(userCoords[0], userCoords[1], templateStrokePath);

            userPm.getPosTan(userLength, userCoords, null);
            boolean endNear = isPointNearTemplate(userCoords[0], userCoords[1], templateStrokePath);

            if (!startNear || !endNear) {
                Log.d(TAG, "checkStrokeCorrectness: Very short stroke: Start or end point not near template. StartNear: " + startNear + ", EndNear: " + endNear);
                return false;
            }
        } else {

            for (float i = 0; i <= userLength; i += sampleStep) {
                userPm.getPosTan(i, userCoords, null);
                if (!isPointNearTemplate(userCoords[0], userCoords[1], templateStrokePath)) {
                    Log.d(TAG, "checkStrokeCorrectness: Point (" + userCoords[0] + ", " + userCoords[1] + ") is too far from template stroke.");
                    return false; // Found an incorrect segment
                }
            }
        }


        Log.d(TAG, "checkStrokeCorrectness: Stroke is correct and sufficiently complete. Returning true.");
        return true;
    }
}
