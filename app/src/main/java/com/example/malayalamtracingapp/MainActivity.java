package com.example.malayalamtracingapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log; // Import Log for debugging

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TracingView tracingView;
    private Button resetButton, prevButton, nextButton;

    // Data class to hold alphabet information
    private static class Alphabet {
        String name;
        List<String> strokes;

        Alphabet(String name, List<String> strokes) {
            this.name = name;
            this.strokes = strokes;
        }
    }

    // List to store parsed Alphabet objects
    private List<Alphabet> malayalamAlphabets;
    private int currentCharacterIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tracingView = findViewById(R.id.tracingView);
        resetButton = findViewById(R.id.resetButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);

        malayalamAlphabets = new ArrayList<>();
        loadAlphabetsFromJson(); // Load data from JSON file

        // Ensure there's at least one character to display
        if (malayalamAlphabets.isEmpty()) {
            Toast.makeText(this, "No Malayalam characters loaded from JSON! Please check res/raw/malayalam_alphabets.json", Toast.LENGTH_LONG).show();
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
                if (currentCharacterIndex < malayalamAlphabets.size() - 1) {
                    currentCharacterIndex++;
                    loadCharacter(currentCharacterIndex);
                } else {
                    Toast.makeText(MainActivity.this, "Last character reached!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Reads and parses alphabet data from res/raw/malayalam_alphabets.json.
     */
    private void loadAlphabetsFromJson() {
        InputStream inputStream = null;
        BufferedReader reader = null;
        StringBuilder jsonString = new StringBuilder();

        try {
            inputStream = getResources().openRawResource(R.raw.malayalam_alphabets);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }

            JSONArray jsonArray = new JSONArray(jsonString.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject alphabetObject = jsonArray.getJSONObject(i);
                String name = alphabetObject.getString("name");
                JSONArray strokesArray = alphabetObject.getJSONArray("strokes");

                List<String> strokes = new ArrayList<>();
                for (int j = 0; j < strokesArray.length(); j++) {
                    strokes.add(strokesArray.getString(j));
                }
                malayalamAlphabets.add(new Alphabet(name, strokes));
                Log.d(TAG, "Loaded character: " + name + " with " + strokes.size() + " strokes.");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading malayalam_alphabets.json", e);
            Toast.makeText(this, "Error loading alphabet data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing malayalam_alphabets.json", e);
            Toast.makeText(this, "Error parsing alphabet data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (reader != null) reader.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    /**
     * Loads the character at the given index into the TracingView.
     * @param index The index of the character to load from the list.
     */
    private void loadCharacter(int index) {
        if (index >= 0 && index < malayalamAlphabets.size()) {
            Alphabet currentAlphabet = malayalamAlphabets.get(index);
            tracingView.setSvgPaths(currentAlphabet.strokes); // Pass the list of strokes
            Toast.makeText(this, "Loading " + currentAlphabet.name + " (" + (index + 1) + " of " + malayalamAlphabets.size() + ")", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Displaying character: " + currentAlphabet.name);
        }
    }
}
