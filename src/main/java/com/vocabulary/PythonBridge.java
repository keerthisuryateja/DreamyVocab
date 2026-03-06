package com.vocabulary;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PythonBridge {
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Gson gson;

    /**
     * Returns the directory that contains this JAR (or the classes directory
     * in a dev build). All sibling resources (backend.exe, etc.) live here.
     */
    private static File getAppDir() {
        try {
            File loc = new File(PythonBridge.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            return loc.isFile() ? loc.getParentFile() : loc;
        } catch (URISyntaxException e) {
            return new File(".");
        }
    }

    public PythonBridge() throws IOException {
        gson = new Gson();

        File appDir = getAppDir();

        // Prefer the compiled backend executable (installed / dist build)
        File backendExe = new File(appDir, "backend.exe");
        ProcessBuilder pb;
        if (backendExe.exists()) {
            pb = new ProcessBuilder(backendExe.getAbsolutePath());
        } else {
            // Dev fallback: run bridge.py directly through the venv python
            // Use the process working dir (project root) so backend/bridge.py resolves correctly
            File projectRoot = new File(System.getProperty("user.dir"));
            String pythonCmd = "python";
            File venvPython = new File(projectRoot, "venv/Scripts/python.exe");
            if (venvPython.exists()) pythonCmd = venvPython.getAbsolutePath();
            File bridgeScript = new File(projectRoot, "backend/bridge.py");
            pb = new ProcessBuilder(pythonCmd, bridgeScript.getAbsolutePath());
            appDir = projectRoot;
        }
        pb.directory(appDir);
        process = pb.start();

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    public synchronized Map<String, Object> sendRequest(Map<String, Object> req) {
        try {
            String jsonReq = gson.toJson(req);
            writer.write(jsonReq);
            writer.newLine();
            writer.flush();

            String jsonResp = reader.readLine();
            while (jsonResp != null) {
                try {
                    return gson.fromJson(jsonResp, new TypeToken<Map<String, Object>>() {
                    }.getType());
                } catch (Exception parseEx) {
                    // Ignore non-json lines and keep reading
                    jsonResp = reader.readLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Map.of("status", "error", "message", "Bridge communication failed");
    }

    public void close() {
        if (process != null) {
            process.destroy();
        }
    }
}
