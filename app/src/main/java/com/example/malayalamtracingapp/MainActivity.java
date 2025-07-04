package com.example.malayalamtracingapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private TracingView tracingView;
    private Button resetButton, prevButton, nextButton;

    // List to store resource IDs of your SVG files
    private List<Integer> malayalamCharacterSvgRawIds;
    private int currentCharacterIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tracingView = findViewById(R.id.tracingView);
        resetButton = findViewById(R.id.resetButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);

        // Initialize Malayalam character SVG resource IDs
        malayalamCharacterSvgRawIds = new ArrayList<>();
        // Add the resource IDs for your SVG files here
        // Example: If you have malayalam_a.svg, malayalam_aa.svg, malayalam_i.svg in res/raw
        malayalamCharacterSvgRawIds.add(R.raw.a); // Replace with your actual file names
        malayalamCharacterSvgRawIds.add(R.raw.aa);
        malayalamCharacterSvgRawIds.add(R.raw.e);
        // Add all your Malayalam letter SVG resource IDs here...

        // Ensure there's at least one character to display
        if (malayalamCharacterSvgRawIds.isEmpty()) {
            Toast.makeText(this, "No Malayalam characters loaded! Please add SVG files to res/raw.", Toast.LENGTH_LONG).show();
            return;
        }

        // Set the initial character
        loadCharacter(currentCharacterIndex);

        // Set up button listeners
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tracingView.resetTracing();
                Toast.makeText(MainActivity.this, "Tracing reset!", Toast.LENGTH_SHORT).show();
            }
        });

        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentCharacterIndex > 0) {
                    currentCharacterIndex--;
                    loadCharacter(currentCharacterIndex);
                } else {
                    Toast.makeText(MainActivity.this, "First character reached!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentCharacterIndex < malayalamCharacterSvgRawIds.size() - 1) {
                    currentCharacterIndex++;
                    loadCharacter(currentCharacterIndex);
                } else {
                    Toast.makeText(MainActivity.this, "Last character reached!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Loads the character at the given index into the TracingView by reading its SVG content.
     * @param index The index of the character to load from the list.
     */
    private void loadCharacter(int index) {
        if (index >= 0 && index < malayalamCharacterSvgRawIds.size()) {
            int rawId = malayalamCharacterSvgRawIds.get(index);
            String svgContent = readRawTextFile(rawId);
            String svgPathData = extractSvgPathData(svgContent);

            if (svgPathData != null && !svgPathData.isEmpty()) {
                tracingView.setSvgPath(svgPathData);
                Toast.makeText(this, "Loading character " + (index + 1) + " of " + malayalamCharacterSvgRawIds.size(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to load SVG path data for character " + (index + 1) + ". Check SVG file format or if 'd' attribute is present.", Toast.LENGTH_LONG).show();
                tracingView.setSvgPath(""); // Clear the view if data is invalid
            }
        }
    }

    /**
     * Reads the content of a raw text file (like an SVG file) into a String.
     * @param resId The resource ID of the raw file (e.g., R.raw.my_svg_file).
     * @return The content of the file as a String, or null if an error occurs.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n"); // Append newline to preserve structure
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * Extracts all 'd' attribute values from all <path> tags in an SVG string
     * and concatenates them into a single string.
     *
     * @param svgContent The full SVG XML content as a String.
     * @return A concatenated string of all 'd' attribute values, or null if no path data found.
     */
    private String extractSvgPathData(String svgContent) {
        if (svgContent == null) {
            return null;
        }
        // Regex to find the 'd' attribute within any <path> tag.
        // The `(?s)` flag enables DOTALL mode, allowing '.' to match newlines.
        // The `g` flag for global matching is handled by the `while(matcher.find())` loop.
        Pattern pattern = Pattern.compile("(?s)<path\\s+[^>]*?d\\s*=\\s*\"([^\"]*)\"[^>]*?>");
        Matcher matcher = pattern.matcher(svgContent);

        StringBuilder combinedPathData = new StringBuilder();
        while (matcher.find()) {
            // Append each found 'd' attribute value, separated by a space
            // This allows TracingView's parser to treat them as sequential commands.
            combinedPathData.append(matcher.group(1)).append(" ");
        }

        if (combinedPathData.length() > 0) {
            // Remove any trailing space
            return combinedPathData.toString().trim();
        }
        return null; // No 'd' attribute found in any <path> tag
    }
}
