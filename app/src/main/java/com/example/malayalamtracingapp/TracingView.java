package com.example.malayalamtracingapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast; // For displaying feedback messages
import android.util.Log; // For logging debug information

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner; // No longer used for main parsing, but might be for other utilities
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom View for tracing characters based on SVG path data.
 * This class handles:
 * 1. Parsing SVG 'd' attribute strings into Android Path objects.
 * 2. Drawing the template character.
 * 3. Capturing and drawing the user's tracing path.
 * 4. Providing visual feedback (correct/incorrect color) during tracing.
 * 5. Evaluating the correctness of each completed stroke.
 */
public class TracingView extends View {

    private static final String TAG = "TracingView"; // Tag for Logcat messages

    // Paint objects for drawing
    private Paint templatePaint; // For the sample character
    private Paint tracePaint;    // For the user's tracing
    private Paint feedbackPaint; // For visual feedback (e.g., correct/incorrect path)
    private Paint debugPaint;    // For drawing a debug rectangle

    // Path objects to store drawing data
    private Path currentTracePath; // Path for the current stroke being drawn by the user
    private List<Path> completedTracePaths; // List to store all completed strokes by the user
    private Path templateCharacterPath; // Path for the sample Malayalam alphabet character (original, unscaled)
    private Path scaledTemplateCharacterPath; // Scaled version of the template path for drawing and comparison

    // Last touch coordinates
    private float lastX, lastY;
    private static final float TOUCH_TOLERANCE = 4; // Tolerance for smooth drawing

    // Feedback mechanism variables
    private boolean isTracingCorrect = true; // Flag to indicate if the current trace is correct
    // This threshold defines how far the user's trace can be from the template path to be considered correct.
    // Adjust this value based on desired leniency.
    private static final float FEEDBACK_DISTANCE_THRESHOLD = 30; // Max distance in pixels

    // New: Completeness threshold for correctness check (e.g., 0.95 means 95% of template must be covered)
    private static final float COMPLETENESS_THRESHOLD = 0.95f; // User must trace at least 95% of the template's length

    // Dimensions of the view after layout
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
        // Initialize template paint (for the character to trace)
        templatePaint = new Paint();
        templatePaint.setColor(Color.BLACK); // Set to Black for maximum visibility
        templatePaint.setStyle(Paint.Style.STROKE); // Changed to STROKE for outline only
        templatePaint.setStrokeJoin(Paint.Join.ROUND);
        templatePaint.setStrokeCap(Paint.Cap.ROUND);
        templatePaint.setStrokeWidth(10); // Increased stroke width for better visibility
        templatePaint.setAntiAlias(true);

        // Initialize trace paint (for user's drawing)
        tracePaint = new Paint();
        tracePaint.setColor(Color.BLUE); // Default blue for tracing
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeWidth(25); // Slightly thicker than template (if template was stroke only)
        tracePaint.setAntiAlias(true);

        // Initialize feedback paint (for highlighting correct/incorrect segments)
        feedbackPaint = new Paint();
        feedbackPaint.setStyle(Paint.Style.STROKE);
        feedbackPaint.setStrokeJoin(Paint.Join.ROUND);
        feedbackPaint.setStrokeCap(Paint.Cap.ROUND);
        feedbackPaint.setStrokeWidth(25);
        feedbackPaint.setAntiAlias(true);

        // Initialize debug paint
        debugPaint = new Paint();
        debugPaint.setColor(Color.RED); // Bright red for debug rectangle
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(5);

        // Initialize paths
        currentTracePath = new Path();
        completedTracePaths = new ArrayList<>();
        templateCharacterPath = new Path();
        scaledTemplateCharacterPath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        Log.d(TAG, "onSizeChanged: View dimensions - Width: " + viewWidth + ", Height: " + viewHeight);
        // When view size changes, re-scale the template path if it's already set
        if (!templateCharacterPath.isEmpty()) {
            scalePathToView();
        }
    }

    /**
     * Sets the template character using an SVG path data string (the 'd' attribute).
     * This method parses the SVG path and sets it as the character to trace.
     * @param svgPathData The SVG path data string (e.g., "M 10 10 L 90 10 Z").
     */
    public void setSvgPath(String svgPathData) {
        templateCharacterPath = parseSvgPath(svgPathData);
        if (viewWidth > 0 && viewHeight > 0) { // Only scale if view dimensions are available
            scalePathToView();
        }
        resetTracing(); // Reset any existing trace when a new character is set
        invalidate(); // Redraw the view with the new character
    }

    /**
     * Scales and translates the template path to fit within the TracingView,
     * maintaining aspect ratio and centering it.
     */
    private void scalePathToView() {
        if (templateCharacterPath.isEmpty() || viewWidth == 0 || viewHeight == 0) {
            Log.w(TAG, "scalePathToView: Template path is empty or view dimensions are zero. Cannot scale.");
            scaledTemplateCharacterPath.reset();
            return;
        }

        RectF bounds = new RectF();
        templateCharacterPath.computeBounds(bounds, true);

        float pathWidth = bounds.width();
        float pathHeight = bounds.height();

        Log.d(TAG, "scalePathToView: Original path bounds - Left: " + bounds.left + ", Top: " + bounds.top +
                ", Right: " + bounds.right + ", Bottom: " + bounds.bottom +
                ", Width: " + pathWidth + ", Height: " + pathHeight);


        if (pathWidth == 0 || pathHeight == 0) { // Avoid division by zero
            Log.e(TAG, "scalePathToView: Path width or height is zero, cannot scale.");
            scaledTemplateCharacterPath.reset();
            return;
        }

        // Calculate scale factors
        float scaleX = (viewWidth * 0.7f) / pathWidth;  // 70% of view width for more padding
        float scaleY = (viewHeight * 0.7f) / pathHeight; // 70% of view height for more padding
        float scale = Math.min(scaleX, scaleY); // Use the smaller scale to fit within both dimensions

        // Calculate translation to center the path
        float translateX = (viewWidth - (pathWidth * scale)) / 2f - bounds.left * scale;
        float translateY = (viewHeight - (pathHeight * scale)) / 2f - bounds.top * scale;

        Log.d(TAG, "scalePathToView: Calculated scale: " + scale +
                ", translateX: " + translateX + ", translateY: " + translateY);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);

        scaledTemplateCharacterPath.reset();
        templateCharacterPath.transform(matrix, scaledTemplateCharacterPath);

        RectF scaledBounds = new RectF();
        scaledTemplateCharacterPath.computeBounds(scaledBounds, true);
        Log.d(TAG, "scalePathToView: Scaled path bounds - Left: " + scaledBounds.left + ", Top: " + scaledBounds.top +
                ", Right: " + scaledBounds.right + ", Bottom: " + scaledBounds.bottom +
                ", Scaled Width: " + scaledBounds.width() + ", Scaled Height: " + scaledBounds.height());
    }


    /**
     * Clears the user's trace and resets the view.
     */
    public void resetTracing() {
        currentTracePath.reset();
        completedTracePaths.clear();
        isTracingCorrect = true; // Reset feedback
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Debug: Draw a red rectangle to confirm view is drawing
        canvas.drawRect(0, 0, getWidth(), getHeight(), debugPaint);
        Log.d(TAG, "onDraw: Debug rectangle drawn. View width: " + getWidth() + ", height: " + getHeight());

        // 1. Draw all completed trace paths (drawn first, so template can be on top)
        for (Path path : completedTracePaths) {
            canvas.drawPath(path, tracePaint);
        }

        // 2. Draw the current trace path (the one being drawn)
        // Apply feedback color to the current path
        feedbackPaint.setColor(isTracingCorrect ? Color.GREEN : Color.RED);
        canvas.drawPath(currentTracePath, feedbackPaint);

        // 3. Draw the scaled template character LAST, so it's always visible on top
        if (!scaledTemplateCharacterPath.isEmpty()) {
            canvas.drawPath(scaledTemplateCharacterPath, templatePaint);
            Log.d(TAG, "onDraw: Scaled template character path drawn.");
        } else {
            Log.w(TAG, "onDraw: Scaled template character path is empty, not drawing.");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start a new path segment
                currentTracePath.moveTo(x, y);
                lastX = x;
                lastY = y;
                // Assume correct at the start of a new stroke, will be updated by feedback logic
                isTracingCorrect = true; // Reset feedback for new stroke
                break;

            case MotionEvent.ACTION_MOVE:
                // Draw a line segment from the last point to the current point
                float dx = Math.abs(x - lastX);
                float dy = Math.abs(y - lastY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    // Use quadTo for smoother curves, or lineTo for straight segments
                    currentTracePath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;

                    // --- Dynamic Feedback Logic ---
                    // Check if the current point (x, y) is near the scaled template path.
                    isTracingCorrect = isPointNearTemplate(x, y);
                }
                break;

            case MotionEvent.ACTION_UP:
                // Add the completed path to the list of completed paths
                currentTracePath.lineTo(x, y); // Ensure the last point is included

                // Perform final correctness check for the entire completed stroke
                boolean strokeOverallCorrect = checkStrokeCorrectness(currentTracePath);
                if (strokeOverallCorrect) {
                    Toast.makeText(getContext(), "Stroke Correct!", Toast.LENGTH_SHORT).show();
                    completedTracePaths.add(new Path(currentTracePath)); // Add only if correct
                } else {
                    Toast.makeText(getContext(), "Stroke Incorrect! Try again.", Toast.LENGTH_SHORT).show();
                    // Optionally, don't add the incorrect path, or add it in a different color
                }

                currentTracePath.reset(); // Clear for the next stroke
                isTracingCorrect = true; // Reset feedback for the next stroke
                break;
        }

        invalidate(); // Request a redraw to update the screen
        return true; // Indicate that we have handled the touch event
    }

    /**
     * Parses an SVG path data string (the 'd' attribute) into an Android Path object.
     * This parser supports a subset of SVG path commands: M, L, H, V, C, S, Q, T, A, Z.
     * It's a robust implementation that handles concatenated commands and numbers.
     *
     * @param svgPathData The SVG path data string.
     * @return An Android Path object representing the SVG path.
     */
    private Path parseSvgPath(String svgPathData) {
        Path path = new Path();
        if (svgPathData == null || svgPathData.trim().isEmpty()) {
            Log.e(TAG, "parseSvgPath: Received null or empty SVG path data.");
            return path;
        }

        // Regex to match either a single letter (command) or a number (including decimals, signs, scientific notation)
        // This pattern is designed to correctly tokenize strings like "M100 200L300-400"
        Pattern tokenPattern = Pattern.compile("([A-Za-z])|([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)");
        Matcher tokenMatcher = tokenPattern.matcher(svgPathData);

        List<String> tokens = new ArrayList<>();
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group());
        }
        Log.d(TAG, "parseSvgPath: Tokenized SVG data: " + tokens);

        float currentX = 0, currentY = 0;
        float lastControlX = 0, lastControlY = 0;
        String currentCommand = "";
        int tokenIndex = 0;

        try {
            while (tokenIndex < tokens.size()) {
                String token = tokens.get(tokenIndex);

                // Determine if the current token is a command or an argument
                if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                    currentCommand = token;
                    tokenIndex++; // Consume the command token
                    Log.d(TAG, "parseSvgPath: Identified command: " + currentCommand);
                } else {
                    // If it's not a command, it must be an argument for the *last* command.
                    // This handles cases like "M 10 10 20 20" where "20 20" implicitly uses 'L'.
                    // For now, we assume explicit commands, but this is where implicit command logic would go.
                    // If no command has been seen yet, this is an error.
                    if (currentCommand.isEmpty()) {
                        Log.e(TAG, "parseSvgPath: Found argument '" + token + "' before any command.");
                        path.reset();
                        return path;
                    }
                    // Do not increment tokenIndex here; the switch statement will consume arguments.
                }

                // Process the command and its arguments
                switch (currentCommand) {
                    case "M": // moveto (absolute)
                    case "m": // moveto (relative)
                        float x = Float.parseFloat(tokens.get(tokenIndex++));
                        float y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("m")) { // Relative move
                            currentX += x;
                            currentY += y;
                        } else { // Absolute move
                            currentX = x;
                            currentY = y;
                        }
                        path.moveTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + " " + y + " -> current: " + currentX + "," + currentY);
                        // After an M/m, subsequent numbers are L/l implicitly
                        if (currentCommand.equals("M")) currentCommand = "L";
                        else if (currentCommand.equals("m")) currentCommand = "l";
                        break;

                    case "L": // lineto (absolute)
                    case "l": // lineto (relative)
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("l")) { // Relative line
                            currentX += x;
                            currentY += y;
                        } else { // Absolute line
                            currentX = x;
                            currentY = y;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + " " + y + " -> current: " + currentX + "," + currentY);
                        break;

                    case "H": // horizontal lineto (absolute)
                    case "h": // horizontal lineto (relative)
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("h")) { // Relative horizontal
                            currentX += x;
                        } else { // Absolute horizontal
                            currentX = x;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + x + " -> current: " + currentX + "," + currentY);
                        break;

                    case "V": // vertical lineto (absolute)
                    case "v": // vertical lineto (relative)
                        y = Float.parseFloat(tokens.get(tokenIndex++));
                        if (currentCommand.equals("v")) { // Relative vertical
                            currentY += y;
                        } else { // Absolute vertical
                            currentY = y;
                        }
                        path.lineTo(currentX, currentY);
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + y + " -> current: " + currentX + "," + currentY);
                        break;

                    case "C": // curveto (absolute)
                    case "c": // curveto (relative)
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
                        } else { // Absolute cubic
                            path.cubicTo(c1x, c1y, c2x, c2y, x, y);
                            lastControlX = c2x;
                            lastControlY = c2y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + c1x + "," + c1y + " " + c2x + "," + c2y + " " + x + "," + y + " -> current: " + currentX + "," + currentY);
                        break;

                    case "S": // shorthand curveto (absolute)
                    case "s": // shorthand curveto (relative)
                        c1x = currentX + (currentX - lastControlX); // Reflect previous control point
                        c1y = currentY + (currentY - lastControlY); // Reflect previous control point

                        c2x = Float.parseFloat(tokens.get(tokenIndex++));
                        c2y = Float.parseFloat(tokens.get(tokenIndex++));
                        x = Float.parseFloat(tokens.get(tokenIndex++));
                        y = Float.parseFloat(tokens.get(tokenIndex++));

                        if (currentCommand.equals("s")) { // Relative shorthand cubic
                            path.cubicTo(c1x, c1y,
                                    currentX + c2x, currentY + c2y,
                                    currentX + x, currentY + y);
                            lastControlX = currentX + c2x;
                            lastControlY = currentY + c2y;
                            currentX += x;
                            currentY += y;
                        } else { // Absolute shorthand cubic
                            path.cubicTo(c1x, c1y, c2x, c2y, x, y);
                            lastControlX = c2x;
                            lastControlY = c2y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (shorthand) -> current: " + currentX + "," + currentY);
                        break;

                    case "Q": // quadratic Bezier curveto (absolute)
                    case "q": // quadratic Bezier curveto (relative)
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
                        } else { // Absolute quadratic
                            path.quadTo(c1x, c1y, x, y);
                            lastControlX = c1x;
                            lastControlY = c1y;
                            currentX = x;
                            currentY = y;
                        }
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " " + c1x + "," + c1y + " " + x + "," + y + " -> current: " + currentX + "," + currentY);
                        break;

                    case "T": // shorthand quadratic Bezier curveto (absolute)
                    case "t": // shorthand quadratic Bezier curveto (relative)
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
                        lastControlX = c1x; // The actual control point for T is the reflected one
                        lastControlY = c1y;
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (shorthand) -> current: " + currentX + "," + currentY);
                        break;

                    case "A": // elliptical arc (absolute)
                    case "a": // elliptical arc (relative)
                        // Arc commands are complex. For a full implementation, you'd need
                        // to convert SVG arc parameters to Android Path.arcTo().
                        // For now, we'll just consume the parameters and fall back to a lineTo.
                        float rx = Float.parseFloat(tokens.get(tokenIndex++));
                        float ry = Float.parseFloat(tokens.get(tokenIndex++));
                        float xAxisRotation = Float.parseFloat(tokens.get(tokenIndex++));
                        int largeArcFlag = Integer.parseInt(tokens.get(tokenIndex++));
                        int sweepFlag = Integer.parseInt(tokens.get(tokenIndex++));
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
                        Log.d(TAG, "parseSvgPath: " + currentCommand + " (arc) to " + currentX + "," + currentY + " (simplified to line)");
                        break;

                    case "Z": // closepath
                    case "z": // closepath
                        path.close();
                        Log.d(TAG, "parseSvgPath: Z (close path)");
                        break;

                    default:
                        Log.e(TAG, "parseSvgPath: Unknown or unhandled SVG path command: '" + currentCommand + "'");
                        path.reset(); // Clear path if an unhandled command breaks parsing
                        return path; // Exit parsing
                }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            Log.e(TAG, "parseSvgPath: Missing or invalid argument for command '" + currentCommand + "'. Error: " + e.getMessage());
            Log.e(TAG, "parseSvgPath: Full error trace: ", e);
            path.reset(); // Clear path if arguments are missing/invalid
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

    /**
     * Checks if a given point (px, py) is "near" the scaled template character path.
     * This uses PathMeasure to sample points along the template path and finds the
     * minimum distance to the current touch point.
     *
     * @param px The x-coordinate of the touch point.
     * @param py The y-coordinate of the touch point.
     * @return True if the point is within the FEEDBACK_DISTANCE_THRESHOLD of the template path, false otherwise.
     */
    private boolean isPointNearTemplate(float px, float py) {
        if (scaledTemplateCharacterPath == null || scaledTemplateCharacterPath.isEmpty()) {
            return false;
        }

        PathMeasure pm = new PathMeasure(scaledTemplateCharacterPath, false);
        float[] coords = new float[2];
        float minDistanceSq = Float.MAX_VALUE; // Squared distance for performance

        do {
            float length = pm.getLength();
            // Sample points along the path. Adjust step for accuracy vs. performance.
            // Smaller step = more accurate but slower.
            float step = 10f;
            for (float i = 0; i < length; i += step) {
                pm.getPosTan(i, coords, null);
                float dx = px - coords[0];
                float dy = py - coords[1];
                float distSq = dx * dx + dy * dy; // Calculate squared distance
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                }
            }
        } while (pm.nextContour()); // Check all contours (sub-paths) if any

        // Compare squared minimum distance with squared threshold
        return minDistanceSq <= (FEEDBACK_DISTANCE_THRESHOLD * FEEDBACK_DISTANCE_THRESHOLD);
    }

    /**
     * Evaluates the correctness of a completed user stroke against the template path.
     * This method iterates through points on the user's stroke and checks if each point
     * is within the FEEDBACK_DISTANCE_THRESHOLD of the template path.
     * It also checks if a sufficient portion of the template path has been covered.
     *
     * @param userStrokePath The Path object representing the user's completed stroke.
     * @return True if the stroke is considered correct, false otherwise.
     */
    private boolean checkStrokeCorrectness(Path userStrokePath) {
        if (userStrokePath == null || userStrokePath.isEmpty() || scaledTemplateCharacterPath.isEmpty()) {
            return false;
        }

        PathMeasure userPm = new PathMeasure(userStrokePath, false);
        float[] userCoords = new float[2];
        float userLength = userPm.getLength();

        // Get the total length of the template path
        PathMeasure templatePm = new PathMeasure(scaledTemplateCharacterPath, false);
        float templateTotalLength = 0;
        do {
            templateTotalLength += templatePm.getLength();
        } while (templatePm.nextContour());

        Log.d(TAG, "checkStrokeCorrectness: User stroke length: " + userLength + ", Template total length: " + templateTotalLength);

        // Check for completeness: User's stroke length must be a significant percentage of the template's length
        if (templateTotalLength > 0 && (userLength / templateTotalLength) < COMPLETENESS_THRESHOLD) {
            Log.d(TAG, "checkStrokeCorrectness: Stroke too short. Completeness: " + (userLength / templateTotalLength) * 100 + "%");
            return false; // Not enough of the template was traced
        }

        // Check if every sampled point on the user's stroke is near the template
        float sampleStep = 20f; // Sample every 20 units along the user's path

        if (userLength < sampleStep) { // Handle very short strokes by checking just the last point
            return isPointNearTemplate(lastX, lastY);
        }

        for (float i = 0; i < userLength; i += sampleStep) {
            userPm.getPosTan(i, userCoords, null);
            if (!isPointNearTemplate(userCoords[0], userCoords[1])) {
                Log.d(TAG, "checkStrokeCorrectness: Point (" + userCoords[0] + ", " + userCoords[1] + ") is too far from template.");
                return false; // Found an incorrect segment
            }
        }

        Log.d(TAG, "checkStrokeCorrectness: Stroke is correct and sufficiently complete.");
        return true; // All sampled points were correct and length is sufficient
    }
}
